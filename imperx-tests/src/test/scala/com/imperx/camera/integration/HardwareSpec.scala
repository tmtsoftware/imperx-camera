package com.imperx.camera.integration

import com.imperx.camera.CameraConfig
import com.imperx.camera.CameraSystem
import com.imperx.camera.nativebridge.JnrImperxBinding
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import javax.imageio.ImageIO
import nom.tam.fits.Fits
import nom.tam.fits.FitsFactory
import nom.tam.util.BufferedFile

class HardwareSpec extends munit.FunSuite {
  private val RequiredEnv = "IMPERX_HW_TESTS"
  private val CameraIpEnv = "IMPERX_CAMERA_IP"
  private val OutputPathEnv = "IMPERX_CAPTURE_OUTPUT"
  private val ExposureEnv = "IMPERX_EXPOSURE_MICROS"
  private val GainEnv = "IMPERX_GAIN"
  private val PixelFormatEnv = "IMPERX_PIXEL_FORMAT"
  private val GrabTimeoutEnv = "IMPERX_GRAB_TIMEOUT_MS"
  private val SoakFramesEnv = "IMPERX_SOAK_FRAMES"
  private val ConfigResource = "/hardware-test.properties"
  private val props = loadProps()

  test("native bridge supports open-configure-stream-grab-close flow") {
    assume(sys.env.get(RequiredEnv).contains("true"), s"$RequiredEnv=true required")
    val binding = JnrImperxBinding.load()
    val system = CameraSystem(binding)
    try {
      val cameras = system.listCameras()
      assert(cameras.nonEmpty)
      val camera = system.open(cameras.head.id, configFromEnv())
      try {
        val stream = camera.startAcquisition()
        try {
          val frame = stream.grabFrame(grabTimeoutMs())
          assert(frame.width > 0)
          assert(frame.height > 0)
          assert(frame.bytes.nonEmpty)
        } finally stream.close()
      } finally camera.close()
    } finally system.close()
  }

  test("captures one frame from configured camera IP and optionally writes it to disk") {
    assume(sys.env.get(RequiredEnv).contains("true"), s"$RequiredEnv=true required")
    val targetIp = envOrProp(CameraIpEnv, "imperx.camera.ip", "192.168.1.228")

    val binding = JnrImperxBinding.load()
    val system = CameraSystem(binding)
    try {
      val cameras = system.listCameras()
      val cameraInfoOpt = cameras.find(_.ipAddress.contains(targetIp))
      assert(cameraInfoOpt.nonEmpty, s"No discovered camera matched IP $targetIp. Found: ${cameras.map(_.ipAddress.getOrElse("?")).mkString(",")}")

      val camera = system.open(cameraInfoOpt.get.id, configFromEnv())
      try {
        val stream = camera.startAcquisition()
        try {
          val frame = stream.grabFrame(grabTimeoutMs())
          assert(frame.width > 0)
          assert(frame.height > 0)
          assert(frame.bytes.nonEmpty)

          outputPathOpt().foreach { out =>
            writeFrame(frame.width, frame.height, frame.pixelFormat, frame.bytes, Paths.get(out))
          }
        } finally stream.close()
      } finally camera.close()
    } finally system.close()
  }

  test("soak capture maintains stable frame shape and mostly monotonic frame ids") {
    assume(sys.env.get(RequiredEnv).contains("true"), s"$RequiredEnv=true required")
    val targetIp = envOrProp(CameraIpEnv, "imperx.camera.ip", "192.168.1.228")
    val framesToGrab = soakFrames()

    val binding = JnrImperxBinding.load()
    val system = CameraSystem(binding)
    try {
      val cameras = system.listCameras()
      val cameraInfoOpt = cameras.find(_.ipAddress.contains(targetIp))
      assert(cameraInfoOpt.nonEmpty, s"No discovered camera matched IP $targetIp. Found: ${cameras.map(_.ipAddress.getOrElse("?")).mkString(",")}")

      val camera = system.open(cameraInfoOpt.get.id, configFromEnv())
      try {
        val stream = camera.startAcquisition()
        try {
          var firstW = -1
          var firstH = -1
          var firstFmt = ""
          var previousFrameId = -1L
          var regressions = 0
          var emptyPayloads = 0

          var i = 0
          while (i < framesToGrab) {
            val frame = stream.grabFrame(grabTimeoutMs())
            if (firstW < 0) {
              firstW = frame.width
              firstH = frame.height
              firstFmt = frame.pixelFormat
            }
            assertEquals(frame.width, firstW)
            assertEquals(frame.height, firstH)
            assertEquals(frame.pixelFormat, firstFmt)
            if (frame.bytes.isEmpty) emptyPayloads += 1

            if (previousFrameId >= 0 && frame.frameId <= previousFrameId) regressions += 1
            previousFrameId = frame.frameId
            i += 1
          }

          assertEquals(emptyPayloads, 0, "one or more frames had empty payload")
          assert(regressions <= math.max(1, framesToGrab / 100), s"frame id regressions too high: $regressions / $framesToGrab")
        } finally stream.close()
      } finally camera.close()
    } finally system.close()
  }

