#!/usr/bin/env python3
"""Renders voice clips with Maya1 (3B open-weights TTS) served by ollama.

Runs ON the ollama host (see MAYA_HOST in generate_voice_prompts.py, which
drives this over ssh): reads a JSON job file
    {"description": "...voice design...", "clips": {"name": "text", ...},
     "out_dir": "/tmp/maya_out"}
prompts Maya1 through ollama's raw generate API, decodes the returned SNAC
codec tokens (7 per 24 kHz audio frame) to WAV files in out_dir.

Setup on the host (one-time):
    ollama pull hf.co/Mungert/maya1-GGUF:BF16
    python3 -m venv ~/maya-tts/venv
    ~/maya-tts/venv/bin/pip install snac soundfile numpy
"""

import json
import pathlib
import re
import sys
import urllib.request

OLLAMA = "http://localhost:11434/api/generate"
MODEL = "hf.co/Mungert/maya1-GGUF:BF16"

# Token layout per the maya-research/maya1 model card: <custom_token_i> is
# id 128256+i; SNAC audio codes span 128266..156937 (7 slots x 4096).
SNAC_MIN_ID = 128266
SNAC_MAX_ID = 156937
PROMPT = (
    '<custom_token_3><|begin_of_text|><description="{description}"> {text}'
    "<|eot_id|><custom_token_4><custom_token_5><custom_token_1>"
)
CODE_END = "<custom_token_2>"
TOKEN_RE = re.compile(r"<custom_token_(\d+)>")


def generate_tokens(description, text, seed):
    body = json.dumps({
        "model": MODEL,
        "prompt": PROMPT.format(description=description, text=text),
        "raw": True,
        "stream": False,
        "options": {
            "temperature": 0.4,
            "top_p": 0.9,
            "repeat_penalty": 1.1,
            "num_predict": 1800,
            "seed": seed,
            "stop": [CODE_END],
        },
    }).encode()
    request = urllib.request.Request(
        OLLAMA, data=body, headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(request, timeout=900) as response:
        text_out = json.load(response)["response"]
    ids = [128256 + int(n) for n in TOKEN_RE.findall(text_out)]
    return [i - SNAC_MIN_ID for i in ids if SNAC_MIN_ID <= i <= SNAC_MAX_ID]


def decode_audio(snac_model, torch, codes):
    """7 interleaved slots per frame -> SNAC's 3 hierarchical levels."""
    frames = len(codes) // 7
    l1, l2, l3 = [], [], []
    for i in range(frames):
        s = codes[i * 7:(i + 1) * 7]
        l1.append(s[0] % 4096)
        l2.extend([s[1] % 4096, s[4] % 4096])
        l3.extend([s[2] % 4096, s[3] % 4096, s[5] % 4096, s[6] % 4096])
    tensors = [
        torch.tensor(level, dtype=torch.long).unsqueeze(0)
        for level in (l1, l2, l3)
    ]
    with torch.inference_mode():
        audio = snac_model.decode(tensors)
    return audio.squeeze().cpu().numpy(), frames


def main():
    job = json.loads(pathlib.Path(sys.argv[1]).read_text())
    out_dir = pathlib.Path(job["out_dir"])
    out_dir.mkdir(parents=True, exist_ok=True)

    import soundfile as sf
    import torch
    from snac import SNAC
    snac_model = SNAC.from_pretrained("hubertsiuzdak/snac_24khz").eval()

    for name, text in job["clips"].items():
        # Deterministic per-name seed so re-renders keep the same take.
        seed = job.get("seed_base", 7) * 1000 + sum(map(ord, name))
        codes = generate_tokens(job["description"], text, seed)
        if len(codes) < 7 * 4:
            print(f"FAIL {name}: only {len(codes)} snac tokens", flush=True)
            continue
        audio, frames = decode_audio(snac_model, torch, codes)
        sf.write(out_dir / f"{name}.wav", audio, 24000)
        print(f"ok {name}: {frames} frames {frames / 12.2:.1f}s", flush=True)


if __name__ == "__main__":
    main()
