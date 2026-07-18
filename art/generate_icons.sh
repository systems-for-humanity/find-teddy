#!/usr/bin/env bash
# Regenerates all launcher icons from the SVG sources in this directory.
# Requires inkscape and imagemagick.
set -euo pipefail
cd "$(dirname "$0")"

RES=../composeApp/src/androidMain/res
IOS=../iosApp/iosApp/Assets.xcassets/AppIcon.appiconset
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

inkscape -w 1024 -h 1024 icon_bg.svg -o "$TMP/bg.png" 2>/dev/null
inkscape -w 1024 -h 1024 icon_fg.svg -o "$TMP/fg.png" 2>/dev/null
magick "$TMP/bg.png" "$TMP/fg.png" -composite "$TMP/square.png"

# legacy shapes for API 24-25 launchers (they show the PNG as-is)
magick -size 1024x1024 xc:none -draw "roundrectangle 0,0,1023,1023,190,190" "$TMP/mask_rr.png"
magick -size 1024x1024 xc:none -draw "circle 511.5,511.5 511.5,6" "$TMP/mask_circle.png"
magick "$TMP/square.png" "$TMP/mask_rr.png" -alpha off -compose CopyOpacity -composite "$TMP/legacy.png"
magick "$TMP/square.png" "$TMP/mask_circle.png" -alpha off -compose CopyOpacity -composite "$TMP/legacy_round.png"

densities=(mdpi hdpi xhdpi xxhdpi xxxhdpi)
adaptive_px=(108 162 216 324 432)
legacy_px=(48 72 96 144 192)

for i in "${!densities[@]}"; do
  d=${densities[$i]}
  mkdir -p "$RES/mipmap-$d"
  a=${adaptive_px[$i]}
  l=${legacy_px[$i]}
  inkscape -w "$a" -h "$a" icon_fg.svg -o "$RES/mipmap-$d/ic_launcher_foreground.png" 2>/dev/null
  inkscape -w "$a" -h "$a" icon_bg.svg -o "$RES/mipmap-$d/ic_launcher_background.png" 2>/dev/null
  magick "$TMP/legacy.png" -resize "${l}x${l}" "$RES/mipmap-$d/ic_launcher.png"
  magick "$TMP/legacy_round.png" -resize "${l}x${l}" "$RES/mipmap-$d/ic_launcher_round.png"
done

# iOS marketing icon must have no alpha channel
mkdir -p "$IOS"
magick "$TMP/square.png" -alpha remove -alpha off "$IOS/AppIcon.png"

echo "icons regenerated"
