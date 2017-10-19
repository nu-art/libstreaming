package com.nu.art.rtsp;

import android.support.annotation.NonNull;
import android.util.Base64;

import com.nu.art.belog.Logger;
import com.nu.art.core.exceptions.runtime.BadImplementationException;
import com.nu.art.core.generics.Processor;
import com.nu.art.core.tools.ArrayTools;
import com.nu.art.cyborg.core.CyborgBuilder;
import com.nu.art.modular.core.ModuleItem;
import com.nu.art.rtsp.RTSPModule.RTSPServerBuilder;
import com.nu.art.rtsp.Response.ResponseCode;
import com.nu.art.rtsp.params.ParamProcessor_Base;
import com.nu.art.rtsp.params.RTSPParams;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.rtsp.RtspServer.OnRtspSessionListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.nu.art.rtsp.Response.ResponseCode.NotAllowed;
import static net.majorkernelpanic.streaming.SessionBuilder.AUDIO_NONE;
import static net.majorkernelpanic.streaming.SessionBuilder.VIDEO_NONE;

public class RTSPServer
		extends ModuleItem
		implements Runnable {

	private RTSPServerBuilder builder;

	RTSPServer() {
	}

	final void setBuilder(RTSPServerBuilder builder) {
		this.builder = builder;
	}

	@Override
	protected void init() {

	}

	public interface RTSPServerEventsListener {

		void onStartingServerError(Throwable t);

		void onConnectingToClientError(Throwable t);

		void onServerStarted();

		void onServerStopped();

		//		void onClientConnected(RTSPClient client);
		//
		//		void onClientDisconnected(RTSPClient client);
	}

	private RTSPClient[] clients = {};

	private ServerSocket serverSocket;

	private Thread serverThread;

	public boolean isStreaming() {
		return clients.length > 0;
	}

	@Override
	public void run() {
		try {
			logInfo("Starting...");
			serverSocket = new ServerSocket(builder.port);
		} catch (final IOException e) {
			dispatchModuleEvent("Error Starting Server: " + builder.serverName, RTSPServerEventsListener.class, new Processor<RTSPServerEventsListener>() {
				@Override
				public void process(RTSPServerEventsListener listener) {
					listener.onStartingServerError(e);
				}
			});
			return;
		}

		try {
			dispatchModuleEvent("On Server Started: " + builder.serverName, RTSPServerEventsListener.class, new Processor<RTSPServerEventsListener>() {
				@Override
				public void process(RTSPServerEventsListener listener) {
					listener.onServerStarted();
				}
			});
			while (serverThread != null) {
				logInfo("Waiting for client");
				Socket clientSocket = serverSocket.accept();

				logInfo("Connecting client");
				new RTSPClient(clientSocket);
			}
		} catch (final IOException e) {
			dispatchModuleEvent("Error connecting to client: " + builder.serverName, RTSPServerEventsListener.class, new Processor<RTSPServerEventsListener>() {
				@Override
				public void process(RTSPServerEventsListener listener) {
					listener.onConnectingToClientError(e);
				}
			});
		} finally {
			dispatchModuleEvent("On Server Stopped: " + builder.serverName, RTSPServerEventsListener.class, new Processor<RTSPServerEventsListener>() {
				@Override
				public void process(RTSPServerEventsListener listener) {
					listener.onServerStopped();
				}
			});
		}
	}

	final void start() {
		if (serverThread != null || serverSocket != null)
			throw new BadImplementationException("RTSP Server instances are for a single use, create another instance with same configuration!!");

		SessionBuilder.getInstance().setSurfaceView(builder.cameraSurface).setPreviewOrientation(builder.orientation).setAudioEncoder(builder.audioEncoder)
				.setVideoEncoder(builder.videoEncoder).setDestination(builder.destination);

		serverThread = new Thread(this, "RTSP-" + builder.serverName);
		serverThread.start();
	}

	public final void stop() {
		for (RTSPClient client : clients) {
			try {
				client.stop();
			} catch (Exception e) {
				logError("Error disconnecting client: " + client, e);
			}
		}

		serverThread = null;

		try {
			serverSocket.close();
		} catch (IOException e) {
			logError("Error closing server socket", e);
		}
	}

	public class RTSPClient
			extends Logger
			implements Runnable {

		private final String remoteHostAddress;

		private final String localHostAddress;

		private final int localPort;

		private final BufferedReader inputStream;

		private final OutputStream outputStream;

		private final Socket clientSocket;

		private Thread clientThread;

		private Session session;

		RTSPClient(Socket clientSocket)
				throws IOException {
			inputStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			this.clientSocket = clientSocket;
			outputStream = clientSocket.getOutputStream();
			remoteHostAddress = clientSocket.getInetAddress().getHostAddress();
			localHostAddress = clientSocket.getLocalAddress().getHostAddress();
			localPort = clientSocket.getLocalPort();
			clientThread = new Thread(this);
			clientThread.start();
		}

		@Override
		public void run() {
			addRTSPClient(this);
			while (!Thread.interrupted()) {
				Request request = new Request();
				Response response = new Response();

				try {
					if (Request.parseRequest(request, inputStream) == null)
						continue;

					request.log(this);
					try {
						processRequest(request, response);
					} catch (IOException e) {
						response.setResponseCode(ResponseCode.InternalServerError);
					}
				} catch (IOException e) {
					logError("IO Error while processing the request", e);
					break;
				} catch (Exception e) {
					logError("Error processing the request", e);
					response.setResponseCode(ResponseCode.BadRequest);
				}

				try {
					response.log(this);
					String realResponse = response.send(outputStream);
					logWarning(realResponse);
				} catch (Exception e) {
					break;
				}
			}
			removeRTSPClient(this);
		}

		private void processRequest(Request request, Response response)
				throws IOException {
			String cseqHeader = request.headers.get("cseq");
			response.addHeader("Cseq", cseqHeader);

			//Ask for authorization unless this is an OPTIONS request
			switch (request.method.toLowerCase()) {
				case "options":
					options(response);
					return;
				default:
			}

			if (!isAuthorized(request)) {
				unauthorized(response);
				return;
			}

			switch (request.method.toLowerCase()) {
				case "describe":
					describe(request, response);
					return;

				case "setup":
					setup(request, response);
					return;

				case "play":
					play(response);
					return;

				case "pause":
					return;
				case "teardown":
					stop();
					return;

				default:
					response.setResponseCode(NotAllowed);
			}
		}

		private boolean isAuthorized(Request request) {
			if (builder.userName == null || builder.password == null)
				return true;

			String authorizationHeader = request.headers.get("authorization");
			if (authorizationHeader == null)
				return false;

			authorizationHeader = authorizationHeader.substring(authorizationHeader.lastIndexOf(" ") + 1);
			String localEncoded = Base64.encodeToString((builder.userName + ":" + builder.password).getBytes(), Base64.NO_WRAP);

			return localEncoded.equals(authorizationHeader);
		}

		private void unauthorized(Response response) {
			response.setResponseCode(ResponseCode.Unauthorized);
			response.addHeader("WWW-Authenticate", "Basic realm=\"" + builder.serverName + "\"");
		}

		private void options(Response response) {response.addHeader("Public", "DESCRIBE,SETUP,TEARDOWN,PLAY,PAUSE");}

		@SuppressWarnings("StringBufferReplaceableByString")
		private void setup(Request request, Response response)
				throws IOException {
			Pattern p;
			Matcher m;
			int p2, p1, ssrc, trackId, src[];
			String destination;

			p = Pattern.compile("trackID=(\\w+)", Pattern.CASE_INSENSITIVE);
			m = p.matcher(request.uri);

			response.addHeader("Server", builder.serverName);

			if (!m.find()) {
				response.setResponseCode(ResponseCode.BadRequest);
				return;
			}

			trackId = Integer.parseInt(m.group(1));

			if (!session.trackExists(trackId)) {
				response.setResponseCode(ResponseCode.NotFound);
				return;
			}

			p = Pattern.compile("client_port=(\\d+)-(\\d+)", Pattern.CASE_INSENSITIVE);
			m = p.matcher(request.headers.get("transport"));

			if (!m.find()) {
				int[] ports = session.getTrack(trackId).getDestinationPorts();
				p1 = ports[0];
				p2 = ports[1];
			} else {
				p1 = Integer.parseInt(m.group(1));
				p2 = Integer.parseInt(m.group(2));
			}

			ssrc = session.getTrack(trackId).getSSRC();
			src = session.getTrack(trackId).getLocalPorts();
			destination = session.getDestination();

			session.getTrack(trackId).setDestinationPorts(p1, p2);
			session.syncStart(trackId);

			StringBuilder stringBuilder = new StringBuilder();
			stringBuilder.append("Transport: RTP/AVP/UDP;");
			stringBuilder.append(InetAddress.getByName(destination).isMulticastAddress() ? "multicast" : "unicast").append(";");
			stringBuilder.append("destination=").append(session.getDestination()).append(";");
			stringBuilder.append("client_port=").append(p1).append("-").append(p2).append(";");
			stringBuilder.append("server_port=").append(src[0]).append("-").append(src[1]).append(";");
			stringBuilder.append("ssrc=").append(Integer.toHexString(ssrc)).append(";");
			stringBuilder.append("mode=play");
			String transportHeaderValue = stringBuilder.toString();

			response.addHeader("Transport", transportHeaderValue);
			response.addHeader("Session", "1185d20035702ca");
			response.addHeader("Cache-Control", "no-cache");
		}

		private void describe(Request request, Response response)
				throws IOException {
			HashMap<String, String> params = extractQueryParams(request.uri);

			SessionBuilder builder = SessionBuilder.getInstance().clone();
			builder.setAudioEncoder(AUDIO_NONE).setVideoEncoder(VIDEO_NONE);
			// Those parameters must be parsed first or else they won't necessarily be taken into account
			for (String paramName : params.keySet()) {
				String paramValue = params.get(paramName);
				if (paramValue == null)
					continue;

				RTSPParams byKey = RTSPParams.getByKey(paramName);
				if (byKey == null) {
					//				logError("Unidentified param: " + paramName + ", with value: " + paramValue);
					continue;
				}

				Class<? extends ParamProcessor_Base> paramProcessorType = byKey.paramProcessorType;
				ParamProcessor_Base rtspParamProcessor = CyborgBuilder.getModule(RTSPModule.class).getRtspParamProcessor(paramProcessorType);
				rtspParamProcessor.processParam(paramValue, builder);
			}

			//			.. need to figure out what the fuck this is for??
			if (builder.getVideoEncoder() == VIDEO_NONE && builder.getAudioEncoder() == AUDIO_NONE) {
				SessionBuilder b = SessionBuilder.getInstance();
				builder.setVideoEncoder(b.getVideoEncoder());
				builder.setAudioEncoder(b.getAudioEncoder());
			}

			session = builder.build();
			session.setOrigin(localHostAddress);
			if (session.getDestination() == null) {
				session.setDestination(remoteHostAddress);
			}

			// Parse the requested URI and configure the session
			dispatchModuleEvent("New Session", OnRtspSessionListener.class, new Processor<OnRtspSessionListener>() {
				@Override
				public void process(OnRtspSessionListener onRtspSessionListener) {
					onRtspSessionListener.onSessionsChanged();
				}
			});
			session.syncConfigure();

			String requestContent = session.getSessionDescription();
			response.addHeader("Content-Base", remoteHostAddress + ":" + localPort + "/");
			response.addHeader("Content-Type", "application/sdp");
			response.body = requestContent;
		}

		private void play(Response response) {
			String value = "";
			if (session.trackExists(0))
				value += "url=rtsp://" + remoteHostAddress + ":" + localPort + "/trackID=" + 0 + ";seq=0";

			if (session.trackExists(1))
				value += ",url=rtsp://" + remoteHostAddress + ":" + localPort + "/trackID=" + 1 + ";seq=0";

			response.addHeader("RTP-Info", value);
			response.addHeader("Session", "1185d20035702ca");
		}

		private void stop() {
			if (session == null)
				return;

			session.stop();
			try {
				clientSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void addRTSPClient(final RTSPClient client) {
		clients = ArrayTools.appendElement(clients, client);
		//		dispatchModuleEvent("On client connected: " + client, RTSPServerEventsListener.class, new Processor<RTSPServerEventsListener>() {
		//			@Override
		//			public void process(RTSPServerEventsListener listener) {
		//				listener.onClientConnected(client);
		//			}
		//		});
	}

	private void removeRTSPClient(final RTSPClient client) {
		clients = ArrayTools.removeElement(clients, client);
		//		dispatchModuleEvent("On client disconnected: " + client, RTSPServerEventsListener.class, new Processor<RTSPServerEventsListener>() {
		//			@Override
		//			public void process(RTSPServerEventsListener listener) {
		//				listener.onClientDisconnected(client);
		//			}
		//		});
	}

	@NonNull
	private static HashMap<String, String> extractQueryParams(String uri)
			throws UnsupportedEncodingException {
		HashMap<String, String> params = new HashMap<>();

		String query = URI.create(uri).getQuery();
		if (query == null)
			return params;

		String[] queryParams = query.split("&");
		if (queryParams.length == 0)
			throw new IllegalStateException("no query params specified");

		for (String param : queryParams) {
			String[] keyValue = param.split("=");
			if (keyValue.length == 1)
				continue;

			params.put(
					URLEncoder.encode(keyValue[0], "UTF-8").toLowerCase(), // Name
					URLEncoder.encode(keyValue[1], "UTF-8").toLowerCase()  // Value
			);
		}
		return params;
	}
}
