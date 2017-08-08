package com.nu.art.rtsp;

import com.nu.art.core.utils.GenericMap;
import com.nu.art.cyborg.core.CyborgModule;
import com.nu.art.reflection.tools.ReflectiveTools;
import com.nu.art.rtsp.params.ParamProcessor_Base;

import net.majorkernelpanic.streaming.gl.SurfaceView;

import static net.majorkernelpanic.streaming.SessionBuilder.AUDIO_NONE;
import static net.majorkernelpanic.streaming.SessionBuilder.VIDEO_NONE;

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

		int port;

		String userName;

		String password;

		SurfaceView cameraSurface;

		int orientation = 90;

		int videoEncoder = VIDEO_NONE;

		int audioEncoder = AUDIO_NONE;

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

		public RTSPServerBuilder setSurfaceView(SurfaceView cameraSurface, int orientation) {
			this.cameraSurface = cameraSurface;
			this.orientation = orientation;
			return this;
		}

		public RTSPServerBuilder setAudioEncoder(int audioEncoder) {
			this.audioEncoder = audioEncoder;
			return this;
		}

		public RTSPServerBuilder setVideoEncoder(int videoEncoder) {
			this.videoEncoder = videoEncoder;
			return this;
		}

		public final RTSPServer build() {
			RTSPServer rtspServer = createModuleItem(RTSPServer.class);
			rtspServer.setBuilder(this);
			rtspServer.start();
			return rtspServer;
		}
	}
}
