package com.hui.bluetooth_enable;

import android.app.Activity;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import androidx.core.app.ActivityCompat;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener;

/** BluetoothEnablePlugin */
public class BluetoothEnablePlugin implements FlutterPlugin, ActivityAware, MethodCallHandler, ActivityResultListener, RequestPermissionsResultListener {
    private static final String TAG = "BluetoothEnablePlugin";

    private Activity activity;
    private MethodChannel channel;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private Result pendingResult;

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_CODE_SCAN_ACTIVITY = 2777;

    // Default constructor (no Registrar)
    public BluetoothEnablePlugin() {
    }

    /* FlutterPlugin implementation */
    @Override
    public void onAttachedToEngine(FlutterPluginBinding binding) {
        channel = new MethodChannel(binding.getBinaryMessenger(), "bluetooth_enable");
        channel.setMethodCallHandler(this);
        // Note: Cannot initialize BluetoothManager here because we don't have an Activity yet.
    }

    @Override
    public void onDetachedFromEngine(FlutterPluginBinding binding) {
        if (channel != null) {
            channel.setMethodCallHandler(null);
            channel = null;
        }
        mBluetoothAdapter = null;
        mBluetoothManager = null;
    }

    /* ActivityAware implementation */
    @Override
    public void onAttachedToActivity(ActivityPluginBinding binding) {
        activity = binding.getActivity();
        mBluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        binding.addActivityResultListener(this);
        binding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onDetachedFromActivity() {
        // Avoid calling activity.finish() in a plugin—simply clear the reference.
        activity = null;
    }

    /* MethodCallHandler implementation */
    @Override
    public void onMethodCall(MethodCall call, Result result) {
        // Check for Bluetooth availability (for all calls except "isAvailable")
        if (mBluetoothAdapter == null && !"isAvailable".equals(call.method)) {
            result.error("bluetooth_unavailable", "the device does not have bluetooth", null);
            return;
        }

        // Request permission to connect if needed.
        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                REQUEST_ENABLE_BLUETOOTH
        );

        switch (call.method) {
            case "enableBluetooth":
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (activity != null) {
                    activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
                }
                Log.d(TAG, "Starting activity to enable Bluetooth");
                pendingResult = result;
                break;
            case "customEnable":
                try {
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    if (adapter != null && !adapter.isEnabled()) {
                        adapter.disable();
                        Thread.sleep(500); // Handle InterruptedException appropriately
                        adapter.enable();
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "customEnable", e);
                }
                result.success("true");
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    /* ActivityResultListener implementation */
    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (pendingResult != null) {
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "User enabled Bluetooth");
                    pendingResult.success("true");
                } else {
                    Log.d(TAG, "User did NOT enable Bluetooth");
                    pendingResult.success("false");
                }
                pendingResult = null;
                return true;
            }
        }
        return false;
    }

    /* RequestPermissionsResultListener implementation */
    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult called");
        // You can handle permission result logic here if needed.
        return false;
    }

    // Optionally, include your BroadcastReceiver if you need to listen for Bluetooth state changes.
    // For example, you could register it in onAttachedToActivity and unregister in onDetachedFromActivity.
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "Bluetooth state: OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "Bluetooth state: TURNING OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "Bluetooth state: ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "Bluetooth state: TURNING ON");
                        break;
                }
            }
        }
    };
}