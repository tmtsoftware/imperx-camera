package com.imperx.camera.tools

import com.imperx.camera.Frame
import munit.FunSuite

import java.nio.file.Files

class DefaultFrameWriterSpec extends FunSuite {
  test("writes png for mono8") {
    val writer = new DefaultFrameWriter
    val out = Files.createTempFile("imperx-", ".png")
    val frame = Frame(8, 8, "Mono8", 0L, 1L, Array.fill[Byte](64)(42))
    writer.write(frame, out)
    assert(Files.size(out) > 0)
  }

  test("rejects png for non-mono8") {
    val writer = new DefaultFrameWriter
    val out = Files.createTempFile("imperx-", ".png")
    val frame = Frame(8, 8, "BayerRG8", 0L, 1L, Array.fill[Byte](64)(42))
    intercept[IllegalArgumentException] {
      writer.write(frame, out)
    }
  }

  test("writes fits for mono8") {
    val writer = new DefaultFrameWriter
    val out = Files.createTempFile("imperx-", ".fits")
    val frame = Frame(8, 8, "Mono8", 0L, 1L, Array.tabulate[Byte](64)(_.toByte))
    writer.write(frame, out)
    assert(Files.size(out) > 0)
  }
}
