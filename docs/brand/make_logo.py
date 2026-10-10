"""Make transparent, square CareTrack logo files (public/) from the original JPEG (black background).

Usage, from the repo root:  python docs/brand/make_logo.py docs/brand/caretrack-logo-original.jpeg public
"""
import sys
from PIL import Image

src, out_dir = sys.argv[1], sys.argv[2]
img = Image.open(src).convert("RGB")

# Black background -> transparent, with a soft edge so curves stay smooth.
# Brightness of the darkest logo colour (navy) is well above the JPEG's near-black noise.
rgba = Image.new("RGBA", img.size)
px_in, px_out = img.load(), rgba.load()
w, h = img.size
for y in range(h):
    for x in range(w):
        r, g, b = px_in[x, y]
        level = max(r, g, b)
        if level <= 24:
            alpha = 0
        elif level >= 60:
            alpha = 255
        else:
            alpha = int((level - 24) * 255 / 36)
        px_out[x, y] = (r, g, b, alpha)

# Trim to the visible logo, then centre it on a square canvas with a little room
box = rgba.getbbox()
logo = rgba.crop(box)
side = int(max(logo.size) * 1.08)
square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
square.paste(logo, ((side - logo.width) // 2, (side - logo.height) // 2), logo)

square.resize((512, 512), Image.LANCZOS).save(f"{out_dir}/logo.png", optimize=True)
square.resize((192, 192), Image.LANCZOS).save(f"{out_dir}/logo-192.png", optimize=True)
square.resize((64, 64), Image.LANCZOS).save(f"{out_dir}/favicon.png", optimize=True)
print("logo box", box, "-> square", side)
