package bittorrent.tracker;

import java.io.IOException;
import java.util.Map;

import bittorrent.bencode.Deserializer;
import bittorrent.torrent.Torrent;
import bittorrent.util.DigestUtils;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class TrackerClient {

	public final OkHttpClient client = new OkHttpClient();

	@SuppressWarnings("unchecked")
	public AnnounceResponse announce(Torrent torrent) throws IOException {
		final var request = new Request.Builder()
			.get()
			.url(
				HttpUrl.parse(torrent.announce())
					.newBuilder()
					.addEncodedQueryParameter("info_hash", DigestUtils.urlEncode(torrent.info().hash()))
					.addQueryParameter("peer_id", "00112233445566778899")
					.addQueryParameter("port", "6881")
					.addQueryParameter("uploaded", "0")
					.addQueryParameter("downloaded", "0")
					.addQueryParameter("left", String.valueOf(torrent.info().length()))
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
				final var deserializer = new Deserializer(inputStream);
				final var root = deserializer.parse();

				return AnnounceResponse.of((Map<String, Object>) root);
			}
		}
	}

}