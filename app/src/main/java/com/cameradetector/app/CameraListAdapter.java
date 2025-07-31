package com.cameradetector.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class CameraListAdapter extends BaseAdapter {
    
    private Context context;
    private List<CameraInfo> cameraList;
    private LayoutInflater inflater;
    
    public CameraListAdapter(Context context, List<CameraInfo> cameraList) {
        this.context = context;
        this.cameraList = cameraList;
        this.inflater = LayoutInflater.from(context);
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
        
        return convertView;
    }
    
    private static class ViewHolder {
        ImageView ivCameraIcon;
        TextView tvCameraName;
        TextView tvCameraType;
        TextView tvCameraStatus;
        TextView tvCameraId;
    }
}