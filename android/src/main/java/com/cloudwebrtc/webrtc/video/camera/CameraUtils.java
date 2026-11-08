package com.cloudwebrtc.webrtc.video.camera;

import android.app.Activity;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.cloudwebrtc.webrtc.GetUserMediaImpl;
import com.cloudwebrtc.webrtc.utils.AnyThreadResult;
import com.cloudwebrtc.webrtc.video.VideoCapturerInfo;

import org.webrtc.Camera1Capturer;
import org.webrtc.Camera2Capturer;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.CameraLensLog;
import org.webrtc.HighSpeedCamera2Capturer;

import java.lang.reflect.Field;
import java.util.List;

import io.flutter.embedding.engine.systemchannels.PlatformChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class CameraUtils {
  private static final String TAG = "CameraUtils";
  Activity activity;
  private GetUserMediaImpl getUserMediaImpl;
  private boolean isTorchOn = false;
  private DeviceOrientationManager deviceOrientationManager;
  public CameraUtils(GetUserMediaImpl getUserMediaImpl, Activity activity) {
    this.getUserMediaImpl = getUserMediaImpl;
    this.activity = activity;
    this.deviceOrientationManager = new DeviceOrientationManager(activity, 0);
    // commented out because you cannot register a reciever when the app is terminated
    // because the activity is null?
    // this causes the call to break if the app is terminated
    // the manager seems to end up at handleOrientationChange which does not do
    // anything at the moment so this should be ok

    // TODO: get a proper fix at some point
    // this.deviceOrientationManager.start();
  }

  public void setFocusMode(MethodCall call, AnyThreadResult result) {
    String trackId = call.argument("trackId");
    String mode = call.argument("mode");
    VideoCapturerInfo info = getUserMediaImpl.getCapturerInfo(trackId);
    if (info == null) {
      resultError("setFocusMode", "Video capturer not found for id: " + trackId, result);
      return;
    }

    if (info.capturer instanceof Camera2Capturer) {
      CameraCaptureSession captureSession;
      CameraDevice cameraDevice;
      CameraEnumerationAndroid.CaptureFormat captureFormat;
      int fpsUnitFactor;
      Surface surface;
      Handler cameraThreadHandler;
      CameraManager manager;

      try {
        Object session =
                getPrivateProperty(
                        Camera2Capturer.class.getSuperclass(), info.capturer, "currentSession");
        manager =
                (CameraManager)
                        getPrivateProperty(Camera2Capturer.class, info.capturer, "cameraManager");
        captureSession =
                (CameraCaptureSession)
                        getPrivateProperty(session.getClass(), session, "captureSession");
        cameraDevice =
                (CameraDevice) getPrivateProperty(session.getClass(), session, "cameraDevice");
        captureFormat =
                (CameraEnumerationAndroid.CaptureFormat) getPrivateProperty(session.getClass(), session, "captureFormat");
        fpsUnitFactor = (int) getPrivateProperty(session.getClass(), session, "fpsUnitFactor");
        surface = (Surface) getPrivateProperty(session.getClass(), session, "surface");
        cameraThreadHandler =
                (Handler) getPrivateProperty(session.getClass(), session, "cameraThreadHandler");
      } catch (NoSuchFieldWithNameException e) {
        // Most likely the upstream Camera2Capturer class have changed
        resultError("setFocusMode", "[FocusMode] Failed to get `" + e.fieldName + "` from `" + e.className + "`", result);
        return;
      }

      try {
        final CaptureRequest.Builder captureRequestBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        switch (mode) {
          case "locked":
            // When locking the auto-focus the camera device should do a one-time focus and afterwards
            // set the auto-focus to idle. This is accomplished by setting the CONTROL_AF_MODE to
            // CONTROL_AF_MODE_AUTO.
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            break;
          case "auto":
            captureRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            break;
          default:
            break;
        }

        captureRequestBuilder.set(
                CaptureRequest.FLASH_MODE,
                isTorchOn ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);

        captureRequestBuilder.set(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                new Range<>(
                        captureFormat.framerate.min / fpsUnitFactor,
                        captureFormat.framerate.max / fpsUnitFactor));

        //captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
        captureRequestBuilder.addTarget(surface);
        captureSession.setRepeatingRequest(
                captureRequestBuilder.build(), null, cameraThreadHandler);
      } catch (CameraAccessException e) {
        // Should never happen since we are already accessing the camera
        throw new RuntimeException(e);
      }


      result.success(null);
      return;
    }

    if (info.capturer instanceof Camera1Capturer) {
      Camera camera;
      try {
        Object session =
                getPrivateProperty(
                        Camera1Capturer.class.getSuperclass(), info.capturer, "currentSession");
        camera = (Camera) getPrivateProperty(session.getClass(), session, "camera");
      } catch (NoSuchFieldWithNameException e) {
        // Most likely the upstream Camera1Capturer class have changed
        resultError("setFocusMode", "[FocusMode] Failed to get `" + e.fieldName + "` from `" + e.className + "`", result);
        return;
      }

      Camera.Parameters params = camera.getParameters();
      params.setFlashMode(
              isTorchOn ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
      if(!params.getSupportedFocusModes().isEmpty()) {
        switch (mode) {
          case "locked":
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            break;
          case "auto":
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            break;
          default:
            break;
        }
        result.success(null);
        return;
      }
    }
    resultError("setFocusMode", "[FocusMode] Video capturer not compatible", result);
  }

  public void setFocusPoint(MethodCall call, Point focusPoint, AnyThreadResult result) {
    String trackId = call.argument("trackId");
    String mode = call.argument("mode");
    VideoCapturerInfo info = getUserMediaImpl.getCapturerInfo(trackId);
    if (info == null) {
      resultError("setFocusMode", "Video capturer not found for id: " + trackId, result);
      return;
    }

    if (info.capturer instanceof Camera2Capturer) {
      // x and y are fractions of the upright picture, unmirrored; null clears the point.
      final float[] at = focusPoint.x == null || focusPoint.y == null
          ? null : new float[] {focusPoint.x.floatValue(), focusPoint.y.floatValue()};
      camera2("setFocusPoint", trackId, info, (state, c) -> state.focusPoint = at, result);
      return;
    }
    if (info.capturer instanceof HighSpeedCamera2Capturer) {
      final float[] at = focusPoint.x == null || focusPoint.y == null
          ? null : new float[] {focusPoint.x.floatValue(), focusPoint.y.floatValue()};
      highSpeed("setFocusPoint", trackId, info, (state, c) -> state.focusPoint = at, result);
      return;
    }

    if (info.capturer instanceof Camera1Capturer) {
      Camera camera;
      try {
        Object session =
                getPrivateProperty(
                        Camera1Capturer.class.getSuperclass(), info.capturer, "currentSession");
        camera = (Camera) getPrivateProperty(session.getClass(), session, "camera");
      } catch (NoSuchFieldWithNameException e) {
        // Most likely the upstream Camera1Capturer class have changed
        resultError("setFocusMode", "[FocusMode] Failed to get `" + e.fieldName + "` from `" + e.className + "`", result);
        return;
      }

      Camera.Parameters params = camera.getParameters();
      params.setFlashMode(
              isTorchOn ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
      params.setFocusAreas(null);

      result.success(null);
      return;
    }
    resultError("setFocusMode", "[FocusMode] Video capturer not compatible", result);
  }

  public void setExposureMode(MethodCall call, AnyThreadResult result) {}

  public void setExposurePoint(MethodCall call,Point exposurePoint,  AnyThreadResult result) {
    String trackId = call.argument("trackId");
    String mode = call.argument("mode");
    VideoCapturerInfo info = getUserMediaImpl.getCapturerInfo(trackId);
    if (info == null) {
      resultError("setExposurePoint", "Video capturer not found for id: " + trackId, result);
      return;
    }

    if (info.capturer instanceof Camera2Capturer) {
      CameraCaptureSession captureSession;
      CameraDevice cameraDevice;
      CameraEnumerationAndroid.CaptureFormat captureFormat;
      int fpsUnitFactor;
      Surface surface;
      Handler cameraThreadHandler;
      CameraManager manager;

      try {
        Object session =
                getPrivateProperty(
                        Camera2Capturer.class.getSuperclass(), info.capturer, "currentSession");
        manager =
                (CameraManager)
                        getPrivateProperty(Camera2Capturer.class, info.capturer, "cameraManager");
        captureSession =
                (CameraCaptureSession)
                        getPrivateProperty(session.getClass(), session, "captureSession");
        cameraDevice =
                (CameraDevice) getPrivateProperty(session.getClass(), session, "cameraDevice");
        captureFormat =
                (CameraEnumerationAndroid.CaptureFormat) getPrivateProperty(session.getClass(), session, "captureFormat");
        fpsUnitFactor = (int) getPrivateProperty(session.getClass(), session, "fpsUnitFactor");
        surface = (Surface) getPrivateProperty(session.getClass(), session, "surface");
        cameraThreadHandler =
                (Handler) getPrivateProperty(session.getClass(), session, "cameraThreadHandler");
      } catch (NoSuchFieldWithNameException e) {
        // Most likely the upstream Camera2Capturer class have changed
        resultError("setExposurePoint", "[setExposurePoint] Failed to get `" + e.fieldName + "` from `" + e.className + "`", result);
        return;
      }

      try {
        final CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(cameraDevice.getId());
        final CaptureRequest.Builder captureRequestBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

        if(CameraRegionUtils.getControlMaxRegionsAutoExposure(cameraCharacteristics) <= 0) {
          resultError("setExposurePoint", "[setExposurePoint] Camera does not support auto exposure", result);
          return;
        }

        MeteringRectangle exposureRectangle = null;
        Size cameraBoundaries = CameraRegionUtils.getCameraBoundaries(cameraCharacteristics, captureRequestBuilder);
        PlatformChannel.DeviceOrientation orientation = deviceOrientationManager.getLastUIOrientation();
        exposureRectangle =
                convertPointToMeteringRectangle(cameraBoundaries, exposurePoint.x, exposurePoint.y, orientation);
        if (exposureRectangle != null) {
          captureRequestBuilder.set(
                  CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[] {exposureRectangle});
        } else {
          MeteringRectangle[] defaultRegions = captureRequestBuilder.get(CaptureRequest.CONTROL_AE_REGIONS);
          captureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, defaultRegions);
        }

        captureRequestBuilder.addTarget(surface);
        captureSession.setRepeatingRequest(
                captureRequestBuilder.build(), null, cameraThreadHandler);
      } catch (CameraAccessException e) {
        // Should never happen since we are already accessing the camera
        throw new RuntimeException(e);
      }


      result.success(null);
      return;
    }

    if (info.capturer instanceof Camera1Capturer) {
      Camera camera;
      try {
        Object session =
                getPrivateProperty(
                        Camera1Capturer.class.getSuperclass(), info.capturer, "currentSession");
        camera = (Camera) getPrivateProperty(session.getClass(), session, "camera");
      } catch (NoSuchFieldWithNameException e) {
        // Most likely the upstream Camera1Capturer class have changed
        resultError("setFocusMode", "[FocusMode] Failed to get `" + e.fieldName + "` from `" + e.className + "`", result);
        return;
      }

      Camera.Parameters params = camera.getParameters();
      params.setFlashMode(
              isTorchOn ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
      params.setFocusAreas(null);
    }
    resultError("setFocusMode", "[FocusMode] Video capturer not compatible", result);
  }

  public void hasTorch(String trackId, MethodChannel.Result result) {
    VideoCapturerInfo info = getUserMediaImpl.getCapturerInfo(trackId);
    if (info == null) {
      resultError("hasTorch", "Video capturer not found for id: " + trackId, result);
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && info.capturer instanceof Camera2Capturer) {
      CameraManager manager;
      CameraDevice cameraDevice;

      try {
        Object session =
                getPrivateProperty(
                        Camera2Capturer.class.getSuperclass(), info.capturer, "currentSession");
        manager =
                (CameraManager)
                        getPrivateProperty(Camera2Capturer.class, info.capturer, "cameraManager");
        cameraDevice =
                (CameraDevice) getPrivateProperty(session.getClass(), session, "cameraDevice");
      } catch (NoSuchFieldWithNameException e) {
        // Most likely the upstream Camera2Capturer class have changed
        resultError("hasTorch", "[TORCH] Failed to get `" + e.fieldName + "` from `" + e.className + "`", result);
        return;
      }

      boolean flashIsAvailable;
      try {
        CameraCharacteristics characteristics =
                manager.getCameraCharacteristics(cameraDevice.getId());
        flashIsAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
      } catch (CameraAccessException e) {
        // Should never happen since we are already accessing the camera
        throw new RuntimeException(e);
      }

      result.success(flashIsAvailable);
      return;
    }

    if (info.capturer instanceof Camera1Capturer) {
      Camera camera;

      try {
        Object session =
                getPrivateProperty(
                        Camera1Capturer.class.getSuperclass(), info.capturer, "currentSession");
        camera = (Camera) getPrivateProperty(session.getClass(), session, "camera");
      } catch (NoSuchFieldWithNameException e) {
        // Most likely the upstream Camera1Capturer class have changed
        resultError("hasTorch", "[TORCH] Failed to get `" + e.fieldName + "` from `" + e.className + "`", result);
        return;
      }

      Camera.Parameters params = camera.getParameters();
      List<String> supportedModes = params.getSupportedFlashModes();

      result.success(
              supportedModes != null && supportedModes.contains(Camera.Parameters.FLASH_MODE_TORCH));
      return;
    }

    resultError("hasTorch", "[TORCH] Video capturer not compatible", result);
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public void setZoom(String trackId, double zoomLevel, MethodChannel.Result result) {
    VideoCapturerInfo info = getUserMediaImpl.getCapturerInfo(trackId);
    if (info == null) {
      resultError("setZoom", "Video capturer not found for id: " + trackId, result);
      return;
    }

    if (info.capturer instanceof Camera2Capturer) {
      final float zoom = (float) zoomLevel;
      camera2("setZoom", trackId, info, (state, c) -> state.zoom = zoom, result);
      return;
    }
    if (info.capturer instanceof HighSpeedCamera2Capturer) {
      final float zoom = (float) zoomLevel;
      highSpeed("setZoom", trackId, info, (state, c) -> state.zoom = zoom, result);
      return;
    }

    if (info.capturer instanceof Camera1Capturer) {
      Camera camera;
      try {
        Object session =
                getPrivateProperty(
                        Camera1Capturer.class.getSuperclass(), info.capturer, "currentSession");
        camera = (Camera) getPrivateProperty(session.getClass(), session, "camera");
      } catch (NoSuchFieldWithNameException e) {
        // Most likely the upstream Camera1Capturer class have changed
        resultError("setZoom", "[ZOOM] Failed to get `" + e.fieldName + "` from `" + e.className + "`", result);
        return;
      }

      Camera.Parameters params = camera.getParameters();
      params.setFlashMode(
              isTorchOn ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
      if(params.isZoomSupported()) {
        int maxZoom = params.getMaxZoom();
        double desiredZoom = Math.max(0, Math.min(zoomLevel, maxZoom));
        params.setZoom((int)desiredZoom);
        result.success(null);
        return;
      }
    }
    resultError("setZoom", "[ZOOM] Video capturer not compatible", result);
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public void setTorch(String trackId, boolean torch, MethodChannel.Result result) {
    VideoCapturerInfo info = getUserMediaImpl.getCapturerInfo(trackId);
    if (info == null) {
      resultError("setTorch", "Video capturer not found for id: " + trackId, result);
      return;
    }
    if (info.capturer instanceof Camera2Capturer) {
      isTorchOn = torch;
      camera2("setTorch", trackId, info, (state, c) -> {}, result);
      return;
    }
    if (info.capturer instanceof HighSpeedCamera2Capturer) {
      isTorchOn = torch;
      highSpeed("setTorch", trackId, info, (state, c) -> {}, result);
      return;
    }

    if (info.capturer instanceof Camera1Capturer) {
      Camera camera;
      try {
        Object session =
                getPrivateProperty(
                        Camera1Capturer.class.getSuperclass(), info.capturer, "currentSession");
        camera = (Camera) getPrivateProperty(session.getClass(), session, "camera");
      } catch (NoSuchFieldWithNameException e) {
        // Most likely the upstream Camera1Capturer class have changed
        resultError("setTorch", "[TORCH] Failed to get `" + e.fieldName + "` from `" + e.className + "`", result);
        return;
      }

      Camera.Parameters params = camera.getParameters();
      params.setFlashMode(
              torch ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
      camera.setParameters(params);

      result.success(null);
      isTorchOn = torch;
      return;
    }
    resultError("setTorch", "[TORCH] Video capturer not compatible", result);
  }


  /** Logs which lens is in use as the zoom moves; see {@link CameraLensLog}. */
  private static final CameraCaptureSession.CaptureCallback LENS_LOG =
      new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
            @NonNull CaptureRequest request, @NonNull android.hardware.camera2.TotalCaptureResult result) {
          CameraLensLog.onResult(result);
        }
      };

  private interface StateChange {
    void apply(CameraControlState state, CameraCharacteristics characteristics);
  }

  /** The parts of a running Camera2 session a new request is built from. */
  private static final class Camera2Parts {
    CameraCaptureSession captureSession;
    CameraDevice cameraDevice;
    CameraEnumerationAndroid.CaptureFormat captureFormat;
    int fpsUnitFactor;
    Surface surface;
    Handler cameraThreadHandler;
    CameraManager manager;
  }

  private Camera2Parts camera2Parts(VideoCapturerInfo info) throws NoSuchFieldWithNameException {
    Object session = getPrivateProperty(Camera2Capturer.class.getSuperclass(), info.capturer, "currentSession");
    Camera2Parts p = new Camera2Parts();
    p.manager = (CameraManager) getPrivateProperty(Camera2Capturer.class, info.capturer, "cameraManager");
    p.captureSession = (CameraCaptureSession) getPrivateProperty(session.getClass(), session, "captureSession");
    p.cameraDevice = (CameraDevice) getPrivateProperty(session.getClass(), session, "cameraDevice");
    p.captureFormat = (CameraEnumerationAndroid.CaptureFormat) getPrivateProperty(session.getClass(), session, "captureFormat");
    p.fpsUnitFactor = (int) getPrivateProperty(session.getClass(), session, "fpsUnitFactor");
    p.surface = (Surface) getPrivateProperty(session.getClass(), session, "surface");
    p.cameraThreadHandler = (Handler) getPrivateProperty(session.getClass(), session, "cameraThreadHandler");
    return p;
  }

  private static boolean has(int[] modes, int mode) {
    if (modes != null) for (int m : modes) if (m == mode) return true;
    return false;
  }

  private static float[] zoomRange(CameraCharacteristics c) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Range<Float> r = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
      if (r != null) return new float[] {r.getLower(), r.getUpper()};
    }
    Float max = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    return new float[] {1f, max == null ? 1f : max};
  }

  private int displayRotationDegrees() {
    try {
      if (activity == null) return 0;
      switch (activity.getWindowManager().getDefaultDisplay().getRotation()) {
        case Surface.ROTATION_90: return 90;
        case Surface.ROTATION_180: return 180;
        case Surface.ROTATION_270: return 270;
        default: return 0;
      }
    } catch (Throwable t) {
      return 0;
    }
  }

  /**
   * Adds the zoom and the focus point to a request.
   *
   * @return whether a focus region was set
   */
  private boolean addControls(CaptureRequest.Builder b, CameraCharacteristics c,
      CameraControlState state, int streamWidth, int streamHeight) {
    // Zoom: a ratio from Android 11, a crop of the active array before.
    final Rect active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
    final float[] range = zoomRange(c);
    state.zoom = Math.max(range[0], Math.min(state.zoom, range[1]));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        && c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) != null) {
      b.set(CaptureRequest.CONTROL_ZOOM_RATIO, state.zoom);
    } else if (active != null) {
      int[] r = CameraRegionMath.cropRegion(active.width(), active.height(), state.zoom);
      b.set(CaptureRequest.SCALER_CROP_REGION, new Rect(r[0], r[1], r[2], r[3]));
    }

    // Focus: the camera keeps refocusing on its own, aimed at the point if there is one.
    final int[] afModes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
    if (has(afModes, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {
      b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
    } else if (has(afModes, CaptureRequest.CONTROL_AF_MODE_AUTO)) {
      b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
    }
    final Integer maxAf = c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
    final float[] at = state.focusPoint;
    if (at == null || active == null || maxAf == null || maxAf <= 0) return false;
    Integer sensor = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
    Integer facing = c.get(CameraCharacteristics.LENS_FACING);
    int rotation = CameraRegionMath.frameRotation(sensor == null ? 90 : sensor, displayRotationDegrees(),
        facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT);
    float[] sp = CameraRegionMath.uprightToSensor(at[0], at[1], rotation);
    int[] window = CameraRegionMath.visibleWindow(active.width(), active.height(),
        streamWidth, streamHeight, state.zoom);
    int[] r = CameraRegionMath.focusRegion(window, sp[0], sp[1], active.width(), active.height(), .15f);
    b.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[] {
        new MeteringRectangle(r[0], r[1], r[2] - r[0], r[3] - r[1], MeteringRectangle.METERING_WEIGHT_MAX)});
    return true;
  }

  private static String owner(String trackId, VideoCapturerInfo info) {
    // Tracks that share a camera share its settings, under the track that opened it.
    return info instanceof GetUserMediaImpl.VideoCapturerInfoEx
        && ((GetUserMediaImpl.VideoCapturerInfoEx) info).primaryTrackId != null
        ? ((GetUserMediaImpl.VideoCapturerInfoEx) info).primaryTrackId : trackId;
  }

  /** The 60 fps path: a constrained high-speed session, which takes bursts only. */
  private void highSpeed(String tag, String trackId, VideoCapturerInfo info, StateChange change,
      MethodChannel.Result result) {
    final HighSpeedCamera2Capturer capturer = (HighSpeedCamera2Capturer) info.capturer;
    final String owner = owner(trackId, info);
    final Handler main = new Handler(android.os.Looper.getMainLooper());
    capturer.retune((b, c, cameraId, w, h) -> {
      final CameraControlState state = CameraControlState.of(owner, cameraId);
      change.apply(state, c);
      b.set(CaptureRequest.FLASH_MODE, isTorchOn ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
      addControls(b, c, state, w, h);
    }, error -> main.post(() -> {
      if (error == null) result.success(null);
      else resultError(tag, error, result);
    }));
  }

  /**
   * The range that holds the camera to exactly this rate, if it has one.
   *
   * WebRTC prefers a range with a low floor, such as 15-60, over 60-60, and a
   * camera given 15-60 slows to 30 in anything but bright light. Only for rates
   * above 30: at 30 the session's own choice is what a camera app would pick.
   */
  private static Range<Integer> fixedFpsRange(CameraCharacteristics c, int fps) {
    if (fps <= 30) return null;
    Range<Integer>[] ranges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
    if (ranges == null) return null;
    for (Range<Integer> r : ranges) {
      if (r.getLower() == fps && r.getUpper() == fps) return r;
    }
    return null;
  }

  /**
   * A camera session has started producing frames: ask it for the rate that was
   * asked for, which the session did not pick for itself. Runs on the camera's
   * thread, and again whenever a switch or restart starts another session.
   */
  public void onCameraStarted(String trackId) {
    final VideoCapturerInfo info = getUserMediaImpl.getCapturerInfo(trackId);
    if (info == null || !(info.capturer instanceof Camera2Capturer) || info.fps <= 30) return;
    final int fps = info.fps;
    camera2("frameRate", trackId, info, (state, c) -> state.targetFps = fps, new MethodChannel.Result() {
      @Override public void success(Object o) {
        Log.i(TAG, "camera held to " + fps + " fps");
      }
      @Override public void error(String code, String message, Object details) {
        Log.w(TAG, "could not hold the camera to " + fps + " fps: " + message);
      }
      @Override public void notImplemented() {}
    });
  }

  /**
   * Changes what is asked of the camera and sends the whole request again.
   *
   * <p>Zoom, focus point, torch and the session's own settings all live in the one
   * repeating request, so each is rebuilt every time rather than set on a bare
   * request that would drop the others.
   */
  private void camera2(String tag, String trackId, VideoCapturerInfo info, StateChange change,
      MethodChannel.Result result) {
    final Camera2Parts p;
    try {
      p = camera2Parts(info);
    } catch (NoSuchFieldWithNameException e) {
      // Most likely the upstream Camera2Capturer class has changed
      resultError(tag, "Failed to get `" + e.fieldName + "` from `" + e.className + "`", result);
      return;
    } catch (RuntimeException e) {
      resultError(tag, "The camera is not running: " + e, result);
      return;
    }
    try {
      final String cameraId = p.cameraDevice.getId();
      final CameraCharacteristics c = p.manager.getCameraCharacteristics(cameraId);
      final CameraControlState state = CameraControlState.of(owner(trackId, info), cameraId);
      change.apply(state, c);

      final CaptureRequest.Builder b = p.cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
      final Range<Integer> fixed = fixedFpsRange(c, state.targetFps);
      b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fixed != null ? fixed : new Range<>(
          p.captureFormat.framerate.min / p.fpsUnitFactor, p.captureFormat.framerate.max / p.fpsUnitFactor));
      b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
      b.set(CaptureRequest.CONTROL_AE_LOCK, false);
      b.set(CaptureRequest.FLASH_MODE, isTorchOn ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);

      // Stabilisation, as the session itself chooses it.
      if (has(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION),
          CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
        b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
        b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
      } else if (has(c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES),
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)) {
        b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
      }

      final boolean aimed = addControls(b, c, state, p.captureFormat.width, p.captureFormat.height);
      final boolean continuous = has(c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),
          CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
      b.addTarget(p.surface);
      p.captureSession.setRepeatingRequest(b.build(), LENS_LOG, p.cameraThreadHandler);

      // Continuous focus follows the region by itself; a camera with only plain
      // auto needs telling to look.
      if (aimed && !continuous) {
        b.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        p.captureSession.capture(b.build(), null, p.cameraThreadHandler);
      }
      result.success(null);
    } catch (CameraAccessException | RuntimeException e) {
      resultError(tag, "Camera request failed: " + e, result);
    }
  }

  /** Where the camera app would mark the zoom: each lens of the cluster, and 2x. */
  private static float[] zoomStops(CameraCharacteristics c, CameraManager manager, float[] range) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        java.util.Set<String> ids = c.getPhysicalCameraIds();
        if (!ids.isEmpty()) {
          float[] focal = new float[ids.size()];
          float[] width = new float[ids.size()];
          int i = 0;
          for (String id : ids) {
            CameraCharacteristics p = manager.getCameraCharacteristics(id);
            float[] f = p.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            android.util.SizeF size = p.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            focal[i] = f != null && f.length > 0 ? f[0] : 0f;
            width[i] = size != null ? size.getWidth() : 0f;
            i++;
          }
          return CameraZoomStops.stops(focal, width, range[0], range[1]);
        }
      }
    } catch (Throwable t) {
      Log.w(TAG, "could not work out the lens stops: " + t);
    }
    return CameraZoomStops.stops(new float[0], new float[0], range[0], range[1]);
  }

  private static java.util.List<Double> asList(float[] values) {
    java.util.List<Double> out = new java.util.ArrayList<>();
    for (float v : values) out.add((double) v);
    return out;
  }

  /** The zoom the camera allows and whether it can be told where to focus. */
  public void getZoomRange(String trackId, MethodChannel.Result result) {
    VideoCapturerInfo info = getUserMediaImpl.getCapturerInfo(trackId);
    if (info == null) {
      resultError("getZoomRange", "Video capturer not found for id: " + trackId, result);
      return;
    }
    if (info.capturer instanceof HighSpeedCamera2Capturer) {
      HighSpeedCamera2Capturer hs = (HighSpeedCamera2Capturer) info.capturer;
      try {
        CameraCharacteristics c = hs.getCameraManager().getCameraCharacteristics(hs.getCameraId());
        CameraLensLog.describe(c, hs.getCameraId(), hs.getCameraManager());
        float[] range = zoomRange(c);
        float[] stops = zoomStops(c, hs.getCameraManager(), range);
        Log.i(TAG, "zoom stops " + java.util.Arrays.toString(stops));
        Integer maxAf = c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("stops", asList(stops));
        m.put("min", (double) range[0]);
        m.put("max", (double) range[1]);
        m.put("focus", maxAf != null && maxAf > 0);
        result.success(m);
      } catch (CameraAccessException | RuntimeException e) {
        resultError("getZoomRange", "Failed to read the camera: " + e, result);
      }
      return;
    }
    if (!(info.capturer instanceof Camera2Capturer)) {
      java.util.Map<String, Object> m = new java.util.HashMap<>();
      m.put("min", 1.0);
      m.put("max", 1.0);
      m.put("focus", false);
      result.success(m);
      return;
    }
    try {
      Camera2Parts p = camera2Parts(info);
      CameraCharacteristics c = p.manager.getCameraCharacteristics(p.cameraDevice.getId());
      CameraLensLog.describe(c, p.cameraDevice.getId(), p.manager);
      float[] range = zoomRange(c);
      float[] stops = zoomStops(c, p.manager, range);
      Log.i(TAG, "zoom stops " + java.util.Arrays.toString(stops));
      Integer maxAf = c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
      java.util.Map<String, Object> m = new java.util.HashMap<>();
      m.put("stops", asList(stops));
      m.put("min", (double) range[0]);
      m.put("max", (double) range[1]);
      m.put("focus", maxAf != null && maxAf > 0);
      result.success(m);
    } catch (NoSuchFieldWithNameException | CameraAccessException | RuntimeException e) {
      resultError("getZoomRange", "Failed to read the camera: " + e, result);
    }
  }


  private class NoSuchFieldWithNameException extends NoSuchFieldException {

    String className;
    String fieldName;

    NoSuchFieldWithNameException(String className, String fieldName, NoSuchFieldException e) {
      super(e.getMessage());
      this.className = className;
      this.fieldName = fieldName;
    }
  }
  static private void resultError(String method, String error, MethodChannel.Result result) {
    String errorMsg = method + "(): " + error;
    result.error(method, errorMsg, null);
    Log.d(TAG, errorMsg);
  }
  private Object getPrivateProperty(Class klass, Object object, String fieldName)
          throws NoSuchFieldWithNameException {
    try {
      Field field = klass.getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(object);
    } catch (NoSuchFieldException e) {
      throw new NoSuchFieldWithNameException(klass.getName(), fieldName, e);
    } catch (IllegalAccessException e) {
      // Should never happen since we are calling `setAccessible(true)`
      throw new RuntimeException(e);
    }
  }
  @NonNull
  public static MeteringRectangle convertPointToMeteringRectangle(
          @NonNull Size boundaries,
          double x,
          double y,
          @NonNull PlatformChannel.DeviceOrientation orientation) {
    assert (boundaries.getWidth() > 0 && boundaries.getHeight() > 0);
    assert (x >= 0 && x <= 1);
    assert (y >= 0 && y <= 1);
    // Rotate the coordinates to match the device orientation.
    double oldX = x, oldY = y;
    switch (orientation) {
      case PORTRAIT_UP: // 90 ccw.
        y = 1 - oldX;
        x = oldY;
        break;
      case PORTRAIT_DOWN: // 90 cw.
        x = 1 - oldY;
        y = oldX;
        break;
      case LANDSCAPE_LEFT:
        // No rotation required.
        break;
      case LANDSCAPE_RIGHT: // 180.
        x = 1 - x;
        y = 1 - y;
        break;
    }
    // Interpolate the target coordinate.
    int targetX = (int) Math.round(x * ((double) (boundaries.getWidth() - 1)));
    int targetY = (int) Math.round(y * ((double) (boundaries.getHeight() - 1)));
    // Determine the dimensions of the metering rectangle (10th of the viewport).
    int targetWidth = (int) Math.round(((double) boundaries.getWidth()) / 10d);
    int targetHeight = (int) Math.round(((double) boundaries.getHeight()) / 10d);
    // Adjust target coordinate to represent top-left corner of metering rectangle.
    targetX -= targetWidth / 2;
    targetY -= targetHeight / 2;
    // Adjust target coordinate as to not fall out of bounds.
    if (targetX < 0) {
      targetX = 0;
    }
    if (targetY < 0) {
      targetY = 0;
    }
    int maxTargetX = boundaries.getWidth() - 1 - targetWidth;
    int maxTargetY = boundaries.getHeight() - 1 - targetHeight;
    if (targetX > maxTargetX) {
      targetX = maxTargetX;
    }
    if (targetY > maxTargetY) {
      targetY = maxTargetY;
    }
    // Build the metering rectangle.
    return MeteringRectangleFactory.create(targetX, targetY, targetWidth, targetHeight, 1);
  }

  static class MeteringRectangleFactory {
    public static MeteringRectangle create(
            int x, int y, int width, int height, int meteringWeight) {
      return new MeteringRectangle(x, y, width, height, meteringWeight);
    }
  }
}

