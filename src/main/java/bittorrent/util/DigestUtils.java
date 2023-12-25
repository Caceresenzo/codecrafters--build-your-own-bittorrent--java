package bittorrent.util;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import bittorrent.bencode.Serializer;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DigestUtils {

	@SneakyThrows
	public static byte[] shaInfo(final Object infoRoot) {
		final var infoOutputStream = new ByteArrayOutputStream();
		new Serializer().write(infoRoot, infoOutputStream);

		final var digest = MessageDigest.getInstance("SHA-1").digest(infoOutputStream.toByteArray());
		return digest;
	}

	@SneakyThrows
	public static String urlEncode(byte[] array) {
		return URLEncoder.encode(new String(array, StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1.name());
	}

}