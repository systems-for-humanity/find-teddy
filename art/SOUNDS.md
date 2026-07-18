# sound credits

all samples are CC0 (public domain) from OpenGameArt.org, processed with
ffmpeg (mono, trimmed, peak-normalized to -1 dB) and shipped as mp3 in
`composeApp/src/commonMain/composeResources/files/` to keep the app small.
the untouched wav masters live in `art/` so the mp3s can be re-rendered.

- `pop_0.mp3` … `pop_5.mp3` — balloon pop from "Balloon Sounds" by
  AntumDeluge (pop recorded by Gniffelbaf):
  https://opengameart.org/content/balloon-sounds
  master kept as `art/pop_source.wav`; the six pitch variants
  (high = small balloon, low = big) are rendered by
  `tools/generate_pop_variants.sh`
- `boom.mp3` — first 1.8 s of `dull_explosion.wav` from "Various Sound
  Effects" by Spring Spring (master: `art/boom_source.wav`):
  https://opengameart.org/content/various-sound-effects-0
- `boom_big.mp3` — same source, pitched down (asetrate 29988/44100) for the
  bigger "determined partner" explosion (master: `art/boom_big_source.wav`)
