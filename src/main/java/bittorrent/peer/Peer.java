package bittorrent.peer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import bittorrent.Main;
import bittorrent.magnet.Magnet;
import bittorrent.peer.protocol.Message;
import bittorrent.peer.protocol.MetadataMessage;
import bittorrent.peer.serial.MessageDescriptor;
import bittorrent.peer.serial.MessageDescriptors;
import bittorrent.peer.serial.MessageSerialContext;
import bittorrent.torrent.TorrentInfo;
import bittorrent.tracker.Announceable;
import bittorrent.util.DigestUtils;
import bittorrent.util.ExposedByteArrayOutputStream;
import lombok.Getter;

public class Peer implements AutoCloseable {

	private static final byte[] PROTOCOL_BYTES = "BitTorrent protocol".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] PADDING_8 = new byte[8];
	private static final byte[] PADDING_MAGNET_8 = { 0, 0, 0, 0, 0, 0x10, 0, 0 };

	private static final MessageSerialContext METADATA_CONTEXT = new MessageSerialContext(MetadataMessage.class);

	private final @Getter byte[] id;
	private final Socket socket;
	private final boolean supportExtensions;

	private boolean bitfield;
	private boolean interested;
	private @Getter int metadataExtensionId = -1;

	private List<Message> receiveQueue;

	public Peer(byte[] id, Socket socket, boolean supportExtensions) {
		this.id = id;
		this.socket = socket;
		this.supportExtensions = supportExtensions;

		this.receiveQueue = new LinkedList<>();
	}

	private Message doReceive(MessageSerialContext context) throws IOException {
		final var dataInputStream = new DataInputStream(socket.getInputStream());

		final int length;
		try {
			length = dataInputStream.readInt();
		} catch (EOFException exception) {
			throw new PeerClosedException(exception);
		}

		final var typeId = length != 0 ? dataInputStream.readByte() : (byte) -1;

		final var descriptor = MessageDescriptors.getByTypeId(typeId);
		final var message = descriptor.deserialize(length - 1, dataInputStream, context);

		System.err.println("recv: typeId=%-2d length=%-6d message=%s".formatted(descriptor.typeId(), length, message));

		return message;
	}

	public Message receive(boolean lookAtQueue, MessageSerialContext context) throws IOException {
		if (lookAtQueue && !receiveQueue.isEmpty()) {
			final var message = receiveQueue.removeFirst();

			System.err.println("queue recv: message=%s".formatted(message));

			return message;
		}

		var message = doReceive(context);

		if (message instanceof Message.KeepAlive) {
			send(message, context);
			return receive(lookAtQueue, context);
		}

		return message;
	}

	public Message waitFor(Predicate<Message> predicate, MessageSerialContext context) throws IOException {
		final var iterator = receiveQueue.listIterator();
		while (iterator.hasNext()) {
			final var message = iterator.next();

			if (predicate.test(message)) {
				System.err.println("wait for: found: message=%s".formatted(message));

				iterator.remove();
				return message;
			}
		}

		while (true) {
			final var message = receive(false, context);

			if (predicate.test(message)) {
				return message;
			}

			System.err.println("wait for: push: message=%s".formatted(message));
			receiveQueue.add(message);
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends Message> T waitFor(Class<T> clazz, MessageSerialContext context) throws IOException {
		return (T) waitFor((message) -> clazz.equals(message.getClass()), context);
	}

	public void send(Message message) throws IOException {
		send(message, null);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void send(Message message, MessageSerialContext context) throws IOException {
		final var dataOutputStream = new DataOutputStream(socket.getOutputStream());

		final MessageDescriptor descriptor = MessageDescriptors.getByClass(message.getClass());

		final var byteArrayOutputStream = new ExposedByteArrayOutputStream();
		final var length = descriptor.serialize(message, new DataOutputStream(byteArrayOutputStream), context);

		System.err.println("send: typeId=%-2d length=%-6d message=%s".formatted(descriptor.typeId(), length, message));

		dataOutputStream.writeInt(length);
		if (length == 0) {
			return;
		}

		dataOutputStream.writeByte(descriptor.typeId());
		dataOutputStream.write(byteArrayOutputStream.getBuffer(), 0, length - 1);
	}

	public void awaitBitfield() throws IOException {
		if (bitfield) {
			return;
		}

		if (supportExtensions) {
			send(
				new Message.Extension(
					(byte) 0,
					new MetadataMessage.Handshake(Map.of(
						"ut_metadata", 42
					))
				),
				METADATA_CONTEXT
			);

			final var extension = waitFor(Message.Extension.class, METADATA_CONTEXT);
			System.err.println("extension: %s".formatted(extension));

			final var metadata = (MetadataMessage.Handshake) extension.content();
			metadataExtensionId = metadata.extensionIds().get("ut_metadata");
		}

		waitFor(Message.Bitfield.class, null);
		bitfield = true;
	}

	public byte[] downloadPiece(TorrentInfo torrentInfo, int pieceIndex) throws IOException, InterruptedException {
		awaitBitfield();
		sendInterested();

		final var fileLength = torrentInfo.length();
		final var pieceLength = torrentInfo.pieceLength();

		var realPieceLength = pieceLength;
		if (torrentInfo.pieces().size() - 1 == pieceIndex) {
			realPieceLength = (int) (fileLength % pieceLength);
		}

		final var pieceHash = torrentInfo.pieces().get(pieceIndex);

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
			final var piece = waitFor(Message.Piece.class, null);

			System.arraycopy(piece.block(), 0, bytes, piece.begin(), piece.block().length);
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

			final var choke = waitFor((message) -> message instanceof Message.Unchoke || message instanceof Message.Choke, null);
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

	public MetadataMessage sendMetadata(MetadataMessage message) throws IOException {
		send(
			new Message.Extension(
				(byte) metadataExtensionId,
				message
			),
			METADATA_CONTEXT
		);

		return (MetadataMessage) waitFor(
			Message.Extension.class,
			METADATA_CONTEXT
		).content();
	}

	public TorrentInfo queryTorrentInfoViaMetadataExtension() throws IOException {
		awaitBitfield();

		final var response = sendMetadata(new MetadataMessage.Request(0));
		if (!(response instanceof MetadataMessage.Data data)) {
			throw new IllegalStateException("no data found: %s".formatted(response));
		}

		return data.torrentInfo();
	}

}