package org.webrtc;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.os.Build;
import android.util.Log;
import java.util.Set;

/**
 * Says which lens of a multi-camera cluster is taking the picture, when that
 * changes.
 *
 * A phone's back cameras are one logical camera to an app, and the camera
 * system hands the picture from the ultrawide to the main to the telephoto lens
 * as the zoom crosses each lens's range -- but only if the app zooms with the
 * zoom ratio and leaves the choice to it. Whether that is happening is not
 * visible from the picture alone, so this logs it.
 */
public final class CameraLensLog {
  private static final String TAG = "CameraLens";
  private static volatile String lastPhysicalId = "";
  private static volatile float lastZoom = -1f;

  private CameraLensLog() {}

  /** Called with the result of each frame; logs only when the lens or zoom changes. */
  public static void onResult(CaptureResult result) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || result == null) {
      return;
    }
    String physical = result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
    Float focal = result.get(CaptureResult.LENS_FOCAL_LENGTH);
    Float zoom = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ? result.get(CaptureResult.CONTROL_ZOOM_RATIO) : null;
    String now = physical == null ? "(single camera)" : physical;
    if (!now.equals(lastPhysicalId)) {
      Log.i(TAG, "active lens " + lastPhysicalId + " -> " + now
          + " focal=" + focal + "mm zoom=" + zoom);
      lastPhysicalId = now;
    }
    if (zoom != null && Math.abs(zoom - lastZoom) >= .25f) {
      lastZoom = zoom;
      Log.d(TAG, "zoom=" + zoom + " lens=" + now + " focal=" + focal + "mm");
    }
  }

  /** What the cluster is made of, once, when a camera is opened. */
  public static void describe(CameraCharacteristics c, String cameraId,
      android.hardware.camera2.CameraManager manager) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
      return;
    }
    try {
      Set<String> physical = c.getPhysicalCameraIds();
      StringBuilder out = new StringBuilder("camera " + cameraId + " physical=[");
      for (String id : physical) {
        CameraCharacteristics p = manager.getCameraCharacteristics(id);
        float[] focals = p.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        out.append(id).append(':').append(focals != null && focals.length > 0 ? focals[0] : 0f)
            .append("mm ");
      }
      out.append(']');
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        out.append(" zoomRange=")
            .append(c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE));
      }
      Log.i(TAG, out.toString());
    } catch (Throwable t) {
      Log.w(TAG, "could not describe camera " + cameraId + ": " + t);
    }
  }
}
