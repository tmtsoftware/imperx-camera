package com.imperx.camera.tools

import com.imperx.camera.CameraConfig
import com.imperx.camera.CameraSystem
import com.imperx.camera.Frame
import com.imperx.camera.nativebridge.ImperxBinding
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

final case class CaptureSettings(
    cameraIp: String,
    outputPath: Option[Path],
    timeoutMs: Long,
    config: CameraConfig
)

trait EnvProvider {
  def get(name: String): Option[String]
}

object SystemEnvProvider extends EnvProvider {
  override def get(name: String): Option[String] = sys.env.get(name).filter(_.nonEmpty)
}

trait BindingProvider {
  def load(): ImperxBinding
}

object JnrBindingProvider extends BindingProvider {
  override def load(): ImperxBinding = JnrImperxBinding.load()
}

trait FrameWriter {
  def write(frame: Frame, outputPath: Path): Unit
}

final class DefaultFrameWriter extends FrameWriter {
  override def write(frame: Frame, outputPath: Path): Unit = {
    val parent = outputPath.getParent
    if (parent != null) Files.createDirectories(parent)
    val name = outputPath.getFileName.toString.toLowerCase
    if (name.endsWith(".png")) {
      require(frame.pixelFormat == "Mono8", s"PNG output currently supports only Mono8; actual pixel format: ${frame.pixelFormat}")
      writeMono8Png(frame.width, frame.height, frame.bytes, outputPath)
    } else if (name.endsWith(".fits") || name.endsWith(".fit")) {
      require(frame.pixelFormat == "Mono8", s"FITS output currently supports only Mono8; actual pixel format: ${frame.pixelFormat}")
      writeMono8Fits(frame.width, frame.height, frame.bytes, outputPath)
    } else {
      Files.write(outputPath, frame.bytes)
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
}

object CaptureArgParser {
  def parse(args: List[String]): Map[String, String] = {
    def loop(rest: List[String], acc: Map[String, String]): Map[String, String] = rest match {
      case Nil => acc
      case "--help" :: tail => loop(tail, acc + ("help" -> "true"))
      case "--ip" :: value :: tail => loop(tail, acc + ("ip" -> value))
      case "--out" :: value :: tail => loop(tail, acc + ("out" -> value))
      case "--timeout-ms" :: value :: tail => loop(tail, acc + ("timeout-ms" -> value))
      case "--exposure-micros" :: value :: tail => loop(tail, acc + ("exposure-micros" -> value))
      case "--gain" :: value :: tail => loop(tail, acc + ("gain" -> value))
      case "--pixel-format" :: value :: tail => loop(tail, acc + ("pixel-format" -> value))
      case key :: _ if key.startsWith("--") =>
        throw new IllegalArgumentException(s"Unknown option: $key")
      case token :: _ =>
        throw new IllegalArgumentException(s"Unexpected argument: $token")
    }
    loop(args, Map.empty)
  }
}

object CaptureSettingsResolver {
  private val EnvCameraIp = "IMPERX_CAMERA_IP"
  private val EnvOutput = "IMPERX_CAPTURE_OUTPUT"
  private val EnvExposure = "IMPERX_EXPOSURE_MICROS"
  private val EnvGain = "IMPERX_GAIN"
  private val EnvPixelFormat = "IMPERX_PIXEL_FORMAT"
  private val EnvTimeout = "IMPERX_GRAB_TIMEOUT_MS"

  def resolve(args: Map[String, String], props: Properties, env: EnvProvider): CaptureSettings = {
    val cameraIp = args.get("ip").orElse(envOrPropOpt(env, EnvCameraIp, "imperx.camera.ip", props)).getOrElse {
      throw new IllegalArgumentException("camera IP is required (set --ip or IMPERX_CAMERA_IP)")
    }
    val outputPath = args.get("out").orElse(envOrPropOpt(env, EnvOutput, "imperx.capture.output", props)).map(Paths.get(_))
    val timeoutMs = args.get("timeout-ms").flatMap(parseLong)
      .orElse(envOrPropOpt(env, EnvTimeout, "imperx.grab.timeout.ms", props).flatMap(parseLong))
      .getOrElse(2000L)
    val config = CameraConfig(
      exposureMicros = args.get("exposure-micros").flatMap(parseDouble)
        .orElse(envOrPropOpt(env, EnvExposure, "imperx.exposure.micros", props).flatMap(parseDouble)),
      gain = args.get("gain").flatMap(parseDouble)
        .orElse(envOrPropOpt(env, EnvGain, "imperx.gain", props).flatMap(parseDouble)),
      pixelFormat = args.get("pixel-format")
        .orElse(envOrPropOpt(env, EnvPixelFormat, "imperx.pixel.format", props))
        .filter(_.nonEmpty)
    )
    CaptureSettings(cameraIp, outputPath, timeoutMs, config)
  }

  private def parseLong(value: String): Option[Long] = scala.util.Try(value.toLong).toOption
  private def parseDouble(value: String): Option[Double] = scala.util.Try(value.toDouble).toOption

  private def envOrPropOpt(env: EnvProvider, envKey: String, propKey: String, props: Properties): Option[String] = {
    env.get(envKey).orElse(Option(props.getProperty(propKey)).filter(_.nonEmpty))
  }
}

final class ImperxCaptureApp(
    bindingProvider: BindingProvider,
    envProvider: EnvProvider,
    frameWriter: FrameWriter,
    props: Properties
) {
  def run(args: List[String]): Int = {
    val argMap = CaptureArgParser.parse(args)
    if (argMap.contains("help")) {
      printUsage()
      return 0
    }
    val settings = CaptureSettingsResolver.resolve(argMap, props, envProvider)

    val system = CameraSystem(bindingProvider.load())
    try {
      val cameras = system.listCameras()
      val cameraInfoOpt = cameras.find(_.ipAddress.contains(settings.cameraIp))
      if (cameraInfoOpt.isEmpty) {
        val found = cameras.map(_.ipAddress.getOrElse("?")).mkString(",")
        throw new IllegalStateException(s"No discovered camera matched IP ${settings.cameraIp}. Found: $found")
      }
      val camera = system.open(cameraInfoOpt.get.id, settings.config)
      try {
        val stream = camera.startAcquisition()
        try {
          val frame = stream.grabFrame(settings.timeoutMs)
          settings.outputPath.foreach { out =>
            frameWriter.write(frame, out)
            println(s"Wrote image: $out (${frame.width}x${frame.height}, ${frame.pixelFormat})")
          }
          if (settings.outputPath.isEmpty) {
            println(s"Captured frame (${frame.width}x${frame.height}, ${frame.pixelFormat}), no output file requested.")
          }
        } finally stream.close()
      } finally camera.close()
    } finally system.close()
    0
  }

  private def printUsage(): Unit = {
    println(
      """imperx-capture options:
        |  --ip <camera-ip>
        |  --out <output-path>          (png/fits/fit/raw extension)
        |  --timeout-ms <ms>
        |  --exposure-micros <double>
        |  --gain <double>
        |  --pixel-format <name>
        |
        |Defaults load from imperx-capture.properties and can be overridden by env vars:
        |  IMPERX_CAMERA_IP, IMPERX_CAPTURE_OUTPUT, IMPERX_GRAB_TIMEOUT_MS,
        |  IMPERX_EXPOSURE_MICROS, IMPERX_GAIN, IMPERX_PIXEL_FORMAT
        |""".stripMargin
    )
  }
}

object ImperxCaptureApp {
  private val ConfigResource = "/imperx-capture.properties"

  def default(): ImperxCaptureApp = {
    new ImperxCaptureApp(JnrBindingProvider, SystemEnvProvider, new DefaultFrameWriter, loadProps())
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
