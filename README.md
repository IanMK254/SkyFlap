# Sky Flap

An addictive, Flappy Bird style arcade game for Android. 100% native Kotlin,
zero image or audio assets: every sprite is drawn with Canvas and every sound
effect is synthesized as raw PCM at startup.

## Features

Core loop: tap to flap, thread the pipes, one mistake ends the run.

Addiction layer:
- Difficulty ramp: pipes speed up and the gap narrows as your score climbs; past 30 points some pipes slowly oscillate up and down
- Near miss bonus: skim a pipe edge and score +2 with a "CLOSE ONE!" flash, rewarding risky play
- Medals: bronze (10), silver (20), gold (30), platinum (40)
- Persistent best score, games played and average, with a "NEW BEST!" fanfare and confetti the moment you beat your record mid-run
- Day/night cycle: the sky blends to night (with twinkling stars) and back every 20 points
- Juice: screen shake and white flash on death, score pop animation, feather and sparkle particles, parallax clouds, hills and scrolling ground
- Synthesized sound effects: flap swoosh, score ding, near miss sparkle, hit thud, new best arpeggio

## Project structure

```
app/src/main/java/com/skyflap/game/
  MainActivity.kt     Fullscreen immersive activity, lifecycle handling
  GameView.kt         SurfaceView plus game loop thread (clamped delta time)
  GameEngine.kt       State machine (MENU/READY/PLAYING/DYING/GAME_OVER), HUD, juice
  Bird.kt             Player physics, rotation, wing animation, Canvas rendering
  PipeManager.kt      Pipe spawning, pooling, difficulty ramp, collision, scoring
  Background.kt       Parallax sky, clouds, hills, ground, day/night palette
  ParticleSystem.kt   Pooled particles: feathers, sparkles, confetti
  SoundManager.kt     Procedural PCM sound effects via AudioTrack
  ScoreStore.kt       SharedPreferences persistence (best, games, total)
```

## Build and run

1. Open the `SkyFlap` folder in Android Studio (Hedgehog or newer). Gradle sync
   downloads everything automatically.
2. Press Run on a device or emulator (Android 8.0, API 26, or newer).

Command line alternative (requires Android SDK):

```
gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

Note: run it from Android Studio the first time so the IDE generates the
Gradle wrapper scripts, or run `gradle wrapper` if you have Gradle installed.

## Tuning the feel

All physics constants live in `Bird.onResize` (gravity, flap impulse) and
`PipeManager` (`speed`, `gapSize`, `spawnInterval`) as fractions of screen
height, so the game plays identically on any device. Tweak those few numbers
to make it easier or crueler.

## Getting an installable APK

Option A, no tools needed (GitHub cloud build):

1. Create a repository on github.com and upload this folder (or `git push` it).
2. The included workflow (`.github/workflows/build-apk.yml`) runs automatically.
3. Open the repo's Actions tab, click the finished "Build APK" run, and
   download the `SkyFlap-APK` artifact. Inside is `app-debug.apk`.

Option B, Android Studio:

1. Open the project, then Build > Build App Bundle(s) / APK(s) > Build APK(s).
2. The APK appears in `app/build/outputs/apk/debug/`.

Installing on your phone:

1. Copy `app-debug.apk` to the phone (USB, Google Drive, or email it to yourself).
2. Tap the file. When prompted, allow "Install unknown apps" for that source.
3. Install and play. The debug APK is self signed and installs on any
   Android 8.0+ device.
