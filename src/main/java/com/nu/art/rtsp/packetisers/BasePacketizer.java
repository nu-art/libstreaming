package com.nu.art.rtsp.packetisers;

import net.majorkernelpanic.streaming.rtp.RtpSocket;

import java.io.InputStream;
import java.util.Random;

/**
 * Created by TacB0sS on 09-Sep 2017.
 */

public class BasePacketizer {

	protected RtpSocket[] socket = {};

	protected long ts = new Random().nextInt();

	protected InputStream mediaStream;


}
