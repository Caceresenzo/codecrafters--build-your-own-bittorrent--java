package bittorrent.util;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import bittorrent.bencode.BencodeSerializer;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DigestUtils {

	@SneakyThrows
	public static byte[] sha1(byte[] array) {
		return MessageDigest.getInstance("SHA-1").digest(array);
	}

	@SneakyThrows
	public static byte[] shaInfo(final Object infoRoot) {
		final var infoOutputStream = new ByteArrayOutputStream();
		new BencodeSerializer().write(infoRoot, infoOutputStream);

		return sha1(infoOutputStream.toByteArray());
	}

	@SneakyThrows
	public static String urlEncode(byte[] array) {
		return URLEncoder.encode(new String(array, StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1.name());
	}

}