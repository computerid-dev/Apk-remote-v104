package com.remote.bt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Menjalankan perangkat sebagai server: membuka BluetoothServerSocket dan
 * menunggu satu koneksi masuk dari perangkat client yang sudah dipasangkan.
 * Setiap perintah yang diterima diteruskan ke {@link CommandHandler} dan
 * hasilnya dikirim balik ke client.
 */
public class ServerThread extends Thread {

    private static final String SOCKET_NAME = "RemoteBTService";

    private final Context context;
    private final BluetoothAdapter adapter;
    private final MainActivity.ConnectionCallback callback;

    private BluetoothServerSocket serverSocket;
    private volatile boolean running = true;

    public ServerThread(Context context, BluetoothAdapter adapter,
                         MainActivity.ConnectionCallback callback) {
        this.context = context.getApplicationContext();
        this.adapter = adapter;
        this.callback = callback;
    }

    @SuppressLint("MissingPermission") // izin BLUETOOTH_CONNECT sudah dicek sebelum thread ini dijalankan
    @Override
    public void run() {
        try {
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(SOCKET_NAME, MainActivity.APP_UUID);
        } catch (IOException e) {
            callback.onError("Gagal membuka server: " + e.getMessage());
            return;
        }

        while (running) {
            BluetoothSocket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                break;
            }

            if (socket != null) {
                handleConnection(socket);
                break; // contoh sederhana: hanya melayani satu client per sesi
            }
        }

        closeServerSocket();
    }

    private void handleConnection(BluetoothSocket socket) {
        callback.onConnected(getRemoteDeviceName(socket));

        try (InputStream input = socket.getInputStream();
             OutputStream output = socket.getOutputStream()) {

            byte[] buffer = new byte[1024];
            int bytesRead;

            while (running && (bytesRead = input.read(buffer)) != -1) {
                String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8).trim();
                if (received.isEmpty()) {
                    continue;
                }
                callback.onMessageReceived("Diterima: " + received);

                String response = CommandHandler.handle(context, received);
                output.write((response + "\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
        } catch (IOException e) {
            callback.onError("Koneksi terputus: " + e.getMessage());
        } finally {
            closeSocket(socket);
            callback.onDisconnected();
        }
    }

    @SuppressLint("MissingPermission")
    private String getRemoteDeviceName(BluetoothSocket socket) {
        String name = socket.getRemoteDevice().getName();
        return name != null ? name : socket.getRemoteDevice().getAddress();
    }

    public void cancel() {
        running = false;
        closeServerSocket();
    }

    private void closeServerSocket() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // server socket sudah tertutup, aman diabaikan
        }
    }

    private void closeSocket(BluetoothSocket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // koneksi sudah putus, aman diabaikan
        }
    }
}
