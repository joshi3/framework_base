package com.mediatek.systemui.qs.tiles;

import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.MobileDataController;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

import com.mediatek.systemui.statusbar.util.SIMHelper;

/**
 * M: Mobile Data Connection Tile.
 */
public class MobileDataTile extends QSTile<QSTile.SignalState> {
    private static final int QS_MOBILE_DISABLE = R.drawable.ic_qs_mobile_off;
    private static final int QS_MOBILE_ENABLE = R.drawable.ic_qs_mobile_white;

    private final NetworkController mController;
    private final MobileDataController mDataController;

    /**
     * Constructs a new MobileDataTile instance with Host.
     * @param host A Host object.
     */
    public MobileDataTile(Host host) {
        super(host);
        mController = host.getNetworkController();
        mDataController = mController.getMobileDataController();
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mController.addNetworkSignalChangedCallback(mCallback);
        } else {
            mController.removeNetworkSignalChangedCallback(mCallback);
        }
    }

    @Override
    protected SignalState newTileState() {
        return new SignalState();
    }

    @Override
    protected void handleClick() {
        if (mDataController.isMobileDataSupported() && mState.enabled) {
            /// M: if try to turn on the data connection.
            if (!mState.connected) {
                int subId = SubscriptionManager.getDefaultDataSubId();
                /// M: if has not default data SIM set, ignore click.
                if (subId < 0) {
                    return;
                /// M: if the default data SIM radio off, ignore click.
                } else if (!SIMHelper.isRadioOn(subId)) {
                    return;
                }
            }
            mDataController.setMobileDataEnabled(!mState.connected);
        }
    }

    @Override
    protected void handleUpdateState(QSTile.SignalState state, Object arg) {
        state.visible = mController.hasMobileDataFeature();
        if (!state.visible) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "handleUpdateState arg=" + arg);
        }

        if (arg == null) {
            return;
        }

        final CallbackInfo cb = (CallbackInfo) arg;

        final boolean enabled = mDataController.isMobileDataSupported()
                && !cb.noSim && !cb.airplaneModeEnabled && cb.enabled && isDefaultDataSimRadioOn();
        final boolean dataConnected = enabled && mDataController.isMobileDataEnabled()
                && (cb.mobileSignalIconId > 0) && (cb.enabledDesc != null);
        final boolean dataNotConnected = (cb.mobileSignalIconId > 0) && (cb.enabledDesc == null);

        state.enabled = enabled;
        state.connected = dataConnected;
        state.activityIn = cb.enabled && cb.activityIn;
        state.activityOut = cb.enabled && cb.activityOut;
        state.filter = true;
        state.label = mContext.getString(R.string.mobile);

        if (!state.enabled) {
            state.icon = ResourceIcon.get(QS_MOBILE_DISABLE);
        } else if (dataConnected) {
            state.icon = ResourceIcon.get(QS_MOBILE_ENABLE);
        } else if (dataNotConnected) {
            state.icon = ResourceIcon.get(QS_MOBILE_DISABLE);
        } else {
            state.icon = ResourceIcon.get(QS_MOBILE_DISABLE);
        }

        if (DEBUG) {
            Log.d(TAG, "handleUpdateState state=" + state);
        }
    }

    private final boolean isDefaultDataSimRadioOn() {
        final int subId = SubscriptionManager.getDefaultDataSubId();
        final boolean isRadioOn = subId >= 0 && SIMHelper.isRadioOn(subId);
        if (DEBUG) {
            Log.d(TAG, "isDefaultDataSimRadioOn subId=" + subId + ", isRadioOn=" + isRadioOn);
        }
        return isRadioOn;
    }

    /**
     * NetworkSignalChanged Callback Info.
     */
    private static final class CallbackInfo {
        public boolean enabled;
        public boolean wifiEnabled;
        public boolean wifiConnected;
        public boolean airplaneModeEnabled;
        public int mobileSignalIconId;
        public int dataTypeIconId;
        public boolean activityIn;
        public boolean activityOut;
        public String enabledDesc;
        public boolean noSim;

        @Override
        public String toString() {
            return new StringBuilder("CallbackInfo[")
                    .append("enabled=").append(enabled)
                    .append(",wifiEnabled=").append(wifiEnabled)
                    .append(",wifiConnected=").append(wifiConnected)
                    .append(",airplaneModeEnabled=").append(airplaneModeEnabled)
                    .append(",mobileSignalIconId=").append(mobileSignalIconId)
                    .append(",dataTypeIconId=").append(dataTypeIconId)
                    .append(",activityIn=").append(activityIn)
                    .append(",activityOut=").append(activityOut)
                    .append(",enabledDesc=").append(enabledDesc)
                    .append(",noSim=").append(noSim)
                    .append(']').toString();
        }
    }

    private final NetworkSignalChangedCallback mCallback = new NetworkSignalChangedCallback() {
        private CallbackInfo mInfo = new CallbackInfo();

        @Override
        public void onWifiSignalChanged(boolean enabled, boolean connected, int wifiSignalIconId,
                boolean activityIn, boolean activityOut,
                String wifiSignalContentDescriptionId, String description) {
            mInfo.wifiEnabled = enabled;
            mInfo.wifiConnected = connected;
            refreshState(mInfo);
        }

        @Override
        public void onMobileDataSignalChanged(boolean enabled,
                int mobileSignalIconId,
                String mobileSignalContentDescriptionId, int dataTypeIconId,
                boolean activityIn, boolean activityOut,
                String dataTypeContentDescriptionId, String description,
                boolean isDataTypeIconWide) {
            mInfo.enabled = enabled;
            mInfo.mobileSignalIconId = mobileSignalIconId;
            mInfo.dataTypeIconId = dataTypeIconId;
            mInfo.activityIn = activityIn;
            mInfo.activityOut = activityOut;
            mInfo.enabledDesc = description;
            if (DEBUG) {
                Log.d(TAG, "onMobileDataSignalChanged mInfo=" + mInfo);
            }
            refreshState(mInfo);
        }

        public void onNoSimVisibleChanged(boolean noSims) {
            mInfo.noSim = noSims;
            if (mInfo.noSim) {
                // Make sure signal gets cleared out when no sims.
                mInfo.mobileSignalIconId = 0;
                mInfo.dataTypeIconId = 0;
                mInfo.enabled = false;

                if (DEBUG) {
                    Log.d(TAG, "onNoSimVisibleChanged noSim=" + noSims);
                }
            }
            refreshState(mInfo);
        }

        @Override
        public void onAirplaneModeChanged(boolean enabled) {
            mInfo.airplaneModeEnabled = enabled;
            if (mInfo.airplaneModeEnabled) {
                mInfo.mobileSignalIconId = 0;
                mInfo.dataTypeIconId = 0;
                mInfo.enabled = false;
            }
            refreshState(mInfo);
        }

        @Override
        public void onMobileDataEnabled(boolean enabled) {
            refreshState(mInfo);
        }
    };
}
