package com.remote.bt;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    /** UUID acak khusus dipakai aplikasi ini untuk mencocokkan server & client. */
    public static final UUID APP_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    /** Kontrak callback yang dipakai bersama oleh ServerThread dan ClientThread. */
    public interface ConnectionCallback {
        void onConnected(String deviceName);
        void onMessageReceived(String message);
        void onDisconnected();
        void onError(String error);
    }

    private RecyclerView recyclerViewDevices;
    private TextView textViewEmptyDevices;
    private TextView textViewStatus;
    private TextView textViewLog;
    private MaterialButton btnBecomeServer;
    private MaterialButton btnRefresh;
    private MaterialButton btnSendCommand;
    private TextInputEditText editTextCommand;

    private BluetoothAdapter bluetoothAdapter;
    private DeviceAdapter deviceAdapter;

    private ServerThread serverThread;
    private ClientThread clientThread;
    private boolean isServerMode = false;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
                boolean allGranted = true;
                for (Boolean granted : grants.values()) {
                    allGranted &= Boolean.TRUE.equals(granted);
                }
                if (allGranted) {
                    loadPairedDevices();
                } else {
                    showToast(getString(R.string.error_permission_denied));
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupBluetoothAdapter();
        setupDeviceList();
        setupListeners();
    }

    private void bindViews() {
        recyclerViewDevices = findViewById(R.id.recyclerViewDevices);
        textViewEmptyDevices = findViewById(R.id.textViewEmptyDevices);
        textViewStatus = findViewById(R.id.textViewStatus);
        textViewLog = findViewById(R.id.textViewLog);
        btnBecomeServer = findViewById(R.id.btnBecomeServer);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnSendCommand = findViewById(R.id.btnSendCommand);
        editTextCommand = findViewById(R.id.editTextCommand);
    }

    private void setupBluetoothAdapter() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

        if (bluetoothAdapter == null) {
            showToast(getString(R.string.error_bluetooth_not_supported));
            btnBecomeServer.setEnabled(false);
            btnRefresh.setEnabled(false);
        }
    }

    private void setupDeviceList() {
        deviceAdapter = new DeviceAdapter(this::onDeviceSelected);
        recyclerViewDevices.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewDevices.setAdapter(deviceAdapter);
    }

    private void setupListeners() {
        btnRefresh.setOnClickListener(v -> ensurePermissionsThen(this::loadPairedDevices));

        btnBecomeServer.setOnClickListener(v -> ensurePermissionsThen(this::toggleServerMode));

        btnSendCommand.setOnClickListener(v -> sendTypedCommand());
    }

    @Override
    protected void onStart() {
        super.onStart();
        ensurePermissionsThen(this::loadPairedDevices);
    }

    // ---------------------------------------------------------------------
    // Permission handling
    // ---------------------------------------------------------------------

    private void ensurePermissionsThen(Runnable action) {
        if (bluetoothAdapter == null) {
            return;
        }
        if (hasAllPermissions()) {
            action.run();
            return;
        }
        permissionLauncher.launch(requiredPermissions());
    }

    private boolean hasAllPermissions() {
        for (String permission : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_CONNECT};
        }
        return new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN};
    }

    // ---------------------------------------------------------------------
    // Daftar perangkat terpasang
    // ---------------------------------------------------------------------

    @android.annotation.SuppressLint("MissingPermission") // izin sudah dicek lewat ensurePermissionsThen()
    private void loadPairedDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            showToast(getString(R.string.error_bluetooth_disabled));
            updateEmptyState(new ArrayList<>());
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        List<BluetoothDevice> devices = new ArrayList<>(pairedDevices);
        deviceAdapter.submitList(devices);
        updateEmptyState(devices);
    }

    private void updateEmptyState(List<BluetoothDevice> devices) {
        boolean isEmpty = devices.isEmpty();
        textViewEmptyDevices.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerViewDevices.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void onDeviceSelected(BluetoothDevice device) {
        if (isServerMode) {
            showToast("Matikan mode server sebelum menyambung sebagai client");
            return;
        }
        if (clientThread != null) {
            clientThread.cancel();
        }
        clientThread = new ClientThread(this, device, connectionCallback);
        clientThread.start();
        appendLog("Menyambung ke " + safeDeviceName(device) + "…");
    }

    // ---------------------------------------------------------------------
    // Mode server
    // ---------------------------------------------------------------------

    private void toggleServerMode() {
        if (isServerMode) {
            stopServer();
        } else {
            startServer();
        }
    }

    private void startServer() {
        serverThread = new ServerThread(this, bluetoothAdapter, connectionCallback);
        serverThread.start();
        isServerMode = true;
        btnBecomeServer.setText(R.string.btn_stop_server);
        setStatus(getString(R.string.status_waiting_connection), R.color.statusIdle);
    }

    private void stopServer() {
        if (serverThread != null) {
            serverThread.cancel();
            serverThread = null;
        }
        isServerMode = false;
        btnBecomeServer.setText(R.string.btn_become_server);
        setStatus(getString(R.string.status_idle), R.color.statusIdle);
    }

    // ---------------------------------------------------------------------
    // Kirim perintah
    // ---------------------------------------------------------------------

    private void sendTypedCommand() {
        String command = editTextCommand.getText() != null
                ? editTextCommand.getText().toString().trim()
                : "";
        if (TextUtils.isEmpty(command) || clientThread == null) {
            return;
        }
        clientThread.sendCommand(command);
        appendLog("Dikirim: " + command);
        editTextCommand.setText("");
    }

    // ---------------------------------------------------------------------
    // Callback dari ServerThread / ClientThread
    // ---------------------------------------------------------------------

    private final ConnectionCallback connectionCallback = new ConnectionCallback() {
        @Override
        public void onConnected(String deviceName) {
            runOnUiThread(() -> {
                setStatus(getString(R.string.status_connected, deviceName), R.color.statusConnected);
                btnSendCommand.setEnabled(true);
                appendLog("Terhubung dengan " + deviceName);
            });
        }

        @Override
        public void onMessageReceived(String message) {
            runOnUiThread(() -> appendLog(message));
        }

        @Override
        public void onDisconnected() {
            runOnUiThread(() -> {
                setStatus(getString(R.string.status_disconnected), R.color.statusDisconnected);
                btnSendCommand.setEnabled(false);
                appendLog(getString(R.string.status_disconnected));
                if (isServerMode) {
                    isServerMode = false;
                    btnBecomeServer.setText(R.string.btn_become_server);
                }
            });
        }

        @Override
        public void onError(String error) {
            runOnUiThread(() -> {
                appendLog("Error: " + error);
                showToast(error);
            });
        }
    };

    // ---------------------------------------------------------------------
    // Helper UI
    // ---------------------------------------------------------------------

    private void setStatus(String text, int colorRes) {
        textViewStatus.setText(text);
        textViewStatus.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    private void appendLog(String line) {
        textViewLog.append(line + "\n");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @android.annotation.SuppressLint("MissingPermission")
    private String safeDeviceName(BluetoothDevice device) {
        String name = device.getName();
        return name != null ? name : device.getAddress();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serverThread != null) {
            serverThread.cancel();
        }
        if (clientThread != null) {
            clientThread.cancel();
        }
    }

    // ---------------------------------------------------------------------
    // Adapter daftar perangkat (RecyclerView)
    // ---------------------------------------------------------------------

    private static class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

        interface OnDeviceClickListener {
            void onDeviceClick(BluetoothDevice device);
        }

        private final List<BluetoothDevice> devices = new ArrayList<>();
        private final OnDeviceClickListener listener;

        DeviceAdapter(OnDeviceClickListener listener) {
            this.listener = listener;
        }

        void submitList(List<BluetoothDevice> newDevices) {
            devices.clear();
            devices.addAll(newDevices);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_device, parent, false);
            return new DeviceViewHolder(view);
        }

        @android.annotation.SuppressLint("MissingPermission")
        @Override
        public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
            BluetoothDevice device = devices.get(position);
            String name = device.getName();
            holder.name.setText(name != null ? name : "(Tanpa nama)");
            holder.address.setText(device.getAddress());
            holder.itemView.setOnClickListener(v -> listener.onDeviceClick(device));
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }

        static class DeviceViewHolder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView address;

            DeviceViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.textDeviceName);
                address = itemView.findViewById(R.id.textDeviceAddress);
            }
        }
    }
}
