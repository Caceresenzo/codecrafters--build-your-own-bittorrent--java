package bittorrent.peer.message;

import java.io.DataOutputStream;
import java.io.IOException;

public record ChokeMessage() implements Message {

	@Override
	public MessageType type() {
		return MessageType.CHOKE;
	}

	@Override
	public int length() {
		return 1;
	}

	@Override
	public void serialize(DataOutputStream output) throws IOException {}

}