package com.nu.art.rtsp.params;

import net.majorkernelpanic.streaming.SessionBuilder;

/**
 * Created by tacb0ss on 07/08/2017.
 */

public abstract class ParamProcessor_Base {

	public abstract void processParam(String paramValue, SessionBuilder builder);
}
