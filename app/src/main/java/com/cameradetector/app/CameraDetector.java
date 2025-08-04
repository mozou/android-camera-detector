package com.cameradetector.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enhanced Camera Detector - Detects various camera devices in the vicinity
 * Including network cameras, Bluetooth cameras, WiFi cameras, etc.
 */
public class CameraDetector {
    
    private static final String TAG = "CameraDetector";
    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private WifiManager wifiManager;
    private ExecutorService executorService;
    
    // Common network camera ports
    private static final int[] CAMERA_PORTS = {
        80, 8080, 554, 1935, 8000, 8888, 9000, 8081, 8082, 8083,
        81, 82, 83, 84, 85, 8001, 8002, 8003, 8004, 8005,
        1024, 1025, 1026, 1027, 1028, 1029, 1030, 1031, 1032
    };
    
    // Common camera WiFi network name keywords
    private static final String[] CAMERA_WIFI_KEYWORDS = {
        "camera", "cam", "webcam", "ipcam", "security", "monitor", "surveillance",
        "摄像头", "监控", "安防", "录像", "视频", "直播", "live", "stream",
        "xiaomi", "hikvision", "dahua", "tp-link", "d-link", "netgear",
        "dvr", "nvr", "ipc", "ip-cam", "cctv"
    };
    
    // Camera device MAC address prefixes (some well-known manufacturers)
    private static final String[] CAMERA_MAC_PREFIXES = {
        "00:12:16", // Hikvision
        "44:19:B6", // Dahua
        "00:0F:7C", // Axis
        "00:40:8C", // Axis
        "00:80:F0", // Panasonic
        "00:02:D1", // Vivotek
        "00:03:C5", // Mobotix
        "C0:56:E3", // Hangzhou Hikvision
        "BC:AD:28", // Hangzhou Hikvision
        "A4:14:37", // Hangzhou Hikvision
        "54:C4:15", // Hangzhou Hikvision
        "4C:11:BF", // Zhejiang Dahua
        "90:02:A9", // Zhejiang Dahua
        "E0:50:8B"  // Zhejiang Dahua
    };
    
    // Common camera HTTP paths for detection
    private static final String[] CAMERA_HTTP_PATHS = {
        "/", "/index.html", "/live", "/view", "/video", "/image", "/img",
        "/snapshot", "/snap.jpg", "/live.jpg", "/mjpg/video.mjpg",
        "/cgi-bin/snapshot.cgi", "/cgi-bin/camera", "/videostream.cgi",
        "/axis-cgi/jpg/image.cgi", "/onvif/device_service"
    };
    
