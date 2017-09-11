package com.nu.art.rtsp.params;

/**
 * Created by tacb0ss on 07/08/2017.
 */

public enum RTSPParams {
//	Flash("flash", ParamProcessor_Flash.class),
//	Camera("camera", ParamProcessor_Camera.class),
//	AudioApi("audioapi", ParamProcessor_AudioApi.class),
//	VideoApi("videoapi", ParamProcessor_VideoApi.class),

	TTL("ttl", ParamProcessor_TTL.class),
	Unicast("unicast", ParamProcessor_Unicast.class),
	Multicast("multicast", ParamProcessor_Multicast.class),
//	H264("h264", ParamProcessor_H264.class),
//	H263("h263", ParamProcessor_H263.class),
//	AMR("amr", ParamProcessor_AMR.class),
//	AMDNB("amdnb", ParamProcessor_AMR.class),
	Aac("aac", ParamProcessor_AAC.class),

	//
	;

	public final String paramName;

	public final Class<? extends ParamProcessor_Base> paramProcessorType;

	RTSPParams(String paramName, Class<? extends ParamProcessor_Base> paramProcessorType) {
		this.paramName = paramName;
		this.paramProcessorType = paramProcessorType;
	}

	public static RTSPParams getByKey(String paramName) {
		for (RTSPParams param : values()) {
			if (!param.paramName.equals(paramName))
				continue;

			return param;
		}

		return null;
	}
}
