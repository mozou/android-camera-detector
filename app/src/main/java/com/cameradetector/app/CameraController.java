package com.cameradetector.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.provider.Settings;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;

public class CameraController {
    
    private Context context;
    private CameraManager cameraManager;
    private BluetoothAdapter bluetoothAdapter;
    
    public CameraController(Context context) {
        this.context = context;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }
    
    /**
     * Test camera access permissions
     */
    public boolean testCameraAccess(CameraInfo cameraInfo) {
        boolean hasAccess = false;
        
        switch (cameraInfo.getType()) {
            case LOCAL:
                hasAccess = testLocalCameraAccess(cameraInfo);
                break;
            case NETWORK:
                hasAccess = testNetworkCameraAccess(cameraInfo);
                break;
            case BLUETOOTH:
                hasAccess = testBluetoothCameraAccess(cameraInfo);
                break;
        }
        
        // Update the camera info with the access result
        cameraInfo.setAccessible(hasAccess);
        
        // If we have access, try to get permission
        if (hasAccess) {
            boolean hasPermission = requestCameraPermission(cameraInfo);
            cameraInfo.setHasPermission(hasPermission);
        }
        
        return hasAccess;
    }
    
    /**
     * Request permission for a camera
     */
    public boolean requestCameraPermission(CameraInfo cameraInfo) {
        switch (cameraInfo.getType()) {
            case LOCAL:
                return requestLocalCameraPermission();
            case NETWORK:
                return requestNetworkCameraPermission(cameraInfo);
            case BLUETOOTH:
                return requestBluetoothCameraPermission(cameraInfo);
            default:
                return false;
        }
    }
    
    private boolean testLocalCameraAccess(CameraInfo cameraInfo) {
        try {
            // Try to get camera characteristics to test access
            cameraManager.getCameraCharacteristics(cameraInfo.getId());
            
            // Check if we have camera permission
            boolean hasCameraPermission = ContextCompat.checkSelfPermission(context, 
                android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                
            // Update permission status
            cameraInfo.setHasPermission(hasCameraPermission);
            
            return true;
        } catch (CameraAccessException e) {
            Log.e("CameraController", "Camera access exception: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e("CameraController", "Error testing local camera: " + e.getMessage());
            return false;
        }
    }
    
    private boolean requestLocalCameraPermission() {
        // For local cameras, we need to check Android permission
        boolean hasCameraPermission = ContextCompat.checkSelfPermission(context, 
            android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            
        if (!hasCameraPermission) {
            // We can't request permission directly from here, need to guide user
            openAppPermissionSettings();
        }
        
        return hasCameraPermission;
    }
    
    private boolean testNetworkCameraAccess(CameraInfo cameraInfo) {
        if (cameraInfo.getIpAddress() == null || cameraInfo.getIpAddress().equals("Unknown")) {
            return false;
        }
        
        try {
            String urlString = "http://" + cameraInfo.getIpAddress() + ":" + cameraInfo.getPort();
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            
            // Check for authentication headers
            String authHeader = connection.getHeaderField("WWW-Authenticate");
            boolean requiresAuth = (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) || 
                                  (authHeader != null && !authHeader.isEmpty());
            
            connection.disconnect();
            
            // If we get a 200 OK, we have both access and permission
            if (responseCode == HttpURLConnection.HTTP_OK) {
                cameraInfo.setHasPermission(true);
                return true;
            }
            
            // If we get a 401 Unauthorized, we have access but need credentials
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                cameraInfo.setHasPermission(false);
                return true;
            }
            
            // Other response codes
            return false;
            
        } catch (IOException e) {
            Log.e("CameraController", "Network camera access error: " + e.getMessage());
            return false;
        }
    }
    
    private boolean requestNetworkCameraPermission(CameraInfo cameraInfo) {
        // For network cameras, we need to try authentication
        if (cameraInfo.getIpAddress() == null || cameraInfo.getIpAddress().equals("Unknown")) {
            return false;
        }
        
        // This would typically show a dialog to enter username/password
        // For now, we'll just check if we already have access
        try {
            String urlString = "http://" + cameraInfo.getIpAddress() + ":" + cameraInfo.getPort();
            URL url = new URL(urlString);
            
            // Try with default credentials (many cameras use admin/admin)
            String userpass = "admin:admin";
            String basicAuth = "Basic " + android.util.Base64.encodeToString(
                userpass.getBytes(), android.util.Base64.NO_WRAP);
                
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", basicAuth);
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            return responseCode == HttpURLConnection.HTTP_OK;
            
        } catch (Exception e) {
            Log.e("CameraController", "Network camera permission error: " + e.getMessage());
            return false;
        }
    }
    
    private boolean testBluetoothCameraAccess(CameraInfo cameraInfo) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return false;
        }
        
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(cameraInfo.getId());
            boolean isBonded = device != null && device.getBondState() == BluetoothDevice.BOND_BONDED;
            
            // Check if we have the necessary Bluetooth permissions
            boolean hasBluetoothPermission = false;
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Android 12+ specific Bluetooth permissions
                hasBluetoothPermission = ContextCompat.checkSelfPermission(context, 
                    android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            } else {
                // Older Android versions
                hasBluetoothPermission = ContextCompat.checkSelfPermission(context, 
                    android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
            }
            
            // Update permission status
            cameraInfo.setHasPermission(isBonded && hasBluetoothPermission);
            
            return isBonded;
        } catch (Exception e) {
            Log.e("CameraController", "Bluetooth camera access error: " + e.getMessage());
            return false;
        }
    }
    
