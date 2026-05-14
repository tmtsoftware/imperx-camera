package com.imperx.camera.nativebridge

import com.imperx.camera.CameraInfo
import com.imperx.camera.CameraConfig
import com.imperx.camera.Frame

trait ImperxBinding {
  def initialize(): Unit
  def shutdown(): Unit
  def listCameras(): Seq[CameraInfo]
  def openCamera(cameraId: String): Long
  def closeCamera(cameraHandle: Long): Unit
  def applyConfig(cameraHandle: Long, config: CameraConfig): Unit
  def startStream(cameraHandle: Long): Long
  def stopStream(streamHandle: Long): Unit
  def grabFrame(streamHandle: Long, timeoutMs: Long): Frame
}
