package com.nu.art.rtsp.params;

/**
 * Created by tacb0ss on 07/08/2017.
 */

public enum RTSPParams {
	Flash("flash", ParamProcessor_Flash.class),
	Camera("camera", ParamProcessor_Camera.class),
	AudioApi("audioapi", ParamProcessor_AudioApi.class),
	VideoApi("videoapi", ParamProcessor_VideoApi.class),
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
