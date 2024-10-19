package bittorrent.peer.serial;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import bittorrent.peer.protocol.Message;

public record MessageDescriptor<T extends Message>(
	Class<T> clazz,
	byte typeId,
	Serializer<T> serializer,
	Deserializer<T> deserializer
) {

	public int serialize(T message, DataOutputStream output, MessageSerialContext context) throws IOException {
		return serializer.serialize(message, output, context);
	}

	public T deserialize(int payloadLength, DataInputStream input, MessageSerialContext context) throws IOException {
		return deserializer.deserialize(payloadLength, input, context);
	}

	@Override
	public final String toString() {
		return "MessageDescriptor[%d, %s]".formatted(typeId, clazz.getSimpleName());
	}

	@FunctionalInterface
	public interface Serializer<T extends Message> {

		int serialize(T message, DataOutputStream output, MessageSerialContext context) throws IOException;

	}

	@FunctionalInterface
	public interface Deserializer<T extends Message> {

		T deserialize(int payloadLength, DataInputStream input, MessageSerialContext context) throws IOException;

	}

}