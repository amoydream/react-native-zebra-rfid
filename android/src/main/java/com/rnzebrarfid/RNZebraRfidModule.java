package com.rnzebrarfid;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.os.AsyncTask;
import android.util.Log;
import androidx.annotation.Nullable;

import com.zebra.rfid.api3.Antennas;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.RFIDReader;
import com.zebra.rfid.api3.Readers;
import com.zebra.rfid.api3.OperationFailureException;
import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.TriggerInfo;
import com.zebra.rfid.api3.TagData;
import com.zebra.rfid.api3.RfidReadEvents;
import com.zebra.rfid.api3.RfidStatusEvents;
import com.zebra.rfid.api3.RfidEventsListener;
import com.zebra.rfid.api3.TagAccess;
import com.zebra.rfid.api3.Readers.RFIDReaderEventHandler;

import com.zebra.rfid.api3.BEEPER_VOLUME;
import com.zebra.rfid.api3.ACCESS_OPERATION_CODE;
import com.zebra.rfid.api3.ACCESS_OPERATION_STATUS;
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE;
import com.zebra.rfid.api3.ENUM_TRANSPORT;
import com.zebra.rfid.api3.START_TRIGGER_TYPE;
import com.zebra.rfid.api3.STATUS_EVENT_TYPE;
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE;
import com.zebra.rfid.api3.ENUM_TRIGGER_MODE;
import com.zebra.rfid.api3.WRITE_FIELD_CODE;
import com.zebra.rfid.api3.MEMORY_BANK;
import com.zebra.rfid.api3.SESSION;
import com.zebra.rfid.api3.INVENTORY_STATE;
import com.zebra.rfid.api3.SL_FLAG;

import java.util.ArrayList;
import java.util.List; import java.util.Optional;


public class RNZebraRfidModule extends ReactContextBaseJavaModule implements RfidEventsListener, RFIDReaderEventHandler {
  private final String TAG = "ReactNative";
  private final ReactApplicationContext reactContext;
  private Readers readers;
  private List<ReaderDevice> devices;
  private int MAX_POWER = 270;


  public RNZebraRfidModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    this.readers = new Readers(reactContext, ENUM_TRANSPORT.BLUETOOTH);
    this.readers.attach(this);
    this.devices = new ArrayList<ReaderDevice>();
  }
  public Optional<RFIDReader> getRfidReader(){
    return this.devices.stream()
      .map(x -> x.getRFIDReader())
      .filter(x -> x.isConnected())
      .findFirst();
  }

  @Override
  public String getName() {
    return "RNZebraRfid";
  }

  @ReactMethod
  public void connect(final String deviceName, final Promise promise) {
    this.devices.stream()
      .filter(x -> x.getName().equals(deviceName))
      .findFirst()
      .map(x -> x.getRFIDReader())
      .ifPresent(x -> {
        Log.d(TAG, "connect to " + deviceName);
        try {
          if(!x.isConnected()){
            x.connect();
          }
          this.initRFIDReader(x);
        } catch (InvalidUsageException | OperationFailureException e) {
          promise.reject(e);
        }
      });
    promise.resolve(deviceName);
  }

  @ReactMethod
  public void setMode(final String mode, Promise promise) {
    this.getRFIDReader().ifPresent(x -> {
      try {
        if(mode.equals("RFID")){
          x.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, true);
        }else if(mode.equals("BARCODE")){
          x.Config.setTriggerMode(ENUM_TRIGGER_MODE.BARCODE_MODE, true);
        }
        promise.resolve(mode);
      } catch (InvalidUsageException | OperationFailureException e) {
        e.printStackTrace();
        promise.reject(e);
      }
    });
  }

  @ReactMethod
  public void setPower(final int power, Promise promise) {
    this.getRFIDReader().ifPresent(x -> {
      try {
        final Antennas.AntennaRfConfig  config = x.Config.Antennas.getAntennaRfConfig(1);
        config.setTransmitPowerIndex(power);
        x.Config.Antennas.setAntennaRfConfig(1, config);
        promise.resolve(power);
      } catch (InvalidUsageException | OperationFailureException e) {
        e.printStackTrace();
        promise.reject(e);
      }
    });
  }

  @ReactMethod
  public void disconnect(final String deviceName, final Promise promise) {
    this.devices.stream()
      .filter(x -> x.getName().equals(deviceName))
      .findFirst()
      .map(x -> x.getRFIDReader())
      .ifPresent(x -> {
        try {
          if(x.isConnected()){
            x.disconnect();
          }
          promise.reject(deviceName);
        } catch (InvalidUsageException | OperationFailureException e) {
          promise.reject(e);
        }
      });
  }

  @ReactMethod
  public void getAvailableDevices(final Promise promise) {
    Log.d(TAG, "GetAvailableReader");
    try {
      this.devices = readers.GetAvailableRFIDReaderList();
      final WritableArray payloads = new WritableNativeArray();
      for (ReaderDevice device : this.devices) {
        payloads.pushMap(this.toDevicePayload(device));
      }
      promise.resolve(payloads);
    } catch (InvalidUsageException e) {
      promise.reject(e);
    }
  }

  private void initRFIDReader(RFIDReader rfidReader) {
    try {
      rfidReader.Events.addEventsListener(this);
      rfidReader.Events.setHandheldEvent(true);
      rfidReader.Events.setTagReadEvent(true);
    } catch (InvalidUsageException | OperationFailureException e) {
      e.printStackTrace();
      Log.d(TAG,"ConfigureReader error");
    }
  }

  private void performInventory() {
    this.getRFIDReader().ifPresent(x -> {
      try {
        x.Actions.Inventory.perform();
      } catch (InvalidUsageException | OperationFailureException e) {
        e.printStackTrace();
      }
    });
  }

  private void stopInventory() {
    this.getRFIDReader().ifPresent(x -> {
      try {
        x.Actions.Inventory.stop();
      } catch (InvalidUsageException | OperationFailureException e) {
        e.printStackTrace();
      }
    });
  }

  @Override
 	public void RFIDReaderAppeared(ReaderDevice device) {
    this.sendEvent("onAppeared", device.getName());
  }

  @Override
 	public void	RFIDReaderDisappeared(ReaderDevice device) {
    this.sendEvent("onDisappeared", device.getName());
  }

  public void eventReadNotify(RfidReadEvents event) {
    this.getRFIDReader().ifPresent(x -> {
      final TagData[] tags = x.Actions.getReadTags(1);
      final WritableArray payload = new WritableNativeArray();
      for (TagData tag : tags) {
        payload.pushString(tag.getTagID());
      }
      this.sendEvent("onRfidRead", payload);
    });
  }

  public void eventStatusNotify(RfidStatusEvents event) {
    Log.d(TAG, "Status Notification: " + event.StatusEventData.getStatusEventType());
    if (event.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {
      this.performInventory();
    }

    if (event.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
      this.stopInventory();
    }
  }

  private Optional<RFIDReader> getRFIDReader(){
    return this.devices.stream()
      .map(x -> x.getRFIDReader())
      .filter(x -> x.isConnected())
      .findFirst();
  }

  private WritableMap toDevicePayload(final ReaderDevice device) {
    final WritableMap payload = new WritableNativeMap();
    payload.putString("name", device.getName());
    payload.putString("address", device.getAddress());
    return payload;
  }

  private void sendEvent(String eventName, @Nullable WritableMap params) {
    this.reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
  }

  private void sendEvent(String eventName, @Nullable WritableArray params) {
    this.reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
  }

  private void sendEvent(String eventName, @Nullable String params) {
    this.reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
  }
}
