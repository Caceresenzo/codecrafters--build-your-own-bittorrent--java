package bittorrent.bencode;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class Deserializer {

	private final InputStream inputStream;

	public Deserializer(String input) {
		this(new ByteArrayInputStream(input.getBytes()));
	}

	public Deserializer(InputStream inputStream) {
		if (!inputStream.markSupported()) {
			inputStream = new BufferedInputStream(inputStream);
		}

		this.inputStream = inputStream;
	}

	public Object parse() throws IOException {
		inputStream.mark(1);
		final var first = inputStream.read();

		if (Character.isDigit(first)) {
			inputStream.reset();
			return parseString();
		}

		if ('i' == first) {
			return parseNumber();
		}

		return first;
	}

	private String parseString() throws IOException {
		final var length = Integer.parseInt(readUntil(':'));
		final var bytes = inputStream.readNBytes(length);

		return new String(bytes);
	}

	private long parseNumber() throws IOException {
		return Long.parseLong(readUntil('e'));
	}

	private String readUntil(char end) throws IOException {
		inputStream.mark(1);

		final var builder = new StringBuilder();

		int value;
		while ((value = inputStream.read()) != -1) {
			if (end == value) {
				break;
			}

			builder.append((char) value);
		}

		return builder.toString();
	}

	public static void main(String[] args) throws IOException {
		System.out.println(new Deserializer("5:hello").parse());
		System.out.println(new Deserializer("i52e").parse());
	}

}