/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.majorkernelpanic.streaming.rtsp;

import com.nu.art.belog.Logger;
import com.nu.art.cyborg.core.CyborgBuilder;
import com.nu.art.rtsp.RTSPModule;
import com.nu.art.rtsp.params.ParamProcessor_Base;
import com.nu.art.rtsp.params.RTSPParams;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;

import java.io.IOException;
import java.util.HashMap;

import static net.majorkernelpanic.streaming.SessionBuilder.AUDIO_NONE;
import static net.majorkernelpanic.streaming.SessionBuilder.VIDEO_NONE;

/**
 * This class parses URIs received by the RTSP server and configures a Session accordingly.
 */
public class UriParser
		extends Logger {

	/**
	 * Configures a Session according to the given URI.
	 * Here are some examples of URIs that can be used to configure a Session:
	 * <ul><li>rtsp://xxx.xxx.xxx.xxx:8086?h264&flash=on</li>
	 * <li>rtsp://xxx.xxx.xxx.xxx:8086?h263&camera=front&flash=on</li>
	 * <li>rtsp://xxx.xxx.xxx.xxx:8086?h264=200-20-320-240</li>
	 * <li>rtsp://xxx.xxx.xxx.xxx:8086?aac</li></ul>
	 *
	 * @param params The URI
	 *
	 * @return A Session configured according to the URI
	 *
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public static Session parse(HashMap<String, String> params)
			throws IllegalStateException, IOException {
		SessionBuilder builder = SessionBuilder.getInstance().clone();
		builder.setAudioEncoder(AUDIO_NONE).setVideoEncoder(VIDEO_NONE);
		// Those parameters must be parsed first or else they won't necessarily be taken into account
		for (String paramName : params.keySet()) {
			String paramValue = params.get(paramName);
			if (paramValue == null)
				continue;

			RTSPParams byKey = RTSPParams.getByKey(paramName);
			if (byKey == null) {
				//				logError("Unidentified param: " + paramName + ", with value: " + paramValue);
				continue;
			}

			Class<? extends ParamProcessor_Base> paramProcessorType = byKey.paramProcessorType;
			ParamProcessor_Base rtspParamProcessor = CyborgBuilder.getModule(RTSPModule.class).getRtspParamProcessor(paramProcessorType);
			rtspParamProcessor.processParam(paramValue, builder);
		}

		//			.. need to figure out what the fuck this is for??
		if (builder.getVideoEncoder() == VIDEO_NONE && builder.getAudioEncoder() == AUDIO_NONE) {
			SessionBuilder b = SessionBuilder.getInstance();
			builder.setVideoEncoder(b.getVideoEncoder());
			builder.setAudioEncoder(b.getAudioEncoder());
		}

		return builder.build();
	}
}
