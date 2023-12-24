package bittorrent;

import java.io.ByteArrayOutputStream;
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

}