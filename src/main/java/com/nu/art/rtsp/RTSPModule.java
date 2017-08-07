package com.nu.art.rtsp;

import com.nu.art.core.utils.GenericMap;
import com.nu.art.cyborg.core.CyborgModule;
import com.nu.art.reflection.tools.ReflectiveTools;

import com.nu.art.rtsp.params.ParamProcessor_Base;

import java.io.IOException;

/**
 * Created by tacb0ss on 05/08/2017.
 */

public class RTSPModule
		extends CyborgModule {

	public GenericMap<ParamProcessor_Base> paramProcessors = new GenericMap<>();

	@Override
	protected void init() {
	}

	public final void startServer(RTSPServer server)
			throws IOException {
		server.start();
	}

	public final void stopServer(RTSPServer server)
			throws IOException {
		server.stop();
	}

	public final ParamProcessor_Base getRtspParamProcessor(Class<? extends ParamProcessor_Base> paramProcessorType) {
		ParamProcessor_Base instance = paramProcessors.get(paramProcessorType);

		if (instance == null) {
			instance = ReflectiveTools.newInstance(paramProcessorType);
			paramProcessors.put(paramProcessorType, instance);
		}

		return instance;
	}
}
