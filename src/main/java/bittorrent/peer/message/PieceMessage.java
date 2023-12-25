package bittorrent.peer.message;

import java.io.DataOutputStream;
import java.io.IOException;

public record PieceMessage(
	int index,
	int begin,
	byte[] block
) implements Message {

	@Override
	public MessageType type() {
		return MessageType.PIECE;
	}

	@Override
	public int length() {
		return 9 + block.length;
	}

	@Override
	public void serialize(DataOutputStream output) throws IOException {
		output.writeInt(index);
		output.writeInt(begin);
		output.write(block);
	}

}