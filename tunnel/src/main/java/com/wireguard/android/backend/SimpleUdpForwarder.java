/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;


import android.util.Log;

import com.wireguard.android.backend.GoBackend.VpnService;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class SimpleUdpForwarder {
    private static final String TAG = "SimpleUdpForwarder";

    private static final String XOR_KEY_SEED = "your_secret_key_or_password";

    private int localListeningPort;

    private final String forwardsToHost;
    private final int forwardsToPort;

    private Thread localToRemoteThread;

    private Thread remoteToLocalThread;

    private static final boolean DEBUG_ENABLED = false;


    SimpleUdpForwarder(final String forwardsToHost, final int forwardsToPort) {
        this.forwardsToHost = forwardsToHost;
        this.forwardsToPort = forwardsToPort;
    }

    public void start(VpnService vpnService) throws Exception {
        final byte[] xorKey = getXorKey();
        final DatagramSocket localListeningSocket = new DatagramSocket(0);
        localListeningPort = localListeningSocket.getLocalPort();

        final DatagramSocket forwardingSocket = new DatagramSocket(0);
        vpnService.protect(forwardingSocket);

        AtomicReference<InetAddress> lastClientAddress = new AtomicReference<>();
        AtomicInteger lastClientPort = new AtomicInteger(-1);

        localToRemoteThread = new Thread(() -> {
            long checkpoint = System.currentTimeMillis();
            byte[] buf = new byte[4096];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    localListeningSocket.receive(packet);

                    synchronized (SimpleUdpForwarder.class) {
                        lastClientAddress.set(packet.getAddress());
                        lastClientPort.set(packet.getPort());
                    }

                    byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
                    byte[] xored = xorWithKey(data, xorKey);

                    DatagramPacket forwardPacket = new DatagramPacket(
                            xored, xored.length, InetAddress.getByName(forwardsToHost), forwardsToPort
                    );

                    forwardingSocket.send(forwardPacket);
                    if (DEBUG_ENABLED) {
                        Log.v(TAG, String.format(">> %d bytes from client:%s:%d -> remote\n", xored.length,
                                packet.getAddress().getHostAddress(), packet.getPort()));
                    }
                    long now = System.currentTimeMillis();
                    if (now - checkpoint > 1000) {
                        Thread.sleep(0);
                        checkpoint = now;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        remoteToLocalThread = new Thread(() -> {
            long checkpoint = System.currentTimeMillis();
            byte[] buf = new byte[4096];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    forwardingSocket.receive(packet);

                    // XORSystem.
                    byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
                    byte[] xored = xorWithKey(data, xorKey);

                    InetAddress clientAddr;
                    int clientPort;

                    synchronized (SimpleUdpForwarder.class) {
                        clientAddr = lastClientAddress.get();
                        clientPort = lastClientPort.get();
                    }

                    if (clientAddr != null && clientPort != -1) {
                        // Send back to last client
                        DatagramPacket reply = new DatagramPacket(xored, xored.length, clientAddr, clientPort);
                        localListeningSocket.send(reply);
                        if (DEBUG_ENABLED) {
                            Log.v(TAG, String.format("<< %d bytes remote -> client:%s:%d\n",
                                    xored.length, clientAddr.getHostAddress(), clientPort));
                        }
                    } else {
                        Log.e(TAG, "No client known to send response to.");
                    }
                    long now = System.currentTimeMillis();
                    if (now - checkpoint > 1000) {
                        Thread.sleep(0);
                        checkpoint = now;
                    }
                } catch (InterruptedException interruptedException) {
                    Log.i(TAG, "returning from remote to local forwarding");
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "failed to perform remote to local forwarding: " + e);
                }
            }
        });

        localToRemoteThread.start();
        remoteToLocalThread.start();
    }

    public int getLocalListeningPort() {
        return localListeningPort;
    }


    public void stop() {
        if (localToRemoteThread != null) {
            localToRemoteThread.interrupt();
            localToRemoteThread = null;
        }
        if (remoteToLocalThread != null) {
            remoteToLocalThread.interrupt();
            remoteToLocalThread = null;
        }
    }

    private static byte[] xorWithKey(final byte[] data, final byte[] key) {
        final byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return out;
    }

    private static byte[] getXorKey() throws Exception {
        return MessageDigest.getInstance("MD5").digest(SimpleUdpForwarder.XOR_KEY_SEED.getBytes(StandardCharsets.UTF_8));
    }
}