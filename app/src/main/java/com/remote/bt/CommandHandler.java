package com.remote.bt;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Menerjemahkan perintah teks yang diterima lewat socket Bluetooth
 * menjadi aksi lokal di perangkat, lalu mengembalikan balasannya.
 *
 * Format perintah yang didukung:
 *   PING            -> balasan "PONG"
 *   GET_BATTERY     -> level baterai perangkat saat ini
 *   VIBRATE         -> getarkan perangkat sebentar
 *   FLASHLIGHT_ON   -> nyalakan senter (jika tersedia)
 *   FLASHLIGHT_OFF  -> matikan senter
 *   MSG:<teks>      -> echo pesan bebas, dipakai untuk uji koneksi
 */
public final class CommandHandler {

    private CommandHandler() {
        // utility class, tidak untuk di-instantiate
    }

    public static String handle(Context context, String rawCommand) {
        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            return "ERROR:EMPTY_COMMAND";
        }

        String command = rawCommand.trim();
        String upper = command.toUpperCase();

        switch (upper) {
            case "PING":
                return "PONG";
            case "GET_BATTERY":
                return getBatteryLevel(context);
            case "VIBRATE":
                vibrate(context);
                return "OK:VIBRATE";
            case "FLASHLIGHT_ON":
                return setFlashlight(context, true) ? "OK:FLASHLIGHT_ON" : "ERROR:NO_FLASHLIGHT";
            case "FLASHLIGHT_OFF":
                return setFlashlight(context, false) ? "OK:FLASHLIGHT_OFF" : "ERROR:NO_FLASHLIGHT";
            default:
                if (upper.startsWith("MSG:")) {
                    return "RECEIVED:" + command.substring(4);
                }
                return "ERROR:UNKNOWN_COMMAND";
        }
    }

    private static String getBatteryLevel(Context context) {
        BatteryManager batteryManager =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager == null) {
            return "ERROR:BATTERY_UNAVAILABLE";
        }
        int level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return "BATTERY:" + level + "%";
    }

    private static void vibrate(Context context) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(250);
        }
    }

    private static boolean setFlashlight(Context context, boolean turnOn) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            return false;
        }
        CameraManager cameraManager =
                (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            return false;
        }
        try {
            String cameraId = cameraManager.getCameraIdList()[0];
            cameraManager.setTorchMode(cameraId, turnOn);
            return true;
        } catch (CameraAccessException | ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }
}
