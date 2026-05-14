package com.imperx.camera.nativebridge

import com.imperx.camera.CameraConfig
import com.imperx.camera.CameraInfo
import com.imperx.camera.Frame
import com.imperx.camera.ImperxTimeoutException

import java.util.concurrent.atomic.AtomicLong

final class FakeImperxBinding extends ImperxBinding {
  private val handleGen = new AtomicLong(1000L)
  private var initialized = false

  override def initialize(): Unit = initialized = true
  override def shutdown(): Unit = initialized = false

  override def listCameras(): Seq[CameraInfo] = {
    ensureInitialized()
    Seq(CameraInfo("FAKE-C1911-01", "C1911", Some("192.168.1.10")))
  }

  override def openCamera(cameraId: String): Long = {
    ensureInitialized()
    if (!cameraId.startsWith("FAKE-")) throw new IllegalArgumentException(s"Unknown camera: $cameraId")
    handleGen.incrementAndGet()
  }

  override def closeCamera(cameraHandle: Long): Unit = ()
  override def applyConfig(cameraHandle: Long, config: CameraConfig): Unit = ()
  override def startStream(cameraHandle: Long): Long = handleGen.incrementAndGet()
  override def stopStream(streamHandle: Long): Unit = ()

  override def grabFrame(streamHandle: Long, timeoutMs: Long): Frame = {
    if (timeoutMs <= 0) throw ImperxTimeoutException(timeoutMs)
    val bytes = Array.tabulate[Byte](64)(i => (i % 255).toByte)
    Frame(8, 8, "Mono8", System.nanoTime(), handleGen.incrementAndGet(), bytes)
  }

  private def ensureInitialized(): Unit = {
    if (!initialized) throw new IllegalStateException("SDK not initialized")
  }
}
