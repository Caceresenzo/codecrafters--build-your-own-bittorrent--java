package bittorrent.bencode;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BencodeDeserializer {

	private final InputStream inputStream;

	public BencodeDeserializer(String input) {
		this(new ByteArrayInputStream(input.getBytes()));
	}

	public BencodeDeserializer(byte[] input) {
		this(new ByteArrayInputStream(input));
	}

	public BencodeDeserializer(InputStream inputStream) {
		if (!inputStream.markSupported()) {
			inputStream = new BufferedInputStream(inputStream);
		}

		this.inputStream = inputStream;
	}

	public List<Object> parseMultiple() throws IOException {
		final var objects = new ArrayList<Object>();

		int first;
		while ((first = peek()) != -1) {
			objects.add(doParse(first));
		}

		return objects;
	}

	public Object parse() throws IOException {
		return doParse(peek());
	}

	private Object doParse(final int first) throws IOException {
		if (Character.isDigit(first)) {
			return parseString();
		}

		if ('i' == first) {
			return parseNumber();
		}

		if ('l' == first) {
			return parseList();
		}

		if ('d' == first) {
			return parseMap();
		}

		throw new UnsupportedOperationException("unknown character: " + (char) first);
	}

	private String parseString() throws IOException {
		final var length = Integer.parseInt(readUntil(':'));
		final var bytes = inputStream.readNBytes(length);

		return new String(bytes, StandardCharsets.ISO_8859_1);
	}

	private long parseNumber() throws IOException {
		inputStream.read(); /* ignore i */

		return Long.parseLong(readUntil('e'));
	}

	private List<Object> parseList() throws IOException {
		inputStream.read(); /* ignore l */

		final var list = new ArrayList<Object>();

		int next;
		while ((next = peek()) != 'e' && next != -1) {
			list.add(parse());
		}

		inputStream.read(); /* ignore e */
		return list;
	}

	private Map<String, Object> parseMap() throws IOException {
		inputStream.read(); /* ignore d */

		final var map = new TreeMap<String, Object>();

		int next;
		while ((next = peek()) != 'e' && next != -1) {
			final var key = parseString();
			final var value = parse();

			map.put(key, value);
		}

		inputStream.read(); /* ignore e */
		return map;
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

}