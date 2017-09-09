package com.nu.art.rtsp;

import android.util.Base64;

import com.nu.art.core.exceptions.runtime.BadImplementationException;
import com.nu.art.core.generics.Processor;
import com.nu.art.core.tools.ArrayTools;
import com.nu.art.cyborg.core.CyborgModuleItem;
import com.nu.art.modular.core.ModuleItem;
import com.nu.art.rtsp.RTSPModule.RTSPServerBuilder;
import com.nu.art.rtsp.descriptors.SessionDescriptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;

import static com.nu.art.rtsp.RTSPClientSession.Regexp_TrackId;
import static com.nu.art.rtsp.Response.ResponseCode.BadRequest;
import static com.nu.art.rtsp.Response.ResponseCode.InternalServerError;
import static com.nu.art.rtsp.Response.ResponseCode.NotFound;
import static com.nu.art.rtsp.Response.ResponseCode.Unauthorized;

public class RTSPServer
		extends ModuleItem
		implements Runnable {

	RTSPServer() {
	}

	final void setBuilder(RTSPServerBuilder builder) {
		this.builder = builder;
	}

	@Override
	protected void init() {

	}

	public interface RTSPServerEventsListener {

		void onClientConnected(RTSPClientSession client);

		void onClientDisconnected(RTSPClientSession client);
	}

	private RTSPClientSession[] clients = {};

	private ServerSocket serverSocket;

	private Thread serverThread;

	private RTSPServerBuilder builder;

	public boolean isStreaming() {
		return clients.length > 0;
	}

	@Override
	public void run() {
		try {
			logInfo("Starting...");
			serverSocket = new ServerSocket(builder.port);
		} catch (IOException e) {
			logError("Error Starting Server: " + builder.serverName, e);
			return;
		}

		try {
			while (serverThread != null) {
				logInfo("Waiting for client");
				Socket clientSocket = serverSocket.accept();

				logInfo("Got a new client connection");
				new ClientSocketHandler(clientSocket);
			}
		} catch (IOException e) {
			logError("Error connecting to client: " + builder.serverName, e);
		} finally {
			try {
				serverSocket.close();
			} catch (IOException e) {
				logError("Error closing server socket", e);
			}
		}
	}

	final void start() {
		if (serverThread != null || serverSocket != null)
			throw new BadImplementationException("RTSP Server instances are for a single use, create another instance with same configuration!!");

		serverThread = new Thread(this, "RTSP-" + builder.serverName);
		serverThread.start();
	}

	public final void stop() {
		serverThread = null;
		try {
			serverSocket.close();
			for (RTSPClientSession client : clients) {
				client.stop();
			}
		} catch (IOException e) {
			logError("Error closing server socket", e);
		}
	}

	private void addRTSPClient(final RTSPClientSession client) {
		clients = ArrayTools.appendElement(clients, client);
		dispatchModuleEvent("On client connected: " + client, RTSPServerEventsListener.class, new Processor<RTSPServerEventsListener>() {
			@Override
			public void process(RTSPServerEventsListener listener) {
				listener.onClientConnected(client);
			}
		});
	}

	private void removeRTSPClient(final RTSPClientSession client) {
		clients = ArrayTools.removeElement(clients, client);
		dispatchModuleEvent("On client disconnected: " + client, RTSPServerEventsListener.class, new Processor<RTSPServerEventsListener>() {
			@Override
			public void process(RTSPServerEventsListener listener) {
				listener.onClientDisconnected(client);
			}
		});
	}

	private class ClientSocketHandler
			extends CyborgModuleItem
			implements Runnable {

		private final BufferedReader inputStream;

		private final OutputStream outputStream;

		private final Socket clientSocket;

		private final String remoteHostAddress;

		private final String localHostAddress;

		private final int localPort;

		private final RTSPClientSession client;

		public ClientSocketHandler(Socket clientSocket)
				throws IOException {
			this.clientSocket = clientSocket;
			inputStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			outputStream = clientSocket.getOutputStream();
			remoteHostAddress = clientSocket.getInetAddress().getHostAddress();
			localHostAddress = clientSocket.getLocalAddress().getHostAddress();
			localPort = clientSocket.getLocalPort();
			client = new RTSPClientSession();
		}

		@Override
		public void run() {
			addRTSPClient(this);

			while (!Thread.interrupted()) {
				Request request = new Request();
				Response response = new Response();

				try {
					Request.parseRequest(request, inputStream);
					request.log(this);
					try {
						processRequest(request, response);
					} catch (IOException e) {
						response.setResponseCode(InternalServerError);
					}
				} catch (IOException e) {
					logError("IO Error while processing the request", e);
					break;
				} catch (Exception e) {
					logError("Error processing the request", e);
					response.setResponseCode(BadRequest);
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

		private void processRequest(Request request, Response response) {
			String cseqHeader = request.headers.get("cseq");
			response.addHeader("Cseq", cseqHeader);

			// OPTIONS does not require authentication... ?
			switch (request.method.toLowerCase()) {
				case "options":
					response.addHeader("Public", "DESCRIBE,SETUP,TEARDOWN,PLAY,PAUSE");
					return;
				default:
			}

			if (!isAuthorized(request)) {
				response.setResponseCode(Unauthorized);
				response.addHeader("WWW-Authenticate", "Basic realm=\"" + builder.serverName + "\"");
				return;
			}

			Matcher m;
			switch (request.method.toLowerCase()) {
				case "describe":
					response.body = builder.sessionDescriptor.getSessionDescription(builder.serverName, localHostAddress, remoteHostAddress);
					response.addHeader("Content-Base", remoteHostAddress + ":" + localPort + "/");
					response.addHeader("Content-Type", "application/sdp");
					return;

				case "setup":
					response.addHeader("Server", builder.serverName);

					m = Regexp_TrackId.matcher(request.uri);
					if (!m.find()) {
						response.setResponseCode(BadRequest);
						return;
					}

					int trackId = Integer.parseInt(m.group(1));
					if (!builder.sessionDescriptor.isTrackExists(trackId)) {
						response.setResponseCode(NotFound);
						return;
					}


					setup(request, response);
					return;
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

		@Override
		protected void init() {
			new Thread(this, "SocketHandler-" + remoteHostAddress).start();
		}
	}
}
