package com.cloudwebrtc.webrtc.video.camera;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the app has asked of one camera: how far it is zoomed in and where it is told
 * to focus.
 *
 * <p>A camera has one repeating capture request, and each call used to replace it
 * with a fresh one, so setting the zoom lost the focus point and the other way
 * round. Every call now changes this and the whole request is rebuilt from it.
 * It is public because the effects pipeline needs the zoom: a zoomed picture has a
 * longer focal length.
 */
public final class CameraControlState {
  private static final Map<String, CameraControlState> STATES = new ConcurrentHashMap<>();

  /** The camera these settings were made for; another camera starts afresh. */
  volatile String cameraId;
  volatile float zoom = 1f;
  /** The frame rate the app asked for; 0 leaves the session's own choice of range. */
  volatile int targetFps;
  /** Focus point in the upright, unmirrored picture (0..1), or null for the camera's own choice. */
  volatile float[] focusPoint;

  static CameraControlState of(String trackId, String cameraId) {
    CameraControlState s = STATES.computeIfAbsent(trackId, k -> new CameraControlState());
    if (cameraId != null && !cameraId.equals(s.cameraId)) {
      s.cameraId = cameraId;
      s.zoom = 1f;
      s.focusPoint = null;
      s.targetFps = 0;
    }
    return s;
  }

  /** The zoom in force on a track's camera, 1 when none has been asked for. */
  public static float zoomRatio(String trackId, String cameraId) {
    CameraControlState s = trackId == null ? null : STATES.get(trackId);
    if (s == null || cameraId == null || !cameraId.equals(s.cameraId)) return 1f;
    return s.zoom;
  }

  static void forget(String trackId) {
    STATES.remove(trackId);
  }
}
