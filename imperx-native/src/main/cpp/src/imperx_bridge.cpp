// Imperx C++ SDK bridge for Scala/JVM consumers.
//
// Why this file exists:
// - The Imperx SDK exposes a C++ object API (classes, virtual methods, C++ types).
// - Our Scala code uses JNR-FFI, which binds cleanly to C ABI functions, not arbitrary C++ class layouts.
// - This file provides a stable C interface (imperx_bridge.h) that hides C++ details and lifecycle rules.
//
// Why JNR-FFI over JNI:
// - Lower integration and maintenance overhead on the JVM side (no generated JNI glue layer).
// - Faster iteration for API evolution because Scala calls map directly to C symbols.
// - Better portability/readability for this use case where a thin C ABI wrapper is sufficient.
// - JNI remains an option if we later need tighter JVM/native control or peak micro-optimization.
//
// Responsibilities:
// - Translate between C handles and Imperx C++ objects.
// - Normalize errors into simple status codes + last-error text.
// - Keep JVM-facing signatures stable even if SDK-specific C++ internals change.
//
// Without this bridge, Scala would need JNI-specific bindings tightly coupled to Imperx C++ types.
#include "imperx_bridge.h"
#include "IpxCameraApi.h"

#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <string>
#include <vector>

namespace {
std::string g_last_error;
bool g_initialized = false;
uint64_t g_next_handle = 1000;
std::mutex g_state_mutex;
std::unordered_map<uint64_t, IpxCam::Device*> g_open_cameras;
std::unordered_map<uint64_t, IpxCam::Stream*> g_open_streams;
std::unordered_map<uint64_t, std::vector<IpxCam::Buffer*>> g_stream_buffers;
std::unordered_map<uint64_t, uint64_t> g_stream_to_camera;

void set_error(const std::string& message) { g_last_error = message; }

bool check_initialized() {
  if (!g_initialized) {
    set_error("SDK not initialized");
    return false;
  }
  return true;
}

int copy_text(const char* src, char* dst, int dst_len) {
  if (dst == nullptr || dst_len <= 0) {
    set_error("Invalid output buffer");
    return IMPERX_ERR_INVALID_ARG;
  }
  const size_t copy_len = std::min(static_cast<size_t>(dst_len - 1), std::strlen(src));
  std::memcpy(dst, src, copy_len);
  dst[copy_len] = '\0';
  return IMPERX_OK;
}

std::vector<IpxCam::DeviceInfo*> enumerate_devices() {
  std::vector<IpxCam::DeviceInfo*> out;
  IpxCam::System* system = IpxCam::IpxCam_GetSystem();
  if (!system) {
    set_error("IpxCam_GetSystem() returned null");
    return out;
  }

  auto del_iface = [](IpxCam::InterfaceList* l) { if (l) l->Release(); };
  std::unique_ptr<IpxCam::InterfaceList, decltype(del_iface)> ifaces(system->GetInterfaceList(), del_iface);
  if (!ifaces) return out;

  for (auto* iface = ifaces->GetFirst(); iface; iface = ifaces->GetNext()) {
    iface->ReEnumerateDevices(nullptr, 200);
    auto del_dev = [](IpxCam::DeviceInfoList* l) { if (l) l->Release(); };
    std::unique_ptr<IpxCam::DeviceInfoList, decltype(del_dev)> devices(iface->GetDeviceInfoList(), del_dev);
    if (!devices) continue;
    for (auto* dev = devices->GetFirst(); dev; dev = devices->GetNext()) {
      if (dev) out.push_back(dev);
    }
  }
  return out;
}
}  // namespace

int imperx_initialize(void) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (g_initialized) {
    return IMPERX_OK;
  }

  IpxCam::System* system = IpxCam::IpxCam_GetSystem();
  if (!system) {
    set_error("Unable to initialize Imperx system");
    return IMPERX_ERR_GENERIC;
  }
  g_initialized = true;
  g_open_cameras.clear();
  g_open_streams.clear();
  g_stream_buffers.clear();
  g_stream_to_camera.clear();
  g_next_handle = 1000;
  set_error("");
  return IMPERX_OK;
}

