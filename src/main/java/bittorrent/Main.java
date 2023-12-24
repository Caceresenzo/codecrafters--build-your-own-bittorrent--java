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

	private static final Gson gson = new Gson();
	//	private static final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		String command = args[0];
		if ("decode".equals(command)) {
			final var encoded = args[1];
			final var decoded = new Deserializer(encoded).parse();

			System.out.println(gson.toJson(decoded));
		} else if ("info".equals(command)) {
			final var path = args[1];
			final var content = Files.readAllBytes(Paths.get(path));
			final var decoded = new Deserializer(content).parse();

			@SuppressWarnings("unchecked")
			final var torrent = Torrent.of((Map<String, Object>) decoded);

			//			System.out.println(prettyGson.toJson(decoded));

			System.out.println("Tracker URL: %s".formatted(torrent.announce()));
			System.out.println("Length: %d".formatted(torrent.info().length()));
			System.out.println("Info Hash: %s".formatted(shaInfo(decoded)));
		} else {
			System.out.println("Unknown command: " + command);
		}
	}

	private static String shaInfo(final Object decoded) throws IOException, NoSuchAlgorithmException {
		@SuppressWarnings("unchecked")
		final var info = ((Map<String, Object>) decoded).get("info");

		final var infoOutputStream = new ByteArrayOutputStream();
		new Serializer().write(info, infoOutputStream);

		final var digest = MessageDigest.getInstance("SHA-1").digest(infoOutputStream.toByteArray());
		return HexFormat.of().formatHex(digest);
	}

}