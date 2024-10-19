package bittorrent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HexFormat;
import java.util.Map;

import com.google.gson.Gson;

import bittorrent.bencode.BencodeDeserializer;
import bittorrent.magnet.Magnet;
import bittorrent.peer.Peer;
import bittorrent.torrent.Torrent;
import bittorrent.torrent.TorrentInfo;
import bittorrent.tracker.TrackerClient;
import okhttp3.OkHttpClient;

public class Main {

	public static final boolean DEBUG = true;

	public static final HexFormat HEX_FORMAT = HexFormat.of();
	public static final OkHttpClient CLIENT = new OkHttpClient();

	public static void main(String[] args) throws Exception {
		final var command = args[0];

		switch (command) {
			case "decode" -> decode(args[1]);
			case "info" -> info(args[1]);
			case "peers" -> peers(args[1]);
			case "handshake" -> handshake(args[1], args[2]);
			case "download_piece" -> downloadPiece(args[3], Integer.parseInt(args[4]), args[2]);
			case "download" -> download(args[3], args[2]);
			case "magnet_parse" -> magnetParse(args[1]);
			case "magnet_handshake" -> magnetHandshake(args[1]);
			case "magnet_info" -> magnetInfo(args[1]);
			case "magnet_download_piece" -> magnetDownloadPiece(args[3], Integer.parseInt(args[4]), args[2]);
			case "magnet_download" -> magnetDownload(args[3], args[2]);
			default -> System.out.println("Unknown command: " + command);
		}
	}

	private static void decode(String encoded) throws IOException {
		final var gson = new Gson();

		final var decoded = new BencodeDeserializer(encoded).parse();

		System.out.println(gson.toJson(decoded));
	}

	private static void info(String path) throws IOException {
		final var torrent = load(path);
		final var info = torrent.info();

		info(torrent.announce(), info);
	}

	private static void peers(String path) throws IOException {
		final var torrent = load(path);

		final var trackerClient = new TrackerClient();
		final var response = trackerClient.announce(torrent);

		for (final var peer : response.peers()) {
			final var line = "%s:%d".formatted(peer.getAddress().getHostAddress(), peer.getPort());

			System.out.println(line);
		}
	}

	private static void handshake(String path, String peerIpAndPort) throws IOException, InterruptedException {
		final var torrent = load(path);

		final var parts = peerIpAndPort.split(":", 2);
		final var socket = new Socket(parts[0], Integer.parseInt(parts[1]));

		try (final var peer = Peer.connect(socket, torrent)) {
			System.out.println("Peer ID: %s".formatted(HEX_FORMAT.formatHex(peer.getId())));
		}
	}

	private static void downloadPiece(String path, int pieceIndex, String outputPath) throws IOException, InterruptedException {
		final var torrent = load(path);
		final var torrentInfo = torrent.info();

		final var trackerClient = new TrackerClient();
		final var firstPeer = trackerClient.announce(torrent).peers().getFirst();

		try (
			final var peer = Peer.connect(firstPeer, torrent);
			final var fileOutputStream = new FileOutputStream(new File(outputPath));
		) {
			final var data = peer.downloadPiece(torrentInfo, pieceIndex);
			fileOutputStream.write(data);
		}
	}

	private static void download(String path, String outputPath) throws IOException, InterruptedException {
		final var torrent = load(path);
		final var torrentInfo = torrent.info();

		final var trackerClient = new TrackerClient();
		final var firstPeer = trackerClient.announce(torrent).peers().getFirst();

		try (
			final var peer = Peer.connect(firstPeer, torrent);
			final var fileOutputStream = new FileOutputStream(new File(outputPath));
		) {
			final var pieceCount = torrent.info().pieces().size();
			for (var index = 0; index < pieceCount; ++index) {
				final var data = peer.downloadPiece(torrentInfo, index);
				fileOutputStream.write(data);
			}
		}
	}

	private static void magnetParse(String link) throws IOException, InterruptedException {
		final var magnet = Magnet.parse(link);

		System.out.println("Tracker URL: %s".formatted(magnet.announce()));
		System.out.println("Info Hash: %s".formatted(HEX_FORMAT.formatHex(magnet.hash())));
	}

	private static void magnetHandshake(String link) throws IOException, InterruptedException {
		final var magnet = Magnet.parse(link);

		final var trackerClient = new TrackerClient();
		final var firstPeer = trackerClient.announce(magnet).peers().getFirst();

		try (final var peer = Peer.connect(firstPeer, magnet)) {
			System.out.println("Peer ID: %s".formatted(HEX_FORMAT.formatHex(peer.getId())));
			peer.awaitBitfield();

			System.out.println("Peer Metadata Extension ID: %s".formatted(peer.getMetadataExtensionId()));
		}
	}

	private static void magnetInfo(String link) throws IOException, InterruptedException {
		final var magnet = Magnet.parse(link);

		final var trackerClient = new TrackerClient();
		final var firstPeer = trackerClient.announce(magnet).peers().getFirst();

		try (final var peer = Peer.connect(firstPeer, magnet)) {
			System.out.println("Peer ID: %s".formatted(HEX_FORMAT.formatHex(peer.getId())));

			final var torrentInfo = peer.queryTorrentInfoViaMetadataExtension();
			info(magnet.announce(), torrentInfo);
		}
	}

	private static void magnetDownloadPiece(String link, int pieceIndex, String outputPath) throws IOException, InterruptedException {
		final var magnet = Magnet.parse(link);

		final var trackerClient = new TrackerClient();
		final var firstPeer = trackerClient.announce(magnet).peers().getFirst();

		try (
			final var peer = Peer.connect(firstPeer, magnet);
			final var fileOutputStream = new FileOutputStream(new File(outputPath));
		) {
			final var torrentInfo = peer.queryTorrentInfoViaMetadataExtension();

			final var data = peer.downloadPiece(torrentInfo, pieceIndex);
			fileOutputStream.write(data);
		}
	}

	private static void magnetDownload(String link, String outputPath) throws IOException, InterruptedException {
		final var magnet = Magnet.parse(link);

		final var trackerClient = new TrackerClient();
		final var firstPeer = trackerClient.announce(magnet).peers().getFirst();

		//		final var firstPeer = new java.net.InetSocketAddress(java.net.InetAddress.getByName("2.204.166.236"), 51414);

		try (
			final var peer = Peer.connect(firstPeer, magnet);
			final var fileOutputStream = new FileOutputStream(new File(outputPath));
		) {
			final var torrentInfo = peer.queryTorrentInfoViaMetadataExtension();

			final var pieceCount = torrentInfo.pieces().size();
			for (var index = 0; index < pieceCount; ++index) {
				final var data = peer.downloadPiece(torrentInfo, index);
				fileOutputStream.write(data);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static Torrent load(String path) throws IOException {
		final var content = Files.readAllBytes(Paths.get(path));
		final var decoded = new BencodeDeserializer(content).parse();

		return Torrent.of((Map<String, Object>) decoded);
	}

	private static void info(String trackerUrl, TorrentInfo info) throws IOException {
		System.out.println("Tracker URL: %s".formatted(trackerUrl));
		System.out.println("Length: %d".formatted(info.length()));
		System.out.println("Info Hash: %s".formatted(HEX_FORMAT.formatHex(info.hash())));
		System.out.println("Piece Length: %d".formatted(info.pieceLength()));

		System.out.println("Piece Hashes:");
		for (final var hash : info.pieces()) {
			System.out.println(HEX_FORMAT.formatHex(hash));
		}
	}

}