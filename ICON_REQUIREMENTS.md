# 🎨 应用图标需求说明

## 📁 当前项目结构

```
FaceDistanceMonitor/
└── app/src/main/res/
    ├── mipmap-anydpi-v26/          # Android 8.0+ 自适应图标
    │   ├── ic_launcher.xml         # Launcher 图标配置
    │   ├── ic_launcher_round.xml   # 圆形裁剪版本
    │   ├── ic_launcher_background.xml
    │   └── ic_launcher_foreground.png  ← 主要修改这个 (432x432)
    │
    ├── mipmap-xxxhdpi/             # 高 DPI 设备
    │   └── ic_launcher.png         ← 也可修改这个 (192x192)
    │
    ├── mipmap-xxhdpi/              # 中等高 DPI
    │   └── ic_launcher.png         (144x144)
    │
    ├── mipmap-xhdpi/               # 高清
    │   └── ic_launcher.png         (96x96)
    │
    ├── mipmap-hdpi/                # 标清
    │   └── ic_launcher.png         (72x72)
    │
    ├── mipmap-mdpi/                # 低清
    │   └── ic_launcher.png         (48x48)
    │
    └── drawable/                   # 应用内使用
        └── ic_logo.png             ← 主界面左上角图标 (96x96)
```

---

## ✅ 需要提供的资源

### 选项 A：提供 PNG 文件（最简单）

**请提供以下文件之一：**

| 文件 | 尺寸 | 用途 |
|------|------|------|
| `ic_launcher_foreground.png` | 432x432 | 自适应图标前景 |
| `ic_launcher.png` | 192x192 | 高 DPI 备用 |
| `ic_logo.png` | 96x96 | 主界面图标（白色背景） |

**格式要求：**
- PNG 格式
- 支持透明通道（RGBA）
- 无压缩损失（建议使用无损压缩）

---

### 选项 B：提供 SVG 矢量图（推荐）

**如果提供 SVG，我可以自动生成所有尺寸：**

```
设计要素：
- 盾牌形状（浅薄荷绿 #7FFFD4）
- 眼睛图案（白色椭圆 + 深青色瞳孔）
- 简洁扁平风格
```

---

## 🎯 期望效果

**你提供的参考图：**
- 盾牌：浅薄荷绿/青绿色
- 眼睛：白色外圈 + 深青色内圈
- 背景：透明（桌面）或白色（应用内）

---

## 📝 外部人员可以这样指导

### 方式一：直接发图片给我

1. 发送正确的图标 PNG/SVG
2. 我说"替换图标"
3. 我更新到项目并提交

### 方式二：提供精确参数

```
盾牌颜色：#7FFFD4（或提供十六进制值）
眼睛颜色：白色 #FFFFFF
瞳孔颜色：#008080
盾牌尺寸：占图标 80% 宽度
眼睛位置：盾牌中心
```

### 方式三：使用在线工具生成

1. 访问 https://romannurik.github.io/AndroidAssetStudio/
2. 上传图标 → 选择 "Launcher Icons"
3. 下载生成的资源包
4. 解压后覆盖到对应目录

---

## 🔧 当前配置（无需修改）

```xml
<!-- mipmap-anydpi-v26/ic_launcher.xml -->
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/transparent"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
```

---

## 🚀 更新流程

1. 提供图标文件 → 2. 我复制到正确位置 → 3. 提交并推送 → 4. 重新构建 APK

---

**仓库地址：** https://github.com/foddyy/fdm  
**分支：** main
