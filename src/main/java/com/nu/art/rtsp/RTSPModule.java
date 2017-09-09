package com.nu.art.rtsp;

import com.nu.art.cyborg.core.CyborgModule;
import com.nu.art.reflection.tools.ReflectiveTools;
import com.nu.art.reflection.utils.GenericMap;
import com.nu.art.rtsp.descriptors.SessionDescriptor;
import com.nu.art.rtsp.params.ParamProcessor_Base;

/**
 * Created by tacb0ss on 05/08/2017.
 */

public class RTSPModule
		extends CyborgModule {

	public GenericMap<ParamProcessor_Base> paramProcessors = new GenericMap<>();

	@Override
	protected void init() {
	}

	public final RTSPServerBuilder createServerBuilder() {
		return new RTSPServerBuilder();
	}

	public final ParamProcessor_Base getRtspParamProcessor(Class<? extends ParamProcessor_Base> paramProcessorType) {
		ParamProcessor_Base instance = paramProcessors.get(paramProcessorType);

		if (instance == null) {
			instance = ReflectiveTools.newInstance(paramProcessorType);
			paramProcessors.put(paramProcessorType, instance);
		}

		return instance;
	}

	public final class RTSPServerBuilder {

		String serverName = "unnamed";

		String userName;

		String password;

		int port;

		SessionDescriptor sessionDescriptor;

		private RTSPServerBuilder() {}

		public RTSPServerBuilder setServerName(String serverName) {
			this.serverName = serverName;
			return this;
		}

		public RTSPServerBuilder setPassword(String password) {
			this.password = password;
			return this;
		}

		public RTSPServerBuilder setPort(int port) {
			this.port = port;
			return this;
		}

		public RTSPServerBuilder setUserName(String userName) {
			this.userName = userName;
			return this;
		}

		public void setSessionDescriptor(SessionDescriptor sessionDescriptor) {
			this.sessionDescriptor = sessionDescriptor;
		}

		public final RTSPServer build() {
			RTSPServer rtspServer = createModuleItem(RTSPServer.class);
			rtspServer.setBuilder(this);
			rtspServer.start();
			return rtspServer;
		}
	}
}
