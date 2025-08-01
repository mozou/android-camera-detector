package com.cameradetector.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.text.format.Formatter;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 增强版摄像头检测器 - 检测周围的各种摄像头设备
 * 包括网络摄像头、蓝牙摄像头、WiFi摄像头、隐藏摄像头等
 */
public class CameraDetector {
    
    private static final String TAG = "CameraDetector";
    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private WifiManager wifiManager;
    private ExecutorService executorService;
    
    // 常见的网络摄像头端口
    private static final int[] CAMERA_PORTS = {
        80, 8080, 554, 1935, 8000, 8888, 9000, 8081, 8082, 8083,
        81, 82, 83, 84, 85, 8001, 8002, 8003, 8004, 8005,
        1024, 1025, 1026, 1027, 1028, 1029, 1030, 1031, 1032
    };
    
    // 摄像头设备常见的WiFi网络名称关键词
    private static final String[] CAMERA_WIFI_KEYWORDS = {
        "camera", "cam", "webcam", "ipcam", "security", "monitor", "surveillance",
        "摄像头", "监控", "安防", "录像", "视频", "直播", "live", "stream",
        "xiaomi", "hikvision", "dahua", "tp-link", "d-link", "netgear"
    };
    
    // 摄像头设备MAC地址前缀（部分知名厂商）
    private static final String[] CAMERA_MAC_PREFIXES = {
        "00:12:16", // Hikvision
        "44:19:B6", // Dahua
        "00:0F:7C", // Axis
        "00:40:8C", // Axis
        "00:80:F0", // Panasonic
        "00:02:D1", // Vivotek
        "00:03:C5"  // Mobotix
    };
    
    public interface OnCameraDetectedListener {
        void onCameraDetected(CameraInfo cameraInfo);
        void onScanComplete();
        void onScanProgress(String status);
    }
    
    public CameraDetector(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.executorService = Executors.newFixedThreadPool(20);
    }
    
    /**
     * 开始全面扫描周围的摄像头设备
     */
    public void startComprehensiveScan(OnCameraDetectedListener listener) {
        listener.onScanProgress("开始扫描周围摄像头设备...");
        
        // 1. 扫描WiFi网络中的摄像头
        scanWifiCameras(listener);
        
        // 2. 扫描网络摄像头
        scanNetworkCameras(listener);
        
        // 3. 扫描蓝牙摄像头
        scanBluetoothCameras(listener);
        
        // 4. 扫描UPnP设备
        scanUPnPCameras(listener);
    }
    
    /**
     * 扫描WiFi网络中的摄像头设备
     */
    public void scanWifiCameras(OnCameraDetectedListener listener) {
        if (wifiManager == null) {
            listener.onScanProgress("WiFi不可用");
            return;
        }
        
        listener.onScanProgress("扫描WiFi网络中的摄像头...");
        
        // 开始WiFi扫描
        wifiManager.startScan();
        
        // 获取扫描结果
        List<ScanResult> scanResults = wifiManager.getScanResults();
        
        for (ScanResult result : scanResults) {
            if (isCameraWifiNetwork(result)) {
                CameraInfo cameraInfo = createWifiCameraInfo(result);
                listener.onCameraDetected(cameraInfo);
            }
        }
    }
    
    /**
     * 扫描网络摄像头（增强版）
     */
    public void scanNetworkCameras(OnCameraDetectedListener listener) {
        listener.onScanProgress("扫描网络摄像头...");
        new EnhancedNetworkCameraScanTask(listener).execute();
    }
    
