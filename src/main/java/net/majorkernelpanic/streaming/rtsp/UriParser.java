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
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.video.VideoQuality;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;

import static net.majorkernelpanic.streaming.SessionBuilder.AUDIO_AAC;
import static net.majorkernelpanic.streaming.SessionBuilder.AUDIO_AMRNB;
import static net.majorkernelpanic.streaming.SessionBuilder.AUDIO_NONE;
import static net.majorkernelpanic.streaming.SessionBuilder.VIDEO_H263;
import static net.majorkernelpanic.streaming.SessionBuilder.VIDEO_H264;
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
		byte audioApi = 0, videoApi = 0;

		builder.setAudioEncoder(AUDIO_NONE).setVideoEncoder(VIDEO_NONE);
		// Those parameters must be parsed first or else they won't necessarily be taken into account
		for (String paramName : params.keySet()) {
			String paramValue = params.get(paramName);
			VideoQuality videoQuality;
			AudioQuality audioQuality;

			RTSPParams byKey = RTSPParams.getByKey(paramName);
			if (byKey == null) {
//				logError("Unidentified param: " + paramName + ", with value: " + paramValue);
				continue;
			}
			Class<? extends ParamProcessor_Base> paramProcessorType = byKey.paramProcessorType;
			ParamProcessor_Base rtspParamProcessor = CyborgBuilder.getModule(RTSPModule.class).getRtspParamProcessor(paramProcessorType);
			rtspParamProcessor.processParam(paramValue, builder);


			switch (paramName.toLowerCase()) {
				// FLASH ON/OFF
				case "flash":
					break;

				// CAMERA -> the client can choose between the front facing camera and the back facing camera
				case "camera":
					if (paramValue == null)
						break;

					break;

				// AUDIOAPI -> can be used to specify what api will be used to encode audio (the MediaRecorder API or the MediaCodec API)
				case "audioapi":
					if (paramValue == null)
						break;

					break;

				// VIDEOAPI -> can be used to specify what api will be used to encode video (the MediaRecorder API or the MediaCodec API)
				case "videoapi":
					if (paramValue == null)
						break;

					break;

				// TTL -> the client can modify the time to live of packets -- Default ttl=64
				case "ttl":
					int ttl = Integer.parseInt(paramValue);
					if (ttl < 0)
						throw new IllegalStateException("The TTL must be a positive integer !");

					builder.setTimeToLive(ttl);
					break;

				// UNICAST -> the client can use this to specify where he wants the stream to be sent
				case "unicast":
					if (paramValue == null)
						break;

					builder.setDestination(paramValue);
					break;

				// MULTICAST -> the stream will be sent to a multicast group
				// The default mutlicast address is 228.5.6.7, but the client can specify another
				case "multicast":
					if (paramValue == null) {
						// Default multicast address
						builder.setDestination("228.5.6.7");
						break;
					}

					try {
						InetAddress addr = InetAddress.getByName(paramValue);
						if (!addr.isMulticastAddress()) {
							// bad preconditions!!
							throw new IllegalStateException("Invalid multicast address !");
						}
						builder.setDestination(paramValue);
					} catch (UnknownHostException e) {
						throw new IllegalStateException("Invalid multicast address !");
					}

					break;

				// H.264
				case "h264":
					videoQuality = VideoQuality.parseQuality(paramValue);
					builder.setVideoQuality(videoQuality).setVideoEncoder(VIDEO_H264);
					break;

				// H.263
				case "h263":
					videoQuality = VideoQuality.parseQuality(paramValue);
					builder.setVideoQuality(videoQuality).setVideoEncoder(VIDEO_H263);
					break;

				// AMR
				case "amrnb":
				case "amr":
					audioQuality = AudioQuality.parseQuality(paramValue);
					builder.setAudioQuality(audioQuality).setAudioEncoder(AUDIO_AMRNB);
					break;

				// AAC
				case "aac":
					audioQuality = AudioQuality.parseQuality(paramValue);
					builder.setAudioQuality(audioQuality).setAudioEncoder(AUDIO_AAC);
					break;
			}
		}

		if (builder.getVideoEncoder() == VIDEO_NONE && builder.getAudioEncoder() == AUDIO_NONE) {
			SessionBuilder b = SessionBuilder.getInstance();
			builder.setVideoEncoder(b.getVideoEncoder());
			builder.setAudioEncoder(b.getAudioEncoder());
		}

		Session session = builder.build();

		if (videoApi > 0 && session.getVideoTrack() != null) {
			session.getVideoTrack().setStreamingMethod(videoApi);
		}

		if (audioApi > 0 && session.getAudioTrack() != null) {
			session.getAudioTrack().setStreamingMethod(audioApi);
		}

		return session;
	}
}
