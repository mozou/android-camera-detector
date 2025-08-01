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
        String[] permissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        };
        
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                permissionsToRequest.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
        } else {
            // 权限已获取，可以开始扫描
            scanForCameras();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (allPermissionsGranted) {
                Toast.makeText(this, "权限已获取，开始扫描摄像头", Toast.LENGTH_SHORT).show();
                scanForCameras();
            } else {
                Toast.makeText(this, "需要权限才能检测摄像头设备", Toast.LENGTH_LONG).show();
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