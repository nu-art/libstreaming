package com.nu.art.rtsp.params;

import net.majorkernelpanic.streaming.SessionBuilder;

/**
 * Created by tacb0ss on 07/08/2017.
 */

class ParamProcessor_Unicast
		extends ParamProcessor_Base {

	@Override
	public void processParam(String paramValue, SessionBuilder builder) {
		builder.setDestination(paramValue);
	}
}
