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

package net.majorkernelpanic.streaming.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.MediaRecorder.AudioSource;
import android.service.textservice.SpellCheckerService.Session;

import com.nu.art.belog.Logger;
import com.nu.art.core.exceptions.runtime.BadImplementationException;
import com.nu.art.core.tools.ArrayTools;

import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.rtp.AACLATMPacketizer;
import net.majorkernelpanic.streaming.rtp.AbstractPacketizer;
import net.majorkernelpanic.streaming.rtp.MediaCodecInputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.nu.art.rtsp.Response.LineBreak;

/**
 * A class for streaming AAC from the camera of an android device using RTP.
 * You should use a {@link Session} instantiated with {@link SessionBuilder} instead of using this class directly.
 * Call {@link #setDestinationAddress(InetAddress)}, {@link #setDestinationPorts(int)} and {@link #setAudioQuality(AudioQuality)}
 * to configure the stream. You can then call {@link #start()} to start the RTP stream.
 * Call {@link #stop()} to stop the stream.
 */
public class AACStream
		extends Logger {

	/**
	 * There are 13 supported frequencies by ADTS.
	 **/
	public static final int[] AUDIO_SAMPLING_RATES = {
			96000,
			// 0
			88200,
			// 1
			64000,
			// 2
			48000,
			// 3
			44100,
			// 4
			32000,
			// 5
			24000,
			// 6
			22050,
			// 7
			16000,
			// 8
			12000,
			// 9
			11025,
			// 10
			8000,
			// 11
			7350,
			// 12
			-1,
			// 13
			-1,
			// 14
			-1,
			// 15
	};

	private int mSamplingRateIndex;

	private int mConfig;

	public AACStream() {
		super();
		AACStreamingSupported();
		mPacketizer = new AACLATMPacketizer();
	}

	private boolean AACStreamingSupported() {

		try {
			MediaRecorder.OutputFormat.class.getField("AAC_ADTS");
			logInfo("AAC supported on this phone");
			return true;
		} catch (Exception e) {
			throw new BadImplementationException("AAC not supported by this phone !", e);
		}
	}

	public synchronized void start()
			throws IllegalStateException, IOException {
		configure();
		if (mDestination == null)
			throw new IllegalStateException("No destination ip address set for the stream !");

		if (mRtpPort <= 0 || mRtcpPort <= 0)
			throw new IllegalStateException("No destination ports set for the stream !");

		mPacketizer.setTimeToLive(mTTL);

		startRecording();
	}

	public synchronized void configure()
			throws IllegalStateException, IOException {
		if (mStreaming)
			throw new IllegalStateException("Can't be called while streaming.");
		if (mPacketizer != null) {
			mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
			mPacketizer.getRtpSocket().setOutputStream(mOutputStream, mChannelIdentifier);
		}
		mConfigured = true;
		mQuality = mRequestedQuality.clone();

		// Checks if the user has supplied an exotic sampling rate
		int i = 0;
		for (; i < AUDIO_SAMPLING_RATES.length; i++) {
			if (AUDIO_SAMPLING_RATES[i] == mQuality.samplingRate) {
				mSamplingRateIndex = i;
				break;
			}
		}
		// If he did, we force a reasonable one: 16 kHz
		if (i > 12)
			mQuality.samplingRate = 16000;

		mPacketizer.setSamplingRate(mQuality.samplingRate);

		mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
		mPacketizer.getRtpSocket().setOutputStream(mOutputStream, mChannelIdentifier);

		int mProfile = 2;
		int mChannel = 1;
		mConfig = (mProfile & 0x1F) << 11 | (mSamplingRateIndex & 0x0F) << 7 | (mChannel & 0x0F) << 3;
	}

	static class BufferItem {

		int len;

		byte[] inputBuffer;

		private long timestamp;

		BufferItem(int bufferLength) {
			this.inputBuffer = new byte[bufferLength];
		}
	}

	public static class RecordBuffer
			extends Logger {

		private int MaxBuffer = 3;

		private final List<BufferItem> bufferedItems = Collections.synchronizedList(new ArrayList<BufferItem>());

		private final AudioQuality quality;

		private final int bufferSize;

		private MediaCodec[] mediaCodecs = {};

		private AudioRecord audioRecord;

		private Thread recordingThread;

		private Thread bufferingThread;

		private static boolean record;

		public RecordBuffer(int bufferSize, AudioQuality quality) {
			this.bufferSize = bufferSize;
			this.quality = quality;
		}

		void addMediaCodec(MediaCodec mediaCode) {
			mediaCodecs = ArrayTools.appendElement(mediaCodecs, mediaCode);
		}

		private synchronized void startRecording(boolean createNew) {
			if (audioRecord != null && !createNew)
				return;

			record = true;

			audioRecord = new AudioRecord(AudioSource.MIC /*MIC has no noise reduction*/, quality.samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
			audioRecord.startRecording();

			recordingThread = new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						while (record) {
							BufferItem buffer = new BufferItem(bufferSize);

							byte[] sampler = buffer.inputBuffer;
							int len = audioRecord.read(sampler, 0, sampler.length);
							if (len == AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE)
								break;

							buffer.len = len;
							buffer.timestamp = System.nanoTime() / 1000;
							synchronized (bufferedItems) {
								bufferedItems.add(0, buffer);
								int size = bufferedItems.size();
								logDebug("Added buffer, total: " + size);

								while (size > MaxBuffer) {
									int index = size - 1;
									logDebug("removed buffer index: " + index);

									bufferedItems.remove(index);
								}
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}

					logError("An error occur with the AudioRecord API !");
					audioRecord.release();
					audioRecord = null;
				}
			});
			recordingThread.setPriority(Thread.MAX_PRIORITY);
			recordingThread.start();

			bufferingThread = new Thread(new Runnable() {
				@Override
				public void run() {
					while (record) {
						int size;
						BufferItem buffer = null;
						synchronized (bufferedItems) {
							size = bufferedItems.size();
							if (size > 0) {
								int index = size - 1;
								logDebug("transmitting buffer: " + index + ", total clients: " + mediaCodecs.length);
								buffer = bufferedItems.remove(index);
							}
						}

						if (size == 0) {
							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							continue;
						}

						for (MediaCodec mediaCodec : mediaCodecs) {
							ByteBuffer[] inputBuffers;
							try {
								inputBuffers = mediaCodec.getInputBuffers();
							} catch (IllegalStateException e) {
								logError(e);
								continue;
							}

							int bufferIndex = mediaCodec.dequeueInputBuffer(10000);
							final ByteBuffer inputBuffer = inputBuffers[bufferIndex];

							inputBuffer.clear();
							inputBuffer.put(buffer.inputBuffer);

							mediaCodec.queueInputBuffer(bufferIndex, 0, buffer.len, buffer.timestamp, 0);
						}
					}
				}
			});

			bufferingThread.setPriority(Thread.MAX_PRIORITY);
			bufferingThread.start();
		}

		private void removeBuffer(MediaCodec mediaCodec) {
			mediaCodecs = ArrayTools.removeElement(mediaCodecs, mediaCodec);
		}

		public static synchronized void stopRecording() {
			record = false;
		}
	}

	public static RecordBuffer recorderBuffer;

	private synchronized void startRecording()
			throws IOException {

		final int bufferSize = AudioRecord.getMinBufferSize(mQuality.samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2;
		mPacketizer.setSamplingRate(mQuality.samplingRate);

		MediaFormat format = new MediaFormat();
		format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
		format.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitRate);
		format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
		format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mQuality.samplingRate);
		format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
		format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);

		mStreaming = true;
		mMediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
		mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mMediaCodec.start();
		if (recorderBuffer == null) {
			recorderBuffer = new RecordBuffer(bufferSize, mQuality);
		}
		recorderBuffer.addMediaCodec(mMediaCodec);

		// The packetizer encapsulates this stream in an RTP stream and send it over the network
		mPacketizer.setInputStream(new MediaCodecInputStream(mMediaCodec));
		mPacketizer.start();

		recorderBuffer.startRecording(false);
	}

	/**
	 * Stops the stream.
	 */
	public synchronized void stop() {
		if (!mStreaming)
			return;

		recorderBuffer.removeBuffer(mMediaCodec);

		try {
			mPacketizer.stop();
			mMediaCodec.stop();
			mMediaCodec.release();
		} catch (Exception e) {
			e.printStackTrace();
		}

		mStreaming = false;
	}

	/**
	 * Returns a description of the stream using SDP. It can then be included in an SDP file.
	 * Will fail if called when streaming.
	 */
	public String getSessionDescription()
			throws IllegalStateException {
		return "m=audio " + String
				.valueOf(getDestinationPorts()[0]) + " RTP/AVP 96" + LineBreak + "a=rtpmap:96 mpeg4-generic/" + mQuality.samplingRate + LineBreak + "a=fmtp:96 streamtype=5; profile-level-id=15; mode=AAC-hbr; config=" + Integer
				.toHexString(mConfig) + "; SizeLength=13; IndexLength=3; IndexDeltaLength=3;" + LineBreak;
	}

	/*
	 *
	 *
	 *
	 *  AUDIO RECORD
	 *
	 *
	 *
	 *
	 */
	protected AudioQuality mRequestedQuality = AudioQuality.DEFAULT_AUDIO_QUALITY.clone();

	protected AudioQuality mQuality = mRequestedQuality.clone();

	public void setAudioQuality(AudioQuality quality) {
		mRequestedQuality = quality;
	}

	/*
	 *
	 *
	 * MEDIA STREAM
	 *
	 *
	 *
	 */

	/**
	 * The packetizer that will read the output of the camera and send RTP packets over the networked.
	 */
	protected AACLATMPacketizer mPacketizer = null;

	protected boolean mStreaming = false;

	protected boolean mConfigured = false;

	protected int mRtpPort = 0, mRtcpPort = 0;

	protected byte mChannelIdentifier = 0;

	protected OutputStream mOutputStream = null;

	protected InetAddress mDestination;

	private int mTTL = 64;

	protected MediaCodec mMediaCodec;

	/**
	 * Sets the destination IP address of the stream.
	 *
	 * @param dest The destination address of the stream
	 */
	public void setDestinationAddress(InetAddress dest) {
		mDestination = dest;
	}

	/**
	 * Sets the destination ports of the stream.
	 * If an odd number is supplied for the destination port then the next
	 * lower even number will be used for RTP and it will be used for RTCP.
	 * If an even number is supplied, it will be used for RTP and the next odd
	 * number will be used for RTCP.
	 *
	 * @param dport The destination port
	 */
	public void setDestinationPorts(int dport) {
		if (dport % 2 == 1) {
			mRtpPort = dport - 1;
			mRtcpPort = dport;
		} else {
			mRtpPort = dport;
			mRtcpPort = dport + 1;
		}
	}

	/**
	 * Sets the destination ports of the stream.
	 *
	 * @param rtpPort  Destination port that will be used for RTP
	 * @param rtcpPort Destination port that will be used for RTCP
	 */
	public void setDestinationPorts(int rtpPort, int rtcpPort) {
		mRtpPort = rtpPort;
		mRtcpPort = rtcpPort;
		mOutputStream = null;
	}

	/**
	 * If a TCP is used as the transport protocol for the RTP session,
	 * the output stream to which RTP packets will be written to must
	 * be specified with this method.
	 */
	public void setOutputStream(OutputStream stream, byte channelIdentifier) {
		mOutputStream = stream;
		mChannelIdentifier = channelIdentifier;
	}

	/**
	 * Sets the Time To Live of packets sent over the network.
	 *
	 * @param ttl The time to live
	 *
	 * @throws IOException
	 */
	public void setTimeToLive(int ttl)
			throws IOException {
		mTTL = ttl;
	}

	/**
	 * Returns a pair of destination ports, the first one is the
	 * one used for RTP and the second one is used for RTCP.
	 **/
	public int[] getDestinationPorts() {
		return new int[]{
				mRtpPort,
				mRtcpPort
		};
	}

	/**
	 * Returns a pair of source ports, the first one is the
	 * one used for RTP and the second one is used for RTCP.
	 **/
	public int[] getLocalPorts() {
		return mPacketizer.getRtpSocket().getLocalPorts();
	}

	/**
	 * Returns the packetizer associated with the {@link AACStream}.
	 *
	 * @return The packetizer
	 */
	public AbstractPacketizer getPacketizer() {
		return mPacketizer;
	}

	/**
	 * Returns an approximation of the bit rate consumed by the stream in bit per seconde.
	 */
	public long getBitrate() {
		return !mStreaming ? 0 : mPacketizer.getRtpSocket().getBitrate();
	}

	/**
	 * Indicates if the {@link AACStream} is streaming.
	 *
	 * @return A boolean indicating if the {@link AACStream} is streaming
	 */
	public boolean isStreaming() {
		return mStreaming;
	}

	/**
	 * Returns the SSRC of the underlying {@link net.majorkernelpanic.streaming.rtp.RtpSocket}.
	 *
	 * @return the SSRC of the stream
	 */
	public int getSSRC() {
		return getPacketizer().getSSRC();
	}
}
