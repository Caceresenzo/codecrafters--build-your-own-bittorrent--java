package bittorrent.peer.protocol.serial;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import bittorrent.peer.protocol.Message;

public record MessageDescriptor<T extends Message>(
	Class<T> clazz,
	byte typeId,
	MessageSerializer<T> serializer,
	MessageDeserializer<T> deserializer
) {

	public int serialize(T message, DataOutputStream output) throws IOException {
		return serializer.serialize(message, output);
	}

	public T deserialize(int payloadLength, DataInputStream input) throws IOException {
		return deserializer.deserialize(payloadLength, input);
	}

}