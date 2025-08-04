package com.cameradetector.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CameraDetector {
    
    private static final String TAG = "CameraDetector";
    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private WifiManager wifiManager;
    private ExecutorService executorService;
    
    private static final int[] PRIMARY_CAMERA_PORTS = {554, 8080, 80, 1935, 8000, 8554};
    private AtomicInteger activeScanCount = new AtomicInteger(0);
    private boolean scanCompleteNotified = false;
    
    public interface OnCameraDetectedListener {
        void onCameraDetected(CameraInfo cameraInfo);
        void onScanComplete();
        void onScanProgress(String status);
        void onProgressUpdate(int current, int total, String currentTask);
    }
    
    public CameraDetector(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.executorService = Executors.newFixedThreadPool(10);
    }
    
    public void startComprehensiveScan(OnCameraDetectedListener listener) {
        listener.onScanProgress("Starting comprehensive camera scan...");
        
        activeScanCount.set(8);
        scanCompleteNotified = false;
        
        executorService.execute(() -> {
            scanWifiCameras(listener);
            checkScanCompletion(listener);
        });
        
        executorService.execute(() -> {
            scanNetworkCameras(listener);
            checkScanCompletion(listener);
        });
        
        executorService.execute(() -> {
            scanBluetoothCameras(listener);
            checkScanCompletion(listener);
        });
        
        executorService.execute(() -> {
            scanUPnPCameras(listener);
            checkScanCompletion(listener);
        });
        
        executorService.execute(() -> {
            scanBroadcastCameras(listener);
            checkScanCompletion(listener);
        });
        
        executorService.execute(() -> {
            scanPublicNetworkCameras(listener);
            checkScanCompletion(listener);
        });
        
        executorService.execute(() -> {
            scanCameraHotspots(listener);
            checkScanCompletion(listener);
        });
        
        executorService.execute(() -> {
            scanWideRangeNetworks(listener);
            checkScanCompletion(listener);
        });
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!scanCompleteNotified) {
                listener.onScanProgress("Scan timeout reached, finalizing results");
                listener.onScanComplete();
                scanCompleteNotified = true;
            }
        }, 90000);
    }
    
    private synchronized void checkScanCompletion(OnCameraDetectedListener listener) {
        if (activeScanCount.decrementAndGet() == 0 && !scanCompleteNotified) {
            listener.onScanProgress("All scans completed");
            listener.onScanComplete();
            scanCompleteNotified = true;
        }
    }
    
    public void scanWifiCameras(OnCameraDetectedListener listener) {
        if (wifiManager == null) {
            listener.onScanProgress("WiFi not available");
            return;
        }
        
        listener.onScanProgress("Scanning for cameras in WiFi networks...");
        
        try {
            List<ScanResult> scanResults = wifiManager.getScanResults();
            
            for (ScanResult result : scanResults) {
                if (result.SSID != null && !result.SSID.isEmpty() && isCameraWifiNetwork(result)) {
                    CameraInfo cameraInfo = createWifiCameraInfo(result);
                    listener.onCameraDetected(cameraInfo);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing WiFi scan results", e);
        }
    }
    
    public void scanNetworkCameras(OnCameraDetectedListener listener) {
        listener.onScanProgress("Scanning for network cameras...");
        new NetworkCameraScanTask(listener).execute();
    }
    
    public void scanBluetoothCameras(OnCameraDetectedListener listener) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            listener.onScanProgress("Bluetooth not available");
            return;
        }
        
        listener.onScanProgress("Scanning for Bluetooth cameras...");
        
        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : pairedDevices) {
                if (isCameraDevice(device)) {
                    CameraInfo cameraInfo = createBluetoothCameraInfo(device);
                    listener.onCameraDetected(cameraInfo);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No Bluetooth permissions", e);
        }
    }
    
    public void scanUPnPCameras(OnCameraDetectedListener listener) {
        listener.onScanProgress("Scanning for UPnP camera devices...");
    }
    
    public void scanBroadcastCameras(OnCameraDetectedListener listener) {
        listener.onScanProgress("Scanning for cameras using broadcast discovery...");
    }
    
    public void scanPublicNetworkCameras(OnCameraDetectedListener listener) {
        listener.onScanProgress("Scanning adjacent networks for cameras...");
        
        if (wifiManager == null) return;
        
        try {
            int currentIp = wifiManager.getConnectionInfo().getIpAddress();
            String currentSubnet = Formatter.formatIpAddress(currentIp);
            String[] parts = currentSubnet.split("\\.");
            
            if (parts.length == 4) {
                String baseNetwork = parts[0] + "." + parts[1] + ".";
                int currentThirdOctet = Integer.parseInt(parts[2]);
                
                for (int i = Math.max(0, currentThirdOctet - 2); 
                     i <= Math.min(255, currentThirdOctet + 2); i++) {
                    
                    if (i != currentThirdOctet) {
                        String targetNetwork = baseNetwork + i + ".";
                        scanNetworkSegment(targetNetwork, listener);
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Adjacent network scan failed", e);
        }
    }
    
    public void scanCameraHotspots(OnCameraDetectedListener listener) {
        listener.onScanProgress("Scanning for camera WiFi hotspots...");
        
        if (wifiManager == null) return;
        
        try {
            List<ScanResult> scanResults = wifiManager.getScanResults();
            
            for (ScanResult result : scanResults) {
                if (result.SSID != null && isCameraHotspot(result.SSID)) {
                    CameraInfo cameraInfo = createHotspotCameraInfo(result);
                    listener.onCameraDetected(cameraInfo);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Camera hotspot scan failed", e);
        }
    }
    
    private void scanWideRangeNetworks(OnCameraDetectedListener listener) {
        listener.onScanProgress("Scanning wide range networks for cameras...");
        
        String[] commonNetworkSegments = {
            "192.168.0.", "192.168.1.", "192.168.2.", "192.168.3.", "192.168.4.",
            "192.168.10.", "192.168.20.", "192.168.30.", "192.168.50.", "192.168.100.",
            "10.0.0.", "10.0.1.", "10.0.2.", "10.0.10.", "10.0.20.",
            "10.1.0.", "10.1.1.", "10.1.10.", "10.10.0.", "10.10.1.",
            "172.16.0.", "172.16.1.", "172.16.10.", "172.17.0.", "172.18.0."
        };
        
        int[] commonCameraIPs = {1, 2, 10, 20, 50, 64, 100, 101, 108, 110, 200, 254};
        
        for (String segment : commonNetworkSegments) {
            for (int ip : commonCameraIPs) {
                final String targetIP = segment + ip;
                
                executorService.execute(() -> {
                    try {
                        if (isHostReachable(targetIP, 1000)) {
                            listener.onScanProgress("Found reachable host: " + targetIP);
                            
                            for (int port : PRIMARY_CAMERA_PORTS) {
                                CameraInfo camera = quickCameraVerification(targetIP, port);
                                if (camera != null) {
                                    listener.onCameraDetected(camera);
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Continue to next IP
                    }
                });
            }
        }
        
        listener.onScanProgress("Wide range network scan completed");
    }
    
    private boolean isCameraWifiNetwork(ScanResult result) {
        String ssid = result.SSID.toLowerCase().trim();
        
        return ssid.matches("ipc[0-9]{3,6}") ||
               ssid.matches("cam[0-9]{2,4}") ||
               ssid.matches("camera[_-]?[0-9]+") ||
               ssid.matches("dvr[_-]?[0-9]+") ||
               ssid.matches("nvr[_-]?[0-9]+") ||
               ssid.matches("hikvision[_-]?.*") ||
               ssid.matches("dahua[_-]?.*") ||
               ssid.matches("axis[_-]?.*") ||
               ssid.contains("ipcam") ||
               ssid.contains("webcam") ||
               ssid.contains("security") ||
               ssid.contains("surveillance") ||
               ssid.contains("monitor") ||
               ssid.contains("摄像头") ||
               ssid.contains("监控") ||
               ssid.contains("安防") ||
               ssid.contains("cctv");
    }
    
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
               lowerName.contains("surveillance") ||
               lowerName.contains("ipcam") ||
               lowerName.contains("dvr") ||
               lowerName.contains("nvr") ||
               lowerName.contains("hikvision") ||
               lowerName.contains("dahua");
    }
    
    private boolean isCameraHotspot(String ssid) {
        String lowerSSID = ssid.toLowerCase();
        
        return lowerSSID.matches(".*cam.*setup.*") ||
               lowerSSID.matches(".*camera.*config.*") ||
               lowerSSID.matches(".*ipc.*[0-9]+.*") ||
               lowerSSID.matches(".*hikvision.*") ||
               lowerSSID.matches(".*dahua.*") ||
               lowerSSID.matches(".*axis.*setup.*") ||
               lowerSSID.matches(".*vivotek.*") ||
               lowerSSID.matches(".*mobotix.*") ||
               lowerSSID.startsWith("cam_") ||
               lowerSSID.startsWith("camera_") ||
               lowerSSID.startsWith("ipc_");
    }
    
    private boolean isHostReachable(String host, int timeout) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isReachable(timeout);
        } catch (Exception e) {
            return false;
        }
    }
    
    private CameraInfo createWifiCameraInfo(ScanResult result) {
        CameraInfo cameraInfo = new CameraInfo();
        cameraInfo.setId(result.BSSID);
        cameraInfo.setName("WiFi Camera: " + result.SSID);
        cameraInfo.setType(CameraInfo.CameraType.NETWORK);
        cameraInfo.setIpAddress("Unknown");
        cameraInfo.setAccessible(true);
        cameraInfo.setHasPermission(false);
        cameraInfo.setDescription("Signal strength: " + result.level + "dBm, Frequency: " + result.frequency + "MHz");
        return cameraInfo;
    }
    
    private CameraInfo createBluetoothCameraInfo(BluetoothDevice device) {
        CameraInfo cameraInfo = new CameraInfo();
        cameraInfo.setId(device.getAddress());
        cameraInfo.setName(device.getName() != null ? device.getName() : "Unknown Bluetooth Camera");
        cameraInfo.setType(CameraInfo.CameraType.BLUETOOTH);
        cameraInfo.setAccessible(device.getBondState() == BluetoothDevice.BOND_BONDED);
        cameraInfo.setHasPermission(false);
        cameraInfo.setDescription("MAC: " + device.getAddress());
        return cameraInfo;
    }
    
    private CameraInfo createHotspotCameraInfo(ScanResult result) {
        CameraInfo cameraInfo = new CameraInfo();
        cameraInfo.setId("hotspot_" + result.BSSID);
        cameraInfo.setName("Camera Hotspot: " + result.SSID);
        cameraInfo.setType(CameraInfo.CameraType.NETWORK);
        cameraInfo.setAccessible(false);
        cameraInfo.setHasPermission(false);
        cameraInfo.setDescription("Camera in setup/configuration mode. Connect to this hotspot to configure the camera.");
        return cameraInfo;
    }
    
    private CameraInfo quickCameraVerification(String ipAddress, int port) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ipAddress, port), 2000);
            socket.close();
            
            URL url = new URL("http://" + ipAddress + ":" + port + "/");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("HEAD");
            
            int responseCode = connection.getResponseCode();
            String server = connection.getHeaderField("Server");
            
            connection.disconnect();
            
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
                responseCode == HttpURLConnection.HTTP_FORBIDDEN ||
                responseCode == HttpURLConnection.HTTP_OK) {
                
                CameraInfo cameraInfo = new CameraInfo();
                cameraInfo.setId("wide_" + ipAddress + ":" + port);
                cameraInfo.setIpAddress(ipAddress);
                cameraInfo.setPort(port);
                cameraInfo.setType(CameraInfo.CameraType.NETWORK);
                cameraInfo.setAccessible(true);
                cameraInfo.setHasPermission(responseCode != HttpURLConnection.HTTP_UNAUTHORIZED);
                
                String name = "Network Camera (" + ipAddress + ":" + port + ")";
                cameraInfo.setName(name);
                
                String description = "Camera found on separate network segment";
                if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    description += " (requires authentication)";
                }
                cameraInfo.setDescription(description);
                
                return cameraInfo;
            }
            
        } catch (Exception e) {
            // Not a camera or not accessible
        }
        
        return null;
    }
    
    private void scanNetworkSegment(String networkPrefix, OnCameraDetectedListener listener) {
        int[] commonIPs = {1, 2, 10, 20, 50, 64, 100, 101, 108, 200, 254};
        
        for (int ip : commonIPs) {
            String targetIP = networkPrefix + ip;
            
            executorService.execute(() -> {
                CameraInfo camera = quickCameraVerification(targetIP, 554);
                if (camera == null) {
                    camera = quickCameraVerification(targetIP, 8080);
                }
                if (camera == null) {
                    camera = quickCameraVerification(targetIP, 80);
                }
                
                if (camera != null) {
                    listener.onCameraDetected(camera);
                }
            });
        }
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
            listener.onScanProgress("Network scan completed");
        }
        
        private void scanLocalNetwork() {
            if (wifiManager == null) return;
            
            try {
                int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
                String subnet = Formatter.formatIpAddress(ipAddress);
                
                String[] parts = subnet.split("\\.");
                if (parts.length != 4) return;
                
                String networkPrefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
                
                String[] priorityIps = {
                    networkPrefix + "1",   // Default gateway
                    networkPrefix + "10",  // Common camera IP
                    networkPrefix + "64",  // Common camera IP
                    networkPrefix + "100", // Common camera IP
                    networkPrefix + "101", // Common camera IP
                    networkPrefix + "254"  // Common camera IP
                };
                
                for (String ip : priorityIps) {
                    executorService.execute(() -> {
                        scanIpForCameras(ip);
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error scanning local network", e);
            }
        }
        
        private void scanIpForCameras(String ipAddress) {
            for (int port : PRIMARY_CAMERA_PORTS) {
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(ipAddress, port), 2000);
                    socket.close();
                    
                    CameraInfo cameraInfo = verifyCamera(ipAddress, port);
                    if (cameraInfo != null) {
                        publishProgress(cameraInfo);
                        return;
                    }
                    
                } catch (IOException e) {
                    // Port not reachable, continue to next
                }
            }
        }
        
        private CameraInfo verifyCamera(String ipAddress, int port) {
            try {
                URL url = new URL("http://" + ipAddress + ":" + port + "/");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestMethod("HEAD");
                
                int responseCode = connection.getResponseCode();
                
                connection.disconnect();
                
                if (responseCode == HttpURLConnection.HTTP_OK || 
                    responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    
                    CameraInfo cameraInfo = new CameraInfo();
                    cameraInfo.setId(ipAddress + ":" + port);
                    cameraInfo.setIpAddress(ipAddress);
                    cameraInfo.setPort(port);
                    cameraInfo.setType(CameraInfo.CameraType.NETWORK);
                    cameraInfo.setAccessible(true);
                    cameraInfo.setHasPermission(responseCode == HttpURLConnection.HTTP_OK);
                    cameraInfo.setName("IP Camera (" + ipAddress + ":" + port + ")");
                    
                    String description = "Verified camera device on port " + port;
                    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        description += " (requires authentication)";
                    }
                    cameraInfo.setDescription(description);
                    
                    return cameraInfo;
                }
                
            } catch (Exception e) {
                // Not a camera or not accessible
            }
            
            return null;
        }
    }
    
    public void destroy() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}