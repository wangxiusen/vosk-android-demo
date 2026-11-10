"""Extract the existing V5 Lottie PNG layers for the standalone web prototype."""

import base64
import json
from pathlib import Path


root = Path(__file__).resolve().parent
source = root.parent / "medical-assistant-talking-v5.json"
assets = root / "assets"
assets.mkdir(exist_ok=True)

for asset in json.loads(source.read_text())["assets"]:
    encoded = asset["p"].split(",", 1)[1]
    (assets / f"{asset['id']}.png").write_bytes(base64.b64decode(encoded))
