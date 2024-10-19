package bittorrent.tracker;

public interface Announceable {

	String getTrackerUrl();

	byte[] getInfoHash();

	long getInfoLength();

}