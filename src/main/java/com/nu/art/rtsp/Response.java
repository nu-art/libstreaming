package com.nu.art.rtsp;

import com.nu.art.core.tools.ArrayTools;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

public class Response {

	enum ResponseCode {
		Ok(200, "OK"),
		BadRequest(400, "Bad Request"),
		Unauthorized(401, "Unauthorized"),
		NotFound(404, "Not Found"),
		NotAllowed(405, "Not Allowed"),
		InternalServerError(500, "Internal Server Error"),
		//
		;

		private final int responseCode;

		private final String responseMessage;

		ResponseCode(int responseCode, String responseMessage) {
			this.responseCode = responseCode;
			this.responseMessage = responseMessage;
		}

	}

	public static final String[] EmptyHeaders = {};

	// Status code definitions
	private ResponseCode responseCode = ResponseCode.Ok;

	public String content = "";

	private HashMap<String, String[]> headers = new HashMap<>();

	public final void addHeader(String key, String value) {
		String[] headers = getHeaders(key);
		setHeaders(key, ArrayTools.appendElement(headers, value));
	}

	private void setHeaders(String key, String[] values) {
		headers.put(key, values);
	}

	private String[] getHeaders(String key) {
		String[] values = headers.get(key);
		return values == null ? EmptyHeaders : values;
	}

	@SuppressWarnings("StringBufferReplaceableByString")
	public void send(OutputStream output)
			throws IOException {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("RTSP/1.0 ").append(responseCode.responseCode).append(" ").append(responseCode.responseMessage).append("\r\n");
		stringBuilder.append("Content-Length: ").append(content.length()).append("\r\n");

		String response = stringBuilder.toString();
		output.write(response.getBytes());
	}

	void setResponseCode(ResponseCode responseCode) {
		this.responseCode = responseCode;
	}
}
