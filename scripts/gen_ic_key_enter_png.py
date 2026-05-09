"""Generate ic_key_enter.png: white on transparent for Drawable#setTint (light/dark)."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

# Base resolution (drawable default density); scales on device.
W = H = 192
STROKE = 10  # ~5% of width; reads crisp when scaled to key size


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    out = root / "app" / "src" / "main" / "res" / "drawable" / "ic_key_enter.png"

    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    white = (255, 255, 255, 255)

    # Classic return / enter: vertical shaft on the right, elbow, horizontal to the left, arrowhead at tip.
    x_shaft = 118
    y_top = 34
    y_elbow = 122
    x_arrow_tip = 34
    stem_top = (x_shaft, y_top)
    elbow = (x_shaft, y_elbow)
    # Horizontal stops before arrow triangle so the joint stays clean
    x_h_end = x_arrow_tip + 26
    horiz_start = elbow
    horiz_end = (x_h_end, y_elbow)

    draw.line([stem_top, elbow], fill=white, width=STROKE, joint="curve")
    draw.line([horiz_start, horiz_end], fill=white, width=STROKE, joint="curve")

    # Filled arrow head (left-pointing)
    aw, ah = 24, 30
    tip = (x_arrow_tip, y_elbow)
    draw.polygon(
        [
            tip,
            (x_arrow_tip + aw, y_elbow - ah // 2),
            (x_arrow_tip + aw, y_elbow + ah // 2),
        ],
        fill=white,
    )

    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    print(f"Wrote {out}")


if __name__ == "__main__":
    main()
