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
    
    // Enhanced camera-specific ports with priority
    private static final int[] PRIMARY_CAMERA_PORTS = {
        554,   // RTSP (most common for IP cameras)
        8080,  // HTTP alternative for cameras
        80,    // HTTP (web interface)
        1935,  // RTMP streaming
        8000,  // Common camera HTTP port
        8554,  // Alternative RTSP port
        10554, // Another RTSP variant
    };
    
    private static final int[] SECONDARY_CAMERA_PORTS = {
        8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8089,
        9000, 9001, 9002, 9003, 9004, 9005,
        7000, 7001, 7002, 7003, 7004, 7005,
        6000, 6001, 6002, 6003, 6004, 6005
    };
    
    // Common camera WiFi network name keywords
    private static final String[] CAMERA_WIFI_KEYWORDS = {
        "camera", "cam", "webcam", "ipcam", "security", "monitor", "surveillance",
        "摄像头", "监控", "安防", "录像", "视频", "直播", "live", "stream",
        "xiaomi", "hikvision", "dahua", "tp-link", "d-link", "netgear",
        "dvr", "nvr", "ipc", "ip-cam", "cctv"
    };
    
    // Comprehensive camera manufacturer MAC prefixes
    private static final String[][] CAMERA_MAC_DATABASE = {
        // Hikvision (multiple prefixes)
        {"00:12:16", "Hikvision"}, {"C0:56:E3", "Hikvision"}, {"BC:AD:28", "Hikvision"},
        {"A4:14:37", "Hikvision"}, {"54:C4:15", "Hikvision"}, {"28:57:BE", "Hikvision"},
        {"44:19:B6", "Hikvision"}, {"68:3E:34", "Hikvision"}, {"B0:7B:25", "Hikvision"},
        
        // Dahua
        {"4C:11:BF", "Dahua"}, {"90:02:A9", "Dahua"}, {"E0:50:8B", "Dahua"},
        {"08:57:00", "Dahua"}, {"6C:C2:17", "Dahua"}, {"F4:5C:89", "Dahua"},
        
        // Axis Communications
        {"00:0F:7C", "Axis"}, {"00:40:8C", "Axis"}, {"AC:CC:8E", "Axis"},
        {"B8:A4:4F", "Axis"}, {"00:80:F0", "Axis"},
        
        // Other major manufacturers
        {"00:02:D1", "Vivotek"}, {"00:03:C5", "Mobotix"}, {"00:80:F0", "Panasonic"},
        {"00:1B:DE", "Bosch"}, {"00:0E:8F", "Bosch"}, {"00:1F:84", "Bosch"},
        {"00:50:C2", "IEEE Registration Authority"}, {"00:1D:7E", "Honeywell"},
        {"00:1C:10", "Pelco"}, {"00:03:7F", "Atheros"}, {"00:1A:70", "Arecont Vision"},
        {"00:40:8C", "Axis"}, {"00:0F:7C", "Axis"}, {"AC:CC:8E", "Axis"},
        
        // Chinese manufacturers
        {"34:CE:00", "TP-Link"}, {"50:C7:BF", "TP-Link"}, {"A0:F3:C1", "TP-Link"},
        {"00:1F:3F", "D-Link"}, {"14:CC:20", "D-Link"}, {"C8:D3:A3", "D-Link"},
        {"00:26:5A", "Netgear"}, {"A0:04:60", "Netgear"}, {"28:C6:8E", "Netgear"}
    };
    
    // Camera-specific HTTP paths and endpoints
    private static final String[] CAMERA_DETECTION_PATHS = {
        // ONVIF standard paths (most reliable for camera detection)
        "/onvif/device_service", "/onvif/Device", "/onvif/Media",
        
        // Common camera API endpoints
        "/cgi-bin/snapshot.cgi", "/cgi-bin/camera", "/cgi-bin/hi3510/snap.cgi",
        "/videostream.cgi", "/mjpg/video.mjpg", "/video.cgi",
        
        // Manufacturer-specific paths
        "/axis-cgi/jpg/image.cgi", "/axis-cgi/mjpg/video.cgi", // Axis
        "/ISAPI/System/deviceInfo", "/ISAPI/Streaming/channels", // Hikvision
        "/cgi-bin/configManager.cgi?action=getConfig&name=General", // Dahua
        "/cgi-bin/guest/Video.cgi?media=MJPEG", // Vivotek
        "/nphMotionJpeg?Resolution=640x480&Quality=Standard", // Mobotix
        
        // Generic camera paths
        "/snapshot.jpg", "/image.jpg", "/video", "/live", "/stream",
        "/cam.jpg", "/webcam.jpg", "/camera.jpg"
    };
    
    // Camera-specific HTTP headers to look for
    private static final String[] CAMERA_SERVER_SIGNATURES = {
        "hikvision", "dahua", "axis", "vivotek", "mobotix", "bosch", "pelco",
        "arecont", "panasonic", "sony", "samsung", "lg", "canon", "nikon",
        "ipcam", "webcam", "camera", "dvr", "nvr", "surveillance", "security"
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
        activeScanCount.set(7); // WiFi, Network, Bluetooth, UPnP, Broadcast, Public IP, Hotspot
        scanCompleteNotified = false;
        
        // Use thread pool to run scans in parallel for better efficiency
        executorService.execute(() -> {
            // 1. Scan for cameras in WiFi networks
            scanWifiCameras(listener);
            checkScanCompletion(listener);
        });
        
        executorService.execute(() -> {
            // 2. Scan for network cameras in local network
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
        
        executorService.execute(() -> {
            // 5. Scan for cameras via broadcast discovery
            scanBroadcastCameras(listener);
            checkScanCompletion(listener);
        });
        
        executorService.execute(() -> {
            // 6. Scan for cameras on public/external networks
            scanPublicNetworkCameras(listener);
            checkScanCompletion(listener);
        });
        
        executorService.execute(() -> {
            // 7. Scan for camera hotspots (cameras creating their own WiFi)
            scanCameraHotspots(listener);
            checkScanCompletion(listener);
        });
        
        // Set a timeout to ensure scan completion is reported even if some scans hang
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!scanCompleteNotified) {
                listener.onScanProgress("Scan timeout reached, finalizing results");
                listener.onScanComplete();
                scanCompleteNotified = true;
            }
        }, 90000); // 90 second timeout for extended scanning
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
     * Enhanced WiFi camera detection with reduced false positives
     */
    private boolean isCameraWifiNetwork(ScanResult result) {
        if (result.SSID == null || result.BSSID == null) {
            return false;
        }
        
        String ssid = result.SSID.toLowerCase().trim();
        String bssid = result.BSSID.toUpperCase();
        
        // Skip empty or very short SSIDs
        if (ssid.length() < 3) {
            return false;
        }
        
        // First check MAC address - most reliable method
        String manufacturer = getManufacturerFromMacDatabase(bssid);
        if (manufacturer != null && !manufacturer.equals("Unknown")) {
            // Known camera manufacturer MAC
            return true;
        }
        
        // Check for specific camera SSID patterns (high confidence)
        if (ssid.matches("ipc[0-9]{3,6}") ||           // IP camera with numbers
            ssid.matches("cam[0-9]{2,4}") ||           // Camera with numbers  
            ssid.matches("camera[_-]?[0-9]+") ||       // Camera with separator and numbers
            ssid.matches("dvr[_-]?[0-9]+") ||          // DVR with numbers
            ssid.matches("nvr[_-]?[0-9]+") ||          // NVR with numbers
            ssid.matches("hikvision[_-]?.*") ||        // Hikvision devices
            ssid.matches("dahua[_-]?.*") ||            // Dahua devices
            ssid.matches("axis[_-]?.*")) {             // Axis devices
            return true;
        }
        
        // Check for camera-specific keywords but with stricter rules
        int cameraKeywordCount = 0;
        String[] strictCameraKeywords = {
            "ipcam", "webcam", "security", "surveillance", "monitor", 
            "摄像头", "监控", "安防", "cctv"
        };
        
        for (String keyword : strictCameraKeywords) {
            if (ssid.contains(keyword)) {
                cameraKeywordCount++;
            }
        }
        
        // Require at least one strong camera keyword
        if (cameraKeywordCount > 0) {
            // Additional validation - check if it's not a common router/phone hotspot
            if (!isLikelyRouterOrHotspot(ssid)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if SSID is likely a router or phone hotspot (to avoid false positives)
     */
    private boolean isLikelyRouterOrHotspot(String ssid) {
        String[] routerPatterns = {
            "tp-link", "d-link", "netgear", "linksys", "asus", "belkin",
            "wifi", "internet", "home", "office", "guest", "public",
            "android", "iphone", "samsung", "xiaomi", "huawei", "oppo", "vivo",
            "hotspot", "mobile", "phone"
        };
        
        for (String pattern : routerPatterns) {
            if (ssid.contains(pattern)) {
                return true;
            }
        }
        
        // Check for common router default SSID patterns
        if (ssid.matches(".*[_-]?[0-9a-f]{4,6}$") ||  // Ends with hex digits
            ssid.matches(".*[_-]?guest$") ||           // Guest networks
            ssid.matches(".*[_-]?5g$") ||              // 5G networks
            ssid.matches(".*[_-]?2\\.4g$")) {          // 2.4G networks
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
            for (String path : CAMERA_DETECTION_PATHS) {
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
     * Enhanced manufacturer detection from MAC address using comprehensive database
     */
    private String getManufacturerFromMac(String macAddress) {
        return getManufacturerFromMacDatabase(macAddress);
    }
    
    private String getManufacturerFromMacDatabase(String macAddress) {
        if (macAddress == null || macAddress.length() < 8) {
            return "Unknown";
        }
        
        String prefix = macAddress.substring(0, 8).toUpperCase();
        
        // Search in the comprehensive MAC database
        for (String[] entry : CAMERA_MAC_DATABASE) {
            if (prefix.startsWith(entry[0])) {
                return entry[1];
            }
        }
        
        return "Unknown";
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
            // First scan primary camera ports (most likely to be cameras)
            CameraInfo detectedCamera = scanPortsForCamera(ipAddress, PRIMARY_CAMERA_PORTS, true);
            
            if (detectedCamera != null) {
                publishProgress(detectedCamera);
                return; // Found a camera, no need to scan secondary ports
            }
            
            // If no camera found on primary ports, try secondary ports
            detectedCamera = scanPortsForCamera(ipAddress, SECONDARY_CAMERA_PORTS, false);
            
            if (detectedCamera != null) {
                publishProgress(detectedCamera);
            }
        }
        
        private CameraInfo scanPortsForCamera(String ipAddress, int[] ports, boolean isPrimary) {
            for (int port : ports) {
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(ipAddress, port), isPrimary ? 2000 : 1000);
                    socket.close();
                    
                    // Port is open, now verify if it's actually a camera
                    CameraInfo cameraInfo = verifyAndCreateCameraInfo(ipAddress, port);
                    
                    if (cameraInfo != null) {
                        return cameraInfo; // Found a verified camera
                    }
                    
                } catch (IOException e) {
                    // Port not reachable, continue to next
                }
            }
            return null;
        }
        
        private CameraInfo verifyAndCreateCameraInfo(String ipAddress, int port) {
            // Try to verify this is actually a camera by checking HTTP responses
            boolean isCameraVerified = false;
            String detectedManufacturer = null;
            String deviceName = null;
            boolean requiresAuth = false;
            
            for (String path : CAMERA_DETECTION_PATHS) {
                try {
                    URL url = new URL("http://" + ipAddress + ":" + port + path);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(3000);
                    connection.setReadTimeout(3000);
                    connection.setRequestMethod("HEAD");
                    connection.setRequestProperty("User-Agent", "Camera-Detector/1.0");
                    
                    int responseCode = connection.getResponseCode();
                    
                    // Check for camera-specific responses
                    if (responseCode == HttpURLConnection.HTTP_OK || 
                        responseCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
                        responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                        
                        // Check server header for camera signatures
                        String server = connection.getHeaderField("Server");
                        String contentType = connection.getHeaderField("Content-Type");
                        String wwwAuth = connection.getHeaderField("WWW-Authenticate");
                        
                        if (server != null) {
                            server = server.toLowerCase();
                            for (String signature : CAMERA_SERVER_SIGNATURES) {
                                if (server.contains(signature)) {
                                    isCameraVerified = true;
                                    detectedManufacturer = extractManufacturerFromServer(server);
                                    break;
                                }
                            }
                        }
                        
                        // Check for camera-specific content types
                        if (contentType != null && (
                            contentType.contains("image/jpeg") ||
                            contentType.contains("multipart/x-mixed-replace") ||
                            contentType.contains("video/") ||
                            contentType.contains("application/soap+xml"))) {
                            isCameraVerified = true;
                        }
                        
                        // Check for ONVIF or camera-specific authentication
                        if (wwwAuth != null && (
                            wwwAuth.contains("ONVIF") ||
                            wwwAuth.contains("Camera") ||
                            wwwAuth.contains("DVR") ||
                            wwwAuth.contains("NVR"))) {
                            isCameraVerified = true;
                            requiresAuth = true;
                        }
                        
                        // Special handling for ONVIF endpoints
                        if (path.contains("onvif")) {
                            isCameraVerified = true;
                            deviceName = "ONVIF Camera";
                        }
                        
                        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                            requiresAuth = true;
                        }
                    }
                    
                    connection.disconnect();
                    
                    if (isCameraVerified) {
                        break; // Found camera evidence, no need to check more paths
                    }
                    
                } catch (Exception e) {
                    // Continue to next path
                }
            }
            
            // Only create camera info if we have strong evidence this is a camera
            if (isCameraVerified) {
                CameraInfo cameraInfo = new CameraInfo();
                cameraInfo.setId(ipAddress + ":" + port);
                cameraInfo.setIpAddress(ipAddress);
                cameraInfo.setPort(port);
                cameraInfo.setType(CameraInfo.CameraType.NETWORK);
                cameraInfo.setAccessible(true);
                cameraInfo.setHasPermission(!requiresAuth);
                
                // Set name based on detected info
                if (deviceName != null) {
                    cameraInfo.setName(deviceName + " (" + ipAddress + ")");
                } else if (detectedManufacturer != null) {
                    cameraInfo.setName(detectedManufacturer + " Camera (" + ipAddress + ")");
                } else {
                    cameraInfo.setName("IP Camera (" + ipAddress + ":" + port + ")");
                }
                
                if (detectedManufacturer != null) {
                    cameraInfo.setManufacturer(detectedManufacturer);
                }
                
                String description = "Verified camera device on port " + port;
                if (requiresAuth) {
                    description += " (requires authentication)";
                }
                cameraInfo.setDescription(description);
                
                return cameraInfo;
            }
            
            return null; // Not verified as a camera
        }
        
        private String extractManufacturerFromServer(String server) {
            server = server.toLowerCase();
            if (server.contains("hikvision")) return "Hikvision";
            if (server.contains("dahua")) return "Dahua";
            if (server.contains("axis")) return "Axis";
            if (server.contains("vivotek")) return "Vivotek";
            if (server.contains("mobotix")) return "Mobotix";
            if (server.contains("bosch")) return "Bosch";
            if (server.contains("pelco")) return "Pelco";
            if (server.contains("panasonic")) return "Panasonic";
            if (server.contains("sony")) return "Sony";
            if (server.contains("samsung")) return "Samsung";
            return "Unknown";
        }
        
    }
    
    public void destroy() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
