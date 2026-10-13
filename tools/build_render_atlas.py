import json
from pathlib import Path

from PIL import Image


assets = Path(__file__).resolve().parents[1] / 'android/app/src/main/assets'
data = json.loads((assets / 'game_data.json').read_text(encoding='utf-8'))
files = set()
for name, kind in data['types'].items():
    if name == 'p3':
        files.update(f['file'] for a in kind['anims'].values() for f in a['frames'])
    elif kind['kind'] == 'particles':
        files.add(kind['image'])
images = [(name, Image.open(assets / name).convert('RGBA')) for name in files]
images.sort(key=lambda item: (-item[1].height, -item[1].width, item[0]))
side = 512
sheet = Image.new('RGBA', (side, side))
regions = {}
x = y = row_height = 0
for name, image in images:
    if x + image.width + 2 > side:
        y += row_height
        x = row_height = 0
    if y + image.height + 2 > side:
        raise RuntimeError('Small-object atlas exceeded 512x512')
    x0, y0 = x + 1, y + 1
    sheet.paste(image, (x0, y0))
    # Extrude a texel to keep linear filtering inside each sprite.
    sheet.paste(image.crop((0, 0, 1, image.height)), (x0 - 1, y0))
    sheet.paste(image.crop((image.width - 1, 0, image.width, image.height)), (x0 + image.width, y0))
    sheet.paste(image.crop((0, 0, image.width, 1)), (x0, y0 - 1))
    sheet.paste(image.crop((0, image.height - 1, image.width, image.height)), (x0, y0 + image.height))
    regions[name] = [x0, y0, image.width, image.height]
    x += image.width + 2
    row_height = max(row_height, image.height + 2)
sheet.save(assets / 'small_objects_atlas.png')
(assets / 'render_atlas.json').write_text(json.dumps({
    'file': 'small_objects_atlas.png', 'size': side, 'regions': regions,
}, ensure_ascii=True, separators=(',', ':')), encoding='utf-8')
print(f'Packed {len(regions)} images into {side}x{side}: 1 MiB RGBA texture')
