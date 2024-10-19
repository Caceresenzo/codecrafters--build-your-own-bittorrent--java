package bittorrent.peer.protocol.serial;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import bittorrent.peer.protocol.Message;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MessageDescriptors {

	private static final Map<Class<?>, MessageDescriptor<?>> CLASS_TO_DESCRIPTOR = new HashMap<>();
	private static final Map<Byte, MessageDescriptor<?>> TYPE_ID_TO_DESCRIPTOR = new HashMap<>();

	@SuppressWarnings("unchecked")
	public static <T extends Message> MessageDescriptor<T> getByClass(Class<T> clazz) {
		final var descriptor = CLASS_TO_DESCRIPTOR.get(clazz);

		if (descriptor == null) {
			throw new IllegalArgumentException("unknown or unregistered message class: %s".formatted(clazz));
		}

		return (MessageDescriptor<T>) descriptor;
	}

	public static MessageDescriptor<?> getByTypeId(byte typeId) {
		final var descriptor = TYPE_ID_TO_DESCRIPTOR.get(typeId);

		if (descriptor == null) {
			throw new IllegalArgumentException("unknown or unregistered message type id: %s".formatted(typeId));
		}

		return descriptor;
	}

	private static <T extends Message> MessageDescriptor<T> register(
		Class<T> clazz,
		byte typeId,
		MessageSerializer<T> serializer,
		MessageDeserializer<T> deserializer
	) {
		return register(new MessageDescriptor<>(
			clazz,
			typeId,
			serializer,
			deserializer
		));
	}

	private static <T extends Message> MessageDescriptor<T> register(
		Class<T> clazz,
		Byte typeId,
		Supplier<T> creator
	) {
		final var instance = creator.get();
		final var length = typeId != null ? 1 : 0;

		return register(new MessageDescriptor<>(
			clazz,
			typeId,
			(message, output) -> length,
			(payloadLength, input) -> instance
		));
	}

	private static <T extends Message> MessageDescriptor<T> register(MessageDescriptor<T> descriptor) {
		CLASS_TO_DESCRIPTOR.put(descriptor.clazz(), descriptor);
		TYPE_ID_TO_DESCRIPTOR.put(descriptor.typeId(), descriptor);

		return descriptor;
	}

	public static final MessageDescriptor<Message.KeepAlive> KEEP_ALIVE = register(
		Message.KeepAlive.class,
		(byte) -1,
		Message.KeepAlive::new
	);

	public static final MessageDescriptor<Message.Choke> CHOKE = register(
		Message.Choke.class,
		(byte) 0,
		Message.Choke::new
	);

	public static final MessageDescriptor<Message.Unchoke> UNCHOKE = register(
		Message.Unchoke.class,
		(byte) 1,
		Message.Unchoke::new
	);

	public static final MessageDescriptor<Message.Interested> INTERESTED = register(
		Message.Interested.class,
		(byte) 2,
		Message.Interested::new
	);

	public static final MessageDescriptor<Message.NotInterested> NOT_INTERESTED = register(
		Message.NotInterested.class,
		(byte) 3,
		Message.NotInterested::new
	);

	public static final MessageDescriptor<Message.Have> HAVE = register(
		Message.Have.class,
		(byte) 4,
		(message, output) -> {
			output.writeInt(message.pieceIndex());

			return 1 + 4;
		},
		(payloadLength, input) -> new Message.Have(
			input.readInt()
		)
	);

	public static final MessageDescriptor<Message.Bitfield> BITFIELD = register(
		Message.Bitfield.class,
		(byte) 5,
		(message, output) -> {
			final var values = message.values();

			output.write(values);

			return 1 + values.length;
		},
		(payloadLength, input) -> new Message.Bitfield(
			input.readNBytes(payloadLength)
		)
	);

	public static final MessageDescriptor<Message.Request> REQUEST = register(
		Message.Request.class,
		(byte) 6,
		(message, output) -> {
			output.writeInt(message.index());
			output.writeInt(message.begin());
			output.writeInt(message.length());

			return 1 + 4 + 4 + 4;
		},
		(payloadLength, input) -> new Message.Request(
			input.readInt(),
			input.readInt(),
			input.readInt()
		)
	);

	public static final MessageDescriptor<Message.Piece> PIECE = register(
		Message.Piece.class,
		(byte) 7,
		(message, output) -> {
			final var block = message.block();

			output.writeInt(message.index());
			output.writeInt(message.begin());
			output.write(block);

			return 1 + 4 + 4 + block.length;
		},
		(payloadLength, input) -> new Message.Piece(
			input.readInt(),
			input.readInt(),
			input.readNBytes(payloadLength - 8)
		)
	);

	public static final MessageDescriptor<Message.Cancel> CANCEL = register(
		Message.Cancel.class,
		(byte) 8,
		(message, output) -> {
			output.writeInt(message.index());
			output.writeInt(message.begin());
			output.writeInt(message.length());

			return 1 + 4 + 4 + 4;
		},
		(payloadLength, input) -> new Message.Cancel(
			input.readInt(),
			input.readInt(),
			input.readInt()
		)
	);

	public static final MessageDescriptor<Message.Port> PORT = register(
		Message.Port.class,
		(byte) 9,
		(message, output) -> {
			output.writeShort(message.port());

			return 1 + 2;
		},
		(payloadLength, input) -> new Message.Port(
			input.readShort()
		)
	);

	public static final MessageDescriptor<Message.Extension> EXTENSION = register(
		Message.Extension.class,
		(byte) 20,
		(message, output) -> {
			throw new UnsupportedOperationException();
		},
		(payloadLength, input) -> {
			throw new UnsupportedOperationException();
		}
	);

}