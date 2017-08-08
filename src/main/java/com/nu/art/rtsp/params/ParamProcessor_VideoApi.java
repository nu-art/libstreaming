package com.nu.art.rtsp.params;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.SessionBuilder;

/**
 * Created by tacb0ss on 07/08/2017.
 */

class ParamProcessor_VideoApi
		extends ParamProcessor_Base {

	@Override
	public void processParam(String paramValue, SessionBuilder builder) {
		byte videoApi;
		switch (paramValue) {
			case "mr":
				videoApi = MediaStream.MODE_MEDIARECORDER_API;
				break;

			case "mc":
			default:
				videoApi = MediaStream.MODE_MEDIACODEC_API;
		}
		builder.setVideoApi(videoApi);
	}
}