int imperx_shutdown(void) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (!g_initialized) {
    return IMPERX_OK;
  }

  for (auto& kv : g_open_streams) {
    uint64_t stream_handle = kv.first;
    IpxCam::Stream* stream = kv.second;
    if (stream) {
      auto cam_it = g_stream_to_camera.find(stream_handle);
      if (cam_it != g_stream_to_camera.end()) {
        auto dev_it = g_open_cameras.find(cam_it->second);
        if (dev_it != g_open_cameras.end() && dev_it->second) {
          auto* params = dev_it->second->GetCameraParameters();
          if (params) {
            params->ExecuteCommand("AcquisitionStop");
            params->SetIntegerValue("TLParamsLocked", 0);
          }
        }
      }
      stream->StopAcquisition(1);
      stream->FlushBuffers(IpxCam::Flush_AllDiscard);
      auto it = g_stream_buffers.find(kv.first);
      if (it != g_stream_buffers.end()) {
        for (IpxCam::Buffer* b : it->second) {
          if (b) stream->RevokeBuffer(b);
        }
      }
      stream->Release();
    }
  }
  g_stream_buffers.clear();
  g_open_streams.clear();
  g_stream_to_camera.clear();

  for (auto& kv : g_open_cameras) {
    if (kv.second) kv.second->Release();
  }
  g_open_cameras.clear();

  g_initialized = false;
  set_error("");
  return IMPERX_OK;
}

int imperx_last_error(char* buffer, int buffer_len) {
  if (buffer == nullptr || buffer_len <= 0) {
    return IMPERX_ERR_INVALID_ARG;
  }
  const size_t copy_len = std::min(static_cast<size_t>(buffer_len - 1), g_last_error.size());
  std::memcpy(buffer, g_last_error.data(), copy_len);
  buffer[copy_len] = '\0';
  return IMPERX_OK;
}

int imperx_list_cameras_count(uint32_t* out_count) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (!check_initialized()) return IMPERX_ERR_GENERIC;
  if (out_count == nullptr) {
    set_error("out_count is null");
    return IMPERX_ERR_INVALID_ARG;
  }
  auto devices = enumerate_devices();
  *out_count = static_cast<uint32_t>(devices.size());
  return IMPERX_OK;
}

int imperx_get_camera_info(uint32_t index,
                           char* id_buffer,
                           int id_buffer_len,
                           char* model_buffer,
                           int model_buffer_len,
                           char* ip_buffer,
                           int ip_buffer_len) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (!check_initialized()) return IMPERX_ERR_GENERIC;
  auto devices = enumerate_devices();
  if (index >= devices.size()) {
    set_error("Camera index out of range");
    return IMPERX_ERR_INVALID_ARG;
  }

  IpxCam::DeviceInfo* camera = devices[index];
  IpxCamErr ip_err = IPX_CAM_ERR_OK;
  const char* id = camera ? camera->GetID() : nullptr;
  const char* model = camera ? camera->GetModel() : nullptr;
  const char* ip = camera ? camera->GetIPAddress(&ip_err) : nullptr;
  int rc = copy_text(id ? id : "", id_buffer, id_buffer_len);
  if (rc != IMPERX_OK) return rc;
  rc = copy_text(model ? model : "", model_buffer, model_buffer_len);
  if (rc != IMPERX_OK) return rc;
  return copy_text((ip_err == IPX_CAM_ERR_OK && ip) ? ip : "", ip_buffer, ip_buffer_len);
}

int imperx_open_camera(const char* camera_id, uint64_t* out_camera_handle) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (!check_initialized()) return IMPERX_ERR_GENERIC;
  if (camera_id == nullptr || out_camera_handle == nullptr) {
    set_error("Invalid camera open arguments");
    return IMPERX_ERR_INVALID_ARG;
  }

  auto devices = enumerate_devices();
  IpxCam::DeviceInfo* matched = nullptr;
  for (IpxCam::DeviceInfo* info : devices) {
    if (info && info->GetID() && std::strcmp(info->GetID(), camera_id) == 0) {
      matched = info;
      break;
    }
  }
  if (!matched) {
    set_error("Camera ID not found");
    return IMPERX_ERR_INVALID_ARG;
  }

  IpxCamErr err = IPX_CAM_ERR_OK;
  IpxCam::Device* device = IpxCam::IpxCam_CreateDevice(matched, IpxCam::Exclusive, &err);
  if (!device || err != IPX_CAM_ERR_OK) {
    set_error("IpxCam_CreateDevice failed");
    return IMPERX_ERR_GENERIC;
  }

  const uint64_t handle = ++g_next_handle;
  g_open_cameras[handle] = device;
  *out_camera_handle = handle;
  return IMPERX_OK;
}

int imperx_close_camera(uint64_t camera_handle) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (!check_initialized()) return IMPERX_ERR_GENERIC;
  auto it = g_open_cameras.find(camera_handle);
  if (it == g_open_cameras.end()) {
    set_error("Unknown camera handle");
    return IMPERX_ERR_INVALID_ARG;
  }
  if (it->second) it->second->Release();
  g_open_cameras.erase(it);
  return IMPERX_OK;
}

