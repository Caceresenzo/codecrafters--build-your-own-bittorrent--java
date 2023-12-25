package bittorrent.peer.message;

import java.io.DataOutputStream;
import java.io.IOException;

public record BitfieldMessage(
	byte[] values
) implements Message {

	@Override
	public MessageType type() {
		return MessageType.BITFIELD;
	}

	@Override
	public int length() {
		return 1 + values.length;
	}

	@Override
	public void serialize(DataOutputStream output) throws IOException {
		output.write(values);
	}

}