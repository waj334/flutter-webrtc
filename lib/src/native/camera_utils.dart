import 'dart:math';

import 'package:webrtc_interface/webrtc_interface.dart';

import 'utils.dart';

enum CameraFocusMode { auto, locked }

enum CameraExposureMode { auto, locked }

class CameraUtils {
  static Future<void> setZoom(
      MediaStreamTrack videoTrack, double zoomLevel) async {
    if (WebRTC.platformIsAndroid || WebRTC.platformIsIOS) {
      await WebRTC.invokeMethod(
        'mediaStreamTrackSetZoom',
        <String, dynamic>{'trackId': videoTrack.id, 'zoomLevel': zoomLevel},
      );
    } else {
      throw Exception('setZoom only support for mobile devices!');
    }
  }

  /// Set the exposure point for the camera, focusMode can be:
  /// 'auto', 'locked'
  static Future<void> setFocusMode(
      MediaStreamTrack videoTrack, CameraFocusMode focusMode) async {
    if (WebRTC.platformIsAndroid || WebRTC.platformIsIOS) {
      await WebRTC.invokeMethod(
        'mediaStreamTrackSetFocusMode',
        <String, dynamic>{
          'trackId': videoTrack.id,
          'focusMode': focusMode.name,
        },
      );
    } else {
      throw Exception('setFocusMode only support for mobile devices!');
    }
  }

  /// What the camera allows: the zoom range and whether it can be told where to focus.
  ///
  /// Also the zoom levels a camera app marks on its dial: each lens of the
  /// cluster, and 2x.
  ///
  /// Android only. Anything else reports no zoom and no focus point.
  static Future<({double min, double max, bool focus, List<double> stops})>
      getZoomRange(MediaStreamTrack videoTrack) async {
    if (!WebRTC.platformIsAndroid) {
      return (min: 1.0, max: 1.0, focus: false, stops: const <double>[1.0]);
    }
    final r = await WebRTC.invokeMethod<Map<dynamic, dynamic>, dynamic>(
      'mediaStreamTrackGetZoomRange',
      <String, dynamic>{'trackId': videoTrack.id},
    );
    return (
      min: (r?['min'] as num?)?.toDouble() ?? 1.0,
      max: (r?['max'] as num?)?.toDouble() ?? 1.0,
      focus: r?['focus'] == true,
      stops:
          (r?['stops'] as List?)?.map((e) => (e as num).toDouble()).toList() ??
              const <double>[1.0],
    );
  }

  /// Aims the focus at [point], given as fractions (0..1) of the upright picture
  /// as it was captured, so unmirrored. Null lets the camera choose again.
  static Future<void> setFocusPoint(
      MediaStreamTrack videoTrack, Point<double>? point) async {
    if (WebRTC.platformIsAndroid || WebRTC.platformIsIOS) {
      await WebRTC.invokeMethod(
        'mediaStreamTrackSetFocusPoint',
        <String, dynamic>{
          'trackId': videoTrack.id,
          'focusPoint': {
            'reset': point == null,
            'x': point?.x,
            'y': point?.y,
          },
        },
      );
    } else {
      throw Exception('setFocusPoint only support for mobile devices!');
    }
  }

  static Future<void> setExposureMode(
      MediaStreamTrack videoTrack, CameraExposureMode exposureMode) async {
    if (WebRTC.platformIsAndroid || WebRTC.platformIsIOS) {
      await WebRTC.invokeMethod(
        'mediaStreamTrackSetExposureMode',
        <String, dynamic>{
          'trackId': videoTrack.id,
          'exposureMode': exposureMode.name,
        },
      );
    } else {
      throw Exception('setExposureMode only support for mobile devices!');
    }
  }

  static Future<void> setExposurePoint(
      MediaStreamTrack videoTrack, Point<double>? point) async {
    if (WebRTC.platformIsAndroid || WebRTC.platformIsIOS) {
      await WebRTC.invokeMethod(
        'mediaStreamTrackSetExposurePoint',
        <String, dynamic>{
          'trackId': videoTrack.id,
          'exposurePoint': {
            'reset': point == null,
            'x': point?.x,
            'y': point?.y,
          },
        },
      );
    } else {
      throw Exception('setExposurePoint only support for mobile devices!');
    }
  }
}
