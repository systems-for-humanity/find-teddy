# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## project

"Find Teddy!" — a KMP (Compose Multiplatform) children's game for android and ios. balls with 2D physics bury a teddy bear; the game speaks a color, the player pops balls of that color and wins by touching the uncovered teddy. shaking the device mixes the balls.

## commands

```sh
./gradlew :composeApp:assembleDebug          # android APK -> composeApp/build/outputs/apk/debug/
./gradlew :composeApp:installDebug           # install on connected device/emulator
./gradlew :composeApp:testDebugUnitTest      # run commonTest unit tests on JVM
./gradlew :composeApp:testDebugUnitTest --tests "*.GameControllerTest.ballsSettleInsideWalls"  # single test
./gradlew :composeApp:compileKotlinIosArm64  # type-check ios code (works on linux, see below)
./art/generate_icons.sh                      # regenerate all launcher icons from art/*.svg (needs inkscape + imagemagick)
python3 tools/generate_voice_prompts.py      # re-render voice clips via TTS (openai key file or edge-tts)
python3 tools/record_voice_prompts.py        # record voice clips with your own mic (interactive; --list, --test)
```

- gradle is not installed globally on this machine — always use `./gradlew`
- ios klibs cross-compile on linux (`kotlin.native.enableKlibsCrossCompilation=true` in gradle.properties), so ios-specific kotlin compiles and type-checks here; linking/running the ios app requires macOS + Xcode (open `iosApp/iosApp.xcodeproj`; a build phase runs `:composeApp:embedAndSignAppleFrameworkForXcode`)

## on-device verification

an AVD named `Pixel_6` exists. headless workflow used to verify gameplay:

```sh
~/Android/Sdk/emulator/emulator -avd Pixel_6 -no-window -no-audio -gpu swiftshader_indirect -no-snapshot &
~/Android/Sdk/platform-tools/adb exec-out screencap -p > shot.png   # inspect visually
~/Android/Sdk/platform-tools/adb emu sensor set acceleration 30:40:30   # trigger shake (reset to 0:9.8:0)
~/Android/Sdk/platform-tools/adb emu kill
```

the emulator image has no TTS voice data, so speech is silent there — test speech on a real device.

## architecture

everything game-related lives in `composeApp/src/commonMain`; the platform source sets only contain entry points and the `expect`/`actual` pairs: `ShakeListener` (composable), `SpeechSynthesizer`, `HapticFeedback`, `VoicePlayer` (voice clips; `awaitReady()` gates game start because SoundPool decodes asynchronously), and `GameSoundPlayer` (constructed from mp3 bytes; SoundPool on android, AVAudioPlayer pools on ios).

