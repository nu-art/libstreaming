package com.nu.art.rtsp;

import com.nu.art.belog.Logger;
import com.nu.art.core.utils.RegexAnalyzer;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Locale;

public class Request {

	// Parse method & uri
	private static RegexAnalyzer Regexp_Method = new RegexAnalyzer("(\\w+) (\\S+) RTSP", false);

	private static RegexAnalyzer Regexp_Header = new RegexAnalyzer("(\\S+):\\s*(.+)", false);

	public String method;

	public String uri;

	public HashMap<String, String> headers = new HashMap<>();

	public final void log(Logger logger) {
		logger.logInfo("+-------------------- Request ---------------------+");
		logger.logInfo("+---- URL: " + uri);
		logger.logInfo("+---- Method: " + method);
		if (headers.size() > 0) {
			logger.logInfo("+---- Headers: ");
			for (String key : headers.keySet()) {
				logger.logInfo("+------ " + key + " <> " + headers.get(key));
			}
		}
		logger.logInfo("+--------------------------------------------------+");
	}

	/**
	 * Parse the method, uri & headers of a RTSP request
	 */
	public static Request parseRequest(Request request, BufferedReader input)
			throws IOException, IllegalStateException {
		String line;

		// Parsing request method & uri
		if ((line = input.readLine()) == null)
			throw new SocketException("Client disconnected");

		String[] result = Regexp_Method.findRegex(1, line, 1, 2);
		request.method = result[0];
		request.uri = result[1];

		// Parsing headers of the request
		while ((line = input.readLine()) != null && line.length() > 3) {
			result = Regexp_Header.findRegex(1, line, 1, 2);
			request.headers.put(result[0].toLowerCase(Locale.US), result[1]);
		}

		if (line == null)
			throw new SocketException("Client disconnected");

		// It's not an error, it's just easier to follow what's happening in logcat with the request in red
		return request;
	}
}

