package com.cameradetector.app;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class CameraControlActivity extends AppCompatActivity {
    
    private TextView tvSelectedCamera;
    private ListView lvCameras;
    private Button btnBlockCamera;
    private Button btnUnblockCamera;
    private Button btnTestAccess;
    
    private List<CameraInfo> cameraList;
    private CameraListAdapter adapter;
    private CameraInfo selectedCamera;
    private CameraManager cameraManager;
    private CameraController cameraController;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_control);
        
        initViews();
        initData();
        setupListeners();
    }
    
    private void initViews() {
        tvSelectedCamera = findViewById(R.id.tv_selected_camera);
        lvCameras = findViewById(R.id.lv_cameras);
        btnBlockCamera = findViewById(R.id.btn_block_camera);
        btnUnblockCamera = findViewById(R.id.btn_unblock_camera);
        btnTestAccess = findViewById(R.id.btn_test_access);
        
        // 初始状态下禁用控制按钮
        btnBlockCamera.setEnabled(false);
        btnUnblockCamera.setEnabled(false);
        btnTestAccess.setEnabled(false);
    }
    
    private void initData() {
        cameraList = getIntent().getParcelableArrayListExtra("cameras");
        if (cameraList == null) {
            cameraList = new ArrayList<>();
        }
        
        adapter = new CameraListAdapter(this, cameraList);
        lvCameras.setAdapter(adapter);
        
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        cameraController = new CameraController(this);
    }
    
    private void setupListeners() {
        lvCameras.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedCamera = cameraList.get(position);
                updateSelectedCameraInfo();
                enableControlButtons();
            }
        });
        
        btnBlockCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                blockSelectedCamera();
            }
        });
        
        btnUnblockCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unblockSelectedCamera();
            }
        });
        
        btnTestAccess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testCameraAccess();
            }
        });
    }
    
    private void updateSelectedCameraInfo() {
        if (selectedCamera != null) {
            String info = "已选择: " + selectedCamera.getName() + "\n" +
                         "类型: " + selectedCamera.getTypeString() + "\n" +
                         "状态: " + selectedCamera.getStatusString();
            tvSelectedCamera.setText(info);
        }
    }
    
    private void enableControlButtons() {
        if (selectedCamera != null) {
            btnBlockCamera.setEnabled(true);
            btnUnblockCamera.setEnabled(true);
            btnTestAccess.setEnabled(true);
        }
    }
    
    private void blockSelectedCamera() {
        if (selectedCamera == null) return;
        
        new AlertDialog.Builder(this)
            .setTitle("阻止摄像头访问")
            .setMessage("确定要阻止对 " + selectedCamera.getName() + " 的访问吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                performCameraBlock();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void unblockSelectedCamera() {
        if (selectedCamera == null) return;
        
        new AlertDialog.Builder(this)
            .setTitle("恢复摄像头访问")
            .setMessage("确定要恢复对 " + selectedCamera.getName() + " 的访问吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                performCameraUnblock();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void performCameraBlock() {
        switch (selectedCamera.getType()) {
            case LOCAL:
                blockLocalCamera();
                break;
            case NETWORK:
                blockNetworkCamera();
                break;
            case BLUETOOTH:
                blockBluetoothCamera();
                break;
        }
    }
    
    private void performCameraUnblock() {
        switch (selectedCamera.getType()) {
            case LOCAL:
                unblockLocalCamera();
                break;
            case NETWORK:
                unblockNetworkCamera();
                break;
            case BLUETOOTH:
                unblockBluetoothCamera();
                break;
        }
    }
    
    private void blockLocalCamera() {
        try {
            // 注意：Android系统不允许应用直接禁用其他应用的摄像头权限
            // 这里只能演示如何检测和提示用户手动操作
            Toast.makeText(this, "本地摄像头需要通过系统设置手动禁用权限", Toast.LENGTH_LONG).show();
            
            // 可以引导用户到应用权限设置页面
            cameraController.openAppPermissionSettings();
            
        } catch (Exception e) {
            Toast.makeText(this, "操作失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void unblockLocalCamera() {
        Toast.makeText(this, "本地摄像头需要通过系统设置手动启用权限", Toast.LENGTH_LONG).show();
        cameraController.openAppPermissionSettings();
    }
    
    private void blockNetworkCamera() {
        // 对于网络摄像头，可以尝试发送控制命令或阻止网络连接
        boolean success = cameraController.blockNetworkCamera(selectedCamera);
        if (success) {
            Toast.makeText(this, "网络摄像头访问已阻止", Toast.LENGTH_SHORT).show();
            selectedCamera.setAccessible(false);
            adapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "无法阻止网络摄像头访问", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void unblockNetworkCamera() {
        boolean success = cameraController.unblockNetworkCamera(selectedCamera);
        if (success) {
            Toast.makeText(this, "网络摄像头访问已恢复", Toast.LENGTH_SHORT).show();
            selectedCamera.setAccessible(true);
            adapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "无法恢复网络摄像头访问", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void blockBluetoothCamera() {
        boolean success = cameraController.blockBluetoothCamera(selectedCamera);
        if (success) {
            Toast.makeText(this, "蓝牙摄像头连接已断开", Toast.LENGTH_SHORT).show();
            selectedCamera.setAccessible(false);
            adapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "无法断开蓝牙摄像头连接", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void unblockBluetoothCamera() {
        boolean success = cameraController.unblockBluetoothCamera(selectedCamera);
        if (success) {
            Toast.makeText(this, "蓝牙摄像头连接已恢复", Toast.LENGTH_SHORT).show();
            selectedCamera.setAccessible(true);
            adapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "无法恢复蓝牙摄像头连接", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void testCameraAccess() {
        if (selectedCamera == null) return;
        
        boolean hasAccess = cameraController.testCameraAccess(selectedCamera);
        
        String message = hasAccess ? 
            "摄像头 " + selectedCamera.getName() + " 可以正常访问" :
            "摄像头 " + selectedCamera.getName() + " 无法访问";
            
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        
        // 更新摄像头状态
        selectedCamera.setAccessible(hasAccess);
        adapter.notifyDataSetChanged();
        updateSelectedCameraInfo();
    }
}