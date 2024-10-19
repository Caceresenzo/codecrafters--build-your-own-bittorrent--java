package bittorrent.peer.protocol;

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
	) implements Message {

		@Override
		public final String toString() {
			return "Bitfield[values.length=%d]".formatted(values.length);
		}

	}

	public record Request(
		int index,
		int begin,
		int length
	) implements Message {}

	public record Piece(
		int index,
		int begin,
		byte[] block
	) implements Message {

		@Override
		public final String toString() {
			return "Piece[index=%d, begin=%d, block.length=%d]".formatted(index, begin, block.length);
		}

	}

	public record Cancel(
		int index,
		int begin,
		int length
	) implements Message {}

	public record Port(
		short port
	) implements Message {}

	public record Extension(
		byte id,
		Object content
	) implements Message {}

}