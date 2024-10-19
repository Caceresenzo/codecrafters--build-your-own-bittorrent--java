package bittorrent.magnet;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.http.client.utils.URLEncodedUtils;

import bittorrent.Main;
import bittorrent.tracker.Announceable;

public record Magnet(
	byte[] hash,
	String displayName,
	String announce
) implements Announceable {

	public static final String SCHEME = "magnet:";
	public static final String HASH_PREFIX = "urn:btih:";

	public Magnet {
		Objects.requireNonNull(hash, "hash");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(announce, "announce");
	}

	public Magnet(String exactTopic, String displayName, String announce) {
		this(
			Main.HEX_FORMAT.parseHex(Objects.requireNonNull(exactTopic, "exactTopic")),
			displayName,
			announce
		);
	}

	@Override
	public String getTrackerUrl() {
		return announce;
	}

	@Override
	public byte[] getInfoHash() {
		return hash;
	}

	@Override
	public long getInfoLength() {
		return 1;
	}

	public static Magnet parse(String link) {
		if (!link.startsWith(SCHEME)) {
			throw new IllegalArgumentException("must start with %s scheme: %s".formatted(SCHEME, link));
		}

		final var query = link.substring(SCHEME.length() + 1);

		String exactTopic = null;
		String displayName = null;
		String addressTracker = null;

		for (final var pair : URLEncodedUtils.parse(query, StandardCharsets.UTF_8)) {
			final var key = pair.getName();
			final var value = pair.getValue();

			switch (key) {
				case "xt" -> exactTopic = value.substring(HASH_PREFIX.length());
				case "dn" -> displayName = value;
				case "tr" -> addressTracker = value;
				default -> System.err.println("unknown parameter: %s=%s".formatted(key, value));
			}
		}

		return new Magnet(exactTopic, displayName, addressTracker);
	}

}