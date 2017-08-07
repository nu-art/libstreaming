package com.nu.art.rtsp.params;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.SessionBuilder;

/**
 * Created by tacb0ss on 07/08/2017.
 */

class ParamProcessor_AudioApi
		extends ParamProcessor_Base {

	@Override
	public void processParam(String paramValue, SessionBuilder builder) {
		switch (paramValue.toLowerCase()) {
			case "mr":
				audioApi = MediaStream.MODE_MEDIARECORDER_API;
				break;

			case "mc":
			default:
				audioApi = MediaStream.MODE_MEDIACODEC_API;
		}
	}
}
