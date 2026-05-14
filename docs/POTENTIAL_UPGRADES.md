# Potential Upgrades

This document tracks high-value upgrades for reliability, ergonomics, and long-term maintainability.

## 1) Non-Blocking Stream Abstraction (Recommended Next)

Current usage is a blocking `while` loop over `grabFrame(timeoutMs)`.  
Add a higher-level non-blocking abstraction so applications can subscribe to frames without managing loop/lifecycle details.

### Suggested API Shape

Option A: callback-based

```scala
trait FrameSubscription extends AutoCloseable
def startFrameStream(
  onFrame: Frame => Unit,
  onError: Throwable => Unit,
  timeoutMs: Long = 2000
): FrameSubscription
```

Option B: queue/iterator-based

```scala
def startFrameQueue(timeoutMs: Long = 2000): java.util.concurrent.BlockingQueue[Frame]
```

Option C: integration module for streaming libraries

- `fs2.Stream[IO, Frame]` module
- Akka Streams `Source[Frame, _]` module

### Why

- Cleaner app integration
- Better separation of capture thread from processing thread
- Easier backpressure/failure handling

## 2) Pixel Format Conversion Layer

Add native conversion support for common formats so PNG/FITS export is not Mono8-only.

Targets:

- Bayer8 -> Mono/RGB
- Mono10/12 packed -> Mono16 or scaled Mono8
- RGB8 direct export

## 3) Metadata Sidecar Output

Add optional JSON sidecar for each saved image:

- frame id
- timestamp
- width/height
- pixel format
- camera id/ip
- applied camera config

Useful for reproducibility and downstream processing.

## 4) Reconnect/Retry Policies

Add configurable retry logic for transient GigE failures:

- stream grab timeout retries
- camera reconnect attempts
- exponential backoff

## 5) Stronger Hardware Regression Matrix

Split tests into explicit categories:

- smoke (fast)
- soak (long-run)
- stress (high frame rate / long duration)

Add summary counters:

- dropped frames
- incomplete frames
- frame id regressions

## 6) Packaging & Distribution

Make consumer adoption easier:

- publish `imperx-camera-core` to internal artifact repo
- provide launch scripts that set native library paths
- optionally publish prebuilt native bridge artifacts per platform