int imperx_set_float_param(uint64_t camera_handle, const char* param_name, double value) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (!check_initialized()) return IMPERX_ERR_GENERIC;
  if (!param_name) {
    set_error("param_name is null");
    return IMPERX_ERR_INVALID_ARG;
  }
  auto it = g_open_cameras.find(camera_handle);
  if (it == g_open_cameras.end() || !it->second) {
    set_error("Unknown camera handle");
    return IMPERX_ERR_INVALID_ARG;
  }
  auto* params = it->second->GetCameraParameters();
  if (!params) {
    set_error("GetCameraParameters returned null");
    return IMPERX_ERR_GENERIC;
  }

  std::vector<std::string> candidates;
  candidates.emplace_back(param_name);
  if (std::strcmp(param_name, "ExposureTime") == 0) {
    candidates.emplace_back("ExposureTimeAbs");
    candidates.emplace_back("ExposureTimeRaw");
  } else if (std::strcmp(param_name, "Gain") == 0) {
    candidates.emplace_back("GainRaw");
    candidates.emplace_back("AnalogGain");
  }

  for (const auto& name : candidates) {
    if (params->SetFloatValue(name.c_str(), value) == IPX_CAM_ERR_OK) return IMPERX_OK;
    if (params->SetIntegerValue(name.c_str(), static_cast<int64_t>(value)) == IPX_CAM_ERR_OK) return IMPERX_OK;
  }
  set_error(std::string("Failed to set numeric parameter '") + param_name + "'");
  return IMPERX_ERR_GENERIC;
}

int imperx_set_enum_param(uint64_t camera_handle, const char* param_name, const char* value) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (!check_initialized()) return IMPERX_ERR_GENERIC;
  if (!param_name || !value) {
    set_error("param_name/value is null");
    return IMPERX_ERR_INVALID_ARG;
  }
  auto it = g_open_cameras.find(camera_handle);
  if (it == g_open_cameras.end() || !it->second) {
    set_error("Unknown camera handle");
    return IMPERX_ERR_INVALID_ARG;
  }
  auto* params = it->second->GetCameraParameters();
  if (!params) {
    set_error("GetCameraParameters returned null");
    return IMPERX_ERR_GENERIC;
  }
  IpxCamErr err = params->SetEnumValueStr(param_name, value);
  if (err != IPX_CAM_ERR_OK) {
    set_error(std::string("SetEnumValueStr failed for ") + param_name);
    return IMPERX_ERR_GENERIC;
  }
  return IMPERX_OK;
}

int imperx_start_stream(uint64_t camera_handle, uint64_t* out_stream_handle) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (!check_initialized()) return IMPERX_ERR_GENERIC;
  if (out_stream_handle == nullptr) {
    set_error("out_stream_handle is null");
    return IMPERX_ERR_INVALID_ARG;
  }
  auto cam_it = g_open_cameras.find(camera_handle);
  if (cam_it == g_open_cameras.end() || !cam_it->second) {
    set_error("Unknown camera handle");
    return IMPERX_ERR_INVALID_ARG;
  }
  IpxCam::Device* device = cam_it->second;
  if (device->GetNumStreams() < 1) {
    set_error("Device has no streams");
    return IMPERX_ERR_GENERIC;
  }
  IpxCam::Stream* stream = device->GetStreamByIndex(0);
  if (!stream) {
    set_error("GetStreamByIndex(0) returned null");
    return IMPERX_ERR_GENERIC;
  }

  const size_t buf_size = stream->GetBufferSize();
  const size_t min_buffers = std::max<size_t>(stream->GetMinNumBuffers(), 4);
  std::vector<IpxCam::Buffer*> buffers;
  buffers.reserve(min_buffers);
  for (size_t i = 0; i < min_buffers; ++i) {
    IpxCamErr err = IPX_CAM_ERR_OK;
    IpxCam::Buffer* b = stream->CreateBuffer(buf_size, nullptr, &err);
    if (!b || err != IPX_CAM_ERR_OK) {
      set_error("CreateBuffer failed");
      for (IpxCam::Buffer* created : buffers) stream->RevokeBuffer(created);
      return IMPERX_ERR_GENERIC;
    }
    buffers.push_back(b);
  }
  if (stream->StartAcquisition() != IPX_CAM_ERR_OK) {
    set_error("StartAcquisition failed");
    for (IpxCam::Buffer* b : buffers) stream->RevokeBuffer(b);
    return IMPERX_ERR_GENERIC;
  }
  auto* params = device->GetCameraParameters();
  if (!params || params->SetIntegerValue("TLParamsLocked", 1) != IPX_CAM_ERR_OK ||
      params->ExecuteCommand("AcquisitionStart") != IPX_CAM_ERR_OK) {
    stream->StopAcquisition(1);
    stream->FlushBuffers(IpxCam::Flush_AllDiscard);
    for (IpxCam::Buffer* b : buffers) stream->RevokeBuffer(b);
    set_error("Failed to start camera acquisition");
    return IMPERX_ERR_GENERIC;
  }

  const uint64_t stream_handle = ++g_next_handle;
  g_open_streams[stream_handle] = stream;
  g_stream_buffers[stream_handle] = std::move(buffers);
  g_stream_to_camera[stream_handle] = camera_handle;
  *out_stream_handle = stream_handle;
  return IMPERX_OK;
}

