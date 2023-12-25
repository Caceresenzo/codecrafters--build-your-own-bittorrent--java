package bittorrent.peer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import bittorrent.torrent.Torrent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Peer implements AutoCloseable {

	private static final byte[] PADDING8 = new byte[8];

	private final @Getter byte[] id;
	private final Socket socket;

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
				return new Peer(peerId, socket);
			}
		} catch (Exception exception) {
			socket.close();
			throw exception;
		}
	}

	@Override
	public void close() throws IOException {
		socket.close();
	}

}