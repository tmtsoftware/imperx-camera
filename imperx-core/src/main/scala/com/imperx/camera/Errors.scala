package com.imperx.camera

sealed abstract class ImperxException(message: String) extends RuntimeException(message)

final case class ImperxNativeException(code: Int, detail: String)
    extends ImperxException(s"Imperx native error $code: $detail")

final case class ImperxTimeoutException(timeoutMs: Long)
    extends ImperxException(s"Frame grab timed out after ${timeoutMs}ms")
