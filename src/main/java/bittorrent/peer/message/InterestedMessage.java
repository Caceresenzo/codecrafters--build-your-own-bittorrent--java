package bittorrent.peer.message;

import java.io.DataOutputStream;
import java.io.IOException;

public record InterestedMessage() implements Message {

	@Override
	public MessageType type() {
		return MessageType.INTERESTED;
	}

	@Override
	public int length() {
		return 1;
	}

	@Override
	public void serialize(DataOutputStream output) throws IOException {}

}