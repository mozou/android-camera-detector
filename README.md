# Android摄像头检测器

一个专业的Android应用，用于自动检测周围的摄像头设备并提供基础控制功能。

## 功能特点

- 🔍 **本地摄像头检测**: 自动识别前置、后置、外部摄像头
- 🌐 **网络摄像头扫描**: 发现局域网中的IP摄像头设备
- 📱 **蓝牙摄像头检测**: 扫描和连接蓝牙摄像头设备
- 🔐 **权限管理**: 智能的权限请求和状态监控
- 🎨 **现代化界面**: Material Design风格的用户界面

## 技术规格

- **最低Android版本**: Android 5.0 (API 21)
- **目标Android版本**: Android 10 (API 29)
- **开发语言**: Java
- **架构**: Camera2 API + Material Design

## 构建方法

### 推荐：GitHub Actions自动构建

1. 将项目上传到GitHub仓库
2. GitHub Actions会自动构建APK
3. 在Actions页面下载生成的APK文件

详细步骤请参考：[GitHub构建指南.md](GitHub构建指南.md)

## 权限说明

应用需要以下权限：
- **摄像头权限**: 检测和访问本地摄像头
- **网络权限**: 扫描网络摄像头设备
- **位置权限**: 用于WiFi网络扫描
- **蓝牙权限**: 发现和连接蓝牙设备

## 项目结构

```
├── app/
│   ├── src/main/
│   │   ├── java/com/cameradetector/app/
│   │   │   ├── MainActivity.java           # 主活动
│   │   │   ├── CameraInfo.java            # 摄像头信息类
│   │   │   ├── CameraDetector.java        # 检测核心逻辑
│   │   │   ├── CameraListAdapter.java     # 列表适配器
│   │   │   ├── CameraControlActivity.java # 控制界面
│   │   │   └── CameraController.java      # 控制逻辑
│   │   ├── res/
│   │   │   ├── layout/                    # 界面布局文件
│   │   │   ├── drawable/                  # 图标和样式资源
│   │   │   └── values/                    # 字符串和样式定义
│   │   └── AndroidManifest.xml            # 应用清单
│   └── build.gradle                       # 应用构建配置
├── .github/workflows/build.yml            # GitHub Actions配置
├── build.gradle                           # 项目构建配置
├── settings.gradle                        # 项目设置
└── README.md                              # 项目说明
```

## 使用说明

1. 安装APK到Android设备
2. 启动应用并授权必要权限
3. 点击"扫描摄像头"开始检测
4. 查看检测结果列表
5. 点击"控制摄像头"进入管理界面

## 许可证

本项目仅供学习和研究使用。
