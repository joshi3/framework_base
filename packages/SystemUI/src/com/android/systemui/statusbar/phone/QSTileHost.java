/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Process;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings.Secure;
import android.util.Log;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.tiles.AirplaneModeTile;
import com.android.systemui.qs.tiles.BluetoothTile;
import com.android.systemui.qs.tiles.CastTile;
import com.android.systemui.qs.tiles.CellularTile;
import com.android.systemui.qs.tiles.ColorInversionTile;
import com.android.systemui.qs.tiles.FlashlightTile;
import com.android.systemui.qs.tiles.HotspotTile;
import com.android.systemui.qs.tiles.IntentTile;
import com.android.systemui.qs.tiles.LocationTile;
import com.android.systemui.qs.tiles.RotationLockTile;
import com.android.systemui.qs.tiles.WifiTile;
import com.android.systemui.settings.CurrentUserTracker;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.CastController;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.RotationLockController;
import com.android.systemui.statusbar.policy.HotspotController;
import com.android.systemui.statusbar.policy.SecurityController;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.statusbar.policy.ZenModeController;

/// M: add DataUsage in quicksetting @{
import com.mediatek.systemui.ext.IQuickSettingsPlugin;
import com.mediatek.systemui.ext.PluginFactory;
import com.mediatek.systemui.qs.tiles.DataUsageTile;
/// add DataUsage in quicksetting @}

/// M: add  quicksetting feature @{
import com.mediatek.systemui.qs.tiles.AudioProfileTile;
import com.mediatek.systemui.qs.tiles.HotKnotTile;
import com.mediatek.systemui.qs.tiles.MobileDataTile;
import com.mediatek.systemui.statusbar.policy.AudioProfileController;
import com.mediatek.systemui.statusbar.policy.DataConnectionController;
import com.mediatek.systemui.statusbar.policy.HotKnotController;
import com.mediatek.systemui.statusbar.util.SIMHelper;
/// add HotKnot in quicksetting @}

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/* Vanzo:yinjun on: Thu, 19 Mar 2015 10:17:40 +0800
 * for timeout tile
 */
import com.android.systemui.qs.tiles.TimeOutTile;
import com.android.featureoption.FeatureOption;
// End of Vanzo: yinjun

/** Platform implementation of the quick settings tile host **/
public class QSTileHost implements QSTile.Host {
    private static final String TAG = "QSTileHost";
    // M: For Debug
    private static final boolean DEBUG = true;

    private static final String TILES_SETTING = "sysui_qs_tiles";

    private final Context mContext;
    private final PhoneStatusBar mStatusBar;
    private final LinkedHashMap<String, QSTile<?>> mTiles = new LinkedHashMap<>();
    private final Observer mObserver = new Observer();
    private final BluetoothController mBluetooth;
    private final LocationController mLocation;
    private final RotationLockController mRotation;
    private final NetworkController mNetwork;
    private final ZenModeController mZen;
    private final HotspotController mHotspot;
    private final CastController mCast;
    private final Looper mLooper;
    private final CurrentUserTracker mUserTracker;
    private final FlashlightController mFlashlight;
    private final UserSwitcherController mUserSwitcherController;
    private final KeyguardMonitor mKeyguard;
    private final SecurityController mSecurity;

    /// M: add HotKnot in quicksetting
    private final HotKnotController mHotKnot;
    /// M: add AudioProfile in quicksetting
    private final AudioProfileController mAudioProfile;
    /// M: add DataConnection in quicksetting
    private final DataConnectionController mDataConnection;
    
    private Callback mCallback;

