# Bundled SQLite native for macOS x86_64

`osx_x64/libsqliteJni.dylib` is the macOS Intel (x86_64) JNI binary for
`androidx.sqlite:sqlite-bundled`.

## Why this file is checked in

`sqlite-bundled` **2.6.2** shipped natives for `linux_arm64`, `linux_x64`,
`osx_arm64`, `osx_x64`, and `windows_x64`. Room 3.0 requires `sqlite-bundled`
**2.7.0**, and 2.7.0 **dropped the `osx_x64` native** — it ships only
`linux_arm64`, `linux_x64`, `osx_arm64`, and `windows_x64`. With no Intel-Mac
binary in the jar, `BundledSQLiteDriver` fails at startup on Intel Macs with:

> IllegalStateException: Cannot find a suitable SQLite binary for mac os x | x86_64

There is no newer `sqlite-bundled` release that restores it (2.7.0 is latest).

## Provenance

This dylib is the `natives/osx_x64/libsqliteJni.dylib` entry copied verbatim from
`androidx.sqlite:sqlite-bundled-jvm:2.6.2`. It is safe to pair with the 2.7.0
Java classes because:

- The `native` method declarations in `BundledSQLiteDriver` /
  `NativeLibraryLoader` are byte-for-byte identical between 2.6.2 and 2.7.0
  (JNI methods are bound by name+signature via dynamic registration).
- Both versions bundle the same SQLite C library version (3.50.1).

## How it is consumed

`natives/osx_x64/` is the exact classpath layout `NativeLibraryLoader` looks up,
so the runtime fallback finds it directly. In packaged builds, Potassium's
`cleanupNativeLibs` extract step (matching target os/arch = macos/x64) copies it
into the app resources dir (on `java.library.path`), and the strip step removes
every native lib from the jars. As a result this dylib ships **only** in the
Intel-Mac package; every other target strips it out.

Remove this file if/when a `sqlite-bundled` release restores an `osx_x64`
native, or when Intel-Mac support is dropped entirely.
