package com.nu.art.rtsp.descriptors;

/**
 * Created by TacB0sS on 07-Sep 2017.
 */

public abstract class AudioDescriptor
		extends TrackDescriptor {

	protected int bitRate = 8000;

	protected int samplingRate = 32000;

	public AudioDescriptor() {
		super(5004);
	}
}
