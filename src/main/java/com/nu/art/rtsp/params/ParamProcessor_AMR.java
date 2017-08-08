package com.nu.art.rtsp.params;

import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;

import static net.majorkernelpanic.streaming.SessionBuilder.AUDIO_AMRNB;

/**
 * Created by tacb0ss on 07/08/2017.
 */

class ParamProcessor_AMR
		extends ParamProcessor_Base {

	@Override
	public void processParam(String paramValue, SessionBuilder builder) {
		AudioQuality audioQuality = AudioQuality.parseQuality(paramValue);
		builder.setAudioQuality(audioQuality).setAudioEncoder(AUDIO_AMRNB);
	}
}
