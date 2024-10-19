package bittorrent.util;

import java.io.ByteArrayOutputStream;

public class ExposedByteArrayOutputStream extends ByteArrayOutputStream {

	public byte[] getBuffer() {
		return buf;
	}

}