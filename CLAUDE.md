# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Warlock is a multi-platform front-end client for the Simutronics Game Engine (SGE) text-based MMORPG. Built with Kotlin Multiplatform and Jetpack Compose, targeting Desktop (JVM), Android, and iOS.

## Build & Development Commands

```bash
# Run the desktop app
./gradlew :desktopApp:run

# Build everything
./gradlew build

# What CI runs (Android Lint and iOS targets are skipped there)
./gradlew check -PiosSkip=true -PlintSkip=true

# Run all tests
./gradlew allTests

# Run JVM tests only
./gradlew jvmTest

# Run a single test class (JVM)
./gradlew jvmTest --tests "com.example.MyTest"

# Build the packaged desktop app locally (output under
# desktopApp/build/potassium/binaries/main/app/)
./gradlew :desktopApp:createDistributable
```

Compilation uses a Java 21 toolchain. The desktop app *runs* and is *packaged*
under JBR 25, configured via `javaHome` in `desktopApp/build.gradle.kts`, so
`:desktopApp:run` and the packaging tasks use a different JVM than the build.

## Module Architecture

The project is organized into 6 Gradle modules:

- **`core/`** — Multiplatform business logic, broken into sub-modules:
  - `client/` — Game client, networking, character data
  - `sge/` — SGE-specific protocol handling
  - `macro/` — ANTLR-based macro language parser
  - `prefs/` — Preferences & configuration (Room database)
  - `script/` — Script handling
  - `compass/`, `text/`, `window/`, `util/` — Supporting domain modules
- **`wrayth/`** — SGE network protocol parsing (ANTLR grammars: `WraythParser.g4`, `WraythLexer.g4`)
- **`scripting/`** — Scripting engine (ANTLR-based WSL plus Lua via lua-kmp, all platforms)
- **`compose/`** — Multiplatform Compose UI components (dashboard, game screen, settings, themes)
- **`desktopApp/`** — JVM desktop entry point (`Main.kt`), uses Jewel for native look & feel
- **`androidApp/`** — Android wrapper around the compose module

## Key Architectural Patterns

**ANTLR Parsing**: Macro, Wrayth protocol, and scripting all use ANTLR grammars (`.g4` files) that auto-generate Kotlin code during the build. Grammar files live alongside their respective modules.

**Room Database**: Preferences are persisted via Room with DAOs, repositories, mappers, and export functionality. Schema is managed via Room migrations.

**Multiplatform**: Shared code lives in `commonMain` source sets. Platform-specific implementations use `jvmMain`, `androidMain`, `iosMain` etc. The `compose/` module contains the bulk of shared UI.

**Dependency Versions**: All dependency versions are centralized in `gradle/libs.versions.toml`. Use version catalog references (e.g., `libs.ktor.client.core`) in build files.

## Distribution

Releases are cut by pushing a git tag; `.github/workflows/release.yaml` does the
rest. Use `./release.sh` rather than tagging by hand: it derives the next
version from the tag history (`-v` prints it without tagging, `-p` cuts a
production release, `-m` starts a new minor).

**The tag is the only source of versioning.** No version string is committed
anywhere. `release.yaml` passes the tag through as `RELEASE_VERSION`, and
`desktopApp/build.gradle.kts` routes a tag containing `beta`/`alpha` to the
matching update channel and marks the GitHub release a prerelease.

Packaging is handled by [Potassium](https://github.com/sproctor/potassium)
(`potassium { }` in `desktopApp/build.gradle.kts`), which produces NSIS for
Windows, DMG + Zip for macOS, and Deb/AppImage/Tar for Linux, with Azure Trusted
Signing on Windows and Developer ID signing + notarization on macOS. The
workflow builds a matrix of Linux amd64/arm64, Windows amd64, and macOS
arm64/intel, then publishes them as a GitHub release. An Android job builds an
AAB and uploads to the Play internal track, gated on the `PUBLISH_ANDROID` repo
variable.

**Verify the packaged app, not just CI.** `check` compiles and tests against
Gradle's resolved classpath, which is not the classpath the shipped app runs on:
the packaged `lib/` directory can contain jars Gradle deduplicated away. A
release has shipped that passed CI and crashed on launch for every user
(`v3.1.0-beta.21`, a duplicate `kotlinx-coroutines` from a transitive IntelliJ
dependency). Before tagging, run `:desktopApp:createDistributable` and run
`desktopApp/build/potassium/binaries/main/app/warlock/bin/warlock --version`
(or another flag that exits before a window opens) and check for exit code 0.
