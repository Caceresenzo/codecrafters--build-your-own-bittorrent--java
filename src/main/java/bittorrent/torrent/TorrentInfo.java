package bittorrent.torrent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record TorrentInfo(
	long length,
	String name,
	long pieceLength,
	List<byte[]> pieces
) {

	public static TorrentInfo of(Map<String, Object> root) {
		final var length = (long) root.getOrDefault("length", -1l);
		final var name = (String) root.get("name");
		final var pieceLength = (long) root.get("piece length");

		final var hashes = ((String) root.get("pieces")).getBytes(StandardCharsets.ISO_8859_1);
		final var pieces = new ArrayList<byte[]>();
		for (var start = 0; start < hashes.length; start += 20) {
			final var piece = Arrays.copyOfRange(hashes, start, start + 20);
			pieces.add(piece);
		}

		return new TorrentInfo(length, name, pieceLength, pieces);
	}

}