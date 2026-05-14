#ifndef IMPERX_BRIDGE_H
#define IMPERX_BRIDGE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

enum imperx_status {
  IMPERX_OK = 0,
  IMPERX_ERR_GENERIC = 1,
  IMPERX_ERR_INVALID_ARG = 2,
  IMPERX_ERR_TIMEOUT = 3
};

typedef struct imperx_camera_info {
  char id[128];
  char model[64];
  char ip_address[64];
} imperx_camera_info;

typedef struct imperx_frame {
  uint32_t width;
  uint32_t height;
  uint32_t stride;
  uint64_t timestamp_nanos;
  uint64_t frame_id;
  uint32_t pixel_format;
  const uint8_t* data;
  size_t size_bytes;
} imperx_frame;

int imperx_initialize(void);
int imperx_shutdown(void);
int imperx_last_error(char* buffer, int buffer_len);
int imperx_list_cameras_count(uint32_t* out_count);
int imperx_get_camera_info(uint32_t index,
                           char* id_buffer,
                           int id_buffer_len,
                           char* model_buffer,
                           int model_buffer_len,
                           char* ip_buffer,
                           int ip_buffer_len);
int imperx_open_camera(const char* camera_id, uint64_t* out_camera_handle);
int imperx_close_camera(uint64_t camera_handle);
int imperx_set_float_param(uint64_t camera_handle, const char* param_name, double value);
int imperx_set_enum_param(uint64_t camera_handle, const char* param_name, const char* value);
int imperx_start_stream(uint64_t camera_handle, uint64_t* out_stream_handle);
int imperx_stop_stream(uint64_t stream_handle);
int imperx_grab_frame(uint64_t stream_handle,
                      uint32_t timeout_ms,
                      uint8_t* out_buffer,
                      size_t out_buffer_len,
                      size_t* out_filled_size,
                      uint32_t* out_width,
                      uint32_t* out_height,
                      uint64_t* out_frame_id,
                      uint64_t* out_timestamp_nanos,
                      uint32_t* out_pixel_format_code);

#ifdef __cplusplus
}
#endif

#endif
