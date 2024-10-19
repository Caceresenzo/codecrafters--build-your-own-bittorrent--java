package bittorrent.peer.protocol;

import java.util.Map;

import bittorrent.bencode.BEncoded;

public sealed interface Message {

	public record KeepAlive() implements Message {}

	public record Choke() implements Message {}

	public record Unchoke() implements Message {}

	public record Interested() implements Message {}

	public record NotInterested() implements Message {}

	public record Have(
		int pieceIndex
	) implements Message {}

	public record Bitfield(
		byte[] values
	) implements Message {}

	public record Request(
		int index,
		int begin,
		int length
	) implements Message {}

	public record Piece(
		int index,
		int begin,
		byte[] block
	) implements Message {}

	public record Cancel(
		int index,
		int begin,
		int length
	) implements Message {}

	public record Port(
		short port
	) implements Message {}

	public record Extension(
		BEncoded<Map<String, Object>> body
	) implements Message {}

}