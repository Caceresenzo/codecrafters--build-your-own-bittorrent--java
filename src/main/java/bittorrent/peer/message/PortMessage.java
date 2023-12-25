package bittorrent.peer.message;

import java.io.DataOutputStream;
import java.io.IOException;

public record PortMessage(
	short port
) implements Message {

	@Override
	public MessageType type() {
		return MessageType.PORT;
	}

	@Override
	public int length() {
		return 3;
	}

	@Override
	public void serialize(DataOutputStream output) throws IOException {
		output.writeShort(port);
	}

}