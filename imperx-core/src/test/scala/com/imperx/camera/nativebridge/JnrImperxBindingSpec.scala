package com.imperx.camera.nativebridge

import com.imperx.camera.ImperxTimeoutException
import com.imperx.camera.ImperxNativeException
import munit.FunSuite

class JnrImperxBindingSpec extends FunSuite {
  test("lists cameras and grabs frame through C API contract") {
    val binding = new JnrImperxBinding(new StubImperxCLibrary)
    binding.initialize()
    val cameras = binding.listCameras()
    assertEquals(cameras.size, 1)
    assertEquals(cameras.head.id, "C1911-FAKE-001")
    val cameraHandle = binding.openCamera(cameras.head.id)
    val streamHandle = binding.startStream(cameraHandle)
    val frame = binding.grabFrame(streamHandle, 100)
    assertEquals(frame.width, 8)
    assertEquals(frame.height, 8)
    assertEquals(frame.pixelFormat, "Mono8")
    assertEquals(frame.bytes.length, 64)
    binding.stopStream(streamHandle)
    binding.closeCamera(cameraHandle)
    binding.shutdown()
  }

  test("maps timeout status to ImperxTimeoutException") {
    val binding = new JnrImperxBinding(new StubImperxCLibrary(timeoutMode = true))
    binding.initialize()
    intercept[ImperxTimeoutException] {
      binding.grabFrame(1L, 10)
    }
  }

  test("applyConfig maps Scala config to native parameter calls") {
    val stub = new StubImperxCLibrary
    val binding = new JnrImperxBinding(stub)
    binding.applyConfig(
      cameraHandle = 101L,
      com.imperx.camera.CameraConfig(
        exposureMicros = Some(1200.0),
        gain = Some(3.5),
        pixelFormat = Some("Mono8")
      )
    )
    assertEquals(stub.floatWrites, Seq((101L, "ExposureTime", 1200.0), (101L, "Gain", 3.5)))
    assertEquals(stub.enumWrites, Seq((101L, "PixelFormat", "Mono8")))
  }

  test("maps non-timeout native failures to ImperxNativeException with last error message") {
    val binding = new JnrImperxBinding(new StubImperxCLibrary(failInitialize = true, lastError = "init failed"))
    val ex = intercept[ImperxNativeException] {
      binding.initialize()
    }
    assertEquals(ex.code, 1)
    assert(ex.detail.contains("init failed"))
  }
}

final class StubImperxCLibrary(
    timeoutMode: Boolean = false,
    failInitialize: Boolean = false,
    lastError: String = ""
) extends ImperxCLibrary {
  var floatWrites: Vector[(Long, String, Double)] = Vector.empty
  var enumWrites: Vector[(Long, String, String)] = Vector.empty
  override def imperx_initialize(): Int = if (failInitialize) 1 else 0
  override def imperx_shutdown(): Int = 0
  override def imperx_last_error(buffer: Array[Byte], bufferLen: Int): Int = {
    writeCString(buffer, lastError)
    0
  }
  override def imperx_list_cameras_count(outCount: Array[Int]): Int = {
    outCount(0) = 1
    0
  }
  override def imperx_get_camera_info(
      index: Int,
      idBuffer: Array[Byte],
      idBufferLen: Int,
      modelBuffer: Array[Byte],
      modelBufferLen: Int,
      ipBuffer: Array[Byte],
      ipBufferLen: Int
  ): Int = {
    writeCString(idBuffer, "C1911-FAKE-001")
    writeCString(modelBuffer, "C1911")
    writeCString(ipBuffer, "192.168.1.10")
    0
  }
  override def imperx_open_camera(cameraId: String, outCameraHandle: Array[Long]): Int = {
    outCameraHandle(0) = 101L
    0
  }
  override def imperx_close_camera(cameraHandle: Long): Int = 0
  override def imperx_set_float_param(cameraHandle: Long, paramName: String, value: Double): Int = {
    floatWrites = floatWrites :+ ((cameraHandle, paramName, value))
    0
  }
  override def imperx_set_enum_param(cameraHandle: Long, paramName: String, value: String): Int = {
    enumWrites = enumWrites :+ ((cameraHandle, paramName, value))
    0
  }
  override def imperx_start_stream(cameraHandle: Long, outStreamHandle: Array[Long]): Int = {
    outStreamHandle(0) = 202L
    0
  }
  override def imperx_stop_stream(streamHandle: Long): Int = 0
  override def imperx_grab_frame(
      streamHandle: Long,
      timeoutMs: Int,
      outBuffer: Array[Byte],
      outBufferLen: Long,
      outFilledSize: Array[Long],
      outWidth: Array[Int],
      outHeight: Array[Int],
      outFrameId: Array[Long],
      outTimestampNanos: Array[Long],
      outPixelFormatCode: Array[Int]
  ): Int = {
    if (timeoutMode) return 3
    val data = Array.tabulate[Byte](64)(i => i.toByte)
    Array.copy(data, 0, outBuffer, 0, data.length)
    outFilledSize(0) = data.length.toLong
    outWidth(0) = 8
    outHeight(0) = 8
    outFrameId(0) = 333L
    outTimestampNanos(0) = 444L
    outPixelFormatCode(0) = 1
    0
  }

  private def writeCString(target: Array[Byte], value: String): Unit = {
    val bytes = value.getBytes("UTF-8")
    val len = math.min(target.length - 1, bytes.length)
    System.arraycopy(bytes, 0, target, 0, len)
    target(len) = 0
  }
}
