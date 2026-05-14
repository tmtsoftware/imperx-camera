package com.imperx.camera.integration

import com.imperx.camera.CameraConfig
import com.imperx.camera.CameraInfo
import com.imperx.camera.Frame
import com.imperx.camera.nativebridge.ImperxBinding

import scala.collection.mutable.ArrayBuffer

final class RecordingBinding extends ImperxBinding {
  val operations: ArrayBuffer[String] = ArrayBuffer.empty[String]

  override def initialize(): Unit = operations += "initialize"
  override def shutdown(): Unit = operations += "shutdown"
  override def listCameras(): Seq[CameraInfo] = {
    operations += "listCameras"
    Seq(CameraInfo("FAKE-C1911-01", "C1911", Some("192.168.1.10")))
  }
  override def openCamera(cameraId: String): Long = {
    operations += s"openCamera:$cameraId"
    1L
  }
  override def closeCamera(cameraHandle: Long): Unit = operations += s"closeCamera:$cameraHandle"
  override def applyConfig(cameraHandle: Long, config: CameraConfig): Unit = operations += s"applyConfig:$cameraHandle"
  override def startStream(cameraHandle: Long): Long = {
    operations += s"startStream:$cameraHandle"
    2L
  }
  override def stopStream(streamHandle: Long): Unit = operations += s"stopStream:$streamHandle"
  override def grabFrame(streamHandle: Long, timeoutMs: Long): Frame = {
    operations += s"grabFrame:$streamHandle:$timeoutMs"
    Frame(1, 1, "Mono8", 0L, 0L, Array[Byte](0))
  }
}
