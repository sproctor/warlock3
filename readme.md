# Warlock

A multi-platform front-end client for [Simutronics](https://www.simutronics.com/) MUDs — DragonRealms, GemStone IV, and other Simutronics titles. Built with Kotlin Multiplatform and Jetpack Compose, with targets for Desktop (Windows, macOS, Linux), Android, and iOS.

For player-facing information, screenshots, and general usage, see the project site at [warlockfe.github.io](https://warlockfe.github.io/).

## Download

Pre-built desktop packages for Windows, macOS (Apple Silicon and Intel), and Linux (deb, AppImage) are available on the [GitHub Releases page](https://github.com/sproctor/warlock3/releases).

Installed copies update themselves automatically against published releases. The Android and iOS versions are still a WIP and haven't had any official releases.

## Contributing

Bug reports and pull requests are welcome.

- **Issues:** file bugs and feature requests at [github.com/sproctor/warlock3/issues](https://github.com/sproctor/warlock3/issues). Include the OS, the build version (Help → About), and steps to reproduce.
- **Pull requests:** branch from `develop`, keep changes focused, and run `./gradlew check` before pushing. ktlint runs as part of `check` — fix any formatting violations with `./gradlew ktlintFormat`.
- **Larger changes:** open an issue first to talk through the approach before writing the code.
- **License:** the project is Apache 2.0 (see [LICENSE](LICENSE)). By contributing, you agree your changes are licensed under the same terms.

## Development

### Requirements

- A JetBrains Runtime (JBR) 21+ JDK. The Gradle toolchain is configured to download one automatically on first build; you don't normally need to install one yourself.
- Git.
- For Android development: Android Studio (or the standalone Android SDK).
- For iOS development: macOS with Xcode.

### Running the desktop app

```
./gradlew :desktopApp:run
```

(Plain `./gradlew run` works too — it resolves to the same task.)

For a faster edit-recompile loop with Compose Hot Reload:

```
./gradlew :desktopApp:hotRun
```

Useful command-line flags exposed by the desktop entry point — pass them after `--args="..."`:

- `-c <name>` — auto-connect to a saved connection by name
- `-i <path>` / `--stdin` — replay a log file or stdin instead of connecting to a server
- `-d` — enable debug logging
- `--sge-host`, `--sge-port`, `--sge-secure` — point at an alternate SGE endpoint

Example:

```
./gradlew :desktopApp:run --args="-c Tefrin -d"
```

### Running tests and lint

```
./gradlew check          # tests + ktlint
./gradlew allTests       # multiplatform tests
./gradlew jvmTest        # JVM tests only
./gradlew ktlintFormat   # auto-fix ktlint violations
```

### Android

Open the project in Android Studio and run the `androidApp` configuration, or from the CLI:

```
./gradlew :androidApp:installDebug
```

## Building packages locally

The desktop packaging tasks produce installers for the current OS using `jpackage` via the [Nucleus](https://github.com/kdroidfilter/nucleus) Gradle plugin.

### Native installer for your current OS

```
./gradlew :desktopApp:packageDistributionForCurrentOS
```

Outputs land in `desktopApp/build/compose/binaries/main/`. Format depends on the host:

- **Linux:** `.deb`, AppImage, `.tar` under `binaries/main/`
- **macOS:** `.dmg` under `binaries/main/dmg/`
- **Windows:** `.msi` and NSIS `.exe` installer under `binaries/main/`

For a release build with ProGuard applied, use `packageReleaseDistributionForCurrentOS`.

### Just the app image (no installer)

Useful for quick smoke tests of the packaged app without building an installer:

```
./gradlew :desktopApp:createDistributable
./gradlew :desktopApp:runDistributable     # build + launch
```

### Individual installer formats

```
./gradlew :desktopApp:packageDeb       # Linux .deb
./gradlew :desktopApp:packageDmg       # macOS .dmg (macOS host only)
./gradlew :desktopApp:packageMsi       # Windows .msi (Windows host only)
./gradlew :desktopApp:packageNsis      # Windows NSIS installer
```

Note: each installer format can only be built on its native host OS. The CI release workflow runs the matrix across all three platforms.

### Configuring the version

By default the build uses the version declared in `gradle.properties`. To override (e.g. to mirror what CI does for tagged releases), set `RELEASE_VERSION`:

```
RELEASE_VERSION=v3.0.167-beta.1 ./gradlew :desktopApp:packageDistributionForCurrentOS
```

The leading `v` is stripped automatically. The base version (without any `-beta.N` suffix) is used for Windows `.exe` `VERSIONINFO` and macOS `CFBundleVersion`, which both require dotted-integer format; the full version is preserved in the artifact filename and on Linux packages.

### Cutting a release

Releases are driven by tag pushes — the `release.yaml` workflow builds and publishes the matrix:

```
./release.sh 3.0.167          # or e.g. 3.0.167-beta.1
```

This tags the current commit and pushes it; CI handles the rest.

## Project structure

Top-level modules:

- `core/` — multiplatform business logic (client, networking, macro/scripting, preferences)
- `wrayth/` — SGE protocol parser (ANTLR)
- `scripting/` — scripting engine (ANTLR + Rhino on JVM/Android)
- `compose/` — shared Compose UI (desktop screens use Jewel; mobile screens use Material3)
- `desktopApp/` — JVM desktop entry point
- `androidApp/` — Android wrapper

See [CLAUDE.md](CLAUDE.md) for a longer architectural tour.

## License

Apache License 2.0. See [LICENSE](LICENSE) for the full text.
