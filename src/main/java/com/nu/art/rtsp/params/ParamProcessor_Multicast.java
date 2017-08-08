package com.nu.art.rtsp.params;

import net.majorkernelpanic.streaming.SessionBuilder;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by tacb0ss on 07/08/2017.
 */

class ParamProcessor_Multicast
		extends ParamProcessor_Base {

	@Override
	public void processParam(String paramValue, SessionBuilder builder) {
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
	}
}
