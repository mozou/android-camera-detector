package com.cameradetector.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private TextView tvCameraCount;
    private ListView lvCameraList;
    private Button btnScanCameras;
    private Button btnControlCameras;
    
    private CameraManager cameraManager;
    private CameraDetector cameraDetector;
    private CameraExploiter cameraExploiter;
    private CameraListAdapter cameraAdapter;
    private List<CameraInfo> detectedCameras;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        initCameraManager();
        requestPermissions();
    }
    
    private void initViews() {
        tvCameraCount = findViewById(R.id.tv_camera_count);
        lvCameraList = findViewById(R.id.lv_camera_list);
        btnScanCameras = findViewById(R.id.btn_scan_cameras);
        btnControlCameras = findViewById(R.id.btn_control_cameras);
        
        detectedCameras = new ArrayList<>();
        cameraAdapter = new CameraListAdapter(this, detectedCameras);
        lvCameraList.setAdapter(cameraAdapter);
        
        btnScanCameras.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanForCameras();
            }
        });
        
        btnControlCameras.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCameraControl();
            }
        });
    }
    
    private void initCameraManager() {
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        cameraDetector = new CameraDetector(this);
        cameraExploiter = new CameraExploiter();
    }
    
    private void requestPermissions() {
        // Create separate permission groups for better user experience
        List<String> basicPermissions = new ArrayList<>();
        List<String> locationPermissions = new ArrayList<>();
        List<String> bluetoothPermissions = new ArrayList<>();
        
        // Basic permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            basicPermissions.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            basicPermissions.add(Manifest.permission.INTERNET);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            basicPermissions.add(Manifest.permission.ACCESS_NETWORK_STATE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            basicPermissions.add(Manifest.permission.ACCESS_WIFI_STATE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            basicPermissions.add(Manifest.permission.CHANGE_WIFI_STATE);
        }
        
        // Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        
        // Bluetooth permissions with Android version check
        if (android.os.Build.VERSION.SDK_INT >= 31) { // VERSION_CODES.S = 31
            // Android 12+ specific Bluetooth permissions
            if (ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED) {
                bluetoothPermissions.add("android.permission.BLUETOOTH_SCAN");
            }
            if (ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) {
                bluetoothPermissions.add("android.permission.BLUETOOTH_CONNECT");
            }
        } else {
            // Older Android versions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                bluetoothPermissions.add(Manifest.permission.BLUETOOTH);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                bluetoothPermissions.add(Manifest.permission.BLUETOOTH_ADMIN);
            }
        }
        
        // Request permissions in sequence for better user experience
        if (!basicPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                basicPermissions.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
        } else if (!locationPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                locationPermissions.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE + 1);
        } else if (!bluetoothPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                bluetoothPermissions.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE + 2);
        } else {
            // All permissions granted, start scanning
            scanForCameras();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode >= PERMISSION_REQUEST_CODE && requestCode <= PERMISSION_REQUEST_CODE + 2) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (allPermissionsGranted) {
                // Continue with permission sequence
                if (requestCode == PERMISSION_REQUEST_CODE) {
                    // Basic permissions granted, request location
                    List<String> locationPermissions = new ArrayList<>();
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        locationPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
                    }
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        locationPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
                    }
                    
                    if (!locationPermissions.isEmpty()) {
                        ActivityCompat.requestPermissions(this, 
                            locationPermissions.toArray(new String[0]), 
                            PERMISSION_REQUEST_CODE + 1);
                        return;
                    }
                }
                
                if (requestCode == PERMISSION_REQUEST_CODE || requestCode == PERMISSION_REQUEST_CODE + 1) {
                    // Location permissions granted, request bluetooth
                    List<String> bluetoothPermissions = new ArrayList<>();
                    
                    if (android.os.Build.VERSION.SDK_INT >= 31) { // VERSION_CODES.S = 31
                        if (ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED) {
                            bluetoothPermissions.add("android.permission.BLUETOOTH_SCAN");
                        }
                        if (ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) {
                            bluetoothPermissions.add("android.permission.BLUETOOTH_CONNECT");
                        }
                    } else {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                            bluetoothPermissions.add(Manifest.permission.BLUETOOTH);
                        }
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                            bluetoothPermissions.add(Manifest.permission.BLUETOOTH_ADMIN);
                        }
                    }
                    
                    if (!bluetoothPermissions.isEmpty()) {
                        ActivityCompat.requestPermissions(this, 
                            bluetoothPermissions.toArray(new String[0]), 
                            PERMISSION_REQUEST_CODE + 2);
                        return;
                    }
                }
                
                // All permission sequences completed
                Toast.makeText(this, "权限已获取，开始扫描摄像头", Toast.LENGTH_SHORT).show();
                scanForCameras();
            } else {
                // Some permissions were denied
                if (requestCode == PERMISSION_REQUEST_CODE) {
                    // Basic permissions denied - critical, show explanation
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("需要基本权限")
                        .setMessage("摄像头和网络权限是应用程序正常工作所必需的。请授予这些权限以继续。")
                        .setPositiveButton("重试", (dialog, which) -> requestPermissions())
                        .setNegativeButton("取消", (dialog, which) -> Toast.makeText(this, "应用功能将受限", Toast.LENGTH_LONG).show())
                        .show();
                } else {
                    // Non-critical permissions, continue with limited functionality
                    Toast.makeText(this, "部分功能可能受限，但将尝试扫描可用设备", Toast.LENGTH_LONG).show();
                    scanForCameras();
                }
            }
        }
    }
    
    private void scanForCameras() {
        detectedCameras.clear();
        
        // 检测本地摄像头
        scanLocalCameras();
        
        // 开始全面扫描
        cameraDetector.startComprehensiveScan(new CameraDetector.OnCameraDetectedListener() {
            @Override
            public void onCameraDetected(CameraInfo cameraInfo) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        detectedCameras.add(cameraInfo);
                        updateCameraList();
                    }
                });
            }
            
            @Override
            public void onScanComplete() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "扫描完成", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onScanProgress(String status) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, status, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    private void scanLocalCameras() {
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            
            for (String cameraId : cameraIds) {
                CameraCharacteristics characteristics = 
                    cameraManager.getCameraCharacteristics(cameraId);
                
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                String cameraType = "未知";
                
                if (facing != null) {
                    switch (facing) {
                        case CameraCharacteristics.LENS_FACING_FRONT:
                            cameraType = "前置摄像头";
                            break;
                        case CameraCharacteristics.LENS_FACING_BACK:
                            cameraType = "后置摄像头";
                            break;
                        case CameraCharacteristics.LENS_FACING_EXTERNAL:
                            cameraType = "外部摄像头";
                            break;
                    }
                }
                
                CameraInfo cameraInfo = new CameraInfo();
                cameraInfo.setId(cameraId);
                cameraInfo.setName("本地" + cameraType);
                cameraInfo.setType(CameraInfo.CameraType.LOCAL);
                cameraInfo.setAccessible(true);
                
                detectedCameras.add(cameraInfo);
            }
            
            updateCameraList();
            
        } catch (CameraAccessException e) {
            Toast.makeText(this, "无法访问摄像头: " + e.getMessage(), 
                         Toast.LENGTH_LONG).show();
        }
    }
    
    private void updateCameraList() {
        tvCameraCount.setText("检测到 " + detectedCameras.size() + " 个摄像头设备");
        cameraAdapter.notifyDataSetChanged();
        
        // 启用控制按钮
        btnControlCameras.setEnabled(detectedCameras.size() > 0);
    }
    
    private void openCameraControl() {
        Intent intent = new Intent(this, CameraControlActivity.class);
        intent.putParcelableArrayListExtra("cameras", 
            (ArrayList<CameraInfo>) detectedCameras);
        startActivity(intent);
    }
}