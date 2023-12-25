package bittorrent.peer.message;

import java.io.DataOutputStream;
import java.io.IOException;

public record KeepAliveMessage() implements Message {

	@Override
	public MessageType type() {
		return null;
	}

	@Override
	public int length() {
		return 0;
	}

	@Override
	public void serialize(DataOutputStream output) throws IOException {}

}