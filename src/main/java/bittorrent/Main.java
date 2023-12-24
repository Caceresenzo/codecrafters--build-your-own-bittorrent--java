package bittorrent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import com.google.gson.Gson;

import bittorrent.bencode.Deserializer;
import bittorrent.bencode.Serializer;
import bittorrent.torrent.Torrent;

public class Main {
	
	public static final HexFormat HEX_FORMAT = HexFormat.of();

	public static void main(String[] args) throws Exception {
		String command = args[0];
		if ("decode".equals(command)) {
			decode(args);
		} else if ("info".equals(command)) {
			info(args);
		} else {
			System.out.println("Unknown command: " + command);
		}
	}

	private static void decode(String[] args) throws IOException {
		final var gson = new Gson();

		final var encoded = args[1];
		final var decoded = new Deserializer(encoded).parse();

		System.out.println(gson.toJson(decoded));
	}

	@SuppressWarnings("unchecked")
	private static void info(String[] args) throws IOException, NoSuchAlgorithmException {
		final var path = args[1];
		final var content = Files.readAllBytes(Paths.get(path));
		final var decoded = new Deserializer(content).parse();

		final var torrent = Torrent.of((Map<String, Object>) decoded);

		System.out.println("Tracker URL: %s".formatted(torrent.announce()));
		System.out.println("Length: %d".formatted(torrent.info().length()));
		System.out.println("Info Hash: %s".formatted(shaInfo(decoded)));
		System.out.println("Piece Length: %d".formatted(torrent.info().pieceLength()));
		System.out.println("Piece Hashes:");
		for (final var hash : torrent.info().pieces()) {
			System.out.println(HEX_FORMAT.formatHex(hash));
		}
	}

	private static String shaInfo(final Object decoded) throws IOException, NoSuchAlgorithmException {
		@SuppressWarnings("unchecked")
		final var info = ((Map<String, Object>) decoded).get("info");

		final var infoOutputStream = new ByteArrayOutputStream();
		new Serializer().write(info, infoOutputStream);

		final var digest = MessageDigest.getInstance("SHA-1").digest(infoOutputStream.toByteArray());
		return HEX_FORMAT.formatHex(digest);
	}

}