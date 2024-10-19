package bittorrent.peer.serial;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import bittorrent.bencode.BencodeDeserializer;
import bittorrent.bencode.BencodeSerializer;
import bittorrent.peer.protocol.Message;
import bittorrent.peer.protocol.MetadataMessage;
import bittorrent.peer.serial.extension.MetadataMessageSerial;
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
		MessageDescriptor.Serializer<T> serializer,
		MessageDescriptor.Deserializer<T> deserializer
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
		final var length = typeId == -1 ? 0 : 1;

		return register(new MessageDescriptor<>(
			clazz,
			typeId,
			(message, output, context) -> length,
			(payloadLength, input, context) -> instance
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
		(message, output, context) -> {
			output.writeInt(message.pieceIndex());

			return 1 + 4;
		},
		(payloadLength, input, context) -> new Message.Have(
			input.readInt()
		)
	);

	public static final MessageDescriptor<Message.Bitfield> BITFIELD = register(
		Message.Bitfield.class,
		(byte) 5,
		(message, output, context) -> {
			final var values = message.values();

			output.write(values);

			return 1 + values.length;
		},
		(payloadLength, input, context) -> new Message.Bitfield(
			input.readNBytes(payloadLength)
		)
	);

	public static final MessageDescriptor<Message.Request> REQUEST = register(
		Message.Request.class,
		(byte) 6,
		(message, output, context) -> {
			output.writeInt(message.index());
			output.writeInt(message.begin());
			output.writeInt(message.length());

			return 1 + 4 + 4 + 4;
		},
		(payloadLength, input, context) -> new Message.Request(
			input.readInt(),
			input.readInt(),
			input.readInt()
		)
	);

	public static final MessageDescriptor<Message.Piece> PIECE = register(
		Message.Piece.class,
		(byte) 7,
		(message, output, context) -> {
			final var block = message.block();

			output.writeInt(message.index());
			output.writeInt(message.begin());
			output.write(block);

			return 1 + 4 + 4 + block.length;
		},
		(payloadLength, input, context) -> new Message.Piece(
			input.readInt(),
			input.readInt(),
			input.readNBytes(payloadLength - 8)
		)
	);

	public static final MessageDescriptor<Message.Cancel> CANCEL = register(
		Message.Cancel.class,
		(byte) 8,
		(message, output, context) -> {
			output.writeInt(message.index());
			output.writeInt(message.begin());
			output.writeInt(message.length());

			return 1 + 4 + 4 + 4;
		},
		(payloadLength, input, context) -> new Message.Cancel(
			input.readInt(),
			input.readInt(),
			input.readInt()
		)
	);

	public static final MessageDescriptor<Message.Port> PORT = register(
		Message.Port.class,
		(byte) 9,
		(message, output, context) -> {
			output.writeShort(message.port());

			return 1 + 2;
		},
		(payloadLength, input, context) -> new Message.Port(
			input.readShort()
		)
	);

	public static final MessageDescriptor<Message.Extension> EXTENSION = register(
		Message.Extension.class,
		(byte) 20,
		(message, output, context) -> {
			final byte[] serializedContent;

			final var extensionType = context.extensionType();
			if (MetadataMessage.class.equals(extensionType)) {
				final var content = MetadataMessageSerial.serialize((MetadataMessage) message.content());
				serializedContent = new BencodeSerializer().writeAsBytes(content);
			} else {
				throw new UnsupportedOperationException("unknown extension: %s".formatted(extensionType.getName()));
			}

			output.writeByte(message.id());
			output.write(serializedContent);

			return 1 + 1 + serializedContent.length;
		},
		(payloadLength, input, context) -> {
			final var id = input.readByte();
			final var raw = input.readNBytes(payloadLength - 1);
			System.err.println(new String(raw));
			final var parsed = new BencodeDeserializer(raw).parseMultiple();

			final var extensionType = context.extensionType();
			if (MetadataMessage.class.equals(extensionType)) {
				return new Message.Extension(
					id,
					MetadataMessageSerial.deserialize(parsed)
				);
			} else {
				throw new UnsupportedOperationException("unknown extension: %s".formatted(extensionType.getName()));
			}
		}
	);

}