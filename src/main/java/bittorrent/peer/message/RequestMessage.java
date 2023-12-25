package bittorrent.peer.message;

import java.io.DataOutputStream;
import java.io.IOException;

public record RequestMessage(
	int index,
	int begin,
	int length
) implements Message {

	@Override
	public MessageType type() {
		return MessageType.REQUEST;
	}

	@Override
	public int length() {
		return 13;
	}

	@Override
	public void serialize(DataOutputStream output) throws IOException {
		output.writeInt(index);
		output.writeInt(begin);
		output.writeInt(length);
	}

}