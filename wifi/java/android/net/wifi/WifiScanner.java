/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.wifi;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.util.AsyncChannel;
import com.android.internal.util.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;


/**
 * This class provides a way to scan the Wifi universe around the device
 * Get an instance of this class by calling
 * {@link android.content.Context#getSystemService(String) Context.getSystemService(Context
 * .WIFI_SCANNING_SERVICE)}.
 * @hide
 */
public class WifiScanner {

    public static final int WIFI_BAND_UNSPECIFIED = 0;      /* not specified */
    public static final int WIFI_BAND_24_GHZ = 1;           /* 2.4 GHz band */
    public static final int WIFI_BAND_5_GHZ = 2;            /* 5 GHz band without DFS channels */
    public static final int WIFI_BAND_5_GHZ_DFS_ONLY  = 4;  /* 5 GHz band with DFS channels */
    public static final int WIFI_BAND_5_GHZ_WITH_DFS  = 6;  /* 5 GHz band with DFS channels */
    public static final int WIFI_BAND_BOTH = 3;             /* both bands without DFS channels */
    public static final int WIFI_BAND_BOTH_WITH_DFS = 7;    /* both bands with DFS channels */

    public static final int MIN_SCAN_PERIOD_MS = 300;       /* minimum supported period */
    public static final int MAX_SCAN_PERIOD_MS = 1024000;   /* maximum supported period */

    public static final int REASON_SUCCEEDED = 0;
    public static final int REASON_UNSPECIFIED = -1;
    public static final int REASON_INVALID_LISTENER = -2;
    public static final int REASON_INVALID_REQUEST = -3;
    public static final int REASON_CONFLICTING_REQUEST = -4;

    public static interface ActionListener {
        public void onSuccess(Object result);
        public void onFailure(int reason, Object exception);
    }

    /**
     * gives you all the possible channels; channel is specified as an
     * integer with frequency in MHz i.e. channel 1 is 2412
     */
    public List<Integer> getAvailableChannels(int band) {
        return null;
    }

    /**
     * provides channel specification to the APIs
     */
    public static class ChannelSpec {
        public int frequency;
        public boolean passive;                                    /* ignored on DFS channels */
        public int dwellTimeMS;                                    /* not supported for now */

        public ChannelSpec(int frequency) {
            this.frequency = frequency;
            passive = false;
            dwellTimeMS = 0;
        }
    }

    public static final int REPORT_EVENT_AFTER_BUFFER_FULL = 0;
    public static final int REPORT_EVENT_AFTER_EACH_SCAN = 1;
    public static final int REPORT_EVENT_FULL_SCAN_RESULT = 2;

    /**
     * scan configuration parameters
     */
    public static class ScanSettings implements Parcelable {

        public int band;                                           /* ignore channels if specified */
        public ChannelSpec[] channels;                             /* list of channels to scan */
        public int periodInMs;                                     /* period of scan */
        public int reportEvents;                                   /* a valid REPORT_EVENT value */

        /** Implement the Parcelable interface {@hide} */
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(band);
            dest.writeInt(periodInMs);
            dest.writeInt(channels.length);

            for (int i = 0; i < channels.length; i++) {
                dest.writeInt(channels[i].frequency);
                dest.writeInt(channels[i].dwellTimeMS);
                dest.writeInt(channels[i].passive ? 1 : 0);
            }
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<ScanSettings> CREATOR =
                new Creator<ScanSettings>() {
                    public ScanSettings createFromParcel(Parcel in) {

                        ScanSettings settings = new ScanSettings();
                        settings.band = in.readInt();
                        settings.periodInMs = in.readInt();
                        int num_channels = in.readInt();
                        settings.channels = new ChannelSpec[num_channels];
                        for (int i = 0; i < num_channels; i++) {
                            int frequency = in.readInt();

                            ChannelSpec spec = new ChannelSpec(frequency);
                            spec.dwellTimeMS = in.readInt();
                            spec.passive = in.readInt() == 1;
                            settings.channels[i] = spec;
                        }

                        return settings;
                    }

                    public ScanSettings[] newArray(int size) {
                        return new ScanSettings[size];
                    }
                };

    }

    public static class InformationElement {
        public int id;
        public byte[] bytes;
    }

    public static class FullScanResult {
        public ScanResult result;
        public InformationElement informationElements[];
    }

    /** @hide */
    public static class ParcelableScanResults implements Parcelable {
        public ScanResult mResults[];

        public ParcelableScanResults(ScanResult[] results) {
            mResults = results;
        }

        public ScanResult[] getResults() {
            return mResults;
        }

