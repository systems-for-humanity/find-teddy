#!/usr/bin/env python3
"""Guided recorder: speak the game's voice prompts in your own voice.

Walks through every prompt line (same clip set as generate_voice_prompts.py),
records each from the microphone, trims silence, peak-normalizes, lets you
review, and saves the mp3 straight into composeResources/files/voice/ under
the correct clip name.

Controls per clip: Enter starts recording, Enter again stops it; the take
plays back and you keep/retry/skip. Already-recorded clips are skipped on a
re-run unless --force is given, so you can do the session in chunks or
re-record single lines (pass name filters, e.g. `win` or `wrong_red`).

Writes a marker file so the TTS generator skips (never overwrites) your
recordings when re-rendering (override with VOICE_OVERWRITE=1).

Usage:
  python3 tools/record_voice_prompts.py            # record whatever is not yours yet
  python3 tools/record_voice_prompts.py --test     # 3s mic check
  python3 tools/record_voice_prompts.py --list     # show all clip names + lines
  python3 tools/record_voice_prompts.py win next_  # only matching clips
  python3 tools/record_voice_prompts.py --force    # re-record even existing takes
"""

import argparse
import pathlib
import shutil
import signal
import subprocess
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import generate_voice_prompts as gen

OUT = gen.OUT
MARKER = OUT / "recorded_by_human.txt"
MARKER_HEADER = (
    "# clips listed below were recorded by a human.\n"
    "# generate_voice_prompts.py keeps them when re-rendering, unless\n"
    "# VOICE_OVERWRITE=1 is set (or this file is deleted).\n"
)
FFMPEG = shutil.which("ffmpeg") or sys.exit("ffmpeg not found")
FFPLAY = shutil.which("ffplay")


def recorded_names():
    if not MARKER.exists():
        return set()
    return {
        line.strip() for line in MARKER.read_text().splitlines()
        if line.strip() and not line.startswith("#")
    }


def mark_recorded(name):
    names = recorded_names() | {name}
    MARKER.write_text(MARKER_HEADER + "\n".join(sorted(names)) + "\n")


def record_until_enter(dest_wav):
    print("  ● recording — press Enter to stop", flush=True)
    proc = subprocess.Popen(
        [FFMPEG, "-y", "-v", "error", "-f", "pulse", "-i", "default",
         "-ac", "1", "-ar", "44100", str(dest_wav)],
        stdin=subprocess.DEVNULL,
    )
    input()
    proc.send_signal(signal.SIGINT)
    try:
        proc.wait(timeout=5)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait()
    return dest_wav.exists() and dest_wav.stat().st_size > 1000


def peak_gain_db(path):
    result = subprocess.run(
        [FFMPEG, "-i", str(path), "-af", "volumedetect", "-f", "null", "-"],
        capture_output=True, text=True,
    )
    for line in result.stderr.splitlines():
        if "max_volume:" in line:
            max_db = float(line.split("max_volume:")[1].replace("dB", "").strip())
            return -1.0 - max_db
    return 0.0


def process(raw_wav, clean_wav):
    """Trim leading/trailing silence, mono 44.1k, normalize peak to -1 dB."""
    trim = ("silenceremove=start_periods=1:start_threshold=-40dB,areverse,"
            "silenceremove=start_periods=1:start_threshold=-40dB,areverse")
    subprocess.run(
        [FFMPEG, "-y", "-v", "error", "-i", str(raw_wav),
         "-af", trim, "-ac", "1", "-ar", "44100", str(clean_wav)],
        check=True,
    )
    gain = peak_gain_db(clean_wav)
    normalized = clean_wav.with_suffix(".norm.wav")
    subprocess.run(
        [FFMPEG, "-y", "-v", "error", "-i", str(clean_wav),
         "-af", f"volume={gain:.1f}dB", "-sample_fmt", "s16", str(normalized)],
        check=True,
    )
    return normalized


def play(path):
    if FFPLAY:
        subprocess.run(
            [FFPLAY, "-nodisp", "-autoexit", "-v", "error", str(path)],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
    else:
        print("  (ffplay not found — cannot play back)")


def save_mp3(wav, name):
    # Same encode settings as generate_voice_prompts.postprocess_to_mp3 so
    # recorded and TTS clips match.
    subprocess.run(
        [FFMPEG, "-y", "-v", "error", "-i", str(wav),
         "-ac", "1", "-ar", "24000",
         "-codec:a", "libmp3lame", "-b:a", "48k", str(OUT / f"{name}.mp3")],
        check=True,
    )


def record_one(name, text, index, total, tmp):
    while True:
        print(f"\n[{index}/{total}] {name}")
        print(f'    say: "{text}"')
        answer = input("  Enter=record  s=skip  q=quit > ").strip().lower()
        if answer == "s":
            return "skipped"
        if answer == "q":
            return "quit"
        raw = tmp / f"{name}.raw.wav"
        if not record_until_enter(raw):
            print("  recording failed — is a microphone available?")
            continue
        try:
            take = process(raw, tmp / f"{name}.clean.wav")
        except subprocess.CalledProcessError:
            print("  processing failed (empty take?) — try again")
            continue
        play(take)
        while True:
            verdict = input("  k=keep  r=retry  p=play  s=skip  q=quit > ").strip().lower()
            if verdict == "p":
                play(take)
            elif verdict == "k":
                save_mp3(take, name)
                mark_recorded(name)
                return "kept"
            elif verdict == "r":
                break
            elif verdict == "s":
                return "skipped"
            elif verdict == "q":
                return "quit"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("filters", nargs="*", help="only clips whose name contains any of these")
    parser.add_argument("--force", action="store_true", help="re-record clips that already exist")
    parser.add_argument("--list", action="store_true", help="print all clip names and lines")
    parser.add_argument("--test", action="store_true", help="3-second mic check")
    args = parser.parse_args()

    clips = gen.build_clips(gen.load_strings())
    if args.list:
        for name, text in clips.items():
            print(f"{name:28} {text}")
        return

    if args.test:
        with tempfile.TemporaryDirectory() as tmp:
            raw = pathlib.Path(tmp) / "test.wav"
            print("Say something for ~3 seconds...")
            subprocess.run(
                [FFMPEG, "-y", "-v", "error", "-f", "pulse", "-i", "default",
                 "-t", "3", "-ac", "1", "-ar", "44100", str(raw)],
            )
            play(raw)
        return

    done = recorded_names()
    todo = {
        name: text for name, text in clips.items()
        if (not args.filters or any(f in name for f in args.filters))
        and (args.force or name not in done)
    }
    if not todo:
        print("nothing to record (use --force to re-record existing takes)")
        return

    OUT.mkdir(parents=True, exist_ok=True)
    kept = 0
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = pathlib.Path(tmpdir)
        for i, (name, text) in enumerate(sorted(todo.items()), 1):
            result = record_one(name, text, i, len(todo), tmp)
            if result == "kept":
                kept += 1
            elif result == "quit":
                break

    remaining = [n for n in clips if n not in recorded_names()]
    print(f"\nkept {kept} takes this session; {len(remaining)} clips not yet in your voice")
    if remaining:
        print("remaining:", ", ".join(remaining[:10]) + ("..." if len(remaining) > 10 else ""))
        print("(they keep the current TTS clips until you record them)")


if __name__ == "__main__":
    main()
