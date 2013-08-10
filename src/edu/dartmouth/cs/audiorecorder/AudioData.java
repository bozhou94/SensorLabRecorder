package edu.dartmouth.cs.audiorecorder;

public class AudioData {
	public short[] mData;
	public int mSize;

	public AudioData(short[] data, int size) {
		this.mData = data;
		this.mSize = size;
	}
}
