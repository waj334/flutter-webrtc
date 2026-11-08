package com.cloudwebrtc.webrtc.video.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The zoom levels a camera app marks on its dial: one for each lens of a
 * multi-camera cluster, where that lens is the whole picture, and 2x.
 *
 * No Android dependencies, so scripts/test_camera_region_math.sh can check it.
 *
 * A lens's zoom is its field of view against the main lens's: focal length over
 * sensor width, since that is what sets how much of the scene a frame holds. The
 * main lens is the one with the biggest sensor, which is where a phone's logical
 * camera sits at 1x.
 */
public final class CameraZoomStops {
  private CameraZoomStops() {}

  /** Two stops this close, in ratio, are the same stop. */
  private static final double SAME = .03;

  /**
   * @param focalMm each physical lens's focal length
   * @param sensorWidthMm each one's sensor width; zero or less when unknown
   * @return stops in ascending order, within [min, max], always including 1x
   */
  public static float[] stops(float[] focalMm, float[] sensorWidthMm, float min, float max) {
    final List<Double> out = new ArrayList<>();
    int main = -1;
    double mainArea = -1;
    for (int i = 0; i < focalMm.length; i++) {
      if (!(focalMm[i] > 0) || !(sensorWidthMm[i] > 0)) continue;
      // Sensors share a shape across a phone's cameras, so width stands for area.
      if (sensorWidthMm[i] > mainArea + 1e-6) {
        mainArea = sensorWidthMm[i];
        main = i;
      }
    }
    if (main >= 0) {
      final double base = focalMm[main] / sensorWidthMm[main];
      for (int i = 0; i < focalMm.length; i++) {
        if (!(focalMm[i] > 0) || !(sensorWidthMm[i] > 0)) continue;
        add(out, (focalMm[i] / sensorWidthMm[i]) / base, min, max);
      }
    }
    add(out, 1.0, min, max);
    // Not a lens: a crop of the main sensor that is still sharp, which camera
    // apps mark all the same.
    add(out, 2.0, min, max);
    Collections.sort(out);
    final float[] result = new float[out.size()];
    for (int i = 0; i < result.length; i++) result[i] = (float) (double) out.get(i);
    return result;
  }

  private static void add(List<Double> stops, double ratio, float min, float max) {
    // A lens a hair outside the range is that end of the range.
    if (ratio < min && ratio > min * (1 - 2 * SAME)) ratio = min;
    if (ratio > max && ratio < max * (1 + 2 * SAME)) ratio = max;
    if (ratio < min || ratio > max) return;
    for (double s : stops) {
      if (Math.abs(Math.log(ratio / s)) < SAME) return;
    }
    stops.add(ratio);
  }
}
