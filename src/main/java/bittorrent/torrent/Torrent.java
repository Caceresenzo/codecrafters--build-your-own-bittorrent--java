package bittorrent.torrent;

import java.util.Map;

import bittorrent.tracker.Announceable;

public record Torrent(
	String announce,
	TorrentInfo info
) implements Announceable {

	@Override
	public String getTrackerUrl() {
		return announce;
	}

	@Override
	public byte[] getInfoHash() {
		return info.hash();
	}

	@Override
	public long getInfoLength() {
		return info.length();
	}

	@SuppressWarnings("unchecked")
	public static Torrent of(Map<String, Object> root) {
		final var announce = (String) root.get("announce");
		final var info = TorrentInfo.of((Map<String, Object>) root.get("info"));

		return new Torrent(announce, info);
	}

}