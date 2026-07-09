from PIL import Image, ImageDraw
import os

# 打开新logo
img = Image.open('/home/foddyy/.hermes/cache/images/img_022e26f78c12.jpg').convert('RGBA')
w, h = img.size
print(f"Original: {w}x{h}")

# 方案1：圆角方形应用图标（512x512 -> 432x432 xxxhdpi）
app_icon_size = 512
app_icon = img.resize((app_icon_size, app_icon_size), Image.Resampling.LANCZOS)

# 创建圆角蒙版
mask = Image.new('L', (app_icon_size, app_icon_size), 0)
draw_mask = ImageDraw.Draw(mask)
draw_mask.rounded_rectangle([0, 0, app_icon_size, app_icon_size], radius=128, fill=255)
app_icon = Image.composite(app_icon, Image.new('RGBA', (app_icon_size, app_icon_size), (0, 0, 0, 0)), mask)

# 保存到xxxhdpi（432x432）
os.makedirs('app/src/main/res/mipmap-xxxhdpi', exist_ok=True)
app_icon_432 = app_icon.resize((432, 432), Image.Resampling.LANCZOS)
app_icon_432.save('app/src/main/res/mipmap-xxxhdpi/ic_launcher.png', 'PNG')
print(f"Mipmap icon saved: {app_icon_432.size}")

# 方案2：透明背景版本用于导航栏（128x128）
nav_size = 128
nav_logo = img.resize((nav_size, nav_size), Image.Resampling.LANCZOS)

# 提取青绿色线条（盾牌+眼睛），去除浅蓝色背景
for y in range(nav_size):
    for x in range(nav_size):
        r, g, b, a = nav_logo.getpixel((x, y))
        # 浅蓝色背景：R、G、B 都较高且接近
        # 青绿色线条：G 和 B 较高，R 较低
        if g > 150 and b > 150 and r < 50:
            pass  # 保留青绿色线条
        else:
            nav_logo.putpixel((x, y), (r, g, b, 0))

os.makedirs('app/src/main/res/drawable', exist_ok=True)
nav_logo.save('app/src/main/res/drawable/ic_logo_transparent.png', 'PNG')
print(f"Transparent logo saved: {nav_logo.size}")

print("\n所有 Logo 已生成！")
