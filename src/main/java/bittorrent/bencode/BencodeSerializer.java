package bittorrent.bencode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class BencodeSerializer {

	public static final byte INTEGER_BYTE = 'i';
	public static final byte COLON_BYTE = ':';
	public static final byte END_BYTE = 'e';
	public static final byte LIST_BYTE = 'l';
	public static final byte MAP_BYTE = 'd';

	public byte[] writeAsBytes(Object root) throws IOException {
		final var byteArrayOutputStream = new ByteArrayOutputStream();
		write(root, byteArrayOutputStream);

		return byteArrayOutputStream.toByteArray();
	}

	public void write(Object root, OutputStream outputStream) throws IOException {
		if (root instanceof String string) {
			writeString(string, outputStream);
		} else if (root instanceof Long number) {
			writeNumber(number, outputStream);
		} else if (root instanceof Integer number) {
			writeNumber(Integer.toUnsignedLong(number), outputStream);
		} else if (root instanceof Short number) {
			writeNumber(Short.toUnsignedInt(number), outputStream);
		} else if (root instanceof Byte number) {
			writeNumber(Byte.toUnsignedInt(number), outputStream);
		} else if (root instanceof List<?> list) {
			writeList(list, outputStream);
		} else if (root instanceof Map<?, ?> map) {
			writeMap(map, outputStream);
		} else {
			throw new UnsupportedOperationException("unsupported type: " + root.getClass());
		}
	}

	public void writeString(String string, OutputStream outputStream) throws IOException {
		outputStream.write(String.valueOf(string.length()).getBytes());
		outputStream.write(COLON_BYTE);
		outputStream.write(string.getBytes(StandardCharsets.ISO_8859_1));
	}

	public void writeNumber(long number, OutputStream outputStream) throws IOException {
		outputStream.write(INTEGER_BYTE);
		outputStream.write(String.valueOf(number).getBytes());
		outputStream.write(END_BYTE);
	}

	public void writeList(List<?> list, OutputStream outputStream) throws IOException {
		outputStream.write(LIST_BYTE);

		for (final var element : list) {
			write(element, outputStream);
		}

		outputStream.write(END_BYTE);
	}

	public void writeMap(Map<?, ?> map, OutputStream outputStream) throws IOException {
		outputStream.write(MAP_BYTE);

		for (final var entry : map.entrySet()) {
			final var key = entry.getKey();
			final var value = entry.getValue();

			writeString((String) key, outputStream);
			write(value, outputStream);
		}

		outputStream.write(END_BYTE);
	}

}