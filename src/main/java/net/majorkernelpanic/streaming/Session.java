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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.nu.art.core.exceptions.runtime.NotImplementedYetException;

import net.majorkernelpanic.streaming.audio.AACStream;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.exceptions.ConfNotSupportedException;
import net.majorkernelpanic.streaming.exceptions.InvalidSurfaceException;
import net.majorkernelpanic.streaming.exceptions.StorageUnavailableException;
import net.majorkernelpanic.streaming.rtsp.RtspClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.nu.art.rtsp.Response.LineBreak;

/**
 * You should instantiate this class with the {@link SessionBuilder}.<br />
 * This is the class you will want to use to stream audio and or video to some peer using RTP.<br />
 *
 * synchronous and asynchronous functions to start and stop those steams.
 * You should implement a callback interface {@link Callback} to receive notifications and error reports.<br />
 *
 * If you want to stream to a RTSP server, you will need an instance of this class and hand it to a {@link RtspClient}.
 *
 * If you don't use the RTSP protocol, you will still need to send a session description to the receiver
 * for him to be able to decode your audio/video streams. You can obtain this session description by calling
 * {@link #configure()} or {@link #syncConfigure()} to configure the session with its parameters
 * (audio samplingrate, video resolution) and then {@link Session#getSessionDescription()}.<br />
 *
 * See the example 2 here: https://github.com/fyhertz/libstreaming-examples to
 * see an example of how to get a SDP.<br />
 *
 * See the example 3 here: https://github.com/fyhertz/libstreaming-examples to
 * see an example of how to stream to a RTSP server.<br />
 */
public class Session {

	public final static String TAG = "Session";

	public final static int STREAM_VIDEO = 0x01;

	public final static int STREAM_AUDIO = 0x00;

	/**
	 * Some app is already using a camera (Camera.open() has failed).
	 */
	public final static int ERROR_CAMERA_ALREADY_IN_USE = 0x00;

	/**
	 * The phone may not support some streaming parameters that you are trying to use (bit rate, frame rate, resolution...).
	 */
	public final static int ERROR_CONFIGURATION_NOT_SUPPORTED = 0x01;

	/**
	 * The internal storage of the phone is not ready.
	 * libstreaming tried to store a test file on the sdcard but couldn't.
	 * See H264Stream and AACStream to find out why libstreaming would want to something like that.
	 */
	public final static int ERROR_STORAGE_NOT_READY = 0x02;

	/**
	 * The phone has no flash.
	 */
	public final static int ERROR_CAMERA_HAS_NO_FLASH = 0x03;

	/**
	 * The supplied SurfaceView is not a valid surface, or has not been created yet.
	 */
	public final static int ERROR_INVALID_SURFACE = 0x04;

	/**
	 * The destination set with {@link Session#setDestination(String)} could not be resolved.
	 * May mean that the phone has no access to the internet, or that the DNS server could not
	 * resolved the host name.
	 */
	public final static int ERROR_UNKNOWN_HOST = 0x05;

	/**
	 * Some other error occurred !
	 */
	public final static int ERROR_OTHER = 0x06;

	private String mOrigin;

	private String mDestination;

	private int mTimeToLive = 64;

	private long mTimestamp;

	private AACStream mAudioStream = null;

	private Callback mCallback;

	private Handler mMainHandler;

	private Handler mHandler;

	/**
	 * Creates a streaming session that can be customized by adding tracks.
	 */
	public Session() {
		long uptime = System.currentTimeMillis();

		HandlerThread thread = new HandlerThread("net.majorkernelpanic.streaming.Session");
		thread.start();

		mHandler = new Handler(thread.getLooper());
		mMainHandler = new Handler(Looper.getMainLooper());
		mTimestamp = (uptime / 1000) << 32 & (((uptime - ((uptime / 1000) * 1000)) >> 32) / 1000); // NTP timestamp
		mOrigin = "127.0.0.1";
	}

	/**
	 * The callback interface you need to implement to get some feedback
	 * Those will be called from the UI thread.
	 */
	public interface Callback {

		/**
		 * Called periodically to inform you on the bandwidth
		 * consumption of the streams when streaming.
		 */
		public void onBitrateUpdate(long bitrate);

		/**
		 * Called when some error occurs.
		 */
		public void onSessionError(int reason, int streamType, Exception e);

		/**
		 * has correctly been started.
		 * If an error occurs while starting the preview,
		 * {@link Callback#onSessionError(int, int, Exception)} will be
		 * called instead of {@link Callback#onPreviewStarted()}.
		 */
		public void onPreviewStarted();

