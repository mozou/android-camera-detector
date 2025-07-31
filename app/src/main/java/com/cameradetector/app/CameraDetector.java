package com.cameradetector.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.text.format.Formatter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraDetector {
    
    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private ExecutorService executorService;
    
    // 常见的网络摄像头端口
    private static final int[] CAMERA_PORTS = {80, 8080, 554, 1935, 8000, 8888, 9000};
    
    public interface OnCameraDetectedListener {
        void onCameraDetected(CameraInfo cameraInfo);
        void onScanComplete();
    }
    
    public CameraDetector(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.executorService = Executors.newFixedThreadPool(10);
    }
    
    public void scanNetworkCameras(OnCameraDetectedListener listener) {
        new NetworkCameraScanTask(listener).execute();
    }
    
    public void scanBluetoothCameras(OnCameraDetectedListener listener) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            listener.onScanComplete();
            return;
        }
        
        // 扫描已配对的蓝牙设备
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (isCameraDevice(device)) {
                CameraInfo cameraInfo = createBluetoothCameraInfo(device);
                listener.onCameraDetected(cameraInfo);
            }
        }
        
        // 开始发现新设备
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && isCameraDevice(device)) {
                        CameraInfo cameraInfo = createBluetoothCameraInfo(device);
                        listener.onCameraDetected(cameraInfo);
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    context.unregisterReceiver(this);
                    listener.onScanComplete();
                }
            }
        };
        
        context.registerReceiver(receiver, filter);
        bluetoothAdapter.startDiscovery();
    }
    
    private boolean isCameraDevice(BluetoothDevice device) {
        String deviceName = device.getName();
        if (deviceName == null) return false;
        
        String lowerName = deviceName.toLowerCase();
        return lowerName.contains("camera") || 
               lowerName.contains("cam") ||
               lowerName.contains("webcam") ||
               lowerName.contains("摄像头") ||
               lowerName.contains("监控");
    }
    
    private CameraInfo createBluetoothCameraInfo(BluetoothDevice device) {
        CameraInfo cameraInfo = new CameraInfo();
        cameraInfo.setId(device.getAddress());
        cameraInfo.setName(device.getName() != null ? device.getName() : "未知蓝牙摄像头");
        cameraInfo.setType(CameraInfo.CameraType.BLUETOOTH);
        cameraInfo.setAccessible(device.getBondState() == BluetoothDevice.BOND_BONDED);
        cameraInfo.setHasPermission(false); // 蓝牙摄像头权限需要进一步验证
        return cameraInfo;
    }
    
    private class NetworkCameraScanTask extends AsyncTask<Void, CameraInfo, Void> {
        
        private OnCameraDetectedListener listener;
        
        public NetworkCameraScanTask(OnCameraDetectedListener listener) {
            this.listener = listener;
        }
        
        @Override
        protected Void doInBackground(Void... voids) {
            scanLocalNetwork();
            return null;
        }
        
        @Override
        protected void onProgressUpdate(CameraInfo... cameraInfos) {
            for (CameraInfo cameraInfo : cameraInfos) {
                listener.onCameraDetected(cameraInfo);
            }
        }
        
        @Override
        protected void onPostExecute(Void aVoid) {
            listener.onScanComplete();
        }
        
        private void scanLocalNetwork() {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            
            if (wifiManager == null) return;
            
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            String subnet = Formatter.formatIpAddress(ipAddress);
            
            // 获取子网前缀 (例如: 192.168.1)
            String[] parts = subnet.split("\\.");
            if (parts.length != 4) return;
            
            String networkPrefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
            
            // 扫描子网中的所有IP地址
            for (int i = 1; i <= 254; i++) {
                final String targetIp = networkPrefix + i;
                
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        scanIpForCameras(targetIp);
                    }
                });
            }
        }
        
        private void scanIpForCameras(String ipAddress) {
            for (int port : CAMERA_PORTS) {
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(ipAddress, port), 1000);
                    socket.close();
                    
                    // 发现开放的端口，可能是摄像头
                    CameraInfo cameraInfo = new CameraInfo();
                    cameraInfo.setId(ipAddress + ":" + port);
                    cameraInfo.setName("网络摄像头 (" + ipAddress + ":" + port + ")");
                    cameraInfo.setType(CameraInfo.CameraType.NETWORK);
                    cameraInfo.setIpAddress(ipAddress);
                    cameraInfo.setPort(port);
                    cameraInfo.setAccessible(true);
                    cameraInfo.setHasPermission(false); // 需要进一步验证权限
                    
                    publishProgress(cameraInfo);
                    
                } catch (IOException e) {
                    // 端口不可达，继续扫描下一个
                }
            }
        }
    }
    
    public void destroy() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}