"""从原始方图生成 Android 自适应图标 + 传统图标。

源图：白底 + 竖排书法，墨迹包围盒 468x1322（1:2.83 的长条）。
自适应图标的安全区是居中的 72dp，圆形遮罩半径 36dp；
长条半宽 w 能容纳的半高上限是 sqrt(36^2 - w^2)，
按 468/1322 的宽高比算下来墨迹最高 66dp 才保证不被圆遮罩切到。
"""
from PIL import Image, ImageDraw
import os

SRC = r"D:\安装包\软件名黄粱一梦.png"
RES = r"D:\安装包\AppBreak-Android-MVP\app\src\main\res"

INK = (27, 28, 30)          # 墨色，取自源图中心像素
BBOX = (536, 72, 1004, 1394)
INK_H = BBOX[3] - BBOX[1]   # 1322
INK_W = BBOX[2] - BBOX[0]   # 468
MARGIN = 10                 # 裁切留边，只为了避免切到抗锯齿边缘

ADAPTIVE_INK_DP = 66.0      # 自适应前景里墨迹的目标高度（dp）
LEGACY_INK_RATIO = 0.86     # 传统图标里墨迹占整边的比例（沿用源图比例）

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}
LEGACY_PX = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def ensure(path):
    os.makedirs(path, exist_ok=True)


def circle_mask(size, supersample=4):
    """抗锯齿的圆形遮罩"""
    big = size * supersample
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, big - 1, big - 1), fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def main():
    src = Image.open(SRC).convert("RGB")
    size = src.size[0]

    # 白底 -> 透明：用亮度当 alpha，墨越黑越不透明，抗锯齿灰边自然半透明
    alpha = src.convert("L").point(lambda v: 255 - v)
    ink = Image.new("RGBA", (size, size), INK + (0,))
    ink.putalpha(alpha)

    # 按墨迹包围盒裁切，让后续居中是以「字」为准而不是以画布为准
    l, t, r, b = BBOX
    crop_box = (max(0, l - MARGIN), max(0, t - MARGIN),
                min(size, r + MARGIN), min(size, b + MARGIN))
    ink_crop = ink.crop(crop_box)

    for name, d in DENSITIES.items():
        # ---------- 自适应前景 ----------
        canvas = round(108 * d)
        scale = (ADAPTIVE_INK_DP * d) / INK_H
        w = max(1, round(ink_crop.size[0] * scale))
        h = max(1, round(ink_crop.size[1] * scale))
        fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        fg.paste(ink_crop.resize((w, h), Image.Resampling.LANCZOS),
                 ((canvas - w) // 2, (canvas - h) // 2), None)
        out = os.path.join(RES, f"drawable-{name}")
        ensure(out)
        fg.save(os.path.join(out, "ic_launcher_foreground.png"))

        # 单色层：同样的形状，颜色交给系统主题去染
        mono = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        mono.paste(fg, (0, 0), fg)
        mono.save(os.path.join(out, "ic_launcher_monochrome.png"))

        # ---------- 传统方形图标：整张源图铺满 ----------
        px = LEGACY_PX[name]
        sq = src.resize((px, px), Image.Resampling.LANCZOS)
        mout = os.path.join(RES, f"mipmap-{name}")
        ensure(mout)
        sq.save(os.path.join(mout, "ic_launcher.png"))

        # ---------- 传统圆形图标：白圆 + 居中的字 ----------
        rnd = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        white = Image.new("RGBA", (px, px), (255, 255, 255, 255))
        white.putalpha(circle_mask(px))
        rnd.paste(white, (0, 0), white)

        iw = max(1, round(px * LEGACY_INK_RATIO * INK_W / INK_H))
        ih = max(1, round(px * LEGACY_INK_RATIO))
        small = ink_crop.resize((iw, ih), Image.Resampling.LANCZOS)
        rnd.paste(small, ((px - iw) // 2, (px - ih) // 2), small)
        rnd.save(os.path.join(mout, "ic_launcher_round.png"))

    print("done")
    for name in DENSITIES:
        for d in (f"drawable-{name}", f"mipmap-{name}"):
            p = os.path.join(RES, d)
            for f in sorted(os.listdir(p)):
                if f.startswith("ic_launcher"):
                    print(f"  {d}/{f}", os.path.getsize(os.path.join(p, f)))


main()
