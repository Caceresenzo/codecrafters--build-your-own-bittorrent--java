package bittorrent.tracker;

import java.io.IOException;
import java.util.Map;

import bittorrent.bencode.BencodeDeserializer;
import bittorrent.util.DigestUtils;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class TrackerClient {

	public final OkHttpClient client = new OkHttpClient();

	@SuppressWarnings("unchecked")
	public AnnounceResponse announce(Announceable announceable) throws IOException {
		final var selfPort = (short) 6881;

		final var request = new Request.Builder()
			.get()
			.url(
				HttpUrl.parse(announceable.getTrackerUrl())
					.newBuilder()
					.addEncodedQueryParameter("info_hash", DigestUtils.urlEncode(announceable.getInfoHash()))
					.addQueryParameter("peer_id", "00112233445566778899")
					.addQueryParameter("port", String.valueOf(selfPort))
					.addQueryParameter("uploaded", "0")
					.addQueryParameter("downloaded", "0")
					.addQueryParameter("left", String.valueOf(announceable.getInfoLength()))
					.addQueryParameter("compact", "1")
					.build()
			)
			.build();

		try (
			final var response = client.newCall(request).execute();
			final var responseBody = response.body();
		) {
			if (!response.isSuccessful()) {
				throw new IllegalStateException(responseBody.string());
			}

			try (final var inputStream = responseBody.byteStream()) {
				final var deserializer = new BencodeDeserializer(inputStream);
				final var root = deserializer.parse();

				return AnnounceResponse.of((Map<String, Object>) root, selfPort);
			}
		}
	}

}