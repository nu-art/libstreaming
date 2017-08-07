package com.nu.art.rtsp;

import android.support.annotation.NonNull;
import android.util.Base64;

import com.nu.art.core.exceptions.runtime.BadImplementationException;
import com.nu.art.core.generics.Processor;
import com.nu.art.cyborg.core.CyborgBuilder;
import com.nu.art.modular.core.ModuleItem;
import com.nu.art.rtsp.Response.ResponseCode;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.rtsp.RtspServer.OnRtspSessionListener;
import net.majorkernelpanic.streaming.rtsp.UriParser;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.nu.art.rtsp.Response.ResponseCode.NotAllowed;

public class RTSPServer
		extends ModuleItem
		implements Runnable {

	@Override
	protected void init() {

	}

	public interface RTSPServerEventsListener {

		void onClientConnected(RTSPClient client);

		void onClientDisconnected(RTSPClient client);
	}

	private final ArrayList<RTSPClient> clients = new ArrayList<>();

	private String serverName = "unnamed";

	private int port;

	private String userName;

	private String password;

	private ServerSocket serverSocket;

	private Thread serverThread;

	public RTSPServer setServerName(String serverName) {
		this.serverName = serverName;
		return this;
	}

	public RTSPServer setPassword(String password) {
		this.password = password;
		return this;
	}

	public RTSPServer setPort(int port) {
		this.port = port;
		return this;
	}

	public RTSPServer setUserName(String userName) {
		this.userName = userName;
		return this;
	}

	@Override
	public void run() {
		try {
			serverSocket = new ServerSocket(port);
			while (serverThread == null) {
				Socket clientSocket = serverSocket.accept();
				new RTSPClient(clientSocket);
			}
			serverSocket.close();
		} catch (IOException e) {

		}
	}

	final void start()
			throws IOException {
		if (serverThread != null || serverSocket != null)
			throw new BadImplementationException("RTSP Server instances are for a single use, create another instance with same configuration!!");

		serverThread = new Thread(this, "RTSP-" + serverName);
		serverThread.start();
	}

	final void stop()
			throws IOException {
		serverThread = null;
	}

	class RTSPClient
			implements Runnable {

		private final String remoteHostAddress;

		private final String localHostAddress;

		private final int localPort;

		private final Socket clientSocket;

		private final BufferedReader inputStream;

		private final OutputStream outputStream;

		private Thread clientThread;

		private Session session;

		RTSPClient(Socket clientSocket)
				throws IOException {
			this.clientSocket = clientSocket;

			inputStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
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
				Response response = new Response(request);

				try {
					Request.parseRequest(request, inputStream);
					try {
						processRequest(request, response);
					} catch (IOException e) {
						response.setResponseCode(ResponseCode.InternalServerError);
					}
				} catch (IOException e) {
					break;
				} catch (Exception e) {
					response.setResponseCode(ResponseCode.BadRequest);
				}

				try {
					response.send(outputStream);
				} catch (Exception e) {
					break;
				}
			}
			removeRTSPClient(this);
		}

		private void processRequest(Request request, Response response)
				throws IOException {

			//Ask for authorization unless this is an OPTIONS request
											/* ********************************************************************************** */
								/* ********************************* Method OPTIONS ********************************* */
								/* ********************************************************************************** */

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
				case "teardown":
					return;

				default:
					response.setResponseCode(NotAllowed);
			}
		}

		private boolean isAuthorized(Request request) {
			if (userName == null || password == null)
				return true;

			String authorizationHeader = request.headers.get("authorization");
			if (authorizationHeader == null)
				return false;

			authorizationHeader = authorizationHeader.substring(authorizationHeader.lastIndexOf(" ") + 1);
			String localEncoded = Base64.encodeToString((userName + ":" + password).getBytes(), Base64.NO_WRAP);

			return localEncoded.equals(authorizationHeader);
		}

		private void unauthorized(Response response) {
			response.setResponseCode(ResponseCode.Unauthorized);
			response.addHeader("WWW-Authenticate", "Basic realm=\"" + serverName + "\"");
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

			session = UriParser.parse(params);
			session.setOrigin(localHostAddress);
			if (session.getDestination() == null) {
				session.setDestination(remoteHostAddress);
			}

			// Parse the requested URI and configure the session
			CyborgBuilder.getInstance().dispatchModuleEvent("New Session", OnRtspSessionListener.class, new Processor<OnRtspSessionListener>() {
				@Override
				public void process(OnRtspSessionListener onRtspSessionListener) {
					onRtspSessionListener.onSessionsChanged();
				}
			});
			session.syncConfigure();

			String requestContent = session.getSessionDescription();
			response.addHeader("Content-Base", remoteHostAddress + ":" + localPort + "/");
			response.addHeader("Content-Type", "application/sdp");
			response.content = requestContent;
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
	}

	private void addRTSPClient(final RTSPClient client) {
		clients.add(client);
		dispatchModuleEvent("On client connected: " + client, RTSPServerEventsListener.class, new Processor<RTSPServerEventsListener>() {
			@Override
			public void process(RTSPServerEventsListener listener) {
				listener.onClientConnected(client);
			}
		});
	}

	private void removeRTSPClient(final RTSPClient client) {
		clients.remove(client);
		dispatchModuleEvent("On client connected: " + client, RTSPServerEventsListener.class, new Processor<RTSPServerEventsListener>() {
			@Override
			public void process(RTSPServerEventsListener listener) {
				listener.onClientDisconnected(client);
			}
		});
	}

	@NonNull
	private static HashMap<String, String> extractQueryParams(String uri)
			throws UnsupportedEncodingException {
		String query = URI.create(uri).getQuery();
		if (query == null)
			throw new IllegalStateException("no query params specified");

		String[] queryParams = query.split("&");
		if (queryParams.length == 0)
			throw new IllegalStateException("no query params specified");

		HashMap<String, String> params = new HashMap<>();
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
