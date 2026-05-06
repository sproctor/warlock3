# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Warlock is a multi-platform front-end client for the Simutronics Game Engine (SGE) text-based MMORPG. Built with Kotlin Multiplatform and Jetpack Compose, targeting Desktop (JVM), Android, and iOS.

## Build & Development Commands

```bash
# Run the desktop app
./gradlew run

# Build everything
./gradlew build

# Run all tests
./gradlew allTests

# Run JVM tests only
./gradlew jvmTest

# Run a single test class (JVM)
./gradlew jvmTest --tests "com.example.MyTest"

# Full release pipeline
./release.sh
```

Requires Java 21 JVM toolchain.

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
- **`scripting/`** — Scripting engine (ANTLR + Rhino JS for JVM/Android)
- **`compose/`** — Multiplatform Compose UI components (dashboard, game screen, settings, themes)
- **`desktopApp/`** — JVM desktop entry point (`Main.kt`), uses Jewel for native look & feel
- **`androidApp/`** — Android wrapper around the compose module

## Key Architectural Patterns

**ANTLR Parsing**: Macro, Wrayth protocol, and scripting all use ANTLR grammars (`.g4` files) that auto-generate Kotlin code during the build. Grammar files live alongside their respective modules.

**Room Database**: Preferences are persisted via Room with DAOs, repositories, mappers, and export functionality. Schema is managed via Room migrations.

**Multiplatform**: Shared code lives in `commonMain` source sets. Platform-specific implementations use `jvmMain`, `androidMain`, `iosMain` etc. The `compose/` module contains the bulk of shared UI.

**Dependency Versions**: All dependency versions are centralized in `gradle/libs.versions.toml`. Use version catalog references (e.g., `libs.ktor.client.core`) in build files.

## Distribution

Desktop packages are built with [Conveyor](https://www.hydraulic.software/). The `conveyor.conf` file configures macOS DMG, Windows MSI, and Linux Deb packaging with code signing and auto-update via GitHub Pages.
