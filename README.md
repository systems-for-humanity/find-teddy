# Find Teddy!

A Kotlin Multiplatform children's game for Android and iOS, built with
Compose Multiplatform. The screen fills with glossy 3D-shaded balls of
different colors and patterns (plain, striped, dotted, star) that stack on
top of each other with real physics. A teddy bear is buried underneath.

- The game **speaks a color** ("Touch the red balls!") and shows it in a banner.
- Touching a ball of that color pops it; touching a wrong ball makes it
  wiggle and the game tells you its actual color.
- **Shaking the phone** tosses and mixes the balls.
- When all balls of the target color are gone, a new color is announced.
- Dig down to the teddy bear and touch it to **win** — confetti and cheers!

## Structure

- `composeApp/src/commonMain` — all game code: physics, ball/teddy rendering,
  game logic, screens (shared between platforms)
- `composeApp/src/androidMain` — Android entry point, accelerometer shake
  detection (`SensorManager`), speech (`TextToSpeech`)
- `composeApp/src/iosMain` — iOS Compose entry point, shake detection
  (`CoreMotion`), speech (`AVSpeechSynthesizer`)
- `iosApp/` — Xcode project wrapping the shared code in a SwiftUI app

## Build & run

### Android

```sh
./gradlew :composeApp:assembleDebug
# install on a connected device/emulator:
./gradlew :composeApp:installDebug
```

APK output: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

### iOS (requires macOS + Xcode)

Open `iosApp/iosApp.xcodeproj` in Xcode, select a simulator or device, and
run. The build phase script compiles the shared Kotlin framework via
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` automatically.
For a physical device, set your development team in Signing & Capabilities.

Note: the shake gesture needs a real device (or use the iOS Simulator's
Device > Shake menu; the simulator has no accelerometer, so use that menu —
Android emulators can send accelerometer events from the extended controls).
