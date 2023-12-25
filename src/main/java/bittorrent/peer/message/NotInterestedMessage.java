package bittorrent.peer.message;

import java.io.DataOutputStream;
import java.io.IOException;

public record NotInterestedMessage() implements Message {

	@Override
	public MessageType type() {
		return MessageType.NOT_INTERESTED;
	}

	@Override
	public int length() {
		return 1;
	}

	@Override
	public void serialize(DataOutputStream output) throws IOException {}

}