package bittorrent.bencode;

import java.io.IOException;

public record BEncoded<T>(
	byte[] serialized,
	T deserialized
) {

	@SuppressWarnings("unchecked")
	public BEncoded(byte[] serialized) throws IOException {
		this(
			serialized,
			(T) new Deserializer(serialized).parse()
		);
	}

	public BEncoded(T deserialized) throws IOException {
		this(
			new Serializer().writeAsBytes(deserialized),
			deserialized
		);
	}

}