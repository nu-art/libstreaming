package com.nu.art.rtsp.params;

import net.majorkernelpanic.streaming.SessionBuilder;

/**
 * Created by tacb0ss on 07/08/2017.
 */

class ParamProcessor_TTL
		extends ParamProcessor_Base {

	@Override
	public void processParam(String paramValue, SessionBuilder builder) {
		int ttl = Integer.parseInt(paramValue);
		if (ttl < 0)
			throw new IllegalStateException("The TTL must be a positive integer !");

		builder.setTimeToLive(ttl);
	}
}
