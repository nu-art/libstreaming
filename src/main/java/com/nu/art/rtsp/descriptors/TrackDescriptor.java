package com.nu.art.rtsp.descriptors;

/**
 * Created by TacB0sS on 07-Sep 2017.
 */

public abstract class TrackDescriptor {

	protected int rtpPort;

	protected int rtcpPort;

	public TrackDescriptor(int rtpPort) {
		this.rtpPort = rtpPort;
		this.rtcpPort = rtpPort + 1;
	}

	public void setPorts(int rtpPort) {
		this.rtpPort = rtpPort;
		this.rtcpPort = rtpPort + 1;
	}

	protected abstract void buildSessionDescription(StringBuilder sdpBuilder);
}
