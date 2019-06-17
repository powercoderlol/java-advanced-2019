package ru.ifmo.rain.polyakov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Accept and respond to requests sent by the Hello UDPClient class.
 * @see HelloUDPClient
 */
public class HelloUDPServer implements HelloServer {

    private ExecutorService serverThread;
    private ExecutorService sendPool;
    private DatagramSocket socket;
    private int bufferSize;

    public static void main(String[] args) {
        if (null == args || args.length != 2) {
            System.err.println("Command format:" +
                    "\n\tHelloUDPServer port threads");
            return;
        }
        int port;
        int threads;
        try {
            port = Integer.parseInt(args[0]);
            threads = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Error, wrong number format: " + e.getMessage());
            return;
        }
        new HelloUDPServer().start(port, threads);
    }

    @Override
    public void start(int port, int threads) {
        if (null!=socket) {
            System.err.println("You cannot restart server");
        } else {
            System.out.println("Starting server at port " + port + " with " + threads + " threads");
        }
        serverThread = Executors.newSingleThreadExecutor();
        sendPool = Executors.newFixedThreadPool(threads);
        try {
            socket = new DatagramSocket(port);
            bufferSize = socket.getReceiveBufferSize();
        } catch (SocketException e) {
            System.err.println("Socket error: " + e.getMessage());
        }
        serverThread.submit(new ServerRunnable());
    }

    @Override
    public void close() {
        serverThread.shutdownNow();
        sendPool.shutdownNow();
        try {
            serverThread.awaitTermination(10, TimeUnit.SECONDS);
            sendPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        socket.close();
    }

    private class ServerRunnable implements Runnable {
        @Override
        public void run() {
            while (!socket.isClosed() && !Thread.interrupted()) {
                var receivePacket = new DatagramPacket(new byte[bufferSize], bufferSize);
                try {
                    socket.receive(receivePacket);
                } catch (IOException e) {
                    System.err.println("Warning (receiving): " + e.getMessage());
                    continue;
                }
                sendPool.submit(() -> {
                    String receiveText = new String(receivePacket.getData(), receivePacket.getOffset(),
                            receivePacket.getLength(), StandardCharsets.UTF_8);
                    String requestText = "Hello, " + receiveText;
                    var sendPacket = new DatagramPacket(requestText.getBytes(StandardCharsets.UTF_8),
                            requestText.length(), receivePacket.getSocketAddress());
                    try {
                        socket.send(sendPacket);
                    } catch (IOException e) {
                        System.err.println("Warning (sending): " + e.getMessage());
                    }
                });
            }
        }
    }
}