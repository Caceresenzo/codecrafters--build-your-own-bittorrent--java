package bittorrent.torrent;

import java.util.Map;

public record Torrent(
	String announce,
	TorrentInfo info
) {

	@SuppressWarnings("unchecked")
	public static Torrent of(Map<String, Object> root) {
		final var announce = new String((byte[]) root.get("announce"));
		final var info = TorrentInfo.of((Map<String, Object>) root.get("info"));

		return new Torrent(announce, info);
	}

}