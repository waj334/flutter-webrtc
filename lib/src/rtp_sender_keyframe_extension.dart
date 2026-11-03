import 'package:webrtc_interface/webrtc_interface.dart';

import 'native/rtc_rtp_sender_impl.dart'
    if (dart.library.js_interop) 'web/rtc_rtp_sender_keyframe_web.dart';

extension RTCRtpSenderKeyFrameExtension on RTCRtpSender {
  /// Forces the underlying encoder for this [RTCRtpSender] to emit a keyframe
  /// on the next frame (used by the low-rate `moderation` track).
  Future<void> generateKeyFrame([List<String>? rids]) async {
    if (this is RTCRtpSenderNative) {
      await (this as RTCRtpSenderNative).generateKeyFrame(rids);
      return;
    }
    // Fallback for web / non-native senders: re-applying active parameters or
    // replaceTrack(track) prompts the browser's RTCRtpSender to emit a keyframe.
    try {
      await replaceTrack(track);
    } catch (_) {}
  }
}
