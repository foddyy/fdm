from PIL import Image
import os

# 打开新logo
img = Image.open('/home/foddyy/.hermes/cache/images/img_022e26f78c12.jpg').convert('RGBA')
w, h = img.size
print(f"Original: {w}x{h}")

# 分析图像中的颜色分布
colors = {}
for y in range(0, h, 10):
    for x in range(0, w, 10):
        r, g, b, a = img.getpixel((x, y))
        key = (r // 20 * 20, g // 20 * 20, b // 20 * 20)
        colors[key] = colors.get(key, 0) + 1

print("Top colors by frequency:")
sorted_colors = sorted(colors.items(), key=lambda x: -x[1])[:10]
for color, count in sorted_colors:
    print(f"  RGB≈{color}: {count} pixels")

# 浅蓝色背景的特征：R、G、B 都较高（>180）
# 青绿色线条的特征：G和B较高，R较低（<50）

nav_size = 128
nav_logo = img.resize((nav_size, nav_size), Image.Resampling.LANCZOS)

# 方法：将浅蓝色背景变为完全透明
for y in range(nav_size):
    for x in range(nav_size):
        r, g, b, a = nav_logo.getpixel((x, y))
        
        # 判断是否是浅蓝色背景
        is_background = (g > 180 and r > 180 and b > 180)
        
        if is_background:
            # 背景设为透明
            nav_logo.putpixel((x, y), (0, 0, 0, 0))
        elif a < 128:
            # 半透明区域也变透明
            nav_logo.putpixel((x, y), (0, 0, 0, 0))

# 保存
os.makedirs('app/src/main/res/drawable', exist_ok=True)
nav_logo.save('app/src/main/res/drawable/ic_logo_transparent.png', 'PNG')
print(f"\nTransparent logo saved: {nav_logo.size}")

# 验证：检查是否还有浅蓝色像素
remaining_bg = 0
for y in range(nav_size):
    for x in range(nav_size):
        r, g, b, a = nav_logo.getpixel((x, y))
        if g > 180 and r > 180 and b > 180 and a > 0:
            remaining_bg += 1

print(f"Remaining background pixels: {remaining_bg}")
