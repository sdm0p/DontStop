-keep class com.dontstop.bridge.** { *; }
-keep class com.dontstop.tracking.DontStopAccessibilityService { *; }
-keep class com.dontstop.tracking.UsageReconcileReceiver { *; }
# React Native's inspector is loaded through JNI in the bridgeless runtime.
-keep class com.facebook.react.devsupport.** { *; }
-keep class com.facebook.react.soloader.** { *; }
