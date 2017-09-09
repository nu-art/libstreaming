package com.nu.art.rtsp.descriptors;

import static com.nu.art.rtsp.Response.LineBreak;

/**
 * Created by TacB0sS on 07-Sep 2017.
 */

public class AACDescriptor
		extends AudioDescriptor {

	public static final int[] SamplingRates = {
			// 0
			96000,
			// 1
			88200,
			// 2
			64000,
			// 3
			48000,
			// 4
			44100,
			// 5
			32000,
			// 6
			24000,
			// 7
			22050,
			// 8
			16000,
			// 9
			12000,
			// 10
			11025,
			// 11
			8000,
			// 12
			7350,
	};

	public void buildSessionDescription(StringBuilder sdpBuilder) {
		int profile = 2; // AAC LC
		int channel = 1;
		int samplingRateIndex = getSamplingRateIndex();

		int config = (profile & 0x1F) << 11 | (samplingRateIndex & 0x0F) << 7 | (channel & 0x0F) << 3;

		sdpBuilder.append("m=audio ").append(rtpPort).append(" RTP/AVP 96").append(LineBreak);
		sdpBuilder.append("a=rtpmap:96 mpeg4-generic/").append(samplingRate).append(LineBreak);
		sdpBuilder.append("a=fmtp:96 streamtype=5; profile-level-id=15; mode=AAC-hbr; config=")
							.append(Integer.toHexString(config))
							.append("; SizeLength=13; IndexLength=3; IndexDeltaLength=3;")
							.append(LineBreak);
	}

	private int getSamplingRateIndex() {
		for (int i = 0; i < SamplingRates.length; i++) {
			if (SamplingRates[i] == samplingRate) {
				return i;
			}
		}

		samplingRate = SamplingRates[8];
		return 8;
	}
}
