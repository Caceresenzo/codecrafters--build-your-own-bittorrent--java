package bittorrent.bencode;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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
		final var first = peek();

		if (Character.isDigit(first)) {
			return parseString();
		}

		if ('i' == first) {
			return parseNumber();
		}

		if ('l' == first) {
			return parseList();
		}

		throw new UnsupportedOperationException("unknown character: " + (char) first);
	}

	private String parseString() throws IOException {
		final var length = Integer.parseInt(readUntil(':'));
		final var bytes = inputStream.readNBytes(length);

		return new String(bytes);
	}

	private long parseNumber() throws IOException {
		inputStream.read(); /* ignore i */
		return Long.parseLong(readUntil('e'));
	}

	private List<Object> parseList() throws IOException {
		inputStream.read(); /* ignore l */

		final var list = new ArrayList<Object>();
		while (peek() != 'e') {
			list.add(parse());
		}

		return list;
	}

	private String readUntil(char end) throws IOException {
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

	private int peek() throws IOException {
		inputStream.mark(1);
		final var value = inputStream.read();
		inputStream.reset();
		return value;
	}

	public static void main(String[] args) throws IOException {
		System.out.println(new Deserializer("5:hello").parse());
		System.out.println(new Deserializer("i52e").parse());
	}

}