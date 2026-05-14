package com.imperx.camera

final case class CameraInfo(id: String, model: String, ipAddress: Option[String])

final case class CameraConfig(
    exposureMicros: Option[Double] = None,
    gain: Option[Double] = None,
    pixelFormat: Option[String] = None
)

final case class Frame(
    width: Int,
    height: Int,
    pixelFormat: String,
    timestampNanos: Long,
    frameId: Long,
    bytes: Array[Byte]
)
