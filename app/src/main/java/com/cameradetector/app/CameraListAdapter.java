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
            holder.tvCameraDetails = convertView.findViewById(R.id.tv_camera_details);
            holder.btnExploit = convertView.findViewById(R.id.btn_exploit);
            holder.btnViewStream = convertView.findViewById(R.id.btn_view_stream);
            holder.btnControl = convertView.findViewById(R.id.btn_control);
            holder.layoutCameraDetails = convertView.findViewById(R.id.layout_camera_details);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        // 设置点击事件，展开/折叠详细信息
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder.layoutCameraDetails.getVisibility() == View.VISIBLE) {
                    holder.layoutCameraDetails.setVisibility(View.GONE);
                } else {
                    holder.layoutCameraDetails.setVisibility(View.VISIBLE);
                }
            }
        });
        
        CameraInfo camera = cameraList.get(position);
        
        // 设置摄像头图标
        switch (camera.getType()) {
            case NETWORK:
                // 根据制造商设置不同的图标
                if (camera.getManufacturer() != null) {
                    String manufacturer = camera.getManufacturer().toLowerCase();
                    if (manufacturer.contains("hikvision")) {
                        holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_hikvision);
                    } else if (manufacturer.contains("dahua")) {
                        holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_dahua);
                    } else if (manufacturer.contains("axis")) {
                        holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_axis);
                    } else {
                        holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_network);
                    }
                } else {
                    holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_network);
                }
                break;
            case BLUETOOTH:
                holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_bluetooth);
                break;
            default:
                holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_unknown);
                break;
        }
        
        // 设置摄像头基本信息
        holder.tvCameraName.setText(camera.getName());
        holder.tvCameraType.setText(camera.getTypeString());
        holder.tvCameraStatus.setText(camera.getStatusString());
        
        // 设置摄像头ID或IP地址
        if (camera.getType() == CameraInfo.CameraType.NETWORK && camera.getIpAddress() != null) {
            if (camera.getPort() > 0) {
                holder.tvCameraId.setText("IP: " + camera.getIpAddress() + ":" + camera.getPort());
            } else {
                holder.tvCameraId.setText("IP: " + camera.getIpAddress());
            }
        } else {
            holder.tvCameraId.setText("ID: " + camera.getId());
        }
        
        // 设置详细信息
        StringBuilder details = new StringBuilder();
        
        // 添加制造商信息
        if (camera.getManufacturer() != null && !camera.getManufacturer().isEmpty()) {
            details.append("制造商: ").append(camera.getManufacturer()).append("\n");
        }
        
        // 添加型号信息
        if (camera.getModel() != null && !camera.getModel().isEmpty()) {
            details.append("型号: ").append(camera.getModel()).append("\n");
        }
        
        // 添加描述信息
        if (camera.getDescription() != null && !camera.getDescription().isEmpty()) {
            details.append("\n").append(camera.getDescription());
        }
        
        holder.tvCameraDetails.setText(details.toString());
        
        // 根据状态设置标签背景颜色
        if (camera.isAccessible() && camera.hasPermission()) {
            holder.tvCameraStatus.setBackgroundResource(R.drawable.bg_tag_status);
        } else if (camera.isAccessible()) {
            holder.tvCameraStatus.setBackgroundResource(R.drawable.bg_button_secondary);
        } else {
            holder.tvCameraStatus.setBackgroundResource(R.drawable.bg_button_danger);
        }
        
        // 设置按钮可见性和点击事件
        if (camera.getType() == CameraInfo.CameraType.NETWORK) {
            // 漏洞利用按钮
            holder.btnExploit.setVisibility(View.VISIBLE);
            holder.btnExploit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    exploitCamera(camera);
                }
            });
            
            // 查看视频流按钮
            holder.btnViewStream.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    viewCameraStream(camera);
                }
            });
            
            // 控制摄像头按钮
            holder.btnControl.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    controlCamera(camera);
                }
            });
            
            // 根据摄像头权限状态设置按钮状态
            holder.btnViewStream.setEnabled(camera.isAccessible());
            holder.btnControl.setEnabled(camera.isAccessible() && camera.hasPermission());
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
    
    /**
     * 查看摄像头视频流
     */
    private void viewCameraStream(CameraInfo camera) {
        if (camera.getType() != CameraInfo.CameraType.NETWORK || !camera.isAccessible()) {
            Toast.makeText(context, "无法访问该摄像头的视频流", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 构建可能的视频流URL
        String streamUrl = null;
        
        // 尝试RTSP流
        if (camera.getPort() == 554) {
            streamUrl = "rtsp://" + camera.getIpAddress() + ":554/live/main";
        } 
        // 尝试HTTP流
        else {
            streamUrl = "http://" + camera.getIpAddress() + ":" + camera.getPort() + "/video";
        }
        
        // 启动视频流查看活动
        Intent intent = new Intent(context, CameraControlActivity.class);
        intent.putExtra("camera_info", camera);
        intent.putExtra("stream_url", streamUrl);
        context.startActivity(intent);
    }
    
    /**
     * 控制摄像头
     */
    private void controlCamera(CameraInfo camera) {
        if (!camera.isAccessible() || !camera.hasPermission()) {
            Toast.makeText(context, "无权限控制该摄像头", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 启动摄像头控制活动
        Intent intent = new Intent(context, CameraControlActivity.class);
        intent.putExtra("camera_info", camera);
        intent.putExtra("control_mode", true);
        context.startActivity(intent);
    }
    
    private static class ViewHolder {
        ImageView ivCameraIcon;
        TextView tvCameraName;
        TextView tvCameraType;
        TextView tvCameraStatus;
        TextView tvCameraId;
        TextView tvCameraDetails;
        Button btnExploit;
        Button btnViewStream;
        Button btnControl;
        LinearLayout layoutCameraDetails;
    }
}