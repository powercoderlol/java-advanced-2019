package ru.ifmo.rain.polyakov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Requests are sent simultaneously in the specified number of threads to server.
 * Each thread will wait for the processing of its request and output
 * the request itself and the result of its processing to the console.
 * If the request has not been processed, send it again.
 * <br/><br/>
 * <b>Request text format:</b> 'query prefix' + 'thread number' + '_' + 'query number in thread'<br/>
 * <b>Answer from server format::</b> 'Hello, ' + 'received request text'
 * @see HelloUDPServer
 */
public class HelloUDPClient implements HelloClient {

    private static final int SOCKET_OPERATIONS_TIMEOUT = 1000;

    public static void main(String[] args) {
        if (null == args || args.length != 5) {
            System.err.println("Command format:" +
                    "\n\tHelloUDPClient server port prefix threads requests");
            return;
        }
        String server;
        String prefix;
        int port;
        int threads;
        int requests;
        try {
            server = args[0];
            port = Integer.parseInt(args[1]);
            prefix = args[2];
            threads = Integer.parseInt(args[3]);
            requests = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            System.err.println("Error, wrong number format: " + e.getMessage());
            return;
        }
        new HelloUDPClient().run(server, port, prefix, threads, requests);
    }

    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        InetSocketAddress serverAddress = new InetSocketAddress(host, port);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch countDown = new CountDownLatch(threads);
        for (int threadNumber = 0; threadNumber < threads; threadNumber++) {
            String msgWithoutReqNum = prefix + threadNumber + "_";
            executor.submit(new RequestSender(serverAddress, requests, msgWithoutReqNum, countDown));
        }
        try {
            countDown.await();
        } catch (InterruptedException ignored) {
        } finally {
            executor.shutdown();
        }
    }

    private class RequestSender implements Runnable {

        private final InetSocketAddress serverAddress;
        private final int requests;
        private final String msgWithoutReqNum;
        private CountDownLatch countDown;

        private RequestSender(InetSocketAddress serverAddress, int requests, String msgWithoutReqNum, CountDownLatch countDown) {
            this.serverAddress = serverAddress;
            this.requests = requests;
            this.msgWithoutReqNum = msgWithoutReqNum;
            this.countDown = countDown;
        }

        @Override
        public void run() {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(SOCKET_OPERATIONS_TIMEOUT);
                for (int requestNumber = 0; requestNumber < requests; requestNumber++) {
                    communicate(requestNumber, socket);
                }
            } catch (SocketException e) {
                System.err.println("Socket error: " + e.getMessage());
            } catch (InterruptedException ignored) {
            } finally {
                countDown.countDown();
            }
        }

        private void communicate(int requestNumber, DatagramSocket socket) throws SocketException, InterruptedException {
            var requestText = msgWithoutReqNum + requestNumber;
            int bufferSize = socket.getReceiveBufferSize();
            var sendPacket = new DatagramPacket(requestText.getBytes(StandardCharsets.UTF_8), requestText.length(), serverAddress);
            var receivePacket = new DatagramPacket(new byte[bufferSize], bufferSize);
            boolean interrupted;
            while (!(interrupted = Thread.interrupted())) {
                try {
                    socket.send(sendPacket);
                    socket.receive(receivePacket);
                } catch (IOException e) {
                    System.err.println("Warning: " + e.getMessage());
                    continue;
                }
                String responseText = new String(receivePacket.getData(), receivePacket.getOffset(),
                        receivePacket.getLength(), StandardCharsets.UTF_8);
                if (responseText.contains(requestText)) {
                    System.out.println("Request: " + requestText);
                    System.out.println("Response: " + responseText);
                    break;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
                throw new InterruptedException();
            }
        }
    }
}