    private boolean requestBluetoothCameraPermission(CameraInfo cameraInfo) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            // Try to enable Bluetooth
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(enableBtIntent);
            } catch (Exception e) {
                Log.e("CameraController", "Failed to request Bluetooth enable: " + e.getMessage());
                return false;
            }
            return false;
        }
        
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(cameraInfo.getId());
            
            // If device is not bonded, we need to initiate pairing
            if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                // This would typically show the pairing dialog
                // For now, we'll just try to create a bond
                try {
                    java.lang.reflect.Method method = device.getClass().getMethod("createBond");
                    method.invoke(device);
                } catch (Exception e) {
                    Log.e("CameraController", "Failed to initiate Bluetooth pairing: " + e.getMessage());
                    return false;
                }
                return false;
            }
            
            // Check if we have the necessary Bluetooth permissions
            boolean hasBluetoothPermission = false;
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Android 12+ specific Bluetooth permissions
                hasBluetoothPermission = ContextCompat.checkSelfPermission(context, 
                    android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            } else {
                // Older Android versions
                hasBluetoothPermission = ContextCompat.checkSelfPermission(context, 
                    android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
            }
            
            if (!hasBluetoothPermission) {
                // We can't request permission directly from here, need to guide user
                openAppPermissionSettings();
                return false;
            }
            
            return true;
        } catch (Exception e) {
            Log.e("CameraController", "Bluetooth camera permission error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 阻止网络摄像头访问
     */
    public boolean blockNetworkCamera(CameraInfo cameraInfo) {
        try {
            // 尝试发送关闭命令到网络摄像头
            // 这里只是示例，实际需要根据具体摄像头的API来实现
            String urlString = "http://" + cameraInfo.getIpAddress() + ":" + cameraInfo.getPort() + "/api/stop";
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            // 如果无法通过API控制，可以尝试其他方法
            // 比如添加到防火墙规则（需要root权限）
            return blockNetworkTraffic(cameraInfo.getIpAddress());
        }
    }
    
    /**
     * 恢复网络摄像头访问
     */
    public boolean unblockNetworkCamera(CameraInfo cameraInfo) {
        try {
            // 尝试发送启动命令到网络摄像头
            String urlString = "http://" + cameraInfo.getIpAddress() + ":" + cameraInfo.getPort() + "/api/start";
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return unblockNetworkTraffic(cameraInfo.getIpAddress());
        }
    }
    
    /**
     * 阻止蓝牙摄像头连接
     */
    public boolean blockBluetoothCamera(CameraInfo cameraInfo) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return false;
        }
        
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(cameraInfo.getId());
            if (device != null && device.getBondState() == BluetoothDevice.BOND_BONDED) {
                // 尝试断开连接（需要反射调用隐藏API）
                return disconnectBluetoothDevice(device);
            }
        } catch (Exception e) {
            return false;
        }
        
        return false;
    }
    
    /**
     * 恢复蓝牙摄像头连接
     */
    public boolean unblockBluetoothCamera(CameraInfo cameraInfo) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return false;
        }
        
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(cameraInfo.getId());
            if (device != null) {
                // 尝试重新连接
                return connectBluetoothDevice(device);
            }
        } catch (Exception e) {
            return false;
        }
        
        return false;
    }
    
    private boolean blockNetworkTraffic(String ipAddress) {
        // 注意：阻止网络流量通常需要root权限
        // 这里只是示例代码，实际实现需要考虑权限问题
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                "su", "-c", "iptables -A OUTPUT -d " + ipAddress + " -j DROP"
            });
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean unblockNetworkTraffic(String ipAddress) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                "su", "-c", "iptables -D OUTPUT -d " + ipAddress + " -j DROP"
            });
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean disconnectBluetoothDevice(BluetoothDevice device) {
        try {
            // 使用反射调用隐藏的disconnect方法
            java.lang.reflect.Method method = device.getClass().getMethod("removeBond");
            return (Boolean) method.invoke(device);
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean connectBluetoothDevice(BluetoothDevice device) {
        try {
            // 使用反射调用隐藏的connect方法
            java.lang.reflect.Method method = device.getClass().getMethod("createBond");
            return (Boolean) method.invoke(device);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 打开应用权限设置页面
     */
    public void openAppPermissionSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}