		/**
		 * Called when the session has correctly been configured
		 * after calling {@link Session#configure()}.
		 * If an error occurs while configuring the {@link Session},
		 * {@link Callback#onSessionError(int, int, Exception)} will be
		 * called instead of  {@link Callback#onSessionConfigured()}.
		 */
		public void onSessionConfigured();

		/**
		 * Called when the streams of the session have correctly been started.
		 * If an error occurs while starting the {@link Session},
		 * {@link Callback#onSessionError(int, int, Exception)} will be
		 * called instead of  {@link Callback#onSessionStarted()}.
		 */
		public void onSessionStarted();

		/**
		 * Called when the stream of the session have been stopped.
		 */
		public void onSessionStopped();
	}

	/**
	 * You probably don't need to use that directly, use the {@link SessionBuilder}.
	 */
	void addAudioTrack(AACStream track) {
		removeAudioTrack();
		mAudioStream = track;
	}

	/**
	 * You probably don't need to use that directly, use the {@link SessionBuilder}.
	 */
	void removeAudioTrack() {
		if (mAudioStream != null) {
			mAudioStream.stop();
			mAudioStream = null;
		}
	}

	/**
	 * Returns the underlying {@link AACStream} used by the {@link Session}.
	 */
	public AACStream getAudioTrack() {
		return mAudioStream;
	}

	/**
	 * Sets the callback interface that will be called by the {@link Session}.
	 *
	 * @param callback The implementation of the {@link Callback} interface
	 */
	public void setCallback(Callback callback) {
		mCallback = callback;
	}

	/**
	 * The origin address of the session.
	 * It appears in the session description.
	 *
	 * @param origin The origin address
	 */
	public void setOrigin(String origin) {
		mOrigin = origin;
	}

	/**
	 * The destination address for all the streams of the session. <br />
	 * Changes will be taken into account the next time you start the session.
	 *
	 * @param destination The destination address
	 */
	public void setDestination(String destination) {
		mDestination = destination;
	}

	/**
	 * Set the TTL of all packets sent during the session. <br />
	 * Changes will be taken into account the next time you start the session.
	 *
	 * @param ttl The Time To Live
	 */
	public void setTimeToLive(int ttl) {
		mTimeToLive = ttl;
	}

	/**
	 * Sets the configuration of the stream. <br />
	 * You can call this method at any time and changes will take
	 * effect next time you call {@link #configure()}.
	 *
	 * @param quality Quality of the stream
	 */
	public void setAudioQuality(AudioQuality quality) {
		if (mAudioStream != null) {
			mAudioStream.setAudioQuality(quality);
		}
	}

	/**
	 * Returns the {@link Callback} interface that was set with
	 * {@link #setCallback(Callback)} or null if none was set.
	 */
	public Callback getCallback() {
		return mCallback;
	}

	/**
	 * Returns a Session Description that can be stored in a file or sent to a client with RTSP.
	 *
	 * @return The Session Description.
	 *
	 * @throws IllegalStateException Thrown when {@link #setDestination(String)} has never been called.
	 */
	public String getSessionDescription() {
		StringBuilder sessionDescription = new StringBuilder();
		if (mDestination == null) {
			throw new IllegalStateException("setDestination() has not been called !");
		}
		sessionDescription.append("v=0").append(LineBreak);
		// TODO: Add IPV6 support
		sessionDescription.append("o=- ").append(mTimestamp).append(" ").append(mTimestamp).append(" IN IP4 ").append(mOrigin).append(LineBreak);
		sessionDescription.append("s=Unnamed").append(LineBreak);
		sessionDescription.append("i=N/A").append(LineBreak);
		sessionDescription.append("c=IN IP4 ").append(mDestination).append(LineBreak);
		// t=0 0 means the session is permanent (we don't know when it will stop)
		sessionDescription.append("t=0 0").append(LineBreak);
		sessionDescription.append("a=recvonly").append(LineBreak);
		// Prevents two different sessions from using the same peripheral at the same time
		if (mAudioStream != null) {
			sessionDescription.append(mAudioStream.getSessionDescription());
			sessionDescription.append("a=control:trackID=" + 0).append(LineBreak);
		}
		return sessionDescription.toString();
	}

	/**
	 * Returns the destination set with {@link #setDestination(String)}.
	 */
	public String getDestination() {
		return mDestination;
	}

	/**
	 * Returns an approximation of the bandwidth consumed by the session in bit per second.
	 */
	public long getBitrate() {
		long sum = 0;
		if (mAudioStream != null)
			sum += mAudioStream.getBitrate();
		return sum;
	}

	/**
	 * Indicates if a track is currently running.
	 */
	public boolean isStreaming() {
		return (mAudioStream != null && mAudioStream.isStreaming());
	}

