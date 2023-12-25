package bittorrent.peer.message;

import java.io.DataOutputStream;
import java.io.IOException;

public sealed interface Message permits
	KeepAliveMessage,
	ChokeMessage,
	UnchokeMessage,
	InterestedMessage,
	NotInterestedMessage,
	HaveMessage,
	BitfieldMessage,
	RequestMessage,
	PieceMessage,
	CancelMessage,
	PortMessage {

	MessageType type();

	int length();
	
	void serialize(DataOutputStream output) throws IOException;

}