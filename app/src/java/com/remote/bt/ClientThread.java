package com.remote.bt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Menjalankan perangkat sebagai client: menyambung ke perangkat lain yang
 * sedang bertindak sebagai server (menjalankan {@link ServerThread}), lalu
 * mengirim perintah dan mendengarkan balasannya.
 */
public class ClientThread extends Thread {

    private final Context context;
    private final BluetoothDevice device;
    private final MainActivity.ConnectionCallback callback;

    private BluetoothSocket socket;
    private OutputStream outputStream;
    private volatile boolean running = true;

    public ClientThread(Context context, BluetoothDevice device,
                         MainActivity.ConnectionCallback callback) {
        this.context = context.getApplicationContext();
        this.device = device;
        this.callback = callback;
    }

    @SuppressLint("MissingPermission") // izin BLUETOOTH_CONNECT sudah dicek sebelum thread ini dijalankan
    @Override
    public void run() {
        try {
            socket = device.createRfcommSocketToServiceRecord(MainActivity.APP_UUID);
            socket.connect();
        } catch (IOException e) {
            callback.onError("Gagal konek ke " + getDeviceLabel() + ": " + e.getMessage());
            closeSocket();
            return;
        }

        callback.onConnected(getDeviceLabel());

        try (InputStream input = socket.getInputStream()) {
            outputStream = socket.getOutputStream();

            byte[] buffer = new byte[1024];
            int bytesRead;

            while (running && (bytesRead = input.read(buffer)) != -1) {
                String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8).trim();
                if (!received.isEmpty()) {
                    callback.onMessageReceived("Server: " + received);
                }
            }
        } catch (IOException e) {
            if (running) {
                callback.onError("Koneksi terputus: " + e.getMessage());
            }
        } finally {
            closeSocket();
            callback.onDisconnected();
        }
    }

    /**
     * Mengirim satu baris perintah ke server. Aman dipanggil dari thread UI.
     */
    public void sendCommand(String command) {
        if (outputStream == null || command == null || command.trim().isEmpty()) {
            return;
        }
        try {
            outputStream.write((command.trim() + "\n").getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException e) {
            callback.onError("Gagal mengirim perintah: " + e.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    private String getDeviceLabel() {
        String name = device.getName();
        return name != null ? name : device.getAddress();
    }

    public void cancel() {
        running = false;
        closeSocket();
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // koneksi sudah putus, aman diabaikan
        }
    }
}
