package com.nu.art.rtsp.descriptors;

import com.nu.art.core.tools.ArrayTools;

import net.majorkernelpanic.streaming.gl.SurfaceView;

import static com.nu.art.rtsp.Response.LineBreak;

/**
 * Created by TacB0sS on 07-Sep 2017.
 */

public class SessionDescriptor {

	private final long timestamp;

	private SurfaceView cameraSurface;

	private int orientation = 90;

	private TrackDescriptor[] trackDescriptors = {};

	public SessionDescriptor() {
		long uptime = System.currentTimeMillis();
		timestamp = (uptime / 1000) << 32 & (((uptime - ((uptime / 1000) * 1000)) >> 32) / 1000); // NTP timestamp
	}

	public SessionDescriptor setSurfaceView(SurfaceView cameraSurface, int orientation) {
		this.cameraSurface = cameraSurface;
		this.orientation = orientation;
		return this;
	}

	public SessionDescriptor addTrackDesctiptor(TrackDescriptor trackDescriptor) {
		trackDescriptors = ArrayTools.appendElement(trackDescriptors, trackDescriptor);
		return this;
	}

	public SessionDescriptor removeTrackDesctiptor(TrackDescriptor trackDescriptor) {
		trackDescriptors = ArrayTools.removeElement(trackDescriptors, trackDescriptor);
		return this;
	}

	public String getSessionDescription(String serverName, String localHostAddress, String remoteHostAddress) {
		StringBuilder sdpBuilder = new StringBuilder();
		sdpBuilder.append("v=0").append(LineBreak);

		// TODO: Add IPV6 support
		sdpBuilder.append("o=- ").append(timestamp).append(" ").append(timestamp).append(" IN IP4 ").append(localHostAddress).append(LineBreak);
		sdpBuilder.append("s=").append(serverName).append(LineBreak);
		sdpBuilder.append("i=N/A").append(LineBreak);
		sdpBuilder.append("c=IN IP4 ").append(remoteHostAddress).append(LineBreak);
		// t=0 0 means the session is permanent (we don't know when it will stop)
		sdpBuilder.append("t=0 0").append(LineBreak);
		sdpBuilder.append("a=recvonly").append(LineBreak);

		for (int i = 0; i < trackDescriptors.length; i++) {
			trackDescriptors[i].buildSessionDescription(sdpBuilder);
			sdpBuilder.append("a=control:trackID=").append(i).append(LineBreak);
		}
		return sdpBuilder.toString();
	}

	public boolean isTrackExists(int trackId) {
		return trackDescriptors.length < trackId;
	}
}
