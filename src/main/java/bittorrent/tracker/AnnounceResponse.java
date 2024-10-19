package bittorrent.tracker;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import bittorrent.Main;
import lombok.SneakyThrows;

public record AnnounceResponse(
	long interval,
	List<InetSocketAddress> peers
) {

	@SneakyThrows
	public static AnnounceResponse of(Map<String, Object> root) {
		if (Main.DEBUG) {
			System.out.println("AnnounceResponse: %s".formatted(root));
		}

		final var interval = (long) root.get("interval");

		final var peersString = ((String) root.get("peers")).getBytes(StandardCharsets.ISO_8859_1);
		final var peers = new ArrayList<InetSocketAddress>();

		for (var start = 0; start < peersString.length; start += 6) {
			final var address = Arrays.copyOfRange(peersString, start, start + 4);
			final var port = ((peersString[start + 4] & 0xff) << 8) + (peersString[start + 5] & 0xff);

			final var peer = new InetSocketAddress(InetAddress.getByAddress(address), port);
			peers.add(peer);
		}

		return new AnnounceResponse(interval, peers);
	}

}