    // Scan completion tracking
    private AtomicInteger activeScanCount = new AtomicInteger(0);
    private boolean scanCompleteNotified = false;
    
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
     * Start comprehensive scanning for camera devices
     */
    public void startComprehensiveScan(OnCameraDetectedListener listener) {
        listener.onScanProgress("Starting to scan for camera devices...");
        
        // Reset scan completion tracking
        activeScanCount.set(4); // WiFi, Network, Bluetooth, UPnP
        scanCompleteNotified = false;
        
        // Use thread pool to run scans in parallel for better efficiency
        executorService.execute(() -> {
            // 1. Scan for cameras in WiFi networks
            scanWifiCameras(listener);
            checkScanCompletion(listener);
        });
        
        executorService.execute(() -> {
            // 2. Scan for network cameras
            scanNetworkCameras(listener);
            checkScanCompletion(listener);
        });
        
        executorService.execute(() -> {
            // 3. Scan for Bluetooth cameras
            scanBluetoothCameras(listener);
            checkScanCompletion(listener);
        });
        
        executorService.execute(() -> {
            // 4. Scan for UPnP devices
            scanUPnPCameras(listener);
            checkScanCompletion(listener);
        });
        
        // Set a timeout to ensure scan completion is reported even if some scans hang
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!scanCompleteNotified) {
                listener.onScanProgress("Scan timeout reached, finalizing results");
                listener.onScanComplete();
                scanCompleteNotified = true;
            }
        }, 60000); // 60 second timeout
    }
    
    /**
     * Check if all scans are complete and notify listener
     */
    private synchronized void checkScanCompletion(OnCameraDetectedListener listener) {
        if (activeScanCount.decrementAndGet() == 0 && !scanCompleteNotified) {
            listener.onScanProgress("All scans completed");
            listener.onScanComplete();
            scanCompleteNotified = true;
        }
    }
    
    /**
     * Scan for cameras in WiFi networks
     */
    public void scanWifiCameras(OnCameraDetectedListener listener) {
        if (wifiManager == null) {
            listener.onScanProgress("WiFi not available");
            return;
        }
        
        listener.onScanProgress("Scanning for cameras in WiFi networks...");
        
        // Register WiFi scan results receiver
        BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                if (success) {
                    // Scan successful, process results
                    processWifiScanResults(listener);
                } else {
                    // Scan failed, use last results
                    listener.onScanProgress("WiFi scan failed, using cached results");
                    processWifiScanResults(listener);
                }
                
                // Unregister receiver
                try {
                    context.unregisterReceiver(this);
                } catch (Exception e) {
                    Log.e(TAG, "Error unregistering WiFi receiver", e);
                }
            }
        };
        
        // Register receiver and start scan
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(wifiScanReceiver, intentFilter);
        
        // Start WiFi scan
        try {
            boolean scanStarted = wifiManager.startScan();
            if (!scanStarted) {
                // If scan can't start, process existing results
                listener.onScanProgress("WiFi scan restricted, using cached results");
                processWifiScanResults(listener);
                try {
                    context.unregisterReceiver(wifiScanReceiver);
                } catch (Exception e) {
                    Log.e(TAG, "Error unregistering WiFi receiver", e);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Insufficient WiFi scan permissions", e);
            listener.onScanProgress("Insufficient WiFi scan permissions, using cached results");
            processWifiScanResults(listener);
            try {
                context.unregisterReceiver(wifiScanReceiver);
            } catch (Exception ex) {
                Log.e(TAG, "Error unregistering WiFi receiver", ex);
            }
        }
        
        // Set a timeout to ensure we don't wait forever for scan results
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                context.unregisterReceiver(wifiScanReceiver);
                listener.onScanProgress("WiFi scan timeout reached");
            } catch (IllegalArgumentException e) {
                // Receiver already unregistered, ignore
            }
        }, 10000); // 10 second timeout
    }
    
    /**
     * Process WiFi scan results
     */
    private void processWifiScanResults(OnCameraDetectedListener listener) {
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
    
    /**
     * Scan for network cameras (enhanced version)
     */
    public void scanNetworkCameras(OnCameraDetectedListener listener) {
        listener.onScanProgress("Scanning for network cameras...");
        new EnhancedNetworkCameraScanTask(listener).execute();
    }
    
    /**
     * Scan for Bluetooth cameras
     */
    public void scanBluetoothCameras(OnCameraDetectedListener listener) {
        if (bluetoothAdapter == null) {
            listener.onScanProgress("Bluetooth adapter not available");
            return;
        }
        
        if (!bluetoothAdapter.isEnabled()) {
            listener.onScanProgress("Bluetooth not enabled");
            // Try to request user to enable Bluetooth
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(enableBtIntent);
                // Give user some time to respond to Bluetooth enable request
                Thread.sleep(5000);
                // Recheck Bluetooth status
                if (!bluetoothAdapter.isEnabled()) {
                    listener.onScanProgress("User did not enable Bluetooth, skipping Bluetooth device scan");
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to request Bluetooth enable", e);
                return;
            }
        }
        
        listener.onScanProgress("Scanning for Bluetooth cameras...");
        
        try {
            // Scan paired Bluetooth devices
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : pairedDevices) {
                if (isCameraDevice(device)) {
                    CameraInfo cameraInfo = createBluetoothCameraInfo(device);
                    listener.onCameraDetected(cameraInfo);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No Bluetooth permissions", e);
            listener.onScanProgress("Missing Bluetooth permissions, cannot get paired devices");
        }
        
        // Start discovering new devices
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    
                    if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                        try {
                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            if (device != null && isCameraDevice(device)) {
                                CameraInfo cameraInfo = createBluetoothCameraInfo(device);
                                listener.onCameraDetected(cameraInfo);
                            }
                        } catch (SecurityException e) {
                            Log.e(TAG, "No Bluetooth permissions", e);
                        }
                    } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                        try {
                            context.unregisterReceiver(this);
                        } catch (Exception e) {
                            Log.e(TAG, "Error unregistering Bluetooth receiver", e);
                        }
                        listener.onScanProgress("Bluetooth scan completed");
                    }
                }
            };
            
            context.registerReceiver(receiver, filter);
            
            // Set timeout to ensure receiver is eventually unregistered
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    context.unregisterReceiver(receiver);
                } catch (Exception e) {
                    // Receiver may already be unregistered, ignore exception
                }
            }, 30000); // 30 second timeout
            
            // Start Bluetooth discovery
            if (!bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.startDiscovery();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No Bluetooth permissions", e);
            listener.onScanProgress("Missing Bluetooth permissions, cannot scan for new devices");
        } catch (Exception e) {
            Log.e(TAG, "Bluetooth scan failed", e);
            listener.onScanProgress("Bluetooth scan failed: " + e.getMessage());
        }
    }
    
    /**
     * Scan for UPnP camera devices
     */
    public void scanUPnPCameras(OnCameraDetectedListener listener) {
        listener.onScanProgress("Scanning for UPnP camera devices...");
        
        executorService.execute(() -> {
            try {
                // Send UPnP discovery message
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
                
                // Listen for responses
                byte[] buffer = new byte[1024];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                
                socket.setSoTimeout(8000); // 8 second timeout
                
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
                    // Timeout or other error, end listening
                    listener.onScanProgress("UPnP scan timeout reached");
                }
                
                socket.close();
                
            } catch (Exception e) {
                Log.e(TAG, "UPnP scan failed", e);
                listener.onScanProgress("UPnP scan failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Determine if a WiFi network is a camera device
     */
    private boolean isCameraWifiNetwork(ScanResult result) {
        // Prevent crashes due to null values
        if (result.SSID == null || result.BSSID == null) {
            return false;
        }
        
        String ssid = result.SSID.toLowerCase();
        String bssid = result.BSSID.toUpperCase();
        
        // Check if SSID contains camera keywords
        for (String keyword : CAMERA_WIFI_KEYWORDS) {
            if (ssid.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        
        // Check MAC address prefix
        for (String prefix : CAMERA_MAC_PREFIXES) {
            if (bssid.startsWith(prefix)) {
                return true;
            }
        }
        
        // Add more heuristic detection methods
        // Check for common camera device SSID patterns
        if (ssid.matches("ipc[0-9]+") || 
            ssid.matches("cam[0-9]+") || 
            ssid.matches("camera[0-9]+") ||
            ssid.matches("ip-?cam.*") ||
            ssid.matches("dvr-?[0-9]+")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Determine if a Bluetooth device is a camera
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
               lowerName.contains("surveillance") ||
               lowerName.contains("ipcam") ||
               lowerName.contains("dvr") ||
               lowerName.contains("nvr") ||
               lowerName.contains("hikvision") ||
               lowerName.contains("dahua");
    }
    
    /**
     * Determine if a UPnP device is a camera
     */
    private boolean isUPnPCameraDevice(String response) {
        String lowerResponse = response.toLowerCase();
        return lowerResponse.contains("camera") ||
               lowerResponse.contains("webcam") ||
               lowerResponse.contains("ipcam") ||
               lowerResponse.contains("surveillance") ||
               lowerResponse.contains("security") ||
               lowerResponse.contains("video") ||
               lowerResponse.contains("onvif") ||
               lowerResponse.contains("rtsp") ||
               lowerResponse.contains("streaming") ||
               lowerResponse.contains("hikvision") ||
               lowerResponse.contains("dahua") ||
               lowerResponse.contains("axis");
    }
    
    /**
     * Create WiFi camera information
     */
    private CameraInfo createWifiCameraInfo(ScanResult result) {
        CameraInfo cameraInfo = new CameraInfo();
        cameraInfo.setId(result.BSSID);
        cameraInfo.setName("WiFi Camera: " + result.SSID);
        cameraInfo.setType(CameraInfo.CameraType.NETWORK);
        cameraInfo.setIpAddress("Unknown");
        cameraInfo.setAccessible(true);
        cameraInfo.setHasPermission(false);
        cameraInfo.setDescription("Signal strength: " + result.level + "dBm, Frequency: " + result.frequency + "MHz");
        
        // Try to determine manufacturer
        String manufacturer = getManufacturerFromMac(result.BSSID);
        if (manufacturer != null) {
            cameraInfo.setManufacturer(manufacturer);
        }
        
        return cameraInfo;
    }
    
    /**
     * Create Bluetooth camera information
     */
    private CameraInfo createBluetoothCameraInfo(BluetoothDevice device) {
        CameraInfo cameraInfo = new CameraInfo();
        cameraInfo.setId(device.getAddress());
        cameraInfo.setName(device.getName() != null ? device.getName() : "Unknown Bluetooth Camera");
        cameraInfo.setType(CameraInfo.CameraType.BLUETOOTH);
        cameraInfo.setAccessible(device.getBondState() == BluetoothDevice.BOND_BONDED);
        cameraInfo.setHasPermission(false);
        
        // Try to get more device info
        try {
            int deviceClass = device.getBluetoothClass().getDeviceClass();
            String bondState = device.getBondState() == BluetoothDevice.BOND_BONDED ? "Paired" : "Not paired";
            cameraInfo.setDescription("MAC: " + device.getAddress() + ", Pairing status: " + bondState + 
                                     ", Device class: " + deviceClass);
        } catch (SecurityException e) {
            cameraInfo.setDescription("MAC: " + device.getAddress() + " (Limited info due to permission restrictions)");
        }
        
        return cameraInfo;
    }
    
    /**
     * Create UPnP camera information
     */
    private CameraInfo createUPnPCameraInfo(String ipAddress, String response) {
        CameraInfo cameraInfo = new CameraInfo();
        cameraInfo.setId("upnp_" + ipAddress);
        
        // Try to extract device name from UPnP response
        String deviceName = extractUPnPDeviceName(response);
        cameraInfo.setName(deviceName != null ? deviceName : "UPnP Camera Device");
        
        cameraInfo.setType(CameraInfo.CameraType.NETWORK);
        cameraInfo.setIpAddress(ipAddress);
        cameraInfo.setAccessible(true);
        cameraInfo.setHasPermission(false);
        
        // Try to extract manufacturer from UPnP response
        String manufacturer = extractUPnPManufacturer(response);
        if (manufacturer != null) {
            cameraInfo.setManufacturer(manufacturer);
        }
        
        cameraInfo.setDescription("Camera device discovered via UPnP protocol");
        
        // Try to determine if this device is actually accessible
        executorService.execute(() -> {
            boolean isAccessible = testUPnPDeviceAccess(ipAddress);
            cameraInfo.setAccessible(isAccessible);
        });
        
        return cameraInfo;
    }
    
    /**
     * Extract device name from UPnP response
     */
    private String extractUPnPDeviceName(String response) {
        // Simple extraction - could be improved with XML parsing
        try {
            int friendlyNameStart = response.indexOf("<friendlyName>");
            if (friendlyNameStart >= 0) {
                int contentStart = friendlyNameStart + "<friendlyName>".length();
                int contentEnd = response.indexOf("</friendlyName>", contentStart);
                if (contentEnd > contentStart) {
                    return response.substring(contentStart, contentEnd).trim();
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return null;
    }
    
    /**
     * Extract manufacturer from UPnP response
     */
    private String extractUPnPManufacturer(String response) {
        // Simple extraction - could be improved with XML parsing
        try {
            int manufacturerStart = response.indexOf("<manufacturer>");
            if (manufacturerStart >= 0) {
                int contentStart = manufacturerStart + "<manufacturer>".length();
                int contentEnd = response.indexOf("</manufacturer>", contentStart);
                if (contentEnd > contentStart) {
                    return response.substring(contentStart, contentEnd).trim();
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return null;
    }
    
    /**
     * Test if a UPnP device is accessible
     */
    private boolean testUPnPDeviceAccess(String ipAddress) {
        // Try common camera HTTP paths
        for (int port : new int[]{80, 8080, 554}) {
            for (String path : CAMERA_HTTP_PATHS) {
                try {
                    URL url = new URL("http://" + ipAddress + ":" + port + path);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(2000);
                    connection.setReadTimeout(2000);
                    connection.setRequestMethod("GET");
                    
                    int responseCode = connection.getResponseCode();
                    connection.disconnect();
                    
                    if (responseCode == HttpURLConnection.HTTP_OK || 
                        responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        return true;
                    }
                } catch (Exception e) {
                    // Connection failed, try next path
                }
            }
        }
        return false;
    }
    
    /**
     * Get manufacturer name from MAC address
     */
    private String getManufacturerFromMac(String macAddress) {
        if (macAddress == null || macAddress.length() < 8) {
            return null;
        }
        
        String prefix = macAddress.substring(0, 8).toUpperCase();
        
        if (prefix.startsWith("00:12:16") || prefix.startsWith("C0:56:E3") || 
            prefix.startsWith("BC:AD:28") || prefix.startsWith("A4:14:37") || 
            prefix.startsWith("54:C4:15")) {
            return "Hikvision";
        } else if (prefix.startsWith("44:19:B6") || prefix.startsWith("4C:11:BF") || 
                   prefix.startsWith("90:02:A9") || prefix.startsWith("E0:50:8B")) {
            return "Dahua";
        } else if (prefix.startsWith("00:0F:7C") || prefix.startsWith("00:40:8C")) {
            return "Axis";
        } else if (prefix.startsWith("00:80:F0")) {
            return "Panasonic";
        } else if (prefix.startsWith("00:02:D1")) {
            return "Vivotek";
        } else if (prefix.startsWith("00:03:C5")) {
            return "Mobotix";
        }
        
        return null;
    }
    
    /**
     * Enhanced network camera scan task
     */
    private class EnhancedNetworkCameraScanTask extends AsyncTask<Void, CameraInfo, Void> {
        
        private OnCameraDetectedListener listener;
        private int totalIpsToScan = 0;
        private AtomicInteger scannedIps = new AtomicInteger(0);
        
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
            listener.onScanProgress("Network scan completed");
        }
        
        private void scanLocalNetwork() {
            if (wifiManager == null) return;
            
            try {
                int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
                String subnet = Formatter.formatIpAddress(ipAddress);
                
                // Get subnet prefix
                String[] parts = subnet.split("\\.");
                if (parts.length != 4) return;
                
                String networkPrefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
                
                // Scan all IP addresses in subnet
                totalIpsToScan = 254;
                List<String> priorityIps = new ArrayList<>();
                
                // Add common camera IP addresses to priority list
                priorityIps.add(networkPrefix + "1");  // Default gateway
                priorityIps.add(networkPrefix + "10"); // Common camera IP
                priorityIps.add(networkPrefix + "20"); // Common camera IP
                priorityIps.add(networkPrefix + "64"); // Common camera IP
                priorityIps.add(networkPrefix + "100"); // Common camera IP
                priorityIps.add(networkPrefix + "101"); // Common camera IP
                priorityIps.add(networkPrefix + "254"); // Common camera IP
                
                // Scan priority IPs first
                for (String ip : priorityIps) {
                    executorService.execute(() -> {
                        scanIpForCameras(ip);
                        updateProgress();
                    });
                }
                
                // Then scan the rest
                for (int i = 1; i <= 254; i++) {
                    final String targetIp = networkPrefix + i;
                    if (!priorityIps.contains(targetIp)) {
                        executorService.execute(() -> {
                            scanIpForCameras(targetIp);
                            updateProgress();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error scanning local network", e);
            }
        }
        
        private void updateProgress() {
            int completed = scannedIps.incrementAndGet();
            if (completed % 25 == 0 || completed == totalIpsToScan) {
                listener.onScanProgress("Network scan progress: " + completed + "/" + totalIpsToScan);
            }
        }
        
        private void scanIpForCameras(String ipAddress) {
            // First try common camera ports
            for (int port : CAMERA_PORTS) {
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(ipAddress, port), 1500);
                    socket.close();
                    
                    // Found open port, create camera info
                    CameraInfo cameraInfo = new CameraInfo();
                    cameraInfo.setId(ipAddress + ":" + port);
                    cameraInfo.setName("Network Camera (" + ipAddress + ":" + port + ")");
                    cameraInfo.setType(CameraInfo.CameraType.NETWORK);
                    cameraInfo.setIpAddress(ipAddress);
                    cameraInfo.setPort(port);
                    cameraInfo.setAccessible(true);
                    cameraInfo.setHasPermission(false);
                    cameraInfo.setDescription("Found open port " + port + ", likely a camera device");
                    
                    // Try to detect camera type and get more info
                    enhanceCameraInfo(cameraInfo);
                    
                    publishProgress(cameraInfo);
                    
                    // Found one port, break to avoid duplicate detections
                    break;
                    
                } catch (IOException e) {
                    // Port not reachable, continue to next
                }
            }
        }
        
        /**
         * Try to enhance camera information by checking HTTP response
         */
        private void enhanceCameraInfo(CameraInfo cameraInfo) {
            String ipAddress = cameraInfo.getIpAddress();
            int port = cameraInfo.getPort();
            
            // Try to get more info about the camera by checking HTTP headers
            for (String path : CAMERA_HTTP_PATHS) {
                try {
                    URL url = new URL("http://" + ipAddress + ":" + port + path);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(1500);
                    connection.setReadTimeout(1500);
                    connection.setRequestMethod("HEAD");
                    
                    int responseCode = connection.getResponseCode();
                    
                    if (responseCode == HttpURLConnection.HTTP_OK || 
                        responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        
                        // Check for camera-specific headers
                        String server = connection.getHeaderField("Server");
                        if (server != null) {
                            if (server.toLowerCase().contains("hikvision")) {
                                cameraInfo.setManufacturer("Hikvision");
                                cameraInfo.setName("Hikvision Camera (" + ipAddress + ")");
                            } else if (server.toLowerCase().contains("dahua")) {
                                cameraInfo.setManufacturer("Dahua");
                                cameraInfo.setName("Dahua Camera (" + ipAddress + ")");
                            } else if (server.toLowerCase().contains("axis")) {
                                cameraInfo.setManufacturer("Axis");
                                cameraInfo.setName("Axis Camera (" + ipAddress + ")");
                            } else {
                                cameraInfo.setDescription(cameraInfo.getDescription() + "\nServer: " + server);
                            }
                        }
                        
                        // Try to get authentication type
                        String auth = connection.getHeaderField("WWW-Authenticate");
                        if (auth != null) {
                            cameraInfo.setDescription(cameraInfo.getDescription() + 
                                                     "\nRequires authentication: " + auth);
                        }
                        
                        // Set permission status based on auth requirement
                        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                            cameraInfo.setHasPermission(false);
                        } else {
                            // If we can access without auth, consider it as having permission
                            cameraInfo.setHasPermission(true);
                        }
                        
                        break;
                    }
                    
                    connection.disconnect();
                    
                } catch (Exception e) {
                    // Failed to get additional info, continue
                }
            }
        }
