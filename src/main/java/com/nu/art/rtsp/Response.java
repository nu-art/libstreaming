package com.nu.art.rtsp;

import com.nu.art.belog.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

public class Response {

	public static final String LineBreak = "\r\n";

	enum ResponseCode {
		Ok(200, "OK"),
		BadRequest(400, "Bad Request"),
		Unauthorized(401, "Unauthorized"),
		NotFound(404, "Not Found"),
		NotAllowed(405, "Not Allowed"),
		UnsupportedTransport(461, "Unsupported Transport"),
		InternalServerError(500, "Internal Server Error"),
		//
		;

		private final int responseCode;

		private final String responseMessage;

		ResponseCode(int responseCode, String responseMessage) {
			this.responseCode = responseCode;
			this.responseMessage = responseMessage;
		}

		@Override
		public String toString() {
			return responseCode + " " + responseMessage;
		}
	}

	public final void log(Logger logger) {
		logger.logInfo("+-------------------- Response ---------------------+");
		logger.logInfo("+---- Response Code: " + responseCode);
		logger.logInfo("+---- Body: " + body);
		if (headers.size() > 0) {
			logger.logDebug("+---- Headers: ");
			for (String key : headers.keySet()) {
				logger.logDebug("+------ " + key + " <> " + headers.get(key));
			}
		}
		logger.logInfo("+--------------------------------------------------+");
	}

	// Status code definitions
	private ResponseCode responseCode = ResponseCode.Ok;

	public String body = "";

	private HashMap<String, String> headers = new HashMap<>();

	final void addHeader(String key, String value) {
		String headerValue = headers.get(key);
		if (headerValue == null)
			headerValue = "";
		else
			headerValue += ";";

		headerValue += value;
		headers.put(key, headerValue);
	}

	@SuppressWarnings("StringBufferReplaceableByString")
	public String send(OutputStream output)
			throws IOException {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("RTSP/1.0 ").append(responseCode.responseCode).append(" ").append(responseCode.responseMessage).append(LineBreak);
		stringBuilder.append("Content-Length: ").append(body.length()).append(LineBreak);
		for (String key : headers.keySet()) {
			stringBuilder.append(key).append(": ").append(headers.get(key)).append(LineBreak);
		}

		stringBuilder.append(LineBreak);
		if (body != null)
			stringBuilder.append(body);

		String response = stringBuilder.toString();
		output.write(response.getBytes());

		return response;
	}

	void setResponseCode(ResponseCode responseCode) {
		this.responseCode = responseCode;
	}
}
