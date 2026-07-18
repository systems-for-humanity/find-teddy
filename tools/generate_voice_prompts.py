#!/usr/bin/env python3
"""Pre-renders the game's spoken prompts to mp3 clips.

Reads the English prompt patterns from the compose-resources strings.xml,
expands the {color}/{target} placeholders over all ball colors, and renders
one clip per line.

Voice direction: an enthusiastic cowboy teddy bear.

Backends, best first:
- maya: Maya1 (open-weights 3B TTS, Apache-2.0) served by ollama on the host
  named by MAYA_HOST (default moffice), driven over ssh via maya1_render.py.
  Designs the cowboy voice from a text description — real western drawl,
  free, local. See maya1_render.py for the one-time host setup.
- openai: gpt-4o-mini-tts (key from ~/.config/openai_api.key or
  OPENAI_API_KEY), voice instructions do a decent drawl. Costs cents.
- edge: edge-tts (free Microsoft neural voices), no drawl — the text alone
  carries the cowboy flavor. Requires: pipx install edge-tts

files/voice/render_manifest.json remembers which backend rendered the
current clips and a per-clip signature of (backend, voice config, text):
re-renders stick to the same backend (so a strings.xml tweak can't silently
change the game's voice — force a switch with VOICE_TTS=maya|openai|edge)
and only clips whose signature changed are re-rendered. Clips listed in
files/voice/recorded_by_human.txt are personal recordings and are never
overwritten. VOICE_OVERWRITE=1 re-renders everything, human clips included.

Run directly or via the :composeApp:generateVoicePrompts gradle task
(which skips it while strings.xml is unchanged).
"""

import concurrent.futures
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET

# maya (ollama) settings
MAYA_HOST = os.environ.get("MAYA_HOST", "moffice")
MAYA_VOICE = (
    "Enthusiastic cowboy teddy bear character, warm friendly male voice "
    "with a strong American southern western drawl. Slightly high pitch, "
    "bouncy excited energy, speaking slowly and clearly to a toddler."
)

# openai settings
OPENAI_MODEL = "gpt-4o-mini-tts"
OPENAI_VOICE = "ash"  # energetic male; try "verse" or "ballad" too
OPENAI_INSTRUCTIONS = (
    "You are an enthusiastic cowboy teddy bear talking to a young child. "
    "Big warm western drawl, bouncy and always excited, like a rodeo "
    "announcer who is also a cuddly toy. Slow enough for a toddler to follow."
)

# edge-tts settings
VOICE = "en-US-GuyNeural"
RATE = "+12%"
PITCH = "+20Hz"

ROOT = pathlib.Path(__file__).resolve().parent.parent
STRINGS = ROOT / "composeApp/src/commonMain/composeResources/values/strings.xml"
OUT = ROOT / "composeApp/src/commonMain/composeResources/files/voice"
MANIFEST = OUT / "render_manifest.json"
COLORS = ["red", "blue", "green", "yellow", "purple", "orange"]


def load_manifest():
    if MANIFEST.exists():
        return json.loads(MANIFEST.read_text())
    return {}


def voice_config(backend):
    """Everything besides the text that shapes a clip's sound."""
    return {
        "maya": f"{MAYA_VOICE}",
        "openai": f"{OPENAI_MODEL}|{OPENAI_VOICE}|{OPENAI_INSTRUCTIONS}",
        "edge": f"{VOICE}|{RATE}|{PITCH}",
    }.get(backend, backend)


def clip_signature(backend, text):
    payload = f"{backend}|{voice_config(backend)}|{text}"
    return hashlib.sha1(payload.encode()).hexdigest()


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
        # the praise stands alone; the game plays next_<color> right after it
        "determined": s["speak_determined"],
    }
    for c in COLORS:
        clips[f"first_{c}"] = s["speak_first_prompt"].replace("{color}", color_word[c])
        clips[f"next_{c}"] = s["speak_next_prompt"].replace("{color}", color_word[c])
        for a in COLORS:
            if a != c:
                clips[f"wrong_{c}_{a}"] = (
                    s["speak_wrong"]
                    .replace("{target}", color_word[c])
                    .replace("{color}", color_word[a])
                )
    return clips


