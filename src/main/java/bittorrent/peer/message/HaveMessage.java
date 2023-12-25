package bittorrent.peer.message;

import java.io.DataOutputStream;
import java.io.IOException;

public record HaveMessage(
	int pieceIndex
) implements Message {

	@Override
	public MessageType type() {
		return MessageType.HAVE;
	}

	@Override
	public int length() {
		return 5;
	}

	@Override
	public void serialize(DataOutputStream output) throws IOException {
		output.writeInt(pieceIndex);
	}

}