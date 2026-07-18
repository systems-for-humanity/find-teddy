#!/usr/bin/env python3
"""Pre-renders the game's spoken prompts to mp3 clips.

Reads the English prompt patterns from the compose-resources strings.xml,
expands the {color}/{target} placeholders over all ball colors, and renders
one clip per line with edge-tts (free Microsoft neural voices).

Voice direction: an enthusiastic cowboy teddy bear.

- With an OpenAI API key present (read from ~/.config/openai_api.key, or
  the OPENAI_API_KEY env var), renders via OpenAI gpt-4o-mini-tts, which
  takes voice instructions and can actually do the western drawl. Costs a
  few cents per full render (needs API platform credit — separate from a
  ChatGPT subscription).
- Otherwise falls back to edge-tts (free): en-US-GuyNeural, the most
  energetic stock voice ("Passion"), rate/pitch pushed up for the bouncy
  teddy feel. No real drawl — the text carries the cowboy flavor.

Run directly or via the :composeApp:generateVoicePrompts gradle task
(which skips it while strings.xml is unchanged). Fallback requires:
pipx install edge-tts
"""

import concurrent.futures
import json
import os
import pathlib
import shutil
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET

# edge-tts fallback settings
VOICE = "en-US-GuyNeural"
RATE = "+12%"
PITCH = "+20Hz"

# OpenAI settings
OPENAI_MODEL = "gpt-4o-mini-tts"
OPENAI_VOICE = "ash"  # energetic male; try "verse" or "ballad" too
OPENAI_INSTRUCTIONS = (
    "You are an enthusiastic cowboy teddy bear talking to a young child. "
    "Big warm western drawl, bouncy and always excited, like a rodeo "
    "announcer who is also a cuddly toy. Slow enough for a toddler to follow."
)

ROOT = pathlib.Path(__file__).resolve().parent.parent
STRINGS = ROOT / "composeApp/src/commonMain/composeResources/values/strings.xml"
OUT = ROOT / "composeApp/src/commonMain/composeResources/files/voice"
COLORS = ["red", "blue", "green", "yellow", "purple", "orange"]


def edge_tts_bin():
    for candidate in (shutil.which("edge-tts"), str(pathlib.Path.home() / ".local/bin/edge-tts")):
        if candidate and pathlib.Path(candidate).exists():
            return candidate
    sys.exit("edge-tts not found — install with: pipx install edge-tts")


def load_strings():
    tree = ET.parse(STRINGS)
    return {e.get("name"): e.text or "" for e in tree.getroot()}


def build_clips(s):
    """Clip name -> spoken text. Names must match VoiceLine.clipFile()."""
    color_word = {c: s[f"color_{c}"] for c in COLORS}
    clips = {
        "all_clean": s["speak_all_clean"],
        "win": s["speak_win"],
    }
    for c in COLORS:
        clips[f"first_{c}"] = s["speak_first_prompt"].replace("{color}", color_word[c])
        clips[f"next_{c}"] = s["speak_next_prompt"].replace("{color}", color_word[c])
        clips[f"determined_{c}"] = s["speak_determined"].replace("{color}", color_word[c])
        for a in COLORS:
            if a != c:
                clips[f"wrong_{c}_{a}"] = (
                    s["speak_wrong"]
                    .replace("{target}", color_word[c])
                    .replace("{color}", color_word[a])
                )
    return clips


def render_edge(binary, name, text):
    dest = OUT / f"{name}.mp3"
    subprocess.run(
        [binary, "--voice", VOICE, f"--rate={RATE}", f"--pitch={PITCH}",
         "--text", text, "--write-media", str(dest)],
        check=True, capture_output=True,
    )
    return name


def render_openai(api_key, name, text):
    request = urllib.request.Request(
        "https://api.openai.com/v1/audio/speech",
        data=json.dumps({
            "model": OPENAI_MODEL,
            "voice": OPENAI_VOICE,
            "input": text,
            "instructions": OPENAI_INSTRUCTIONS,
            "response_format": "mp3",
        }).encode(),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(request) as response:
        (OUT / f"{name}.mp3").write_bytes(response.read())
    return name


def openai_api_key():
    key_file = pathlib.Path.home() / ".config/openai_api.key"
    if key_file.exists():
        key = key_file.read_text().strip()
        if key:
            return key
    return os.environ.get("OPENAI_API_KEY")


def main():
    api_key = openai_api_key()
    if api_key:
        print(f"rendering with OpenAI {OPENAI_MODEL} voice={OPENAI_VOICE}")
        render = lambda kv: render_openai(api_key, *kv)
    else:
        binary = edge_tts_bin()
        print("rendering with edge-tts (set OPENAI_API_KEY for the real cowboy voice)")
        render = lambda kv: render_edge(binary, *kv)
    OUT.mkdir(parents=True, exist_ok=True)
    clips = build_clips(load_strings())
    for stale in OUT.glob("*.mp3"):
        if stale.stem not in clips:
            stale.unlink()
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        for name in pool.map(render, clips.items()):
            print(f"rendered {name}")
    print(f"{len(clips)} clips in {OUT}")


if __name__ == "__main__":
    main()
