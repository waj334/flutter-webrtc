package com.cloudwebrtc.webrtc.video.camera;

/**
 * Where a tap on the picture lands on the sensor, with no Android dependencies so
 * scripts/test_camera_region_math.sh can check it.
 *
 * <p>The picture a person sees is the sensor's, rotated upright, cropped to the
 * stream's shape and then zoomed. A focus region is in the active array's own
 * coordinates, so a tap has to be walked back through each of those.
 */
public final class CameraRegionMath {
  private CameraRegionMath() {}

  /**
   * The clockwise turn that makes a sensor frame upright, which is the one WebRTC
   * stamps on its frames: the sensor's own orientation, plus the display's turn for
   * a front camera and minus it for a back one.
   */
  public static int frameRotation(int sensorOrientation, int displayRotation, boolean front) {
    int r = front ? sensorOrientation + displayRotation : sensorOrientation - displayRotation;
    return ((r % 360) + 360) % 360;
  }

  /**
   * A point in the upright picture (0..1 from its top left) as a point in the
   * sensor's frame (0..1 from its top left).
   */
  public static float[] uprightToSensor(float u, float v, int rotation) {
    switch (rotation) {
      case 90:
        return new float[] {v, 1f - u};
      case 180:
        return new float[] {1f - u, 1f - v};
      case 270:
        return new float[] {1f - v, u};
      default:
        return new float[] {u, v};
    }
  }

  /**
   * The part of the active array the stream shows: the largest centred region of
   * the stream's shape, divided by the zoom about the centre.
   *
   * @return left, top, right, bottom in active-array pixels
   */
  public static int[] visibleWindow(int activeWidth, int activeHeight,
      int streamWidth, int streamHeight, float zoom) {
    double w = activeWidth, h = activeHeight;
    if (streamWidth > 0 && streamHeight > 0) {
      // The stream is described in the sensor's orientation, so no swap is needed.
      double aspect = (double) streamWidth / streamHeight;
      if (w / h > aspect) w = h * aspect;
      else h = w / aspect;
    }
    double z = zoom >= 1f ? zoom : 1.0;
    w /= z;
    h /= z;
    double cx = activeWidth / 2.0, cy = activeHeight / 2.0;
    return new int[] {
        (int) Math.round(cx - w / 2), (int) Math.round(cy - h / 2),
        (int) Math.round(cx + w / 2), (int) Math.round(cy + h / 2)};
  }

  /**
   * A square focus region centred on a point of the sensor's frame, kept inside
   * the active array.
   *
   * @param window the visible window; the point is a fraction of it
   * @param sizeFraction the side, as a fraction of the window's shorter side
   * @return left, top, right, bottom in active-array pixels
   */
  public static int[] focusRegion(int[] window, float sx, float sy,
      int activeWidth, int activeHeight, float sizeFraction) {
    int ww = window[2] - window[0], wh = window[3] - window[1];
    double px = window[0] + clamp01(sx) * ww, py = window[1] + clamp01(sy) * wh;
    double half = Math.max(1.0, Math.min(ww, wh) * sizeFraction / 2.0);
    int left = (int) Math.round(Math.max(0, Math.min(activeWidth - 2 * half, px - half)));
    int top = (int) Math.round(Math.max(0, Math.min(activeHeight - 2 * half, py - half)));
    int side = (int) Math.round(2 * half);
    return new int[] {left, top, Math.min(activeWidth, left + side), Math.min(activeHeight, top + side)};
  }

  /** The crop rectangle for a digital zoom, centred. */
  public static int[] cropRegion(int activeWidth, int activeHeight, float zoom) {
    return visibleWindow(activeWidth, activeHeight, 0, 0, zoom);
  }

  private static float clamp01(float v) {
    return v < 0f ? 0f : Math.min(v, 1f);
  }
}
