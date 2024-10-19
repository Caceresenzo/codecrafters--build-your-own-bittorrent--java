package bittorrent.peer.protocol;

import java.util.Map;

import bittorrent.torrent.TorrentInfo;

public sealed interface MetadataMessage {

	public record Handshake(
		Map<String, Integer> extensionIds
	) implements MetadataMessage {}

	public record Request(
		int piece
	) implements MetadataMessage {}

	public record Data(
		int piece,
		long totalSize,
		TorrentInfo torrentInfo
	) implements MetadataMessage {}

	public record Reject() implements MetadataMessage {}

}