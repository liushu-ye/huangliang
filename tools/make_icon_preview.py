"""渲染图标预览：把自适应图标套上几种常见启动器遮罩，外加传统图标。"""
from PIL import Image, ImageDraw
import os

RES = r"D:\安装包\AppBreak-Android-MVP\app\src\main\res"
OUT = r"D:\安装包\icon-preview.png"

CELL = 260
PAD = 24
COUNT = 5

fg = Image.open(os.path.join(RES, "drawable-xxxhdpi", "ic_launcher_foreground.png")).convert("RGBA")
mono = Image.open(os.path.join(RES, "drawable-xxxhdpi", "ic_launcher_monochrome.png")).convert("RGBA")
legacy_round = Image.open(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_round.png")).convert("RGBA")
legacy_square = Image.open(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher.png")).convert("RGBA")

WHITE = (255, 255, 255, 255)


def shaped(mask_img, size):
    """白底 + 前景，按给定遮罩裁形"""
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    fill = Image.new("RGBA", (size, size), WHITE)
    fill.putalpha(mask_img)
    layer.paste(fill, (0, 0), fill)
    f = fg.resize((size, size), Image.Resampling.LANCZOS)
    layer.paste(f, (0, 0), f)
    return layer


def circle(size):
    s = size * 4
    m = Image.new("L", (s, s), 0)
    ImageDraw.Draw(m).ellipse((0, 0, s - 1, s - 1), fill=255)
    return m.resize((size, size), Image.Resampling.LANCZOS)


def rounded(size, ratio):
    s = size * 4
    m = Image.new("L", (s, s), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, s - 1, s - 1), radius=int(s * ratio), fill=255)
    return m.resize((size, size), Image.Resampling.LANCZOS)


def themed(size):
    """Android 13+ 主题图标：系统染色 + 圆形"""
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    tint = Image.new("RGBA", (size, size), (90, 110, 160, 255))
    m = mono.resize((size, size), Image.Resampling.LANCZOS)
    tint.putalpha(m.split()[3])
    layer.paste(tint, (0, 0), tint)
    # 主题图标一般配一个浅色底
    base = Image.new("RGBA", (size, size), (222, 228, 240, 255))
    base.putalpha(circle(size))
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(base, (0, 0), base)
    out.paste(layer, (0, 0), layer)
    return out


W = COUNT * CELL + (COUNT + 1) * PAD
H = CELL + 2 * PAD
sheet = Image.new("RGB", (W, H), (238, 238, 238))

variants = [
    shaped(circle(CELL), CELL),          # 圆形遮罩
    shaped(rounded(CELL, 0.30), CELL),   # 圆角方形
    shaped(rounded(CELL, 0.18), CELL),   # 小圆角
    legacy_square.resize((CELL, CELL), Image.Resampling.LANCZOS),   # 传统方形
    themed(CELL),                        # 主题图标
]

for i, img in enumerate(variants):
    x = PAD + i * (CELL + PAD)
    sheet.paste(img, (x, PAD), img)

sheet.save(OUT)
print("saved", OUT, sheet.size)

# 顺便报告一下每个变体里非背景像素的占比，确认字确实画上去了
for i, img in enumerate(variants):
    rgba = img.convert("RGBA")
    px = rgba.load()
    ink = 0
    for y in range(0, CELL, 3):
        for x in range(0, CELL, 3):
            r, g, b, a = px[x, y]
            if a > 40 and r < 200:
                ink += 1
    print(f"  variant {i}: ink samples {ink}")