    public QSTileHost(Context context, PhoneStatusBar statusBar,
            BluetoothController bluetooth, LocationController location,
            RotationLockController rotation, NetworkController network,
            ZenModeController zen, HotspotController hotspot,
            CastController cast, FlashlightController flashlight,
            UserSwitcherController userSwitcher, KeyguardMonitor keyguard,
            SecurityController security,
            /// M: add HotKnot in quicksetting
            HotKnotController hotknot,
            /// M: add AudioProfile in quicksetting
            AudioProfileController audioprofile,
            /// M: add DataConnection in quicksetting
            DataConnectionController dataconnection
            ) {
        mContext = context;
        mStatusBar = statusBar;
        mBluetooth = bluetooth;
        mLocation = location;
        mRotation = rotation;
        mNetwork = network;
        mZen = zen;
        mHotspot = hotspot;
        mCast = cast;
        mFlashlight = flashlight;
        mUserSwitcherController = userSwitcher;
        mKeyguard = keyguard;
        mSecurity = security;

        /// M: add HotKnot in quicksetting
        mHotKnot = hotknot;
        /// M: add AudioProfile in quicksetting
        mAudioProfile = audioprofile;
        /// M: add DataConnection in quicksetting
        mDataConnection = dataconnection;

        final HandlerThread ht = new HandlerThread(QSTileHost.class.getSimpleName(),
                Process.THREAD_PRIORITY_BACKGROUND);
        ht.start();
        mLooper = ht.getLooper();

        mUserTracker = new CurrentUserTracker(mContext) {
            @Override
            public void onUserSwitched(int newUserId) {
                recreateTiles();
                for (QSTile<?> tile : mTiles.values()) {
                    tile.userSwitch(newUserId);
                }
                mSecurity.onUserSwitched(newUserId);
                mNetwork.onUserSwitched(newUserId);
                mObserver.register();
            }
        };
        recreateTiles();

        mUserTracker.startTracking();
        mObserver.register();
    }

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public Collection<QSTile<?>> getTiles() {
        return mTiles.values();
    }

    @Override
    public void startSettingsActivity(final Intent intent) {
        mStatusBar.postStartSettingsActivity(intent, 0);
    }

    @Override
    public void warn(String message, Throwable t) {
        // already logged
    }

    @Override
    public void collapsePanels() {
        mStatusBar.postAnimateCollapsePanels();
    }

