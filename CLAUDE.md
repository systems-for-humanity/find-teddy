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

everything game-related lives in `composeApp/src/commonMain`; the platform source sets only contain entry points and two `expect`/`actual` pairs (`ShakeListener` composable, `SpeechSynthesizer` class).

- `game/GameController.kt` is the heart: physics simulation + all game rules in one plain class. it exposes compose state (`frameTick`, `targetColor`, `message`); the ui drives it via `update(dt)` from a `withFrameNanos` loop in GameScreen.kt, and the canvas draw block reads `frameTick` so every physics step invalidates the draw. balls are plain mutable objects, not compose state.
- physics works in raw canvas pixels; every size (ball radii, gravity, teddy proportions) derives from screen width/height so it scales to any device. impulse collisions + 4 positional-correction iterations, then a final wall clamp pass — the clamp must run last or piled balls get pushed through the floor (there is a regression test for this).
- speech is injected into GameController as a `(String) -> Unit` lambda, never the platform class — this keeps game logic constructible in plain JVM unit tests (`commonTest`)
- the teddy deliberately has no physics colliders: balls settle over him (he draws first) which is what buries him. the win check `isUncoveredAt` requires a real opening (no ball within 1.35× its radius of the tap), otherwise taps through pinhole gaps between touching balls would win while he is still buried
- ball rendering fakes 3D per frame: radial-gradient base → clipped pattern (per BallType) → dark rim gradient → specular highlight (`ui/BallDrawing.kt`)
- taps are handled on pointer-down (not tap-up) via `awaitPointerEventScope`, each pointer separately, so multi-finger mashing works
- insets: the play field is bottom-inset via `WindowInsets.safeDrawing` (GamePlayField in GameScreen.kt) so the floor sits above the android nav bar / ios home indicator, while the background gradient stays edge-to-edge. keep this split when touching GameScreen layout

## platform gotchas

- android `SpeechSynthesizer` reads the global `appContext` (set in MainActivity.onCreate before `setContent`) — constructing it earlier crashes
- ios `CMAccelerometerData.acceleration` is a cinterop CValue — read fields inside `useContents { }`
- ns_enum constants like `AVSpeechBoundaryImmediate` are nested in their kotlin enum class (`AVSpeechBoundary.AVSpeechBoundaryImmediate`), not top-level
