# Imperx Camera Scala SDK

Scala package for controlling Imperx cameras through the Imperx C++ SDK.

## Platform support

The full installation is Linux-only. Imperx does not provide a supported macOS
SDK for this setup, and the native bridge in this repository links against the
Linux Imperx SDK libraries under `/opt/IpxCameraSDK-1.5.0.83/lib/Linux64_x64`.

On macOS, you can still build the Scala/JVM jar for compile-time and simulated
testing in downstream projects. That jar contains the public Scala API and JNR
binding layer, but it does not include a working native camera runtime. Real
camera discovery, connection, streaming, and capture still require the Linux
native bridge and Imperx SDK.

## Modules

- `imperx-core`: Scala domain model and API over native bridge.
- `imperx-native`: C/C++ bridge contract and starter implementation.
- `imperx-tests`: integration and hardware-gated tests.

## Linux Full Installation

Use this path when you need real Imperx camera access.

### Imperx SDK prerequisites

- Imperx SDK path: `/opt/IpxCameraSDK-1.5.0.83/`
- Linux dynamic loader must find native bridge and Imperx SDK libraries.

Example:

```bash
export LD_LIBRARY_PATH=/opt/IpxCameraSDK-1.5.0.83/lib:$LD_LIBRARY_PATH
```

### Build Native Bridge

```bash
cmake -S imperx-native/src/main/cpp -B imperx-native/build
cmake --build imperx-native/build -j
```

This produces:

```bash
imperx-native/build/libimperx_bridge.so
```

### Build and publish Scala API

```bash
sbt "project imperxCore" test
sbt "project imperxCore" publishLocal
```

This publishes:

```scala
"com.imperx" %% "imperx-camera-core" % "0.1.0-SNAPSHOT"
```

### Use From Another sbt Project

For dependency setup, Linux runtime library configuration, and Scala
streaming/capture examples, see:

- `docs/USAGE_EXAMPLES.md`

## macOS JVM-only Build

Use this path when you are developing on macOS and only need the JVM artifact
for downstream compilation, unit tests, simulated-camera workflows, or API
integration work.

Build the Scala jar:

```bash
sbt "project imperxCore" package
```

This produces:

```bash
imperx-core/target/scala-2.13/imperx-camera-core_2.13-0.1.0-SNAPSHOT.jar
```

Optionally publish it to your local Ivy cache:

```bash
sbt "project imperxCore" publishLocal
```

The local dependency is:

```scala
"com.imperx" %% "imperx-camera-core" % "0.1.0-SNAPSHOT"
```

For an unmanaged jar dependency, point the consuming project at the built jar.
For example, from `~/tmtsoftware/lgsf-prototype/bto-prototype/`:

```bash
sbt -Dimperx.core.jar=/Users/weiss/tmtsoftware/imperx-camera/imperx-core/target/scala-2.13/imperx-camera-core_2.13-0.1.0-SNAPSHOT.jar \
  "project lgsf-pac-prototypehcd" compile
```

What works on macOS:

- compiling downstream projects against `com.imperx.camera.*`
- running tests that use fake, stub, or simulated camera implementations
- validating application-level integration around the Imperx API boundary

What does not work on macOS with the current vendor installation:

- loading `imperx_bridge` as a real native camera bridge
- discovering or opening Imperx cameras through the vendor SDK
- streaming or capturing real frames through `JnrImperxBinding.load(...)`

Those operations require the Linux `libimperx_bridge.so` and the Linux Imperx
SDK shared libraries.

## Test strategy

- Unit and contract tests:

```bash
sbt test
```

- Hardware tests (requires connected camera and network setup):

```bash
LD_LIBRARY_PATH=/home/jweiss/tmtsoftware/ImperxCamera/imperx-native/build:/opt/IpxCameraSDK-1.5.0.83/lib/Linux64_x64:$LD_LIBRARY_PATH \
IMPERX_HW_TESTS=true \
sbt "project imperx-tests" test
```

Hardware test defaults are in:

```bash
imperx-tests/src/test/resources/hardware-test.properties
```

Override with env vars as needed:

- `IMPERX_CAMERA_IP`
- `IMPERX_CAPTURE_OUTPUT` (`.png`, `.fits`, `.fit`, or raw bytes for other extensions)
- `IMPERX_EXPOSURE_MICROS`
- `IMPERX_GAIN`
- `IMPERX_PIXEL_FORMAT`
- `IMPERX_GRAB_TIMEOUT_MS`
- `IMPERX_SOAK_FRAMES`

## CLI Capture

For CLI usage and examples, see:

- `docs/USAGE_EXAMPLES.md`

CLI defaults are in:

- `imperx-tests/src/main/resources/imperx-capture.properties`

## Roadmap

For potential upgrades and roadmap items (including non-blocking stream abstraction), see:

- `docs/POTENTIAL_UPGRADES.md`
