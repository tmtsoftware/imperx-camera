package com.imperx.camera.tools

import com.imperx.camera.CameraConfig
import com.imperx.camera.CameraInfo
import com.imperx.camera.Frame
import com.imperx.camera.nativebridge.ImperxBinding
import munit.FunSuite

import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

class ImperxCaptureAppSpec extends FunSuite {
  test("capture app uses injected writer and returns success") {
    val binding = new FakeBinding
    val provider = new BindingProvider {
      override def load(): ImperxBinding = binding
    }
    val env = new EnvProvider {
      override def get(name: String): Option[String] = None
    }
    val props = new Properties()
    props.setProperty("imperx.camera.ip", "192.168.1.228")
    props.setProperty("imperx.capture.output", "/tmp/test.png")
    val writer = new RecordingWriter

    val app = new ImperxCaptureApp(provider, env, writer, props)
    val rc = app.run(Nil)

    assertEquals(rc, 0)
    assertEquals(writer.writes.size, 1)
    assertEquals(writer.writes.head._2, Paths.get("/tmp/test.png"))
  }
}

final class RecordingWriter extends FrameWriter {
  var writes: Vector[(Frame, Path)] = Vector.empty
  override def write(frame: Frame, outputPath: Path): Unit = {
    writes = writes :+ (frame -> outputPath)
  }
}

final class FakeBinding extends ImperxBinding {
  override def initialize(): Unit = ()
  override def shutdown(): Unit = ()
  override def listCameras(): Seq[CameraInfo] = Seq(CameraInfo("cam-1", "C1911", Some("192.168.1.228")))
  override def openCamera(cameraId: String): Long = 1L
  override def closeCamera(cameraHandle: Long): Unit = ()
  override def applyConfig(cameraHandle: Long, config: CameraConfig): Unit = ()
  override def startStream(cameraHandle: Long): Long = 2L
  override def stopStream(streamHandle: Long): Unit = ()
  override def grabFrame(streamHandle: Long, timeoutMs: Long): Frame =
    Frame(8, 8, "Mono8", 1L, 1L, Array.fill[Byte](64)(1))
}
