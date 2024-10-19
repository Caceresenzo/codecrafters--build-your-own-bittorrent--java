package bittorrent.peer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;

import bittorrent.Main;
import bittorrent.magnet.Magnet;
import bittorrent.peer.protocol.Message;
import bittorrent.peer.protocol.serial.MessageDescriptor;
import bittorrent.peer.protocol.serial.MessageDescriptors;
import bittorrent.torrent.Torrent;
import bittorrent.tracker.Announceable;
import bittorrent.util.DigestUtils;
import bittorrent.util.ExposedByteArrayOutputStream;
import lombok.Getter;

public class Peer implements AutoCloseable {

	private static final byte[] PROTOCOL_BYTES = "BitTorrent protocol".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] PADDING_8 = new byte[8];
	private static final byte[] PADDING_MAGNET_8 = { 0, 0, 0, 0, 0, 0x10, 0, 0 };

	private final @Getter byte[] id;
	private final Socket socket;
	private boolean supportExtensions;
	private boolean bitfield;
	private boolean interested;

	private @Getter int peerMetadataExtensionId = -1;

	public Peer(byte[] id, Socket socket, boolean supportExtensions) {
		this.id = id;
		this.socket = socket;
		this.supportExtensions = supportExtensions;
	}

	private Message doReceive() throws IOException {
		final var dataInputStream = new DataInputStream(socket.getInputStream());

		final var length = dataInputStream.readInt();
		final var typeId = length != 0 ? dataInputStream.readByte() : (byte) -1;

		final var descriptor = MessageDescriptors.getByTypeId(typeId);
		final var message = descriptor.deserialize(length - 1, dataInputStream);

		System.err.println("recv: typeId=%-2d length=%-6d message=%s".formatted(descriptor.typeId(), length, message));

		return message;
	}

	public Message receive() throws IOException {
		var message = doReceive();

		if (message instanceof Message.KeepAlive) {
			send(message);
			return receive();
		}

		return message;
	}

	public Message waitFor(Predicate<Message> predicate) throws IOException {
		while (true) {
			final var message = receive();

			if (predicate.test(message)) {
				return message;
			}

			System.err.println("discard: " + message);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void send(Message message) throws IOException {
		final var dataOutputStream = new DataOutputStream(socket.getOutputStream());

		final MessageDescriptor descriptor = MessageDescriptors.getByClass(message.getClass());

		final var byteArrayOutputStream = new ExposedByteArrayOutputStream();
		final var length = descriptor.serialize(message, new DataOutputStream(byteArrayOutputStream));

		System.err.println("send: typeId=%-2d length=%-6d message=%s".formatted(descriptor.typeId(), length, message));

		dataOutputStream.writeInt(length);
		if (length == 0) {
			return;
		}

		dataOutputStream.writeByte(descriptor.typeId());
		dataOutputStream.write(byteArrayOutputStream.getBuffer(), 0, length - 1);
	}

	@SuppressWarnings("rawtypes")
	public void awaitBitfield() throws IOException {
		if (bitfield) {
			return;
		}

		if (supportExtensions) {
			send(new Message.Extension((byte) 0, Map.of("m", Map.of("ut_metadata", 42))));

			var message = receive();

			if (message instanceof Message.Bitfield) {
				bitfield = true;
			} else if (message instanceof Message.Extension extension) {
				System.err.println("extension: %s".formatted(extension));

				final var metadata = (Map) extension.content().deserialized().get("m");
				peerMetadataExtensionId = ((Number) metadata.get("ut_metadata")).intValue();
			} else {
				throw new IllegalStateException("first message is not bitfield or extension: " + message);
			}

			message = receive();
			if (!(message instanceof Message.Bitfield)) {
				throw new IllegalStateException("second message is not bitfield: " + message);
			}
		} else {
			final var message = receive();
			if (!(message instanceof Message.Bitfield)) {
				throw new IllegalStateException("first message is not bitfield: " + message);
			}
		}

		bitfield = true;
	}

	public byte[] downloadPiece(Torrent torrent, int pieceIndex) throws IOException, InterruptedException {
		awaitBitfield();
		sendInterested();

		final var fileLength = torrent.info().length();
		final var pieceLength = torrent.info().pieceLength();

		var realPieceLength = pieceLength;
		if (torrent.info().pieces().size() - 1 == pieceIndex) {
			realPieceLength = (int) (fileLength % pieceLength);
		}

		final var pieceHash = torrent.info().pieces().get(pieceIndex);

		final var bytes = new byte[realPieceLength];

		final var blockSize = (int) Math.pow(2, 14);
		var blockCount = 0;

		var blockStart = 0;
		for (; blockStart < realPieceLength - blockSize; blockStart += blockSize) {
			++blockCount;

			send(new Message.Request(
				pieceIndex,
				blockStart,
				blockSize
			));
		}

		final var remaining = realPieceLength - blockStart;
		if (remaining != 0) {
			++blockCount;

			send(new Message.Request(
				pieceIndex,
				blockStart,
				remaining
			));
		}

		for (var index = 0; index < blockCount; ++index) {
			final var pieceMessage = (Message.Piece) waitFor((message) -> message instanceof Message.Piece);

			System.arraycopy(pieceMessage.block(), 0, bytes, pieceMessage.begin(), pieceMessage.block().length);
		}

		final var downloadedPieceHash = DigestUtils.sha1(bytes);
		if (!Arrays.equals(pieceHash, downloadedPieceHash)) {
			throw new IllegalStateException("piece hash does not match");
		}

		return bytes;
	}

	public void sendInterested() throws IOException, InterruptedException {
		if (interested) {
			return;
		}

		while (true) {
			send(new Message.Interested());

			final var choke = waitFor((message) -> message instanceof Message.Unchoke || message instanceof Message.Choke);
			if (choke instanceof Message.Unchoke) {
				interested = true;
				break;
			}

			System.err.println("peer is chocked");
			Thread.sleep(Duration.ofSeconds(1));
		}
	}

	@Override
	public void close() throws IOException, InterruptedException {
		socket.close();
	}

	public static Peer connect(InetSocketAddress address, Announceable announceable) throws IOException {
		System.err.println("peer: trying to connect: %s".formatted(address));

		final var socket = new Socket(address.getAddress(), address.getPort());
		return connect(socket, announceable);
	}

	public static Peer connect(Socket socket, Announceable announceable) throws IOException {
		final var infoHash = announceable.getInfoHash();
		final var padding = announceable instanceof Magnet ? PADDING_MAGNET_8 : PADDING_8;

		try {
			final var inputStream = new DataInputStream(socket.getInputStream());
			final var outputStream = socket.getOutputStream();

			{
				/* length of the protocol string */
				outputStream.write(19);

				/* the string BitTorrent protocol */
				outputStream.write(PROTOCOL_BYTES);

				/* eight reserved bytes */
				outputStream.write(padding);

				/* sha1 infohash */
				outputStream.write(announceable.getInfoHash());

				/* peer id */
				outputStream.write("42112233445566778899".getBytes(StandardCharsets.US_ASCII));
			}

			{
				final var length = inputStream.readByte();
				if (length != 19) {
					throw new IllegalStateException("invalid protocol length: " + length);
				}

				final var receivedProtocol = inputStream.readNBytes(19);
				if (!Arrays.equals(receivedProtocol, PROTOCOL_BYTES)) {
					System.out.println(Main.HEX_FORMAT.formatHex(receivedProtocol));
					throw new IllegalStateException("invalid protocol string: " + new String(receivedProtocol));
				}

				/* padding */
				final var receivedPadding = inputStream.readNBytes(8);
				final var supportExtensions = receivedPadding[5] == 0x10; // TODO Bugged https://forum.codecrafters.io/t/pk2-reserved-bit-in-handshake-for-extension-protocol-seems-to-be-set-incorrectly-by-codecrafters-server/2461
				//				final var supportExtensions = announceable instanceof Magnet;
				System.err.println("peer: padding: %s".formatted(Main.HEX_FORMAT.formatHex(receivedPadding)));

				final var receivedInfoHash = inputStream.readNBytes(20);
				if (!Arrays.equals(receivedInfoHash, infoHash)) {
					throw new IllegalStateException("invalid info hash: " + Arrays.toString(infoHash));
				}

				final var peerId = inputStream.readNBytes(20);
				return new Peer(peerId, socket, supportExtensions);
			}
		} catch (Exception exception) {
			socket.close();
			throw exception;
		}
	}

}