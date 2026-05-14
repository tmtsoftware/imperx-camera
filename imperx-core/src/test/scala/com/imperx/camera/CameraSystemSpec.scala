package com.imperx.camera

import com.imperx.camera.nativebridge.FakeImperxBinding
import com.imperx.camera.nativebridge.ImperxBinding

class CameraSystemSpec extends munit.FunSuite {
  test("discovers and opens a fake camera") {
    val system = CameraSystem(new FakeImperxBinding)
    try {
      val cameras = system.listCameras()
      assert(cameras.nonEmpty)
      assert(cameras.head.model == "C1911")
      val camera = system.open(cameras.head.id, CameraConfig(exposureMicros = Some(5000.0)))
      camera.close()
    } finally {
      system.close()
    }
  }

  test("grabs a frame from fake stream") {
    val system = CameraSystem(new FakeImperxBinding)
    try {
      val cameraId = system.listCameras().head.id
      val camera = system.open(cameraId)
      try {
        val stream = camera.startAcquisition()
        try {
          val frame = stream.grabFrame(100)
          assert(frame.width == 8)
          assert(frame.height == 8)
          assert(frame.bytes.length == 64)
        } finally stream.close()
      } finally camera.close()
    } finally system.close()
  }

  test("close operations are idempotent and call native close only once") {
    val binding = new RecordingBinding
    val system = CameraSystem(binding)
    val camera = system.open("cam-1")
    val stream = camera.startAcquisition()

    stream.close()
    stream.close()
    camera.close()
    camera.close()
    system.close()
    system.close()

    assertEquals(binding.stopStreamCalls, 1)
    assertEquals(binding.closeCameraCalls, 1)
    assertEquals(binding.shutdownCalls, 1)
  }

  test("camera workflow uses expected call ordering") {
    val binding = new RecordingBinding
    val system = CameraSystem(binding)
    try {
      val camera = system.open("cam-1", CameraConfig(exposureMicros = Some(123.0), gain = Some(1.0)))
      try {
        val stream = camera.startAcquisition()
        try {
          stream.grabFrame(100)
        } finally stream.close()
      } finally camera.close()
    } finally system.close()

    assertEquals(
      binding.operations,
      Vector(
        "initialize",
        "openCamera:cam-1",
        "applyConfig:11",
        "startStream:11",
        "grabFrame:22:100",
        "stopStream:22",
        "closeCamera:11",
        "shutdown"
      )
    )
  }
}

final class RecordingBinding extends ImperxBinding {
  var operations: Vector[String] = Vector.empty
  var stopStreamCalls = 0
  var closeCameraCalls = 0
  var shutdownCalls = 0

  override def initialize(): Unit = operations :+= "initialize"
  override def shutdown(): Unit = {
    shutdownCalls += 1
    operations :+= "shutdown"
  }
  override def listCameras(): Seq[CameraInfo] = Seq(CameraInfo("cam-1", "C1911", Some("192.168.1.10")))
  override def openCamera(cameraId: String): Long = {
    operations :+= s"openCamera:$cameraId"
    11L
  }
  override def closeCamera(cameraHandle: Long): Unit = {
    closeCameraCalls += 1
    operations :+= s"closeCamera:$cameraHandle"
  }
  override def applyConfig(cameraHandle: Long, config: CameraConfig): Unit = {
    operations :+= s"applyConfig:$cameraHandle"
  }
  override def startStream(cameraHandle: Long): Long = {
    operations :+= s"startStream:$cameraHandle"
    22L
  }
  override def stopStream(streamHandle: Long): Unit = {
    stopStreamCalls += 1
    operations :+= s"stopStream:$streamHandle"
  }
  override def grabFrame(streamHandle: Long, timeoutMs: Long): Frame = {
    operations :+= s"grabFrame:$streamHandle:$timeoutMs"
    Frame(8, 8, "Mono8", 1L, 1L, Array.fill[Byte](64)(0))
  }
}
