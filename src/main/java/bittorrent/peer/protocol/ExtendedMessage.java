package bittorrent.peer.protocol;

public sealed interface ExtendedMessage {

	public record Handshake() implements ExtendedMessage {}

}