# Imperx Camera Usage Examples

This document shows practical examples for using this project from Scala.

## Prerequisites

1. Publish core library locally:

```bash
sbt "project imperxCore" publishLocal
```

2. Add dependency in your other `sbt` project:

```scala
libraryDependencies += "com.imperx" %% "imperx-camera-core" % "0.1.0-SNAPSHOT"
```

3. Ensure native libraries are available at runtime:

```bash
export LD_LIBRARY_PATH=/path/to/ImperxCamera/imperx-native/build:/opt/IpxCameraSDK-1.5.0.83/lib/Linux64_x64:$LD_LIBRARY_PATH
```

## Basic One-Frame Capture

```scala
import com.imperx.camera.{CameraConfig, CameraSystem}
import com.imperx.camera.nativebridge.JnrImperxBinding

val binding = JnrImperxBinding.load("imperx_bridge")
val system = CameraSystem(binding)

try {
  val camera = system.listCameras().head
  val session = system.open(camera.id, CameraConfig(pixelFormat = Some("Mono8")))
  try {
    val stream = session.startAcquisition()
    try {
      val frame = stream.grabFrame(2000)
      println(s"Captured ${frame.width}x${frame.height}, format=${frame.pixelFormat}, bytes=${frame.bytes.length}")
    } finally stream.close()
  } finally session.close()
} finally system.close()
```

## Continuous Streaming (Blocking Loop)

Use this pattern if your app wants a steady stream of frames:

```scala
import com.imperx.camera.{CameraConfig, CameraSystem}
import com.imperx.camera.nativebridge.JnrImperxBinding

object StreamExample {
  def main(args: Array[String]): Unit = {
    val targetIp = "192.168.1.228"

    val binding = JnrImperxBinding.loadFrom("/path/to/ImperxCamera/imperx-native/build")
    val system = CameraSystem(binding)

    try {
      val camInfo = system.listCameras().find(_.ipAddress.contains(targetIp))
        .getOrElse(sys.error(s"Camera $targetIp not found"))

      val camera = system.open(
        camInfo.id,
        CameraConfig(
          exposureMicros = None,
          gain = None,
          pixelFormat = Some("Mono8")
        )
      )

      try {
        val stream = camera.startAcquisition()
        try {
          var running = true
          while (running) {
            val frame = stream.grabFrame(2000)
            println(s"Frame ${frame.frameId}: ${frame.width}x${frame.height} ${frame.pixelFormat}")
            // Add your processing here.
          }
        } finally stream.close()
      } finally camera.close()
    } finally system.close()
  }
}
```

## CLI Capture Tool (From This Repo)

This repo also includes a CLI for quick capture:

```bash
LD_LIBRARY_PATH=/path/to/ImperxCamera/imperx-native/build:/opt/IpxCameraSDK-1.5.0.83/lib/Linux64_x64:$LD_LIBRARY_PATH \
sbt "project imperxTests" "runMain com.imperx.camera.tools.ImperxCaptureCli --ip 192.168.1.228 --out /tmp/imperx_capture.fits"
```

Supported outputs by extension:

- `.png` (Mono8)
- `.fits` / `.fit` (Mono8)
- other extensions: raw bytes
