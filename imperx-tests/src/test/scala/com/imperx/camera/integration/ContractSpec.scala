package com.imperx.camera.integration

import com.imperx.camera.CameraSystem

class ContractSpec extends munit.FunSuite {
  test("camera system initializes, lists, and closes through binding contract") {
    val binding = new RecordingBinding
    val system = CameraSystem(binding)
    try {
      val cameras = system.listCameras()
      assert(cameras.map(_.id) == Seq("FAKE-C1911-01"))
      assert(binding.operations.contains("initialize"))
      assert(binding.operations.contains("listCameras"))
    } finally {
      system.close()
      assert(binding.operations.last == "shutdown")
    }
  }
}
