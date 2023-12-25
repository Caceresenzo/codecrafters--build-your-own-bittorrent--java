package bittorrent.peer.message;

import java.io.DataInputStream;
import java.io.IOException;

@FunctionalInterface
public interface MessageDeserializer {

	Message deserialize(int payloadLength, DataInputStream input) throws IOException;

	static MessageDeserializer stateless(Message message) {
		return (payloadLength, input) -> message;
	}
	
}