package com.nu.art.rtsp.params;

import android.hardware.Camera.CameraInfo;

import net.majorkernelpanic.streaming.SessionBuilder;

/**
 * Created by tacb0ss on 07/08/2017.
 */

class ParamProcessor_Camera
		extends ParamProcessor_Base {

	@Override
	public void processParam(String paramValue, SessionBuilder builder) {
		switch (paramValue) {
			case "back":
				builder.setCamera(CameraInfo.CAMERA_FACING_BACK);
				break;
			case "front":
			default:
				builder.setCamera(CameraInfo.CAMERA_FACING_FRONT);
		}
	}
}
