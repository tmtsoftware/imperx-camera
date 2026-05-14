package com.imperx.camera.tools

object ImperxCaptureCli {
  def main(args: Array[String]): Unit = {
    val app = ImperxCaptureApp.default()
    val code = app.run(args.toList)
    if (code != 0) sys.exit(code)
  }
}