def human_recorded():
    marker = OUT / "recorded_by_human.txt"
    if not marker.exists() or os.environ.get("VOICE_OVERWRITE"):
        return set()
    return {
        line.strip() for line in marker.read_text().splitlines()
        if line.strip() and not line.startswith("#")
    }


def cap_pauses(wav_in, wav_out, max_pause=0.30, trigger=0.35, block=0.01):
    """Shorten mid-line silences longer than `trigger` s to `max_pause` s.

    TTS pauses can run past half a second, which makes the lines feel
    staccato. Pure python (16-bit mono wav) because ffmpeg's silenceremove
    cannot cap internal silences reliably.
    """
    import array
    import wave

    with wave.open(str(wav_in), "rb") as reader:
        params = reader.getparams()
        samples = array.array("h", reader.readframes(params.nframes))
    n = int(params.framerate * block)
    peaks = [max(map(abs, samples[i:i + n]), default=0)
             for i in range(0, len(samples), n)]
    threshold = max(peaks) * 0.02  # ~ -34 dB relative to the clip's peak
    quiet = [p <= threshold for p in peaks]

    kept = array.array("h")
    i = 0
    while i < len(peaks):
        if not quiet[i]:
            kept.extend(samples[i * n:(i + 1) * n])
            i += 1
            continue
        run = i
        while run < len(peaks) and quiet[run]:
            run += 1
        length = run - i
        # Leading/trailing silence is the edge trim's job; only cap runs
        # strictly inside the clip.
        if i > 0 and run < len(peaks) and length * block > trigger:
            length = int(max_pause / block)
        kept.extend(samples[i * n:(i + length) * n])
        i = run

    with wave.open(str(wav_out), "wb") as writer:
        writer.setnchannels(params.nchannels)
        writer.setsampwidth(params.sampwidth)
        writer.setframerate(params.framerate)
        writer.writeframes(kept.tobytes())


def postprocess_to_mp3(wav, name):
    """Trim edge silence, peak-normalize to -1 dB, encode into OUT.

    24 kHz mono 48 kbps: maya renders 24 kHz and speech doesn't need more —
    keeps the 45 bundled clips small. record_voice_prompts.py must encode
    with the same settings.
    """
    capped = wav.with_suffix(".capped.wav")
    cap_pauses(wav, capped)
    wav = capped
    trim = (
        "silenceremove=start_periods=1:start_threshold=-40dB,areverse,"
        "silenceremove=start_periods=1:start_threshold=-40dB,areverse,"
        # soften the hard-trimmed edges with a short lead-in/tail
        "adelay=60,apad=pad_dur=0.15"
    )
    probe = subprocess.run(
        ["ffmpeg", "-i", str(wav), "-af", f"{trim},volumedetect", "-f", "null", "-"],
        capture_output=True, text=True,
    )
    gain = 0.0
    for line in probe.stderr.splitlines():
        if "max_volume:" in line:
            gain = -1.0 - float(line.split("max_volume:")[1].replace("dB", "").strip())
    subprocess.run(
        ["ffmpeg", "-y", "-v", "error", "-i", str(wav),
         "-af", f"{trim},volume={gain:.1f}dB", "-ac", "1", "-ar", "24000",
         "-codec:a", "libmp3lame", "-b:a", "48k", str(OUT / f"{name}.mp3")],
        check=True,
    )


def maya_available():
    probe = subprocess.run(
        ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=5", MAYA_HOST,
         "PATH=$PATH:/opt/homebrew/bin:/usr/local/bin ollama list 2>/dev/null | grep -qi maya1"],
        capture_output=True,
    )
    return probe.returncode == 0