    @Override
    public Looper getLooper() {
        return mLooper;
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public BluetoothController getBluetoothController() {
        return mBluetooth;
    }

    @Override
    public LocationController getLocationController() {
        return mLocation;
    }

    @Override
    public RotationLockController getRotationLockController() {
        return mRotation;
    }

    @Override
    public NetworkController getNetworkController() {
        return mNetwork;
    }

    @Override
    public ZenModeController getZenModeController() {
        return mZen;
    }

    @Override
    public HotspotController getHotspotController() {
        return mHotspot;
    }

    @Override
    public CastController getCastController() {
        return mCast;
    }

    @Override
    public FlashlightController getFlashlightController() {
        return mFlashlight;
    }

    @Override
    public KeyguardMonitor getKeyguardMonitor() {
        return mKeyguard;
    }

    public UserSwitcherController getUserSwitcherController() {
        return mUserSwitcherController;
    }

    public SecurityController getSecurityController() {
        return mSecurity;
    }

    /// M: add HotKnot in quicksetting @{
    @Override
    public HotKnotController getHotKnotController() {
        return mHotKnot;
    }    
    /// add HotKnot in quicksetting @}

    /// M: add AudioProfile in quicksetting @{
    @Override
    public AudioProfileController getAudioProfileController() {
        return mAudioProfile;
    }
    /// add AudioProfile in quicksetting @}

    /// M: add DataConnection in quicksetting @{
    @Override
    public DataConnectionController getDataConnectionController() {
        return mDataConnection;
    }
    /// M: add DataConnection in quicksetting @}

    private void recreateTiles() {
        if (DEBUG) Log.d(TAG, "Recreating tiles");
        final List<String> tileSpecs = loadTileSpecs();
        for (Map.Entry<String, QSTile<?>> tile : mTiles.entrySet()) {
            if (!tileSpecs.contains(tile.getKey())) {
                if (DEBUG) Log.d(TAG, "Destroying tile: " + tile.getKey());
                tile.getValue().destroy();
            }
        }
        final LinkedHashMap<String, QSTile<?>> newTiles = new LinkedHashMap<>();
        for (String tileSpec : tileSpecs) {
            if (mTiles.containsKey(tileSpec)) {
                newTiles.put(tileSpec, mTiles.get(tileSpec));
            } else {
                if (DEBUG) Log.d(TAG, "Creating tile: " + tileSpec);
                try {
                    newTiles.put(tileSpec, createTile(tileSpec));
                } catch (Throwable t) {
                    Log.w(TAG, "Error creating tile for spec: " + tileSpec, t);
                }
            }
        }
        if (mTiles.equals(newTiles)) return;
        mTiles.clear();
        mTiles.putAll(newTiles);
        if (mCallback != null) {
            mCallback.onTilesChanged();
        }
    }

    private QSTile<?> createTile(String tileSpec) {
        if (tileSpec.equals("wifi")) return new WifiTile(this);
        else if (tileSpec.equals("bt")) return new BluetoothTile(this);
        else if (tileSpec.equals("inversion")) return new ColorInversionTile(this);
        /** M: Customize to replace the cell to Data usage by some operators.@{*/
        else if (tileSpec.equals("cell")) {
            IQuickSettingsPlugin quickSettingsPlugin = PluginFactory
                    .getQuickSettingsPlugin(mContext);
            boolean displayDataUsage = quickSettingsPlugin.customizeDisplayDataUsage(false);
            Log.i(TAG, "createTile, cell displayDataUsage = " + displayDataUsage);
            if (displayDataUsage) {
                return new DataUsageTile(this);
            } else {
                return new CellularTile(this);
            }
        } /** M: Customize to replace the cell to Data usage by some operators.@}*/
        else if (tileSpec.equals("airplane")) return new AirplaneModeTile(this);
        else if (tileSpec.equals("rotation")) return new RotationLockTile(this);
        else if (tileSpec.equals("flashlight")) return new FlashlightTile(this);
        else if (tileSpec.equals("location")) return new LocationTile(this);
        // M: Remove CastTile when WFD is not support in quicksetting
        else if (tileSpec.equals("cast") && SIMHelper.isWifiDisplaySupport())
            return new CastTile(this);
        else if (tileSpec.equals("hotspot")) return new HotspotTile(this);
/* Vanzo:yinjun on: Thu, 19 Mar 2015 10:21:04 +0800
 * for timeout tile
 */
        else if (tileSpec.equals("timeout") && FeatureOption.VANZO_FEATURE_SYSTEMUI_TIMEOUT_TILE)
            return new TimeOutTile(this,mUserTracker);
// End of Vanzo: yinjun

        /// M: add HotKnot in quicksetting @{
        else if (tileSpec.equals("hotknot") && SIMHelper.isMtkHotKnotSupport())
            return new HotKnotTile(this);
        /// add HotKnot in quicksetting @}
        /// M: add AudioProfile in quicksetting @{
        else if (tileSpec.equals("audioprofile") && SIMHelper.isMtkAudioProfilesSupport())
            return new AudioProfileTile(this);
        /// add AudioProfile in quicksetting @}
        /// M: add DataConnection in quicksetting @{
        else if (tileSpec.equals("dataconnection") && !SIMHelper.isWifiOnlyDevice())
            return new MobileDataTile(this);
        /// M: add DataConnection in quicksetting @}

        else if (tileSpec.startsWith(IntentTile.PREFIX)) return IntentTile.create(this,tileSpec);
        else throw new IllegalArgumentException("Bad tile spec: " + tileSpec);
    }

    private List<String> loadTileSpecs() {
        final Resources res = mContext.getResources();
        String defaultTileList = res.getString(R.string.quick_settings_tiles_default);

        /// M: Customize the quick settings tile order for operator. @{
        IQuickSettingsPlugin quickSettingsPlugin = PluginFactory.getQuickSettingsPlugin(mContext);
        defaultTileList = quickSettingsPlugin.customizeQuickSettingsTileOrder(defaultTileList);
        /// M: Customize the quick settings tile order for operator. @}

        String tileList = Secure.getStringForUser(mContext.getContentResolver(), TILES_SETTING,
                mUserTracker.getCurrentUserId());
        if (tileList == null) {
            tileList = res.getString(R.string.quick_settings_tiles);
            if (DEBUG) Log.d(TAG, "Loaded tile specs from config: " + tileList);
        } else {
            if (DEBUG) Log.d(TAG, "Loaded tile specs from setting: " + tileList);
        }
        final ArrayList<String> tiles = new ArrayList<String>();
        boolean addedDefault = false;
        for (String tile : tileList.split(",")) {
            tile = tile.trim();
            if (tile.isEmpty()) continue;
            if (tile.equals("default")) {
                if (!addedDefault) {
                    tiles.addAll(Arrays.asList(defaultTileList.split(",")));
                    addedDefault = true;
                }
            } else {
                tiles.add(tile);
            }
        }
        return tiles;
    }

    private class Observer extends ContentObserver {
        private boolean mRegistered;

        public Observer() {
            super(new Handler(Looper.getMainLooper()));
        }

        public void register() {
            if (mRegistered) {
                mContext.getContentResolver().unregisterContentObserver(this);
            }
            mContext.getContentResolver().registerContentObserver(Secure.getUriFor(TILES_SETTING),
                    false, this, mUserTracker.getCurrentUserId());
            mRegistered = true;
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            recreateTiles();
        }
    }
}