	/**
	 * Configures all streams of the session.
	 **/
	public void configure() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				try {
					syncConfigure();
				} catch (Exception e) {}
				;
			}
		});
	}

	/**
	 * Does the same thing as {@link #configure()}, but in a synchronous manner. <br />
	 * Throws exceptions in addition to calling a callback
	 * {@link Callback#onSessionError(int, int, Exception)} when
	 * an error occurs.
	 **/
	public void syncConfigure()
			throws RuntimeException, IOException {

		for (int id = 0; id < 2; id++) {
			AACStream stream = mAudioStream;
			if (stream != null && !stream.isStreaming()) {
				try {
					stream.configure();
				} catch (StorageUnavailableException e) {
					postError(ERROR_STORAGE_NOT_READY, id, e);
					throw e;
				} catch (ConfNotSupportedException e) {
					postError(ERROR_CONFIGURATION_NOT_SUPPORTED, id, e);
					throw e;
				} catch (InvalidSurfaceException e) {
					postError(ERROR_INVALID_SURFACE, id, e);
					throw e;
				} catch (IOException e) {
					postError(ERROR_OTHER, id, e);
					throw e;
				}
			}
		}
		postSessionConfigured();
	}

	/**
	 * Asynchronously starts all streams of the session.
	 **/
	public void start() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				try {
					syncStart();
				} catch (Exception e) {}
			}
		});
	}

	/**
	 * Starts a stream in a synchronous manner. <br />
	 * Throws exceptions in addition to calling a callback.
	 *
	 * @param id The id of the stream to start
	 **/
	public void syncStart(int id)
			throws ConfNotSupportedException, InvalidSurfaceException, IOException {

		AACStream stream = mAudioStream;
		if (stream != null && !stream.isStreaming()) {
			try {
				InetAddress destination = InetAddress.getByName(mDestination);
				stream.setTimeToLive(mTimeToLive);
				stream.setDestinationAddress(destination);
				stream.start();
				if (getTrack(id) == null)
					if (getTrack(id).isStreaming())
						postSessionStarted();
					else
						mHandler.post(mUpdateBitrate);
			} catch (UnknownHostException e) {
				postError(ERROR_UNKNOWN_HOST, id, e);
				throw e;
			} catch (StorageUnavailableException e) {
				postError(ERROR_STORAGE_NOT_READY, id, e);
				throw e;
			} catch (ConfNotSupportedException e) {
				postError(ERROR_CONFIGURATION_NOT_SUPPORTED, id, e);
				throw e;
			} catch (InvalidSurfaceException e) {
				postError(ERROR_INVALID_SURFACE, id, e);
				throw e;
			} catch (IOException e) {
				postError(ERROR_OTHER, id, e);
				throw e;
			}
		}
	}

	/**
	 * Does the same thing as {@link #start()}, but in a synchronous manner. <br />
	 * Throws exceptions in addition to calling a callback.
	 **/
	public void syncStart()
			throws ConfNotSupportedException, InvalidSurfaceException, IOException {
		try {
			syncStart(0);
		} catch (Exception e) {
			syncStop(0);
		}
	}

	/**
	 * Stops all existing streams.
	 */
	public void stop() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				syncStop();
			}
		});
	}

	/**
	 * Stops one stream in a synchronous manner.
	 *
	 * @param id The id of the stream to stop
	 **/
	private void syncStop(final int id) {
		AACStream stream = mAudioStream;
		if (stream != null) {
			stream.stop();
		}
	}

	/**
	 * Stops all existing streams in a synchronous manner.
	 */
	public void syncStop() {
		syncStop(0);
		syncStop(1);
		postSessionStopped();
	}

	/**
	 * Deletes all existing tracks & release associated resources.
	 */
	public void release() {
		removeAudioTrack();
		mHandler.getLooper().quit();
	}

	private void postSessionConfigured() {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onSessionConfigured();
				}
			}
		});
	}

	private void postSessionStarted() {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onSessionStarted();
				}
			}
		});
	}

	private void postSessionStopped() {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onSessionStopped();
				}
			}
		});
	}

	private void postError(final int reason, final int streamType, final Exception e) {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onSessionError(reason, streamType, e);
				}
			}
		});
	}

	private void postBitRate(final long bitrate) {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onBitrateUpdate(bitrate);
				}
			}
		});
	}

	private Runnable mUpdateBitrate = new Runnable() {
		@Override
		public void run() {
			if (isStreaming()) {
				postBitRate(getBitrate());
				mHandler.postDelayed(mUpdateBitrate, 500);
			} else {
				postBitRate(0);
			}
		}
	};

	public boolean trackExists(int id) {
		if (id == 0)
			return mAudioStream != null;

		return false;
	}

	public AACStream getTrack(int id) {
		if (id == 0)
			return mAudioStream;
		throw new NotImplementedYetException("no video yet");
	}
}