int imperx_stop_stream(uint64_t stream_handle) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (!check_initialized()) return IMPERX_ERR_GENERIC;
  auto it = g_open_streams.find(stream_handle);
  if (it == g_open_streams.end() || !it->second) {
    set_error("Unknown stream handle");
    return IMPERX_ERR_INVALID_ARG;
  }
  IpxCam::Stream* stream = it->second;
  auto cam_it = g_stream_to_camera.find(stream_handle);
  if (cam_it != g_stream_to_camera.end()) {
    auto dev_it = g_open_cameras.find(cam_it->second);
    if (dev_it != g_open_cameras.end() && dev_it->second) {
      auto* params = dev_it->second->GetCameraParameters();
      if (params) {
        params->ExecuteCommand("AcquisitionStop");
        params->SetIntegerValue("TLParamsLocked", 0);
      }
    }
  }
  stream->StopAcquisition(1);
  stream->FlushBuffers(IpxCam::Flush_AllDiscard);
  auto buf_it = g_stream_buffers.find(stream_handle);
  if (buf_it != g_stream_buffers.end()) {
    for (IpxCam::Buffer* b : buf_it->second) {
      if (b) stream->RevokeBuffer(b);
    }
    g_stream_buffers.erase(buf_it);
  }
  stream->Release();
  g_open_streams.erase(it);
  g_stream_to_camera.erase(stream_handle);
  return IMPERX_OK;
}

int imperx_grab_frame(uint64_t stream_handle,
                      uint32_t timeout_ms,
                      uint8_t* out_buffer,
                      size_t out_buffer_len,
                      size_t* out_filled_size,
                      uint32_t* out_width,
                      uint32_t* out_height,
                      uint64_t* out_frame_id,
                      uint64_t* out_timestamp_nanos,
                      uint32_t* out_pixel_format_code) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (!check_initialized()) return IMPERX_ERR_GENERIC;
  auto it = g_open_streams.find(stream_handle);
  if (it == g_open_streams.end() || !it->second) {
    set_error("Unknown stream handle");
    return IMPERX_ERR_INVALID_ARG;
  }
  if (out_buffer == nullptr || out_filled_size == nullptr || out_width == nullptr ||
      out_height == nullptr || out_frame_id == nullptr || out_timestamp_nanos == nullptr ||
      out_pixel_format_code == nullptr) {
    set_error("Invalid frame output arguments");
    return IMPERX_ERR_INVALID_ARG;
  }

  IpxCam::Stream* stream = it->second;
  IpxCamErr err = IPX_CAM_ERR_OK;
  IpxCam::Buffer* buffer = stream->GetBuffer(timeout_ms, &err);
  if (!buffer) {
    if (err == IPX_GC_ERR_TIMEOUT) {
      set_error("Frame timeout");
      return IMPERX_ERR_TIMEOUT;
    }
    set_error("GetBuffer failed");
    return IMPERX_ERR_GENERIC;
  }

  const size_t image_offset = buffer->GetImageOffset();
  const size_t src_size = buffer->GetBufferSize();
  if (src_size < image_offset) {
    stream->QueueBuffer(buffer);
    set_error("Invalid image offset");
    return IMPERX_ERR_GENERIC;
  }
  const size_t image_size = src_size - image_offset;
  if (out_buffer_len < image_size) {
    stream->QueueBuffer(buffer);
    set_error("Output buffer too small");
    return IMPERX_ERR_INVALID_ARG;
  }
  const uint8_t* image_ptr = static_cast<const uint8_t*>(buffer->GetBufferPtr()) + image_offset;
  std::memcpy(out_buffer, image_ptr, image_size);
  *out_filled_size = image_size;
  *out_width = static_cast<uint32_t>(buffer->GetWidth());
  *out_height = static_cast<uint32_t>(buffer->GetHeight());
  *out_frame_id = buffer->GetFrameID();
  *out_timestamp_nanos = buffer->GetTimestamp();
  *out_pixel_format_code = static_cast<uint32_t>(buffer->GetPixelFormat() & 0xffffffffu);
  stream->QueueBuffer(buffer);
  return IMPERX_OK;
}
