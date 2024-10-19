package bittorrent.peer.serial.extension;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import bittorrent.peer.protocol.MetadataMessage;
import bittorrent.torrent.TorrentInfo;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MetadataMessageSerial {

	public static final String EXTENSION_IDS_KEY = "m";
	public static final String PIECE_KEY = "piece";
	public static final String TOTAL_SIZE = "total_size";
	public static final String MESSAGE_TYPE_KEY = "msg_type";

	public Map<String, ?> serialize(MetadataMessage message) {
		return switch (message) {
			case MetadataMessage.Handshake handshake -> Map.of(
				EXTENSION_IDS_KEY, handshake.extensionIds()
			);

			case MetadataMessage.Request request -> Map.of(
				MESSAGE_TYPE_KEY, 0,
				PIECE_KEY, request.piece()
			);

			default -> throw new UnsupportedOperationException();
		};
	}

	@SuppressWarnings("unchecked")
	public MetadataMessage deserialize(List<Object> objects) {
		final var content = (Map<String, Object>) objects.getFirst();

		final var type = (Long) content.get(MESSAGE_TYPE_KEY);

		if (type == null) {
			return new MetadataMessage.Handshake(
				((Map<String, Long>) content.get(EXTENSION_IDS_KEY)).entrySet()
					.stream()
					.collect(Collectors.toMap(Map.Entry::getKey, (entry) -> entry.getValue().intValue()))
			);
		}

		return switch (type.intValue()) {
			case 0 -> throw new UnsupportedOperationException();

			case 1 -> new MetadataMessage.Data(
				((Long) content.get(PIECE_KEY)).intValue(),
				((Long) content.get(TOTAL_SIZE)).longValue(),
				TorrentInfo.of((Map<String, Object>) objects.get(1))
			);

			case 2 -> throw new UnsupportedOperationException();
			default -> throw new UnsupportedOperationException("unknown type: %s".formatted(type));
		};
	}

}