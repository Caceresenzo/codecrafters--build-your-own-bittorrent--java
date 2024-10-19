package bittorrent.peer.protocol.serial;

import java.io.DataOutputStream;
import java.io.IOException;

import bittorrent.peer.protocol.Message;

@FunctionalInterface
public interface MessageSerializer<T extends Message> {

	int serialize(T message, DataOutputStream output) throws IOException;

}