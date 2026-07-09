from PIL import Image, ImageDraw
import os

# 打开原图
img = Image.open('/home/foddyy/.hermes/cache/images/img_e01e24514176.jpg').convert('RGBA')
w, h = img.size
print(f"Original: {w}x{h}")

# 根据分析：图标主体占90%，居中；水印在右下角
# 裁剪图标主体（去除外围深灰边框）
margin_x = int(w * 0.05)  # 5%
margin_y = int(h * 0.05)  # 5%
icon_main = img.crop((margin_x, margin_y, w - margin_x, h - margin_y))
iw, ih = icon_main.size
print(f"After border crop: {iw}x{ih}")

# 去掉右下角水印区域（约12% x 7%）
watermark_x = int(iw * 0.86)
watermark_y = int(ih * 0.88)
clean_icon = icon_main.crop((0, 0, watermark_x, watermark_y))
cw, ch = clean_icon.size
print(f"After watermark removal: {cw}x{ch}")

# 找到盾牌+眼睛的边界（去除浅青色背景）
# 浅青色背景特征：G通道很高(>200)，R和B较低(<180)
top, bottom, left, right = ch, -1, cw, -1
step = 4

for y in range(0, ch, step):
    for x in range(0, cw, step):
        r, g, b, a = clean_icon.getpixel((x, y))
        # 检测非浅青色背景像素
        is_bg = (g > 200 and r < 180 and b < 180)
        if not is_bg:
            top = min(top, y)
            bottom = max(bottom, y)
            left = min(left, x)
            right = max(right, x)

print(f"Rough bounds: ({left}, {top}) to ({right}, {bottom})")

# 精细查找边界
ft, fb, fl, fr = ch, -1, cw, -1
for y in range(max(0, top-10), min(ch, bottom+10)):
    for x in range(max(0, left-10), min(cw, right+10)):
        r, g, b, a = clean_icon.getpixel((x, y))
        is_bg = (g > 200 and r < 180 and b < 180)
        if not is_bg:
            ft = min(ft, y)
            fb = max(fb, y)
            fl = min(fl, x)
            fr = max(fr, x)

print(f"Fine bounds: ({fl}, {ft}) to ({fr}, {fb}) -> {fr-fl+1}x{fb-ft+1}")

# 裁剪盾牌+眼睛主体
shield_eye = clean_icon.crop((fl, ft, fr + 1, fb + 1))
sw, sh = shield_eye.size
print(f"Shield+Eye: {sw}x{sh}")

# ==================== 方案1：圆角方形应用图标 ====================
# 创建带渐变背景的圆角图标
app_icon_size = 512
app_icon = Image.new('RGBA', (app_icon_size, app_icon_size), (0, 0, 0, 0))
draw_app = ImageDraw.Draw(app_icon)

# 绘制圆角矩形背景（浅青蓝渐变）
radius = 128
for y in range(app_icon_size):
    t = y / app_icon_size
    # 顶部：#B2EBF2，底部：#E0F7FA
    r = int(178 + (224 - 178) * t)
    g = int(235 + (247 - 235) * t)
    b = int(242 + (250 - 242) * t)
    draw_app.line([(0, y), (app_icon_size, y)], fill=(r, g, b, 255))

# 圆角蒙版
mask = Image.new('L', (app_icon_size, app_icon_size), 0)
draw_mask = ImageDraw.Draw(mask)
draw_mask.rounded_rectangle([0, 0, app_icon_size, app_icon_size], radius=radius, fill=255)

# 应用圆角
app_icon = Image.composite(app_icon, Image.new('RGBA', (app_icon_size, app_icon_size), (0, 0, 0, 0)), mask)

# 缩放盾牌+眼睛到图标中心
scale = app_icon_size / max(sw, sh)
new_w = int(sw * scale)
new_h = int(sh * scale)
shield_eye_scaled = shield_eye.resize((new_w, new_h), Image.Resampling.LANCZOS)

# 居中粘贴
offset_x = (app_icon_size - new_w) // 2
offset_y = (app_icon_size - new_h) // 2
app_icon.paste(shield_eye_scaled, (offset_x, offset_y), shield_eye_scaled)

# 保存xxxhdpi图标（432x432）
os.makedirs('app/src/main/res/mipmap-xxxhdpi', exist_ok=True)
app_icon_432 = app_icon.resize((432, 432), Image.Resampling.LANCZOS)
app_icon_432.save('app/src/main/res/mipmap-xxxhdpi/ic_launcher.png', 'PNG')
print(f"Mipmap icon saved: {app_icon_432.size}")

# ==================== 方案2：透明背景导航栏Logo ====================
nav_size = 128
nav_logo = Image.new('RGBA', (nav_size, nav_size), (0, 0, 0, 0))
scale_nav = nav_size / max(sw, sh)
new_w_nav = int(sw * scale_nav)
new_h_nav = int(sh * scale_nav)
shield_eye_nav = shield_eye.resize((new_w_nav, new_h_nav), Image.Resampling.LANCZOS)

offset_x_nav = (nav_size - new_w_nav) // 2
offset_y_nav = (nav_size - new_h_nav) // 2
nav_logo.paste(shield_eye_nav, (offset_x_nav, offset_y_nav), shield_eye_nav)

os.makedirs('app/src/main/res/drawable', exist_ok=True)
nav_logo.save('app/src/main/res/drawable/ic_logo_transparent.png', 'PNG')
print(f"Transparent logo saved: {nav_logo.size}")

# ==================== 方案3：预览图 ====================
preview = Image.new('RGB', (1024, 512), (255, 255, 255))
preview.paste(app_icon.resize((512, 512), Image.Resampling.LANCZOS).convert('RGB'), (0, 0))
preview.paste(nav_logo.resize((256, 256), Image.Resampling.LANCZOS).convert('RGB'), (512, 128))
preview.save('/tmp/logo_preview.png', 'PNG')
print("Preview saved to /tmp/logo_preview.png")

print("\n所有logo已生成！")
