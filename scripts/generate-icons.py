"""从 SVG 源文件生成 Android 图标。依赖：Python 3 + Pillow。"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/branding/app-icon.svg"
ANDROID = "http://schemas.android.com/apk/res/android"
SVG = "http://www.w3.org/2000/svg"


def points(data):
    """采样本图标使用的 M/L/Q/C/Z 路径，栅格资源与矢量共用几何。"""
    tokens = re.findall(r"[MLQCZ]|-?\d+(?:\.\d+)?", data)
    result, i, current = [], 0, (0, 0)
    while i < len(tokens):
        op = tokens[i]
        i += 1
        if op == "Z":
            continue
        count = {"M": 2, "L": 2, "Q": 4, "C": 6}[op]
        values = list(map(float, tokens[i:i + count]))
        i += count
        if op in ("M", "L"):
            current = tuple(values)
            result.append(current)
        else:
            start = current
            end = tuple(values[-2:])
            for step in range(1, 33):
                t, u = step / 32, 1 - step / 32
                if op == "Q":
                    p = tuple(u*u*start[k] + 2*u*t*values[k] + t*t*end[k] for k in (0, 1))
                else:
                    p = tuple(u**3*start[k] + 3*u*u*t*values[k] + 3*u*t*t*values[k+2] + t**3*end[k] for k in (0, 1))
                result.append(p)
            current = end
    return result


def vector(paths, size=108):
    root = ET.Element("vector", {"xmlns:android": ANDROID,
        "android:width": f"{size}dp", "android:height": f"{size}dp",
        "android:viewportWidth": "108", "android:viewportHeight": "108"})
    for path in paths:
        ET.SubElement(root, "path", {"android:pathData": path.get("d"), "android:fillColor": path.get("fill")})
    ET.indent(root, space="    ")
    return ET.tostring(root, encoding="unicode") + "\n"


def render(paths, size, round_icon=False):
    scale = size * 4 / 108
    image = Image.new("RGBA", (size * 4, size * 4))
    draw = ImageDraw.Draw(image)
    for path in paths:
        draw.polygon([(round(x*scale), round(y*scale)) for x,y in points(path.get("d"))], fill=path.get("fill"))
    if round_icon:
        mask = Image.new("L", image.size)
        ImageDraw.Draw(mask).ellipse((0, 0, image.width-1, image.height-1), fill=255)
        image.putalpha(mask)
    return image.resize((size, size), Image.Resampling.LANCZOS)


def main():
    svg = ET.parse(SOURCE).getroot()
    paths = list(svg.iter(f"{{{SVG}}}path"))
    foreground = [p for p in paths if p.get("id") != "background"]
    res = ROOT / "app/src/main/res"
    background = ET.Element("path", {"d": "M0 0 L108 0 L108 108 L0 108 Z", "fill": paths[0].get("fill")})
    (res / "drawable/ic_launcher_background.xml").write_text(vector([background]), encoding="utf-8")
    (res / "drawable/ic_launcher_foreground.xml").write_text(vector(foreground), encoding="utf-8")
    # 单色图标将键槽和 X 做成镂空，支持 Android 13 主题着色。
    mono = ET.Element("vector", {"xmlns:android": ANDROID, "android:width": "108dp",
        "android:height": "108dp", "android:viewportWidth": "108", "android:viewportHeight": "108"})
    body = next(p for p in foreground if p.get("id") == "keyboard")
    diamond = next(p for p in foreground if p.get("id") == "x-key")
    slots = [p for p in foreground if p.get("id", "").startswith("cutout")]
    keyboard_group = ET.SubElement(mono, "group")
    ET.SubElement(keyboard_group, "clip-path", {"android:fillType": "evenOdd",
        "android:pathData": "M0 0 L108 0 L108 108 L0 108 Z " + diamond.get("d")})
    ET.SubElement(keyboard_group, "path", {"android:fillColor": "#FFFFFF", "android:fillType": "evenOdd",
        "android:pathData": body.get("d") + " " + " ".join(p.get("d") for p in slots)})
    ET.SubElement(mono, "path", {"android:fillColor": "#FFFFFF", "android:fillType": "evenOdd",
        "android:pathData": diamond.get("d") + " " + next(p for p in foreground if p.get("id") == "x").get("d")})
    ET.indent(mono, space="    ")
    (res / "drawable/ic_launcher_monochrome.xml").write_text(ET.tostring(mono, encoding="unicode") + "\n", encoding="utf-8")
    # 候选栏保留紧凑的同一标记；背景键槽为实色以适配现有 tint 行为。
    for name, size in [("logo", 108), ("logo_dark", 108), ("ic_logo", 24)]:
        (res / f"drawable/{name}.xml").write_text(vector(paths, size), encoding="utf-8")
    for name in ("ic_launcher", "ic_launcher_round", "ic_launcher_light", "ic_launcher_light_round"):
        (res / f"mipmap-anydpi-v26/{name}.xml").write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n', encoding="utf-8")
    for density, size in [("mdpi",48),("hdpi",72),("xhdpi",96),("xxhdpi",144),("xxxhdpi",192)]:
        for name in ("ic_launcher", "ic_launcher_round", "ic_launcher_light", "ic_launcher_light_round"):
            render(paths, size, "round" in name).save(res / f"mipmap-{density}/{name}.webp", lossless=True)
    for name in ("ic_launcher-playstore.png", "ic_launcher_light-playstore.png"):
        render(paths, 512).save(ROOT / f"app/src/main/{name}")
    render(paths, 512).save(ROOT / "docs/branding/app-icon.png")
    print("Generated adaptive, monochrome, legacy, in-app and store icons from", SOURCE)


if __name__ == "__main__":
    main()
