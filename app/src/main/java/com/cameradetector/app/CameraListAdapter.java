package com.cameradetector.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class CameraListAdapter extends BaseAdapter {
    
    private Context context;
    private List<CameraInfo> cameraList;
    private LayoutInflater inflater;
    private CameraExploiter cameraExploiter;
    
    public CameraListAdapter(Context context, List<CameraInfo> cameraList) {
        this.context = context;
        this.cameraList = cameraList;
        this.inflater = LayoutInflater.from(context);
        this.cameraExploiter = new CameraExploiter();
    }
    
    @Override
    public int getCount() {
        return cameraList.size();
    }
    
    @Override
    public Object getItem(int position) {
        return cameraList.get(position);
    }
    
    @Override
    public long getItemId(int position) {
        return position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_camera, parent, false);
            holder = new ViewHolder();
            holder.ivCameraIcon = convertView.findViewById(R.id.iv_camera_icon);
            holder.tvCameraName = convertView.findViewById(R.id.tv_camera_name);
            holder.tvCameraType = convertView.findViewById(R.id.tv_camera_type);
            holder.tvCameraStatus = convertView.findViewById(R.id.tv_camera_status);
            holder.tvCameraId = convertView.findViewById(R.id.tv_camera_id);
            holder.btnExploit = convertView.findViewById(R.id.btn_exploit);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        CameraInfo camera = cameraList.get(position);
        
        // 设置摄像头图标
        switch (camera.getType()) {
            case LOCAL:
                holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_local);
                break;
            case NETWORK:
                holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_network);
                break;
            case BLUETOOTH:
                holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_bluetooth);
                break;
        }
        
        // 设置摄像头信息
        holder.tvCameraName.setText(camera.getName());
        holder.tvCameraType.setText(camera.getTypeString());
        holder.tvCameraStatus.setText(camera.getStatusString());
        holder.tvCameraId.setText("ID: " + camera.getId());
        
        // 根据状态设置文字颜色
        if (camera.isAccessible() && camera.hasPermission()) {
            holder.tvCameraStatus.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
        } else if (camera.isAccessible()) {
            holder.tvCameraStatus.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
        } else {
            holder.tvCameraStatus.setTextColor(context.getResources().getColor(android.R.color.holo_red_dark));
        }
        
        // 设置漏洞利用按钮
        if (camera.getType() == CameraInfo.CameraType.NETWORK) {
            holder.btnExploit.setVisibility(View.VISIBLE);
            holder.btnExploit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    exploitCamera(camera);
                }
            });
        } else {
            holder.btnExploit.setVisibility(View.GONE);
        }
        
        return convertView;
    }
    
    /**
     * 执行摄像头漏洞利用
     */
    private void exploitCamera(CameraInfo camera) {
        Toast.makeText(context, "开始尝试获取 " + camera.getName() + " 的权限...", Toast.LENGTH_SHORT).show();
        
        cameraExploiter.exploitCamera(camera, new CameraExploiter.ExploitCallback() {
            @Override
            public void onExploitAttempt(String method, String status) {
                ((MainActivity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, method + ": " + status, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onExploitSuccess(String method, String result) {
                ((MainActivity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "✅ " + method + " 成功!\n" + result, Toast.LENGTH_LONG).show();
                        // 更新摄像头权限状态
                        camera.setHasPermission(true);
                        notifyDataSetChanged();
                    }
                });
            }
            
            @Override
            public void onExploitComplete(List<CameraExploiter.ExploitResult> results) {
                ((MainActivity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StringBuilder summary = new StringBuilder();
                        int successCount = 0;
                        
                        for (CameraExploiter.ExploitResult result : results) {
                            if (result.success) {
                                successCount++;
                                summary.append("✅ ").append(result.method).append("\n");
                            }
                        }
                        
                        if (successCount > 0) {
                            summary.insert(0, "漏洞利用完成! 成功: " + successCount + "/" + results.size() + "\n\n");
                            Toast.makeText(context, summary.toString(), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, "未发现可利用的漏洞，该摄像头安全性较好", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }
    
    private static class ViewHolder {
        ImageView ivCameraIcon;
        TextView tvCameraName;
        TextView tvCameraType;
        TextView tvCameraStatus;
        TextView tvCameraId;
        Button btnExploit;
    }
}