package com.nu.art.rtsp;

import android.support.annotation.NonNull;
import android.util.Base64;

import com.nu.art.belog.Logger;
import com.nu.art.core.generics.Processor;
import com.nu.art.cyborg.core.CyborgBuilder;
import com.nu.art.cyborg.core.CyborgModuleItem;
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
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.nu.art.rtsp.Response.ResponseCode.BadRequest;
import static com.nu.art.rtsp.Response.ResponseCode.InternalServerError;
import static com.nu.art.rtsp.Response.ResponseCode.NotAllowed;
import static com.nu.art.rtsp.Response.ResponseCode.NotFound;
import static com.nu.art.rtsp.Response.ResponseCode.Unauthorized;
import static net.majorkernelpanic.streaming.SessionBuilder.AUDIO_NONE;
import static net.majorkernelpanic.streaming.SessionBuilder.VIDEO_NONE;

class RTSPClientSession {

	public static final Pattern Regexp_TrackId = Pattern.compile("trackID=(\\w+)", Pattern.CASE_INSENSITIVE);

	public static final Pattern Pattern_ClientPorts = Pattern.compile("client_port=(\\d+)-(\\d+)", Pattern.CASE_INSENSITIVE);

	private Session session;

	RTSPClientSession() {}

	private void processRequest(Request request, Response response)
			throws IOException {

		switch (request.method.toLowerCase()) {
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

	@SuppressWarnings("StringBufferReplaceableByString")
	private void setup(Request request, Response response)
			throws IOException {
		Matcher m;
		int p2, p1, ssrc, trackId, src[];
		String destination;

		m = Regexp_TrackId.matcher(request.uri);

		if (!m.find()) {
			response.setResponseCode(BadRequest);
			return;
		}

		trackId = Integer.parseInt(m.group(1));

		if (!session.trackExists(trackId)) {
			response.setResponseCode(NotFound);
			return;
		}


		ssrc = session.getTrack(trackId).getSSRC();
		src = session.getTrack(trackId).getLocalPorts();
		destination = session.getDestination();

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
		session.stop();
		try {
			clientSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@NonNull
	private HashMap<String, String> extractQueryParams(String uri)
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