/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.majorkernelpanic.streaming;

import android.hardware.Camera.CameraInfo;

import net.majorkernelpanic.streaming.audio.AACStream;
import net.majorkernelpanic.streaming.audio.AMRNBStream;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.audio.AudioStream;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.video.H263Stream;
import net.majorkernelpanic.streaming.video.H264Stream;
import net.majorkernelpanic.streaming.video.VideoQuality;
import net.majorkernelpanic.streaming.video.VideoStream;

import java.io.IOException;

/**
 * Call {@link #getInstance()} to get access to the SessionBuilder.
 */
public class SessionBuilder {

	public final static String TAG = "SessionBuilder";

	/**
	 * Can be used with {@link #setVideoEncoder}.
	 */
	public final static int VIDEO_NONE = 0;

	/**
	 * Can be used with {@link #setVideoEncoder}.
	 */
	public final static int VIDEO_H264 = 1;

	/**
	 * Can be used with {@link #setVideoEncoder}.
	 */
	public final static int VIDEO_H263 = 2;

	/**
	 * Can be used with {@link #setAudioEncoder}.
	 */
	public final static int AUDIO_NONE = 0;

	/**
	 * Can be used with {@link #setAudioEncoder}.
	 */
	public final static int AUDIO_AMRNB = 3;

	/**
	 * Can be used with {@link #setAudioEncoder}.
	 */
	public final static int AUDIO_AAC = 5;

	// Default configuration
	private VideoQuality mVideoQuality = VideoQuality.DEFAULT_VIDEO_QUALITY;

	private AudioQuality mAudioQuality = AudioQuality.DEFAULT_AUDIO_QUALITY;

	private int mVideoEncoder = VIDEO_H263;

	private int mAudioEncoder = AUDIO_AMRNB;

	private int mCamera = CameraInfo.CAMERA_FACING_BACK;

	private int mTimeToLive = 64;

	private int mOrientation = 0;

	private boolean mFlash = false;

	private SurfaceView mSurfaceView = null;

	private String mDestination = null;

	private Session.Callback mCallback = null;

	private byte videoApi;

	private byte audioApi;

	// Removes the default public constructor
	private SessionBuilder() {}

	// The SessionManager implements the singleton pattern
	private static volatile SessionBuilder sInstance = null;

	/**
	 * Returns a reference to the {@link SessionBuilder}.
	 *
	 * @return The reference to the {@link SessionBuilder}
	 */
	public final static SessionBuilder getInstance() {
		if (sInstance == null) {
			synchronized (SessionBuilder.class) {
				if (sInstance == null) {
					SessionBuilder.sInstance = new SessionBuilder();
				}
			}
		}
		return sInstance;
	}

	/**
	 * Creates a new {@link Session}.
	 *
	 * @return The new Session
	 *
	 * @throws IOException
	 */
	public Session build() {
		Session session;

		session = new Session();
		session.setDestination(mDestination);
		session.setTimeToLive(mTimeToLive);
		session.setCallback(mCallback);

		switch (mAudioEncoder) {
			case AUDIO_AAC:
				AACStream stream = new AACStream();
				session.addAudioTrack(stream);
				break;

			case AUDIO_AMRNB:
				session.addAudioTrack(new AMRNBStream());
				break;
		}

		switch (mVideoEncoder) {
			case VIDEO_H263:
				session.addVideoTrack(new H263Stream(mCamera));
				break;

			case VIDEO_H264:
				H264Stream stream = new H264Stream(mCamera);
				session.addVideoTrack(stream);
				break;
		}

		if (session.getVideoTrack() != null) {
			VideoStream video = session.getVideoTrack();
			video.setFlashState(mFlash);
			video.setVideoQuality(mVideoQuality);
			video.setSurfaceView(mSurfaceView);
			video.setPreviewOrientation(mOrientation);
			video.setDestinationPorts(5006);
			if (videoApi > 0)
				video.setStreamingMethod(videoApi);
		}

		if (session.getAudioTrack() != null) {
			AudioStream audio = session.getAudioTrack();
			audio.setAudioQuality(mAudioQuality);
			audio.setDestinationPorts(5004);
			if (audioApi > 0)
				audio.setStreamingMethod(audioApi);
		}

		return session;
	}

	/**
	 * Sets the destination of the session.
	 */
	public SessionBuilder setDestination(String destination) {
		mDestination = destination;
		return this;
	}

	/**
	 * Sets the video stream quality.
	 */
	public SessionBuilder setVideoQuality(VideoQuality quality) {
		mVideoQuality = quality.clone();
		return this;
	}

	/**
	 * Sets the audio encoder.
	 */
	public SessionBuilder setAudioEncoder(int encoder) {
		mAudioEncoder = encoder;
		return this;
	}

	/**
	 * Sets the audio quality.
	 */
	public SessionBuilder setAudioQuality(AudioQuality quality) {
		mAudioQuality = quality.clone();
		return this;
	}

	/**
	 * Sets the default video encoder.
	 */
	public SessionBuilder setVideoEncoder(int encoder) {
		mVideoEncoder = encoder;
		return this;
	}

	public SessionBuilder setFlashEnabled(boolean enabled) {
		mFlash = enabled;
		return this;
	}

	public SessionBuilder setCamera(int camera) {
		mCamera = camera;
		return this;
	}

	public SessionBuilder setTimeToLive(int ttl) {
		mTimeToLive = ttl;
		return this;
	}

	/**
	 * Sets the SurfaceView required to preview the video stream.
	 **/
	public SessionBuilder setSurfaceView(SurfaceView surfaceView) {
		mSurfaceView = surfaceView;
		return this;
	}

	/**
	 * Sets the orientation of the preview.
	 *
	 * @param orientation The orientation of the preview
	 */
	public SessionBuilder setPreviewOrientation(int orientation) {
		mOrientation = orientation;
		return this;
	}

	public SessionBuilder setCallback(Session.Callback callback) {
		mCallback = callback;
		return this;
	}

	/**
	 * Returns the audio encoder set with {@link #setAudioEncoder(int)}.
	 */
	public int getAudioEncoder() {
		return mAudioEncoder;
	}

	/**
	 * Returns the id of the {@link android.hardware.Camera} set with {@link #setCamera(int)}.
	 */
	public int getCamera() {
		return mCamera;
	}

	/**
	 * Returns the video encoder set with {@link #setVideoEncoder(int)}.
	 */
	public int getVideoEncoder() {
		return mVideoEncoder;
	}

	public SessionBuilder clone() {
		return new SessionBuilder().setDestination(mDestination)
															 .setSurfaceView(mSurfaceView)
															 .setPreviewOrientation(mOrientation)
															 .setVideoQuality(mVideoQuality)
															 .setVideoEncoder(mVideoEncoder)
															 .setFlashEnabled(mFlash)
															 .setCamera(mCamera)
															 .setTimeToLive(mTimeToLive)
															 .setAudioEncoder(mAudioEncoder)
															 .setAudioQuality(mAudioQuality)
															 .setCallback(mCallback);
	}

	public void setVideoApi(byte videoApi) {
		this.videoApi = videoApi;
	}

	public void setAudioApi(byte audioApi) {
		this.audioApi = audioApi;
	}
}
