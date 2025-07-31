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
     * 测试摄像头访问权限
     */
    public boolean testCameraAccess(CameraInfo cameraInfo) {
        switch (cameraInfo.getType()) {
            case LOCAL:
                return testLocalCameraAccess(cameraInfo);
            case NETWORK:
                return testNetworkCameraAccess(cameraInfo);
            case BLUETOOTH:
                return testBluetoothCameraAccess(cameraInfo);
            default:
                return false;
        }
    }
    
    private boolean testLocalCameraAccess(CameraInfo cameraInfo) {
        try {
            // 尝试获取摄像头特性来测试访问权限
            cameraManager.getCameraCharacteristics(cameraInfo.getId());
            return true;
        } catch (CameraAccessException e) {
            return false;
        }
    }
    
    private boolean testNetworkCameraAccess(CameraInfo cameraInfo) {
        try {
            String urlString = "http://" + cameraInfo.getIpAddress() + ":" + cameraInfo.getPort();
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            return responseCode == HttpURLConnection.HTTP_OK || 
                   responseCode == HttpURLConnection.HTTP_UNAUTHORIZED; // 401也表示服务存在
        } catch (IOException e) {
            return false;
        }
    }
    
    private boolean testBluetoothCameraAccess(CameraInfo cameraInfo) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return false;
        }
        
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(cameraInfo.getId());
        return device != null && device.getBondState() == BluetoothDevice.BOND_BONDED;
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