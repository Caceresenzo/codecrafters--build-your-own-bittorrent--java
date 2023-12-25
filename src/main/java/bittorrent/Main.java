package bittorrent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HexFormat;
import java.util.Map;

import com.google.gson.Gson;

import bittorrent.bencode.Deserializer;
import bittorrent.torrent.Torrent;
import bittorrent.tracker.TrackerClient;
import okhttp3.OkHttpClient;

public class Main {

	public static final HexFormat HEX_FORMAT = HexFormat.of();
	public static final OkHttpClient CLIENT = new OkHttpClient();

	public static void main(String[] args) throws Exception {
		final var command = args[0];
		final var argument = args[1];

		switch (command) {
			case "decode" -> decode(argument);
			case "info" -> info(argument);
			case "peers" -> peers(argument);
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

	@SuppressWarnings("unchecked")
	private static Torrent load(String path) throws IOException {
		final var content = Files.readAllBytes(Paths.get(path));
		final var decoded = new Deserializer(content).parse();

		return Torrent.of((Map<String, Object>) decoded);
	}

}