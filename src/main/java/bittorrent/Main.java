package bittorrent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import bittorrent.bencode.Deserializer;
import bittorrent.torrent.Torrent;

public class Main {

	private static final Gson gson = new Gson();
	private static final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

	public static void main(String[] args) throws Exception {
		String command = args[0];
		if ("decode".equals(command)) {
			final var encoded = args[1];
			final var decoded = new Deserializer(encoded).parse();

			System.out.println(gson.toJson(decoded));
		} else if ("info".equals(command)) {
			final var path = args[1];
			final var content = readFileContent(path);
			final var decoded = new Deserializer(content).parse();

			@SuppressWarnings("unchecked")
			final var torrent = Torrent.of((Map<String, Object>) decoded);

			//			System.out.println(prettyGson.toJson(decoded));

			System.out.println("Tracker URL: %s".formatted(torrent.announce()));
			System.out.println("Length: %d".formatted(torrent.info().length()));
		} else {
			System.out.println("Unknown command: " + command);
		}
	}

	static byte[] readFileContent(String path) throws FileNotFoundException, IOException {
		try (final var inputStream = new FileInputStream(new File(path))) {
			return inputStream.readAllBytes();
		}
	}

}