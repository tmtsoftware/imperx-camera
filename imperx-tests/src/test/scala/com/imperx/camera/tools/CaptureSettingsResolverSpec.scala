package com.imperx.camera.tools

import munit.FunSuite

import java.util.Properties

class CaptureSettingsResolverSpec extends FunSuite {
  test("args override env and props") {
    val props = new Properties()
    props.setProperty("imperx.camera.ip", "192.168.1.10")
    props.setProperty("imperx.grab.timeout.ms", "2000")
    val env = stubEnv(Map("IMPERX_CAMERA_IP" -> "192.168.1.11", "IMPERX_GRAB_TIMEOUT_MS" -> "2500"))
    val args = Map("ip" -> "192.168.1.12", "timeout-ms" -> "3000")

    val s = CaptureSettingsResolver.resolve(args, props, env)
    assertEquals(s.cameraIp, "192.168.1.12")
    assertEquals(s.timeoutMs, 3000L)
  }

  test("env overrides props") {
    val props = new Properties()
    props.setProperty("imperx.camera.ip", "192.168.1.10")
    val env = stubEnv(Map("IMPERX_CAMERA_IP" -> "192.168.1.11"))
    val s = CaptureSettingsResolver.resolve(Map.empty, props, env)
    assertEquals(s.cameraIp, "192.168.1.11")
  }

  test("props used when env and args missing") {
    val props = new Properties()
    props.setProperty("imperx.camera.ip", "192.168.1.10")
    props.setProperty("imperx.exposure.micros", "1234")
    val s = CaptureSettingsResolver.resolve(Map.empty, props, stubEnv(Map.empty))
    assertEquals(s.cameraIp, "192.168.1.10")
    assertEquals(s.config.exposureMicros, Some(1234.0))
  }

  private def stubEnv(values: Map[String, String]): EnvProvider = new EnvProvider {
    override def get(name: String): Option[String] = values.get(name)
  }
}
