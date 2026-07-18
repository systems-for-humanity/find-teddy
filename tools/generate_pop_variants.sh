#!/usr/bin/env bash
# Renders the six pitch variants of the balloon pop from art/pop_source.wav.
# pop_0 = highest pitch (smallest balloon) ... pop_5 = lowest (biggest).
# mp3 (not wav) to keep the bundled resources small.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=composeApp/src/commonMain/composeResources/files
factors=(1.35 1.20 1.06 0.95 0.84 0.75)
for i in "${!factors[@]}"; do
  f=${factors[$i]}
  rate=$(awk "BEGIN{printf \"%d\", 44100*$f}")
  ffmpeg -y -v error -i art/pop_source.wav \
    -af "asetrate=$rate,aresample=44100" -ac 1 \
    -codec:a libmp3lame -q:a 4 "$OUT/pop_$i.mp3"
done
echo "6 pop variants in $OUT"
