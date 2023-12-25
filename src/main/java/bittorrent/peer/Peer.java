package bittorrent.peer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Predicate;

import bittorrent.peer.message.BitfieldMessage;
import bittorrent.peer.message.ChokeMessage;
import bittorrent.peer.message.InterestedMessage;
import bittorrent.peer.message.KeepAliveMessage;
import bittorrent.peer.message.Message;
import bittorrent.peer.message.MessageType;
import bittorrent.peer.message.PieceMessage;
import bittorrent.peer.message.RequestMessage;
import bittorrent.peer.message.UnchokeMessage;
import bittorrent.torrent.Torrent;
import bittorrent.util.DigestUtils;
import lombok.Getter;

public class Peer implements AutoCloseable {

	public static boolean DEBUG = true;
	private static final byte[] PADDING8 = new byte[8];

	private final @Getter byte[] id;
	private final Torrent torrent;
	private final Socket socket;
	private boolean bitfield;
	private boolean interested;

	public Peer(byte[] id, Torrent torrent, Socket socket) throws IOException {
		this.id = id;
		this.torrent = torrent;
		this.socket = socket;
	}

	private Message doReceive() throws IOException {
		final var inputStream = socket.getInputStream();
		final var dataInputStream = new DataInputStream(inputStream);

		final var length = dataInputStream.readInt();
		if (length == 0) {
			return new KeepAliveMessage();
		}

		final var typeId = (int) dataInputStream.readByte();
		final var messageType = MessageType.valueOf(typeId);

		final var payloadLength = length - 1;
		final var message = messageType.getDeserializer().deserialize(payloadLength, dataInputStream);

		System.err.println("recv: type=%s length=%d message=%s".formatted(messageType, length, message));

		return message;
	}

	public Message receive() throws IOException {
		var message = doReceive();

		if (message instanceof KeepAliveMessage) {
			send(message);
			return receive();
		}

		return message;
	}

	public Message waitFor(Predicate<Message> predicate) throws InterruptedException, IOException {
		while (true) {
			final var message = receive();

			if (predicate.test(message)) {
				return message;
			}

			System.err.println("discard: " + message);
		}
	}

	public void send(Message message) throws IOException {
		final var outputStream = socket.getOutputStream();
		final var dataOutputStream = new DataOutputStream(outputStream);

		final var length = message.length();
		dataOutputStream.writeInt(length);

		if (length == 0) {
			return;
		}

		final var messageType = message.type();
		final var typeId = (byte) messageType.ordinal();
		dataOutputStream.writeByte(typeId);

		System.err.println("send: type=%s length=%d message=%s".formatted(messageType, length, message));

		message.serialize(dataOutputStream);
	}
	
	public void awaitBitfield() throws IOException {
		if (bitfield) {
			return;
		}
		
		final var message = receive();
		if (!(message instanceof BitfieldMessage)) {
			throw new IllegalStateException("first message is not bitfield: " + message);
		}
		
		bitfield = true;
	}

	public byte[] downloadPiece(int pieceIndex) throws IOException, InterruptedException {
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

			send(new RequestMessage(
				pieceIndex,
				blockStart,
				blockSize
			));
		}

		final var remaining = realPieceLength - blockStart;
		if (remaining != 0) {
			++blockCount;

			send(new RequestMessage(
				pieceIndex,
				blockStart,
				remaining
			));
		}

		for (var index = 0; index < blockCount; ++index) {
			final var pieceMessage = (PieceMessage) waitFor((message) -> message instanceof PieceMessage);

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
			send(new InterestedMessage());

			final var choke = waitFor((message) -> message instanceof UnchokeMessage || message instanceof ChokeMessage);
			if (choke instanceof UnchokeMessage) {
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

	public static Peer connect(InetSocketAddress address, Torrent torrent) throws IOException {
		final var socket = new Socket(address.getAddress(), address.getPort());

		return connect(socket, torrent);
	}

	public static Peer connect(Socket socket, Torrent torrent) throws IOException {
		try {
			final var inputStream = socket.getInputStream();
			final var outputStream = socket.getOutputStream();

			{
				/* length of the protocol string */
				outputStream.write(19);

				/* the string BitTorrent protocol */
				outputStream.write("BitTorrent protocol".getBytes(StandardCharsets.US_ASCII));

				/* eight reserved bytes, which are all set to zero */
				outputStream.write(PADDING8);

				/* sha1 infohash */
				outputStream.write(torrent.info().hash());

				/* peer id */
				outputStream.write("00112233445566778899".getBytes(StandardCharsets.US_ASCII));
			}

			{
				final var length = inputStream.read();
				if (length != 19) {
					throw new IllegalStateException("invalid protocol length: " + length);
				}

				final var protocolString = new String(inputStream.readNBytes(19), StandardCharsets.US_ASCII);
				if (!"BitTorrent protocol".equals(protocolString)) {
					throw new IllegalStateException("invalid protocol string: " + protocolString);
				}

				/* padding */
				inputStream.readNBytes(8);

				final var infoHash = inputStream.readNBytes(20);
				if (!Arrays.equals(infoHash, torrent.info().hash())) {
					throw new IllegalStateException("invalid info hash: " + Arrays.toString(infoHash));
				}

				final var peerId = inputStream.readNBytes(20);
				return new Peer(peerId, torrent, socket);
			}
		} catch (Exception exception) {
			socket.close();
			throw exception;
		}
	}

}