package bittorrent;

import com.google.gson.Gson;

import bittorrent.bencode.Deserializer;

public class Main {

	private static final Gson gson = new Gson();

	public static void main(String[] args) throws Exception {
		String command = args[0];
		if ("decode".equals(command)) {
			final var encoded = args[1];
			final var decoded = new Deserializer(encoded).parse();

			System.out.println(gson.toJson(decoded));
		} else {
			System.out.println("Unknown command: " + command);
		}
	}

}