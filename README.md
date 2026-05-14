# Imperx Camera Scala SDK

Scala package for controlling Imperx cameras through the Imperx C++ SDK.

## Modules

- `imperx-core`: Scala domain model and API over native bridge.
- `imperx-native`: C/C++ bridge contract and starter implementation.
- `imperx-tests`: integration and hardware-gated tests.

## Imperx SDK prerequisites

- Imperx SDK path: `/opt/IpxCameraSDK-1.5.0.83/`
- Linux dynamic loader must find native bridge and Imperx SDK libraries.

Example:

```bash
export LD_LIBRARY_PATH=/opt/IpxCameraSDK-1.5.0.83/lib:$LD_LIBRARY_PATH
```

## Build Native Bridge

```bash
cmake -S imperx-native/src/main/cpp -B imperx-native/build
cmake --build imperx-native/build -j
```

This produces:

```bash
imperx-native/build/libimperx_bridge.so
```

## Use From Another sbt Project

For dependency setup and Scala streaming/capture examples, see:

- `docs/USAGE_EXAMPLES.md`

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