        /** Implement the Parcelable interface {@hide} */
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mResults.length);
            for (int i = 0; i < mResults.length; i++) {
                ScanResult result = mResults[i];
                result.writeToParcel(dest, flags);
            }
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<ParcelableScanResults> CREATOR =
                new Creator<ParcelableScanResults>() {
                    public ParcelableScanResults createFromParcel(Parcel in) {
                        int n = in.readInt();
                        ScanResult results[] = new ScanResult[n];
                        for (int i = 0; i < n; i++) {
                            results[i] = ScanResult.CREATOR.createFromParcel(in);
                        }
                        return new ParcelableScanResults(results);
                    }

                    public ParcelableScanResults[] newArray(int size) {
                        return new ParcelableScanResults[size];
                    }
                };
    }

    /**
     * Framework is co-ordinating scans across multiple apps; so it may not give exactly the
     * same period requested. The period granted is stated on the onSuccess() event; and
     * onPeriodChanged() will be called if/when it is changed because of multiple conflicting
     * requests. This is similar to the way timers are handled.
     */
    public interface ScanListener extends ActionListener {
        public void onPeriodChanged(int periodInMs);
        public void onResults(ScanResult[] results);
        public void onFullResult(FullScanResult fullScanResult);
    }

    public void scan(ScanSettings settings, ScanListener listener) {
        validateChannel();
        sAsyncChannel.sendMessage(CMD_SCAN, 0, putListener(listener), settings);
    }
    public void startBackgroundScan(ScanSettings settings, ScanListener listener) {
        validateChannel();
        sAsyncChannel.sendMessage(CMD_START_BACKGROUND_SCAN, 0, putListener(listener), settings);
    }
    public void stopBackgroundScan(boolean flush, ScanListener listener) {
        validateChannel();
        sAsyncChannel.sendMessage(CMD_STOP_BACKGROUND_SCAN, 0, removeListener(listener));
    }
    public void retrieveScanResults(boolean flush, ScanListener listener) {
        validateChannel();
        sAsyncChannel.sendMessage(CMD_GET_SCAN_RESULTS, 0, getListenerKey(listener));
    }

    public static class HotspotInfo {
        public String bssid;
        public int low;                                            /* minimum RSSI */
        public int high;                                           /* maximum RSSI */
    }

    public static class WifiChangeSettings {
        public int rssiSampleSize;                                 /* sample size for RSSI averaging */
        public int lostApSampleSize;                               /* samples to confirm AP's loss */
        public int unchangedSampleSize;                            /* samples to confirm no change */
        public int minApsBreachingThreshold;                       /* change threshold to trigger event */
        public HotspotInfo[] hotspotInfos;
    }

    /* overrides the significant wifi change state machine configuration */
    public void configureSignificantWifiChange(
            int rssiSampleSize,                             /* sample size for RSSI averaging */
            int lostApSampleSize,                           /* samples to confirm AP's loss */
            int unchangedSampleSize,                        /* samples to confirm no change */
            int minApsBreachingThreshold,                   /* change threshold to trigger event */
            HotspotInfo[] hotspotInfos                      /* signal thresholds to crosss */
            )
    {
        validateChannel();
        WifiChangeSettings settings = new WifiChangeSettings();
        settings.rssiSampleSize = rssiSampleSize;
        settings.lostApSampleSize = lostApSampleSize;
        settings.unchangedSampleSize = unchangedSampleSize;
        settings.minApsBreachingThreshold = minApsBreachingThreshold;
        settings.hotspotInfos = hotspotInfos;

        sAsyncChannel.sendMessage(CMD_CONFIGURE_WIFI_CHANGE, 0, 0, settings);
    }

    public interface SignificantWifiChangeListener extends ActionListener {
        public void onChanging(ScanResult[] results);           /* changes are found */
        public void onQuiescence(ScanResult[] results);         /* changes settled down */
    }

    public void trackSignificantWifiChange(SignificantWifiChangeListener listener) {
        validateChannel();
        sAsyncChannel.sendMessage(CMD_START_TRACKING_CHANGE, 0, putListener(listener));
    }
    public void untrackSignificantWifiChange(SignificantWifiChangeListener listener) {
        validateChannel();
        sAsyncChannel.sendMessage(CMD_STOP_TRACKING_CHANGE, 0, removeListener(listener));
    }

    public void configureSignificantWifiChange(WifiChangeSettings settings) {
        validateChannel();
        sAsyncChannel.sendMessage(CMD_CONFIGURE_WIFI_CHANGE, 0, 0, settings);
    }

    public static interface HotlistListener extends ActionListener {
        public void onFound(ScanResult[] results);
    }

    /** @hide */
    public static class HotlistSettings implements Parcelable {
        public HotspotInfo[] hotspotInfos;
        public int apLostThreshold;

        /** Implement the Parcelable interface {@hide} */
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(apLostThreshold);
            dest.writeInt(hotspotInfos.length);
            for (int i = 0; i < hotspotInfos.length; i++) {
                HotspotInfo info = hotspotInfos[i];
                dest.writeString(info.bssid);
                dest.writeInt(info.low);
                dest.writeInt(info.high);
            }
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<HotlistSettings> CREATOR =
                new Creator<HotlistSettings>() {
                    public HotlistSettings createFromParcel(Parcel in) {
                        HotlistSettings settings = new HotlistSettings();
                        settings.apLostThreshold = in.readInt();
                        int n = in.readInt();
                        settings.hotspotInfos = new HotspotInfo[n];
                        for (int i = 0; i < n; i++) {
                            HotspotInfo info = new HotspotInfo();
                            info.bssid = in.readString();
                            info.low = in.readInt();
                            info.high = in.readInt();
                            settings.hotspotInfos[i] = info;
                        }
                        return settings;
                    }

                    public HotlistSettings[] newArray(int size) {
                        return new HotlistSettings[size];
                    }
                };
    }

    public void setHotlist(HotspotInfo[] hotspots,
            int apLostThreshold, HotlistListener listener) {
        validateChannel();
        HotlistSettings settings = new HotlistSettings();
        settings.hotspotInfos = hotspots;
        sAsyncChannel.sendMessage(CMD_SET_HOTLIST, 0, putListener(listener), settings);
    }

    public void resetHotlist(HotlistListener listener) {
        validateChannel();
        sAsyncChannel.sendMessage(CMD_RESET_HOTLIST, 0, removeListener(listener));
    }


    /* private members and methods */

    private static final String TAG = "WifiScanner";
    private static final boolean DBG = true;

    /* commands for Wifi Service */
    private static final int BASE = Protocol.BASE_WIFI_SCANNER;

    /** @hide */
    public static final int CMD_SCAN                        = BASE + 0;
    /** @hide */
    public static final int CMD_START_BACKGROUND_SCAN       = BASE + 2;
    /** @hide */
    public static final int CMD_STOP_BACKGROUND_SCAN        = BASE + 3;
    /** @hide */
    public static final int CMD_GET_SCAN_RESULTS            = BASE + 4;
    /** @hide */
    public static final int CMD_SCAN_RESULT                 = BASE + 5;
    /** @hide */
    public static final int CMD_SET_HOTLIST                 = BASE + 6;
    /** @hide */
    public static final int CMD_RESET_HOTLIST               = BASE + 7;
    /** @hide */
    public static final int CMD_AP_FOUND                    = BASE + 9;
    /** @hide */
    public static final int CMD_AP_LOST                     = BASE + 10;
    /** @hide */
    public static final int CMD_START_TRACKING_CHANGE       = BASE + 11;
    /** @hide */
    public static final int CMD_STOP_TRACKING_CHANGE        = BASE + 12;
    /** @hide */
    public static final int CMD_CONFIGURE_WIFI_CHANGE       = BASE + 13;
    /** @hide */
    public static final int CMD_WIFI_CHANGE_DETECTED        = BASE + 15;
    /** @hide */
    public static final int CMD_WIFI_CHANGES_STABILIZED     = BASE + 16;
    /** @hide */
    public static final int CMD_OP_SUCCEEDED                = BASE + 17;
    /** @hide */
    public static final int CMD_OP_FAILED                   = BASE + 18;
    /** @hide */
    public static final int CMD_PERIOD_CHANGED              = BASE + 19;
    /** @hide */
    public static final int CMD_FULL_SCAN_RESULT            = BASE + 20;

    private Context mContext;
    private IWifiScanner mService;

    private static final int INVALID_KEY = 0;
    private static int sListenerKey = 1;

    private static final SparseArray sListenerMap = new SparseArray();
    private static final Object sListenerMapLock = new Object();

    private static AsyncChannel sAsyncChannel;
    private static CountDownLatch sConnected;

    private static final Object sThreadRefLock = new Object();
    private static int sThreadRefCount;
    private static HandlerThread sHandlerThread;

    /**
     * Create a new WifiScanner instance.
     * Applications will almost always want to use
     * {@link android.content.Context#getSystemService Context.getSystemService()} to retrieve
     * the standard {@link android.content.Context#WIFI_SERVICE Context.WIFI_SERVICE}.
     * @param context the application context
     * @param service the Binder interface
     * @hide
     */
    public WifiScanner(Context context, IWifiScanner service) {
        mContext = context;
        mService = service;
        init();
    }

    private void init() {
        synchronized (sThreadRefLock) {
            if (++sThreadRefCount == 1) {
                Messenger messenger = null;
                try {
                    messenger = mService.getMessenger();
                } catch (RemoteException e) {
                    /* do nothing */
                } catch (SecurityException e) {
                    /* do nothing */
                }

                if (messenger == null) {
                    sAsyncChannel = null;
                    return;
                }

                sHandlerThread = new HandlerThread("WifiScanner");
                sAsyncChannel = new AsyncChannel();
                sConnected = new CountDownLatch(1);

                sHandlerThread.start();
                Handler handler = new ServiceHandler(sHandlerThread.getLooper());
                sAsyncChannel.connect(mContext, handler, messenger);
                try {
                    sConnected.await();
                } catch (InterruptedException e) {
                    Log.e(TAG, "interrupted wait at init");
                }
            }
        }
    }

    private void validateChannel() {
        if (sAsyncChannel == null) throw new IllegalStateException(
                "No permission to access and change wifi or a bad initialization");
    }

    private static int putListener(Object listener) {
        if (listener == null) return INVALID_KEY;
        int key;
        synchronized (sListenerMapLock) {
            do {
                key = sListenerKey++;
            } while (key == INVALID_KEY);
            sListenerMap.put(key, listener);
        }
        return key;
    }

    private static Object getListener(int key) {
        if (key == INVALID_KEY) return null;
        synchronized (sListenerMapLock) {
            Object listener = sListenerMap.get(key);
            return listener;
        }
    }

    private static int getListenerKey(Object listener) {
        if (listener == null) return INVALID_KEY;
        synchronized (sListenerMapLock) {
            int index = sListenerMap.indexOfValue(listener);
            if (index == -1) {
                return INVALID_KEY;
            } else {
                return sListenerMap.keyAt(index);
            }
        }
    }

    private static Object removeListener(int key) {
        if (key == INVALID_KEY) return null;
        synchronized (sListenerMapLock) {
            Object listener = sListenerMap.get(key);
            sListenerMap.remove(key);
            return listener;
        }
    }

    private static int removeListener(Object listener) {
        int key = getListenerKey(listener);
        if (key == INVALID_KEY) return key;
        synchronized (sListenerMapLock) {
            sListenerMap.remove(key);
            return key;
        }
    }

    private static class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                    if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                        sAsyncChannel.sendMessage(AsyncChannel.CMD_CHANNEL_FULL_CONNECTION);
                    } else {
                        Log.e(TAG, "Failed to set up channel connection");
                        // This will cause all further async API calls on the WifiManager
                        // to fail and throw an exception
                        sAsyncChannel = null;
                    }
                    sConnected.countDown();
                    return;
                case AsyncChannel.CMD_CHANNEL_FULLY_CONNECTED:
                    return;
                case AsyncChannel.CMD_CHANNEL_DISCONNECTED:
                    Log.e(TAG, "Channel connection lost");
                    // This will cause all further async API calls on the WifiManager
                    // to fail and throw an exception
                    sAsyncChannel = null;
                    getLooper().quit();
                    return;
            }

            Object listener = getListener(msg.arg2);
            if (DBG) Log.d(TAG, "listener key = " + msg.arg2);

            switch (msg.what) {
                    /* ActionListeners grouped together */
                case CMD_OP_SUCCEEDED :
                    ((ActionListener) listener).onSuccess(msg.obj);
                    break;
                case CMD_OP_FAILED :
                    ((ActionListener) listener).onFailure(msg.arg1, msg.obj);
                    break;
                case CMD_SCAN_RESULT :
                    ((ScanListener) listener).onResults(
                            ((ParcelableScanResults) msg.obj).getResults());
                    return;
                case CMD_FULL_SCAN_RESULT :
                    FullScanResult result = (FullScanResult) msg.obj;
                    ((ScanListener) listener).onFullResult(result);
                    return;
                case CMD_AP_FOUND:
                    ((HotlistListener) listener).onFound(
                            ((ParcelableScanResults) msg.obj).getResults());
                    return;
                case CMD_WIFI_CHANGE_DETECTED:
                    ((SignificantWifiChangeListener) listener).onChanging(
                            ((ParcelableScanResults) msg.obj).getResults());
                   return;
                case CMD_WIFI_CHANGES_STABILIZED:
                    ((SignificantWifiChangeListener) listener).onQuiescence(
                            ((ParcelableScanResults) msg.obj).getResults());
                    return;
                default:
                    if (DBG) Log.d(TAG, "Ignoring message " + msg.what);
                    return;
            }
        }
    }
}