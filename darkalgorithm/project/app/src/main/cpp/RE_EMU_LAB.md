# RE/EMU/MEDIA LAB — final resolution

Checked again before closing this out: there is no single verified Maven
Central AAR for Capstone+Keystone+Unicorn on Android the way there is for
ONNX Runtime or Rhino. That's not a gap in searching — these are C/C++
projects without an official pre-built Android package; apps that embed
them build from source via the NDK. So the resolution isn't "find the
right dependency," it's "build them the way everyone else building this
kind of tool does."

## What's here
`CMakeLists.txt` is the real wiring pattern: NDK cross-compilation via
`externalNativeBuild`, referencing each project's own upstream CMake build
as a git submodule, producing one `.so` per ABI FARCAST already targets
(armeabi-v7a/arm64-v8a/x86_64). This is genuine, standard Android NDK
practice — not a workaround.

## What's NOT here, and why
Actually running this build — checking out Capstone/Keystone/Unicorn's
source at a pinned commit, confirming their CMake options cross-compile
cleanly for `armeabi-v7a` specifically (32-bit ARM has historically been
the ABI most likely to have a rough edge in a project primarily tested on
arm64/x86), and producing working `.so` files — needs an actual NDK
toolchain and a real build-and-test cycle. That's not something to fake a
transcript of; it's follow-up engineering work with a device or emulator
in the loop, the same as the Stage 7 testing this project has flagged
throughout as needing a real build environment.

## FFmpeg specifically
ffmpeg-kit (the previously-common wrapper) was retired by its maintainer —
don't reach for it as if it's still current. The two real remaining paths:
build FFmpeg from source via the NDK (FFmpeg's own project documents this),
or use a currently-maintained community fork — check which forks are
actually active before picking one, the situation has shifted since
ffmpeg-kit's retirement and a confident recommendation right now risks
being stale by the time anyone reads this. For media PLAYBACK specifically
(as opposed to arbitrary ffmpeg CLI-style processing), `androidx.media3` is
the maintained, verified-current Google library and covers a meaningful
chunk of what a "media lab" panel needs without touching FFmpeg at all.

## Recommendation
Treat this as its own follow-up task, scoped as: "cross-compile Capstone,
Keystone, Unicorn for Android via NDK/CMake, verify each on armeabi-v7a and
arm64-v8a in a real emulator, wire the resulting .so files into the
CMakeLists.txt scaffold here." That's buildable, testable work — just not
work this conversation can finish without an actual NDK build environment
to verify against.
