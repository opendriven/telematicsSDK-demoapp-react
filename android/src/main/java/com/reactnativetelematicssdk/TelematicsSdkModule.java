package com.reactnativetelematicssdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;

import com.telematicssdk.tracking.TrackingApi;
import com.telematicssdk.tracking.Settings;
import com.telematicssdk.tracking.utils.permissions.PermissionsWizardActivity;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.telematicssdk.tracking.LocationListener;
public class TelematicsSdkModule extends ReactContextBaseJavaModule implements ActivityEventListener {
  public static final String NAME = "TelematicsSdk";
  private static final String TAG = "TelematicsSdkModule";
  private final String LOCATION_CHANGED_EVENT_NAME = "onLocationChanged";

  private Promise permissionsPromise = null;

  private final TrackingApi api = TrackingApi.getInstance();
  private final TagsProcessor tagsProcessor = new TagsProcessor();

  public TelematicsSdkModule(ReactApplicationContext reactContext) {
    super(reactContext);
    reactContext.addActivityEventListener(this);
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  // Initialization and permission request
  @ReactMethod
  public void initialize() {
    Log.d(TAG, "init method");
    if (!api.isInitialized()) {
      api.initialize(this.getReactApplicationContext(), setTelematicsSettings());
      Log.d(TAG, "Tracking api is initialized");
    }
    api.addTagsProcessingCallback(tagsProcessor);
    startLocationListener();
    Log.d(TAG, "Tag callback is set");
  }

  //startPersistentTrackingMethod
  @ReactMethod
  public void startPersistentTracking(Promise promise) {
    promise.resolve(api.startPersistentTracking());
  }

  /**
   * Default Setting constructor
   * Stop tracking time is 5 minute.
   * Parking radius is 100 meters.
   * Auto start tracking is true.
   * hfOn - true if HIGH FREQUENCY data recording from sensors (acc, gyro) is ON and false otherwise.
   * isElmOn - true if data recording from ELM327 devices is ON and false otherwise.
   * isAdOn - false to keep accident detection disabled
   */
  public Settings setTelematicsSettings() {
    Settings settings = new Settings(
      Settings.getStopTrackingTimeHigh(),
      Settings.getAccuracyHigh(),
      true,
      true,
      false,
      false
    );
    Log.d(TAG, "setTelematicsSettings");
    return settings;
  }

  @ReactMethod
  public void requestPermissions(Promise promise) {
    permissionsPromise = promise;
    if (!api.areAllRequiredPermissionsGranted()) {
      this.getReactApplicationContext().
        startActivityForResult(PermissionsWizardActivity.Companion.getStartWizardIntent(
          this.getReactApplicationContext(),
          false,
          false
        ), PermissionsWizardActivity.WIZARD_PERMISSIONS_CODE, null);
    } else {
      permissionsPromise.resolve(true);
    }
  }

  // API Status
  @ReactMethod
  public void getStatus(Promise promise) {
    promise.resolve(api.isSdkEnabled());
  }

  // Device token
  @ReactMethod
  public void getDeviceToken(Promise promise) {
    promise.resolve(api.getDeviceId());
  }

  // Enabling and disabling SDK
  @SuppressLint("MissingPermission")
  @ReactMethod
  public void enable(String deviceToken, Promise promise) {
    if(deviceToken.isEmpty()) {
      promise.reject("Error", "Missing token value");
      return;
    }
    if (!api.areAllRequiredPermissionsGranted() || !api.isInitialized()) {
      Log.d(TAG, "Failed to start SDK");
      promise.resolve(false);
      return;
    }
    api.setDeviceID(deviceToken);
    api.setEnableSdk(true);
    Log.d(TAG, "SDK Started");
    promise.resolve(true);
  }

  @SuppressLint("MissingPermission")
  @ReactMethod
  public void disable() {
    if(!api.isInitialized()) {
      Log.d(TAG, "Failed to stop SDK");
      return;
    }
    //api.setEnableSdk(false);
    //api.clearDeviceID();
    api.setDisableWithUpload();
    Log.d(TAG, "SDK is stopped");
  }

  // Tags API
  @ReactMethod
  public void getFutureTrackTags(Promise promise) {
    Log.d(TAG, "Fetching future tracks");
    if(!api.isInitialized()) {
      promise.reject("Error", "Tracking api is not initialized");
      return;
    }
    tagsProcessor.setOnGetTags(promise);
    api.getFutureTrackTags();
  }

  @ReactMethod
  public void addFutureTrackTag(String tag, String source, Promise promise) {
    Log.d(TAG, "Adding new track");
    if(!api.isInitialized()) {
      promise.reject("Error", "Tracking api is not initialized");
      return;
    }
    tagsProcessor.setOnAddTag(promise);
    api.addFutureTrackTag(tag, source);
  }

  @ReactMethod
  public void removeFutureTrackTag(String tag, String source, Promise promise) {
    // We don't use source yet, may be used in the new android sdk
    Log.d(TAG, "Removing track");
    if(!api.isInitialized()) {
      promise.reject("Error", "Tracking api is not initialized");
      return;
    }
    tagsProcessor.setOnTagRemove(promise);
    api.removeFutureTrackTag(tag);
  }

  @ReactMethod
  public void removeAllFutureTrackTags(Promise promise) {
    Log.d(TAG, "Removing all tracks");
    if(!api.isInitialized()) {
      promise.reject("Error", "Tracking api is not initialized");
      return;
    }
    tagsProcessor.setOnAllTagsRemove(promise);
    api.removeAllFutureTrackTags();
  }

  // Permission wizard result
  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    if (requestCode == 50005) {
      switch(resultCode) {
        case -1:
          Log.d(TAG, "onActivityResult: WIZARD_RESULT_ALL_GRANTED");
          if(permissionsPromise == null) break;
          permissionsPromise.resolve(true);
          break;
        case 0:
          Log.d(TAG, "onActivityResult: WIZARD_RESULT_CANCELED");
          if(permissionsPromise == null) break;
          permissionsPromise.resolve(false);
          break;
        case 1:
          Log.d(TAG, "onActivityResult: WIZARD_RESULT_NOT_ALL_GRANTED");
          if(permissionsPromise == null) break;
          permissionsPromise.resolve(false);
          break;
      }
    }
  }

  @Override
  public void onNewIntent(Intent intent) {

  }

  public void startLocationListener() {
    LocationListener callback = new LocationListener() {

      @Override
      public void onLocationChanged(@Nullable Location location) {
        WritableMap params = Arguments.createMap();
        params.putDouble("latitude", location != null ? location.getLatitude() : 0);
        params.putDouble("longitude", location != null ? location.getLongitude() : 0);
        params.putDouble("altitude", location != null ? location.getAltitude() : 0);
        params.putDouble("speed", location != null ? location.getSpeed() : 0);
        params.putDouble("timestamp", location != null ? location.getTime() : 0);
        sendEvent(getReactApplicationContext(), LOCATION_CHANGED_EVENT_NAME, params);
      }
    };

    try {
      api.setLocationListener(callback);
    } catch (Exception e) {
      Log.e(TAG, "Error setting location listener", e);
    }
  }

  private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
      reactContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
          .emit(eventName, params);
  }

  @ReactMethod
  public void addListener(String eventName) {

  }

  @ReactMethod
  public void removeListeners(Integer count) {

  }
}