def render_maya(clips):
    """Batch-render on the ollama host; wavs come back over scp."""
    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg is required to post-process maya clips")
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = pathlib.Path(tmpdir)
        (tmp / "maya_job.json").write_text(json.dumps({
            "description": MAYA_VOICE,
            "out_dir": "/tmp/maya_out",
            "seed_base": 7,
            "clips": clips,
        }))
        script = pathlib.Path(__file__).resolve().parent / "maya1_render.py"
        subprocess.run(["ssh", MAYA_HOST, "rm -rf /tmp/maya_out"], check=True)
        subprocess.run(
            ["scp", "-q", str(script), str(tmp / "maya_job.json"), f"{MAYA_HOST}:/tmp/"],
            check=True,
        )
        subprocess.run(
            ["ssh", MAYA_HOST,
             "~/maya-tts/venv/bin/python /tmp/maya1_render.py /tmp/maya_job.json"],
            check=True,
        )
        subprocess.run(["scp", "-q", f"{MAYA_HOST}:/tmp/maya_out/*.wav", tmpdir], check=True)
        failed = []
        for name in clips:
            wav = tmp / f"{name}.wav"
            if wav.exists():
                postprocess_to_mp3(wav, name)
                print(f"rendered {name}")
            else:
                failed.append(name)
        if failed:
            sys.exit(f"maya failed to render: {', '.join(failed)}")


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


def choose_backend():
    forced = os.environ.get("VOICE_TTS")
    if forced:
        return forced
    previous = load_manifest().get("backend")
    if previous == "maya":
        if maya_available():
            return "maya"
        # Don't silently re-voice the whole game with another engine (and
        # don't fail the build): keep the committed clips.
        print(f"voice clips were rendered with maya but {MAYA_HOST} is "
              "unreachable — keeping existing clips (VOICE_TTS=openai|edge to switch)")
        return None
    if previous == "openai" and openai_api_key():
        return "openai"
    if maya_available():
        return "maya"
    if openai_api_key():
        return "openai"
    return "edge"


def main():
    backend = choose_backend()
    if backend is None:
        return
    clips = build_clips(load_strings())
    human = human_recorded()
    force = bool(os.environ.get("VOICE_OVERWRITE"))
    previous = load_manifest().get("clips", {})
    todo = {
        n: t for n, t in clips.items()
        if n not in human
        and (force or previous.get(n) != clip_signature(backend, t)
             or not (OUT / f"{n}.mp3").exists())
    }
    for name in sorted(human):
        print(f"keeping human recording: {name}")
    OUT.mkdir(parents=True, exist_ok=True)
    for stale in OUT.glob("*.mp3"):
        if stale.stem not in clips:
            stale.unlink()
    if not todo:
        print(f"all {len(clips)} clips up to date ({backend})")
        return
    if backend == "maya":
        print(f"rendering with maya1 (ollama on {MAYA_HOST})")
        render_maya(todo)
    elif backend == "openai":
        api_key = openai_api_key()
        if not api_key:
            sys.exit("VOICE_TTS=openai but no api key found")
        print(f"rendering with OpenAI {OPENAI_MODEL} voice={OPENAI_VOICE}")
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            for name in pool.map(lambda kv: render_openai(api_key, *kv), todo.items()):
                print(f"rendered {name}")
    elif backend == "edge":
        binary = edge_tts_bin()
        print("rendering with edge-tts (no drawl — maya/openai do the real cowboy voice)")
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            for name in pool.map(lambda kv: render_edge(binary, *kv), todo.items()):
                print(f"rendered {name}")
    else:
        sys.exit(f"unknown backend {backend!r} (use maya, openai or edge)")
    MANIFEST.write_text(json.dumps({
        "backend": backend,
        "clips": {
            n: clip_signature(backend, t)
            for n, t in clips.items() if n not in human
        },
    }, indent=1, sort_keys=True) + "\n")
    print(f"{len(todo)} clips rendered by {backend}, "
          f"{len(human)} human recordings kept, in {OUT}")


if __name__ == "__main__":
    main()