- `game/GameController.kt` is the heart: physics simulation + all game rules in one plain class. it exposes compose state (`frameTick`, `targetColor`, `message`); the ui drives it via `update(dt)` from a `withFrameNanos` loop in GameScreen.kt, and the canvas draw block reads `frameTick` so every physics step invalidates the draw. balls are plain mutable objects, not compose state.
- physics works in raw canvas pixels; every size (ball radii, gravity, teddy proportions) derives from screen width/height so it scales to any device. impulse collisions + 4 positional-correction iterations, then a final wall clamp pass — the clamp must run last or piled balls get pushed through the floor (there is a regression test for this).
- all side effects are injected into GameController as lambdas (`speak`, `onPop`, `onExplode(big)`), never platform classes — this keeps game logic constructible in plain JVM unit tests (`commonTest`)
- sounds are CC0 samples shipped as mp3 in `composeApp/src/commonMain/composeResources/files/` (provenance + wav masters in art/, see art/SOUNDS.md), loaded asynchronously with `Res.readBytes` in GameScreen; `GameSoundPlayer` is created only after the bytes arrive, so effect lambdas null-check it. voice clips are 24 kHz 48 kbps mono mp3 — the recorder and generator share those encode settings
- voice prompts are pre-rendered mp3 clips in `composeResources/files/voice/` — one clip per prompt × color combination plus color-free singles (45 total). three TTS backends, best first: maya1 (open 3B model on the `moffice` mac mini via ollama, driven over ssh by `tools/maya1_render.py` — real western drawl from a voice-design description), openai gpt-4o-mini-tts (key in `~/.config/openai_api.key`), edge-tts (free, no drawl). the backend that rendered the current clips is remembered in `files/voice/rendered_by.txt` and re-renders stick to it (unreachable maya host → keep existing clips, don't re-voice); force a switch with `VOICE_TTS=maya|openai|edge`. the "determined partner" praise is one color-free clip; GameController queues the `next_<color>` prompt to play `DETERMINED_PRAISE_SECONDS` after it (any newer speech cancels the queued line — keep that constant ≥ the praise clip's length or the prompt cuts it off). GameController speaks semantic `VoiceLine` values; the UI resolves them to a clip (`VoiceLine.clipFile()`) or to localized TTS text (`VoiceLine.text()`) when the locale has no clips (`voice_prerendered` string resource is the switch). the gradle task `:composeApp:generateVoicePrompts` re-renders when strings.xml changes (clips are committed, so normal builds are UP-TO-DATE offline; use `-x generateVoicePrompts` if strings changed while offline). clip names in `tools/generate_voice_prompts.py` and `VoiceLine.clipFile()` must stay in sync. clips can instead be recorded with a human voice via `tools/record_voice_prompts.py`; that writes `files/voice/recorded_by_human.txt` (a list of human-recorded clip names); the TTS generator skips those clips when re-rendering unless `VOICE_OVERWRITE=1`. missing clips are tolerated at runtime (per-line TTS fallback)
- i18n: all user-facing text lives in `composeResources/values/strings.xml` (add translations as `values-<lang>/strings.xml`; a translated locale should also set `voice_prerendered` to false unless clips are rendered for it). composables use `stringResource`; GameController takes a preloaded `GameStrings` bundle (built via suspend `getString` in GamePlayField before the controller is created) whose patterns use `{color}`/`{target}` placeholders replaced at speak time. tests build `GameStrings` inline. the android launcher label is separate (`androidMain/res/values*/strings.xml`); TTS follows the device locale. test a locale on-device with `adb shell cmd locale set-app-locales com.messytable.findteddy --locales es`
- explosions: popping the last ball of a color calls `explode(ball, scale=1)` — a fragment burst plus a shockwave with linear falloff whose total kick is tuned to feel almost as strong as a shake, radiating from the ball. the "determined partner" rule (tapping the same wrong ball repeatedly) uses scale 2 and a deeper boom; its required streak escalates 3, 4, 6, 7, ... (alternating +1/+2) and resets each round
- the teddy deliberately has no physics colliders: balls settle over him (he draws first) which is what buries him. the win check `isUncoveredAt` requires a real opening (no ball within 1.35× its radius of the tap), otherwise taps through pinhole gaps between touching balls would win while he is still buried
- ball rendering fakes 3D per frame: radial-gradient base → clipped pattern (per BallType) → dark rim gradient → specular highlight (`ui/BallDrawing.kt`)
- taps are handled on pointer-down (not tap-up) via `awaitPointerEventScope`, each pointer separately, so multi-finger mashing works
- insets: the play field is bottom-inset via `WindowInsets.safeDrawing` (GamePlayField in GameScreen.kt) while the background gradient stays edge-to-edge. the app also runs immersive — MainActivity hides the android nav bar (swipe reveals it transiently) and ContentView.swift hides the ios home indicator — so the bottom inset is usually zero, but keep the inset split: it still handles display cutouts and the transiently revealed bars

## platform gotchas

- deleting a file from `composeResources/files/` does not remove it from the packaged app — the resource copy task never deletes stale outputs, so the dead file ships in every APK until a `./gradlew :composeApp:clean`. after removing or renaming resource files, clean once and check with `unzip -l` on the APK

- android `SpeechSynthesizer` reads the global `appContext` (set in MainActivity.onCreate before `setContent`) — constructing it earlier crashes
- ios `CMAccelerometerData.acceleration` is a cinterop CValue — read fields inside `useContents { }`
- ns_enum constants like `AVSpeechBoundaryImmediate` are nested in their kotlin enum class (`AVSpeechBoundary.AVSpeechBoundaryImmediate`), not top-level
