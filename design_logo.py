from PIL import Image, ImageDraw, ImageFont
import math

# 创建高分辨率画布
size = 512
img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

# ==================== 方案1: 现代简约眼睛 + 绿叶 ====================
# 绿色渐变背景
gradient_bg = Image.new('RGB', (size, size))
for y in range(size):
    r = int(0 + (76 * y / size))
    g = int(175 + (81 * y / size))
    b = int(76 + (41 * y / size))
    for x in range(size):
        gradient_bg.putpixel((x, y), (r, g, b))

# 眼睛形状
eye_center_x, eye_center_y = 256, 256
eye_width = 200
eye_height = 100

# 绘制外圈眼睛轮廓
draw.ellipse([eye_center_x - eye_width//2, eye_center_y - eye_height//2, 
              eye_center_x + eye_width//2, eye_center_y + eye_height//2],
             outline='#FFFFFF', width=6)

# 瞳孔
draw.ellipse([eye_center_x - 40, eye_center_y - 40,
              eye_center_x + 40, eye_center_y + 40],
             fill='#FFFFFF')

# 瞳孔中心
draw.ellipse([eye_center_x - 15, eye_center_y - 15,
              eye_center_x + 15, eye_center_y + 15],
             fill='#4CAF50')

# 绿叶装饰（右上角）
leaf_points = [
    (320, 120),  # 叶尖
    (350, 140),  # 右上
    (340, 180),  # 右中
    (300, 160),  # 右下
    (280, 130),  # 左下
]
draw.polygon(leaf_points, fill='#81C784', outline='#FFFFFF')
# 叶脉
draw.line([(300, 160), (320, 120)], fill='#FFFFFF', width=2)

# 保存方案1
img.save('/tmp/logo_scheme1.png', 'PNG')
print("方案1保存完成")

# ==================== 方案2: 抽象眼睛 + 盾牌保护 ====================
img2 = Image.new('RGBA', (size, size), (0, 0, 0, 0))
draw2 = ImageDraw.Draw(img2)

# 深蓝绿色渐变背景
gradient_bg2 = Image.new('RGB', (size, size))
for y in range(size):
    r = int(2 + (27 * y / size))
    g = int(89 + (115 * y / size))
    b = int(123 + (32 * y / size))
    for x in range(size):
        gradient_bg2.putpixel((x, y), (r, g, b))

# 盾牌形状
shield_points = [
    (256, 60),   # 顶部
    (420, 120),  # 右上
    (420, 280),  # 右侧
    (256, 440),  # 底部尖端
    (92, 280),   # 左侧
    (92, 120),   # 左上
]
draw2.polygon(shield_points, fill='#1B5E20', outline='#4CAF50', width=4)

# 盾牌内的眼睛
eye2_width = 120
eye2_height = 60
draw2.ellipse([256 - eye2_width//2, 256 - eye2_height//2,
               256 + eye2_width//2, 256 + eye2_height//2],
              outline='#FFFFFF', width=4)
draw2.ellipse([256 - 25, 256 - 25,
               256 + 25, 256 + 25],
              fill='#FFFFFF')
draw2.ellipse([256 - 10, 256 - 10,
               256 + 10, 256 + 10],
              fill='#1B5E20')

img2.save('/tmp/logo_scheme2.png', 'PNG')
print("方案2保存完成")

# ==================== 方案3: 极简眼睛 + 时间环 ====================
img3 = Image.new('RGBA', (size, size), (0, 0, 0, 0))
draw3 = ImageDraw.Draw(img3)

# 纯白色背景，绿色主题
bg3 = Image.new('RGB', (size, size), '#FFFFFF')
img3 = Image.merge('RGBA', [bg3.split()[0], bg3.split()[1], bg3.split()[2], img3.split()[3]])

# 外圈圆环
draw3.ellipse([60, 60, 452, 452], outline='#2E7D32', width=8)

# 内圈圆环
draw3.ellipse([100, 100, 412, 412], outline='#4CAF50', width=4)

# 眼睛形状（水平椭圆）
eye3_width = 180
eye3_height = 80
draw3.ellipse([256 - eye3_width//2, 256 - eye3_height//2,
               256 + eye3_width//2, 256 + eye3_height//2],
              fill='#2E7D32')

# 瞳孔
draw3.ellipse([256 - 35, 256 - 35,
               256 + 35, 256 + 35],
              fill='#FFFFFF')

# 瞳孔高光点
draw3.ellipse([256 - 12, 256 - 12,
               256 + 12, 256 + 12],
              fill='#2E7D32')

img3.save('/tmp/logo_scheme3.png', 'PNG')
print("方案3保存完成")

print("\n三个方案已保存到/tmp/logo_scheme1.png, /tmp/logo_scheme2.png, /tmp/logo_scheme3.png")
