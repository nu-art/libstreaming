package com.nu.art.rtsp.params;

import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.video.VideoQuality;

import static net.majorkernelpanic.streaming.SessionBuilder.VIDEO_H263;

/**
 * Created by tacb0ss on 07/08/2017.
 */

class ParamProcessor_H263
		extends ParamProcessor_Base {

	@Override
	public void processParam(String paramValue, SessionBuilder builder) {
		VideoQuality videoQuality = VideoQuality.parseQuality(paramValue);
		builder.setVideoQuality(videoQuality).setVideoEncoder(VIDEO_H263);
	}
}
