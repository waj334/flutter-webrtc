package org.webrtc;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;

/**
 * Lets the app add to a camera session's repeating request -- zoom, where to
 * focus -- without reaching into the session's fields. The session builds the
 * request it needs to run at all and hands it over to be added to.
 */
public interface CameraRequestTuner {
  /** Adds to the request. Runs on the camera's thread. May throw to refuse. */
  void tune(CaptureRequest.Builder request, CameraCharacteristics characteristics,
      String cameraId, int captureWidth, int captureHeight);

  /** Reports back on the camera's thread: null on success, or what went wrong. */
  interface Done {
    void done(String error);
  }
}
