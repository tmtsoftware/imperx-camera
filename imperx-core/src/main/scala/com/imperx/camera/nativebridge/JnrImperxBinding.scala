package com.imperx.camera.nativebridge

import com.imperx.camera.CameraConfig
import com.imperx.camera.CameraInfo
import com.imperx.camera.Frame
import com.imperx.camera.ImperxNativeException
import com.imperx.camera.ImperxTimeoutException
import jnr.ffi.LibraryLoader

object JnrImperxBinding {
  def load(libraryName: String = "imperx_bridge"): JnrImperxBinding = {
    val native = LibraryLoader.create(classOf[ImperxCLibrary]).load(libraryName)
    new JnrImperxBinding(native)
  }

  def loadFrom(searchPath: String, libraryName: String = "imperx_bridge"): JnrImperxBinding = {
    val native = LibraryLoader
      .create(classOf[ImperxCLibrary])
      .search(searchPath)
      .load(libraryName)
    new JnrImperxBinding(native)
  }
}

final class JnrImperxBinding(native: ImperxCLibrary) extends ImperxBinding {
  override def initialize(): Unit = check(native.imperx_initialize())
  override def shutdown(): Unit = check(native.imperx_shutdown())

  override def listCameras(): Seq[CameraInfo] = {
    val countRef = Array(0)
    check(native.imperx_list_cameras_count(countRef))
    (0 until countRef(0)).map { i =>
      val id = readStringBuffer(128)
      val model = readStringBuffer(64)
      val ip = readStringBuffer(64)
      check(native.imperx_get_camera_info(i, id, id.length, model, model.length, ip, ip.length))
      val ipText = cString(ip)
      CameraInfo(cString(id), cString(model), Option.when(ipText.nonEmpty)(ipText))
    }
  }

  override def openCamera(cameraId: String): Long = {
    val handleRef = Array(0L)
    check(native.imperx_open_camera(cameraId, handleRef))
    handleRef(0)
  }

  override def closeCamera(cameraHandle: Long): Unit = check(native.imperx_close_camera(cameraHandle))

  override def applyConfig(cameraHandle: Long, config: CameraConfig): Unit = {
    config.exposureMicros.foreach(v => check(native.imperx_set_float_param(cameraHandle, "ExposureTime", v)))
    config.gain.foreach(v => check(native.imperx_set_float_param(cameraHandle, "Gain", v)))
    config.pixelFormat.foreach(v => check(native.imperx_set_enum_param(cameraHandle, "PixelFormat", v)))
  }

  override def startStream(cameraHandle: Long): Long = {
    val handleRef = Array(0L)
    check(native.imperx_start_stream(cameraHandle, handleRef))
    handleRef(0)
  }

  override def stopStream(streamHandle: Long): Unit = check(native.imperx_stop_stream(streamHandle))

  override def grabFrame(streamHandle: Long, timeoutMs: Long): Frame = {
    val buffer = new Array[Byte](1024 * 1024 * 8)
    val filledSize = Array(0L)
    val width = Array(0)
    val height = Array(0)
    val frameId = Array(0L)
    val timestamp = Array(0L)
    val pixelFormat = Array(0)
    val code = native.imperx_grab_frame(
      streamHandle,
      timeoutMs.toInt,
      buffer,
      buffer.length.toLong,
      filledSize,
      width,
      height,
      frameId,
      timestamp,
      pixelFormat
    )
    if (code == 3) throw ImperxTimeoutException(timeoutMs)
    check(code)
    val size = filledSize(0).toInt
    val format = pixelFormatToName(pixelFormat(0))
    Frame(width(0), height(0), format, timestamp(0), frameId(0), buffer.take(size))
  }

  private def check(code: Int): Unit = {
    if (code != 0) {
      val buf = Array.fill[Byte](512)(0)
      native.imperx_last_error(buf, buf.length)
      val text = new String(buf.takeWhile(_ != 0))
      throw ImperxNativeException(code, text)
    }
  }

  private def readStringBuffer(size: Int): Array[Byte] = Array.fill[Byte](size)(0)

  private def cString(bytes: Array[Byte]): String = new String(bytes.takeWhile(_ != 0))

  private def pixelFormatToName(code: Int): String = code match {
    // 1 is test-stub value; 0x01080001 is PFNC Mono8 used by many GigE/USB3 cameras.
    case 1 => "Mono8"
    case 0x01080001 => "Mono8"
    case _ => s"Unknown($code)"
  }
}