  private def writeFrame(
      width: Int,
      height: Int,
      pixelFormat: String,
      bytes: Array[Byte],
      outputPath: Path
  ): Unit = {
    val parent = outputPath.getParent
    if (parent != null) Files.createDirectories(parent)

    val name = outputPath.getFileName.toString.toLowerCase
    if (name.endsWith(".png")) {
      require(
        pixelFormat == "Mono8",
        s"PNG output currently supports only Mono8; actual pixel format: $pixelFormat"
      )
      writeMono8Png(width, height, bytes, outputPath)
    } else if (name.endsWith(".fits") || name.endsWith(".fit")) {
      require(
        pixelFormat == "Mono8",
        s"FITS output currently supports only Mono8; actual pixel format: $pixelFormat"
      )
      writeMono8Fits(width, height, bytes, outputPath)
    } else {
      Files.write(outputPath, bytes)
    }
  }

  private def writeMono8Png(width: Int, height: Int, bytes: Array[Byte], outputPath: Path): Unit = {
    val image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
    val rasterData = image.getRaster.getDataBuffer.asInstanceOf[DataBufferByte].getData
    val copyLen = math.min(rasterData.length, bytes.length)
    System.arraycopy(bytes, 0, rasterData, 0, copyLen)
    ImageIO.write(image, "png", outputPath.toFile)
  }

  private def writeMono8Fits(width: Int, height: Int, bytes: Array[Byte], outputPath: Path): Unit = {
    val data = Array.ofDim[Short](height, width)
    var y = 0
    while (y < height) {
      // FITS viewers typically interpret row 0 as the bottom of the image.
      // Flip rows on write so orientation matches PNG output and camera view.
      val srcY = (height - 1) - y
      var x = 0
      while (x < width) {
        val idx = srcY * width + x
        val pixel = if (idx < bytes.length) bytes(idx) & 0xff else 0
        data(y)(x) = pixel.toShort
        x += 1
      }
      y += 1
    }

    val fits = new Fits()
    fits.addHDU(FitsFactory.hduFactory(data))
    val bf = new BufferedFile(outputPath.toString, "rw")
    try fits.write(bf)
    finally bf.close()
  }

  private def configFromEnv(): CameraConfig = {
    CameraConfig(
      exposureMicros = parseDoubleValue(ExposureEnv, "imperx.exposure.micros"),
      gain = parseDoubleValue(GainEnv, "imperx.gain"),
      pixelFormat = envOrPropOpt(PixelFormatEnv, "imperx.pixel.format")
    )
  }

  private def grabTimeoutMs(): Long = {
    parseLongValue(GrabTimeoutEnv, "imperx.grab.timeout.ms").getOrElse(2000L)
  }

  private def soakFrames(): Int = {
    parseLongValue(SoakFramesEnv, "imperx.soak.frames").map(_.toInt).getOrElse(300)
  }

  private def outputPathOpt(): Option[String] = {
    envOrPropOpt(OutputPathEnv, "imperx.capture.output")
  }

  private def parseDoubleValue(envKey: String, propKey: String): Option[Double] = {
    envOrPropOpt(envKey, propKey).flatMap(s => scala.util.Try(s.toDouble).toOption)
  }

  private def parseLongValue(envKey: String, propKey: String): Option[Long] = {
    envOrPropOpt(envKey, propKey).flatMap(parseLong)
  }

  private def parseLong(value: String): Option[Long] = {
    scala.util.Try(value.toLong).toOption
  }

  private def envOrProp(envKey: String, propKey: String, default: String): String = {
    sys.env.get(envKey).filter(_.nonEmpty).orElse(Option(props.getProperty(propKey)).filter(_.nonEmpty)).getOrElse(default)
  }

  private def envOrPropOpt(envKey: String, propKey: String): Option[String] = {
    sys.env.get(envKey).filter(_.nonEmpty).orElse(Option(props.getProperty(propKey)).filter(_.nonEmpty))
  }

  private def loadProps(): Properties = {
    val p = new Properties()
    val in: InputStream = getClass.getResourceAsStream(ConfigResource)
    if (in != null) {
      try p.load(in)
      finally in.close()
    }
    p
  }
}
