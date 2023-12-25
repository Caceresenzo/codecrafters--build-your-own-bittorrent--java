package bittorrent.peer.message;

import java.util.function.Supplier;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum MessageType {

	CHOKE(ChokeMessage::new),
	UNCHOKE(UnchokeMessage::new),
	INTERESTED(InterestedMessage::new),
	NOT_INTERESTED(NotInterestedMessage::new),
	HAVE((payloadLength, input) -> new HaveMessage(input.readInt())),
	BITFIELD((payloadLength, input) -> new BitfieldMessage(input.readNBytes(payloadLength))),
	REQUEST((payloadLength, input) -> new RequestMessage(input.readInt(), input.readInt(), input.readInt())),
	PIECE((payloadLength, input) -> new PieceMessage(input.readInt(), input.readInt(), input.readNBytes(payloadLength - 8))),
	CANCEL((payloadLength, input) -> new CancelMessage(input.readInt(), input.readInt(), input.readInt())),
	PORT((payloadLength, input) -> new PortMessage(input.readShort()));

	private static final MessageType[] VALUES = MessageType.values();

	private final @Getter MessageDeserializer deserializer;

	private MessageType(Supplier<Message> statelessSupplier) {
		this(MessageDeserializer.stateless(statelessSupplier.get()));
	}

	public static MessageType valueOf(int ordinal) {
		return VALUES[ordinal];
	}

}