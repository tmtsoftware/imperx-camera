package com.imperx.camera

import com.imperx.camera.nativebridge.ImperxBinding

final class CameraSystem private (binding: ImperxBinding) extends AutoCloseable {
  binding.initialize()
  private var isClosed = false

  def listCameras(): Seq[CameraInfo] = binding.listCameras()

  def open(cameraId: String, config: CameraConfig = CameraConfig()): CameraSession = {
    val cameraHandle = binding.openCamera(cameraId)
    binding.applyConfig(cameraHandle, config)
    new CameraSession(binding, cameraHandle)
  }

  override def close(): Unit = {
    if (!isClosed) {
      isClosed = true
      binding.shutdown()
    }
  }
}

object CameraSystem {
  def apply(binding: ImperxBinding): CameraSystem = new CameraSystem(binding)
}

final class CameraSession private[camera] (
    binding: ImperxBinding,
    cameraHandle: Long
) extends AutoCloseable {
  private var closed = false

  def startAcquisition(): StreamSession = {
    val streamHandle = binding.startStream(cameraHandle)
    new StreamSession(binding, streamHandle)
  }

  override def close(): Unit = {
    if (!closed) {
      closed = true
      binding.closeCamera(cameraHandle)
    }
  }
}

final class StreamSession private[camera] (
    binding: ImperxBinding,
    streamHandle: Long
) extends AutoCloseable {
  private var stopped = false

  def grabFrame(timeoutMs: Long): Frame = binding.grabFrame(streamHandle, timeoutMs)

  override def close(): Unit = {
    if (!stopped) {
      stopped = true
      binding.stopStream(streamHandle)
    }
  }
}
