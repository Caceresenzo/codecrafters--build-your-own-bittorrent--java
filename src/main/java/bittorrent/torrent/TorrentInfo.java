package bittorrent.torrent;

import java.util.Map;

public record TorrentInfo(
	long length,
	String name,
	long pieceLength,
	String pieces
) {

	public static TorrentInfo of(Map<String, Object> root) {
		final var length = (long) root.getOrDefault("length", -1l);
		final var name = (String) root.get("name");
		final var pieceLength = (long) root.get("piece length");
		final var pieces = (String) root.get("pieces");

		return new TorrentInfo(length, name, pieceLength, pieces);
	}

}