    /**
     * 扫描蓝牙摄像头
     */
    public void scanBluetoothCameras(OnCameraDetectedListener listener) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            listener.onScanProgress("蓝牙不可用");
            return;
        }
        
        listener.onScanProgress("扫描蓝牙摄像头...");
        
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
                    listener.onScanProgress("蓝牙扫描完成");
                }
            }
        };
        
        context.registerReceiver(receiver, filter);
        bluetoothAdapter.startDiscovery();
    }
    
    /**
     * 扫描UPnP摄像头设备
     */
    public void scanUPnPCameras(OnCameraDetectedListener listener) {
        listener.onScanProgress("扫描UPnP摄像头设备...");
        
        executorService.execute(() -> {
            try {
                // 发送UPnP发现消息
                String ssdpMsg = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "ST: upnp:rootdevice\r\n" +
                        "MX: 3\r\n\r\n";
                
                DatagramSocket socket = new DatagramSocket();
                DatagramPacket packet = new DatagramPacket(
                        ssdpMsg.getBytes(),
                        ssdpMsg.length(),
                        InetAddress.getByName("239.255.255.250"),
                        1900
                );
                
                socket.send(packet);
                
                // 监听响应
                byte[] buffer = new byte[1024];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                
                socket.setSoTimeout(5000); // 5秒超时
                
                try {
                    while (true) {
                        socket.receive(response);
                        String responseStr = new String(response.getData(), 0, response.getLength());
                        
                        if (isUPnPCameraDevice(responseStr)) {
                            CameraInfo cameraInfo = createUPnPCameraInfo(response.getAddress().getHostAddress(), responseStr);
                            listener.onCameraDetected(cameraInfo);
                        }
                    }
                } catch (IOException e) {
                    // 超时或其他错误，结束监听
                }
                
                socket.close();
                
            } catch (Exception e) {
                Log.e(TAG, "UPnP扫描失败", e);
            }
        });
    }
                        "MX: 3\r\n\r\n";
                
                DatagramSocket socket = new DatagramSocket();
                DatagramPacket packet = new DatagramPacket(
                        ssdpMsg.getBytes(),
                        ssdpMsg.length(),
                        InetAddress.getByName("239.255.255.250"),
                        1900
                );
                
                socket.send(packet);
                
                // 监听响应
                byte[] buffer = new byte[1024];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                
                socket.setSoTimeout(5000); // 5秒超时
                
                try {
                    while (true) {
                        socket.receive(response);
                        String responseStr = new String(response.getData(), 0, response.getLength());
                        
                        if (isUPnPCameraDevice(responseStr)) {
                            CameraInfo cameraInfo = createUPnPCameraInfo(response.getAddress().getHostAddress(), responseStr);
                            listener.onCameraDetected(cameraInfo);
                        }
                    }
                } catch (IOException e) {
                    // 超时或其他错误，结束监听
                }
                
                socket.close();
                
            } catch (Exception e) {
                Log.e(TAG, "UPnP扫描失败", e);
            }
        });
    }
    
    /**
     * 判断WiFi网络是否为摄像头设备
     */
    private boolean isCameraWifiNetwork(ScanResult result) {
        String ssid = result.SSID.toLowerCase();
        String bssid = result.BSSID.toUpperCase();
        
        // 检查SSID是否包含摄像头关键词
        for (String keyword : CAMERA_WIFI_KEYWORDS) {
            if (ssid.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        
        // 检查MAC地址前缀
        for (String prefix : CAMERA_MAC_PREFIXES) {
            if (bssid.startsWith(prefix)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 判断蓝牙设备是否为摄像头
     */
    private boolean isCameraDevice(BluetoothDevice device) {
        String deviceName = device.getName();
        if (deviceName == null) return false;
        
        String lowerName = deviceName.toLowerCase();
        return lowerName.contains("camera") || 
               lowerName.contains("cam") ||
               lowerName.contains("webcam") ||
               lowerName.contains("摄像头") ||
               lowerName.contains("监控") ||
               lowerName.contains("security") ||
               lowerName.contains("surveillance");
    }
    
    /**
     * 判断UPnP设备是否为摄像头
     */
    private boolean isUPnPCameraDevice(String response) {
        String lowerResponse = response.toLowerCase();
        return lowerResponse.contains("camera") ||
               lowerResponse.contains("webcam") ||
               lowerResponse.contains("ipcam") ||
               lowerResponse.contains("surveillance") ||
               lowerResponse.contains("security") ||
               lowerResponse.contains("video");
    }
    
    /**
     * 创建WiFi摄像头信息
     */
    private CameraInfo createWifiCameraInfo(ScanResult result) {
        CameraInfo cameraInfo = new CameraInfo();
        cameraInfo.setId(result.BSSID);
        cameraInfo.setName("WiFi摄像头: " + result.SSID);
        cameraInfo.setType(CameraInfo.CameraType.NETWORK);
        cameraInfo.setIpAddress("未知");
        cameraInfo.setAccessible(true);
        cameraInfo.setHasPermission(false);
        cameraInfo.setDescription("信号强度: " + result.level + "dBm, 频率: " + result.frequency + "MHz");
        return cameraInfo;
    }
    
    /**
     * 创建蓝牙摄像头信息
     */
    private CameraInfo createBluetoothCameraInfo(BluetoothDevice device) {
        CameraInfo cameraInfo = new CameraInfo();
        cameraInfo.setId(device.getAddress());
        cameraInfo.setName(device.getName() != null ? device.getName() : "未知蓝牙摄像头");
        cameraInfo.setType(CameraInfo.CameraType.BLUETOOTH);
        cameraInfo.setAccessible(device.getBondState() == BluetoothDevice.BOND_BONDED);
        cameraInfo.setHasPermission(false);
        cameraInfo.setDescription("MAC: " + device.getAddress() + ", 配对状态: " + 
                (device.getBondState() == BluetoothDevice.BOND_BONDED ? "已配对" : "未配对"));
        return cameraInfo;
    }
    
    /**
     * 创建UPnP摄像头信息
     */
    private CameraInfo createUPnPCameraInfo(String ipAddress, String response) {
        CameraInfo cameraInfo = new CameraInfo();
        cameraInfo.setId("upnp_" + ipAddress);
        cameraInfo.setName("UPnP摄像头设备");
        cameraInfo.setType(CameraInfo.CameraType.NETWORK);
        cameraInfo.setIpAddress(ipAddress);
        cameraInfo.setAccessible(true);
        cameraInfo.setHasPermission(false);
        cameraInfo.setDescription("通过UPnP协议发现的摄像头设备");
        return cameraInfo;
    }
    
    /**
     * 增强版网络摄像头扫描任务
     */
    private class EnhancedNetworkCameraScanTask extends AsyncTask<Void, CameraInfo, Void> {
        
        private OnCameraDetectedListener listener;
        
        public EnhancedNetworkCameraScanTask(OnCameraDetectedListener listener) {
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
            listener.onScanProgress("网络扫描完成");
        }
        
        private void scanLocalNetwork() {
            if (wifiManager == null) return;
            
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            String subnet = Formatter.formatIpAddress(ipAddress);
            
            // 获取子网前缀
            String[] parts = subnet.split("\\.");
            if (parts.length != 4) return;
            
            String networkPrefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
            
            // 扫描子网中的所有IP地址
            for (int i = 1; i <= 254; i++) {
                final String targetIp = networkPrefix + i;
                
                executorService.execute(() -> scanIpForCameras(targetIp));
            }
        }
        
        private void scanIpForCameras(String ipAddress) {
            for (int port : CAMERA_PORTS) {
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(ipAddress, port), 2000);
                    socket.close();
                    
                    // 发现开放的端口，创建摄像头信息
                    CameraInfo cameraInfo = new CameraInfo();
                    cameraInfo.setId(ipAddress + ":" + port);
                    cameraInfo.setName("网络摄像头 (" + ipAddress + ":" + port + ")");
                    cameraInfo.setType(CameraInfo.CameraType.NETWORK);
                    cameraInfo.setIpAddress(ipAddress);
                    cameraInfo.setPort(port);
                    cameraInfo.setAccessible(true);
                    cameraInfo.setHasPermission(false);
                    cameraInfo.setDescription("发现开放端口 " + port + "，可能是摄像头设备");
                    
                    publishProgress(cameraInfo);
                    
                    // 找到一个端口就跳出，避免重复检测同一设备
                    break;
                    
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