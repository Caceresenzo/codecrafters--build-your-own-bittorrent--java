package bittorrent;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;

import com.google.gson.Gson;

import bittorrent.bencode.Deserializer;
import bittorrent.torrent.Torrent;
import bittorrent.tracker.TrackerClient;
import okhttp3.OkHttpClient;

public class Main {

	public static final byte[] PADDING8 = new byte[8];

	public static final HexFormat HEX_FORMAT = HexFormat.of();
	public static final OkHttpClient CLIENT = new OkHttpClient();

	public static void main(String[] args) throws Exception {
		final var command = args[0];

		switch (command) {
			case "decode" -> decode(args[1]);
			case "info" -> info(args[1]);
			case "peers" -> peers(args[1]);
			case "handshake" -> handshake(args[1], args[2]);
			default -> System.out.println("Unknown command: " + command);
		}
	}

	private static void decode(String encoded) throws IOException {
		final var gson = new Gson();

		final var decoded = new Deserializer(encoded).parse();

		System.out.println(gson.toJson(decoded));
	}

	private static void info(String path) throws IOException {
		final var torrent = load(path);
		final var info = torrent.info();

		System.out.println("Tracker URL: %s".formatted(torrent.announce()));
		System.out.println("Length: %d".formatted(info.length()));
		System.out.println("Info Hash: %s".formatted(HEX_FORMAT.formatHex(info.hash())));
		System.out.println("Piece Length: %d".formatted(info.pieceLength()));
		System.out.println("Piece Hashes:");
		for (final var hash : info.pieces()) {
			System.out.println(HEX_FORMAT.formatHex(hash));
		}
	}

	private static void peers(String path) throws IOException {
		final var torrent = load(path);

		final var trackerClient = new TrackerClient();
		final var response = trackerClient.announce(torrent);

		for (final var peer : response.peers()) {
			final var line = "%s:%d".formatted(peer.getAddress().getHostAddress(), peer.getPort());

			System.out.println(line);
		}
	}

	private static void handshake(String path, String peerIpAndPort) throws IOException {
		final var torrent = load(path);

		final var parts = peerIpAndPort.split(":", 2);

		try (
			final var socket = new Socket(parts[0], Integer.parseInt(parts[1]));
			final var inputStream = socket.getInputStream();
			final var outputStream = socket.getOutputStream();
		) {
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

				System.out.println("Peer ID: %s".formatted(HEX_FORMAT.formatHex(peerId)));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static Torrent load(String path) throws IOException {
		final var content = Files.readAllBytes(Paths.get(path));
		final var decoded = new Deserializer(content).parse();

		return Torrent.of((Map<String, Object>) decoded);
	}

}