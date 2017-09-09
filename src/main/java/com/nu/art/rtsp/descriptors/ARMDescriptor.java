package com.nu.art.rtsp.descriptors;

import static com.nu.art.rtsp.Response.LineBreak;

/**
 * Created by TacB0sS on 07-Sep 2017.
 */

public class ARMDescriptor
		extends AudioDescriptor {


	public void buildSessionDescription(StringBuilder sdpBuilder) {
		return "m=audio " + rtpPort + " RTP/AVP 96"  + LineBreak + "a=rtpmap:96 AMR/" + bitRate + LineBreak + "a=fmtp:96 octet-align=1;";
	}
}
