package com.imperx.camera.nativebridge

trait ImperxCLibrary {
  def imperx_initialize(): Int
  def imperx_shutdown(): Int
  def imperx_last_error(buffer: Array[Byte], bufferLen: Int): Int
  def imperx_list_cameras_count(outCount: Array[Int]): Int
  def imperx_get_camera_info(
      index: Int,
      idBuffer: Array[Byte],
      idBufferLen: Int,
      modelBuffer: Array[Byte],
      modelBufferLen: Int,
      ipBuffer: Array[Byte],
      ipBufferLen: Int
  ): Int
  def imperx_open_camera(cameraId: String, outCameraHandle: Array[Long]): Int
  def imperx_close_camera(cameraHandle: Long): Int
  def imperx_set_float_param(cameraHandle: Long, paramName: String, value: Double): Int
  def imperx_set_enum_param(cameraHandle: Long, paramName: String, value: String): Int
  def imperx_start_stream(cameraHandle: Long, outStreamHandle: Array[Long]): Int
  def imperx_stop_stream(streamHandle: Long): Int
  def imperx_grab_frame(
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
  ): Int
}
