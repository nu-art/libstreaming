package com.nu.art.rtsp.params;

import net.majorkernelpanic.streaming.SessionBuilder;

/**
 * Created by tacb0ss on 07/08/2017.
 */

class ParamProcessor_Flash
		extends ParamProcessor_Base {

	@Override
	public void processParam(String paramValue, SessionBuilder builder) {
		builder.setFlashEnabled(paramValue.equals("on"));
	}
}
