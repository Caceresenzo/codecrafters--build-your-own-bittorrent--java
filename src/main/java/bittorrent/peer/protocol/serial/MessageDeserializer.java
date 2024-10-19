package bittorrent.peer.protocol.serial;

import java.io.DataInputStream;
import java.io.IOException;

import bittorrent.peer.protocol.Message;

@FunctionalInterface
public interface MessageDeserializer<T extends Message> {

	T deserialize(int payloadLength, DataInputStream input) throws IOException;

}