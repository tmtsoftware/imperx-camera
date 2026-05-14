package com.imperx.camera.tools

import munit.FunSuite

class CaptureArgParserSpec extends FunSuite {
  test("parses supported args") {
    val parsed = CaptureArgParser.parse(
      List("--ip", "192.168.1.228", "--out", "/tmp/a.png", "--timeout-ms", "1500", "--pixel-format", "Mono8")
    )
    assertEquals(parsed("ip"), "192.168.1.228")
    assertEquals(parsed("out"), "/tmp/a.png")
    assertEquals(parsed("timeout-ms"), "1500")
    assertEquals(parsed("pixel-format"), "Mono8")
  }

  test("throws on unknown option") {
    intercept[IllegalArgumentException] {
      CaptureArgParser.parse(List("--unknown", "x"))
    }
  }
}
