package com.android.keyguard;


import com.fingerprints.keyguard.IFingerprintUnlockCallback;
import com.fingerprints.keyguard.IFingerprintUnlockInterface;
import com.android.internal.widget.LockPatternUtils;

import android.util.Log;
import android.view.View;
import android.app.ActivityManagerNative;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;

import android.os.Message;
import android.os.RemoteException;


public class FingerprintUnlock implements BiometricSensorUnlock, Handler.Callback {
	
    private final Context mContext;
    private final LockPatternUtils mLockPatternUtils;
    
    private IFingerprintUnlockInterface mService;

    private View mFingerprintUnlockView;
    private KeyguardSecurityCallback mKeyguardScreenCallback;
    
    private volatile boolean mIsRunning = false;
    private boolean mBoundToService = false;
    
    private Handler mHandler;
    private final int MSG_SHOW_VIEW = 0;
    private final int MSG_HIDE_VIEW = 1;
    private final int MSG_SERVICE_CONNECTED = 2;
    private final int MSG_SERVICE_DISCONNECTED = 3;
    private final int MSG_UNLOCK = 4;
    private final int MSG_CANCEL = 5;
    private final int MSG_REPORT_FAILED_ATTEMPT = 6;
    private final int MSG_EXPOSE_FALLBACK = 7;
    private final int MSG_POKE_WAKELOCK = 8;
    
    private final int BACKUP_LOCK_TIMEOUT = 5000;
    
    private final static String TAG = "FPCUnlock";
   
    private final IFingerprintUnlockCallback mFingerUnlockCallback = new IFingerprintUnlockCallback.Stub() {
        
        @Override
        public void unlock(int userId) {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_UNLOCK, userId, 0));
        }

        @Override
        public void cancel() {
            mHandler.sendEmptyMessage(MSG_CANCEL);
        }

        @Override
        public void reportFailedAttempt() {
            mHandler.sendEmptyMessage(MSG_REPORT_FAILED_ATTEMPT);
        }

        @Override
        public void exposeFallback() {
            mHandler.sendEmptyMessage(MSG_EXPOSE_FALLBACK);
        }

        public void pokeWakelock(int millis) {     
            Message message = mHandler.obtainMessage(MSG_POKE_WAKELOCK, millis, -1);
            mHandler.sendMessage(message);
        }

    };
    
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder iservice) {
            mService = IFingerprintUnlockInterface.Stub.asInterface(iservice);
            mHandler.sendEmptyMessage(MSG_SERVICE_CONNECTED);
        }
        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.e(TAG, "Unexpected disconnect from FingerprintUnlock service");
            mHandler.sendEmptyMessage(MSG_SERVICE_DISCONNECTED);
        }
    };

    public FingerprintUnlock(Context context) {
    	mContext = context;
    	mLockPatternUtils = new LockPatternUtils(context);
    	mHandler = new Handler(this);
    }
    
    public void setKeyguardCallback(KeyguardSecurityCallback keyguardSecurityCallback) {
    	mKeyguardScreenCallback = keyguardSecurityCallback;
    }
    
	
	@Override
	public void initializeView(View biometricUnlockView) {
		mFingerprintUnlockView = biometricUnlockView;
	}

	@Override
	public boolean isRunning() {
		return mIsRunning;
	}


	@Override
    public boolean start() {
		if (!mBoundToService) {
                    Intent mIntent = new Intent();
                    ComponentName mComponentName = new ComponentName("com.fingerprints.keyguard", "com.fingerprints.keyguard.FingerprintUnlockService");
                    mIntent.setComponent(mComponentName);
		    mBoundToService = mContext.bindService(mIntent, 
		            mConnection,
		            Context.BIND_AUTO_CREATE);//,
		            //mLockPatternUtils.getCurrentUser());
		    if (!mBoundToService) {
		    	handleExposeFallback();
		    	return false;
		    }
		}
		mIsRunning = true;
		return true;
	}

	@Override
	public boolean stop() {
		if (!mBoundToService)
			return false;

        final boolean wasRunning = mIsRunning;
        try {
            if (mService != null) {  
            	mService.stopUi();
                mService.unregisterCallback(mFingerUnlockCallback);   
            }
            mContext.unbindService(mConnection);      
        } catch (RemoteException e) {
        	e.printStackTrace();
       	mService = null;
		}
        
		mBoundToService = false;
        mIsRunning = false;
        
        return wasRunning;
	}

	@Override
	public void cleanUp() {
		Log.d(TAG, "cleanup() mBoundToService =" + mBoundToService);
		stop();
	}

	@Override
	public int getQuality() {
		return 0;
	}

	@Override
	public boolean handleMessage(Message msg) {
		 switch (msg.what) {
         case MSG_SHOW_VIEW:
             handleShowView();
             break;
         case MSG_HIDE_VIEW:
             handleHideView();
             break;
         case MSG_SERVICE_CONNECTED:
             handleServiceConnected();
             break;
         case MSG_SERVICE_DISCONNECTED:
             handleServiceDisconnected();
             break;
         case MSG_UNLOCK:
             handleUnlock(msg.arg1);
             break;
         case MSG_CANCEL:
             handleCancel();
             break;
         case MSG_REPORT_FAILED_ATTEMPT:
             handleReportFailedAttempt();
             break;
         case MSG_EXPOSE_FALLBACK:
             handleExposeFallback();
             break;
         case MSG_POKE_WAKELOCK:
             handlePokeWakelock(msg.arg1);
             break;
         default:
             Log.e(TAG, "Unhandled message");
             return false;
     }
     return true;
	}

    void handleShowView() {    
        if(mFingerprintUnlockView != null) {
        	mFingerprintUnlockView.setVisibility(View.VISIBLE);
        }
    }

    void handleHideView() {
        if (mFingerprintUnlockView != null) {
        	mFingerprintUnlockView.setVisibility(View.INVISIBLE);
        }
    }
    
    void handleServiceConnected() {
    	if (mIsRunning == false) {
    		Log.d(TAG, "handleServiceConnected() mIsRunning = false, returning..");
    		return;
    	}
    	try {
	        mService.registerCallback(mFingerUnlockCallback);
	      
	        if (mFingerprintUnlockView != null) {
	            IBinder windowToken = mFingerprintUnlockView.getWindowToken();
	            if (windowToken != null) {
	                mKeyguardScreenCallback.userActivity();
	                int[] position = new int[2];
	                mFingerprintUnlockView.getLocationInWindow(position);
	                mService.startUi(windowToken, position[0], position[1], mFingerprintUnlockView.getWidth(),
	                		mFingerprintUnlockView.getHeight());
	            } else {
	                Log.e(TAG, "windowToken is null in handleServiceConnected()");
	            }
	        }
    	} catch (RemoteException e) {
    		handleServiceDisconnected();
    		e.printStackTrace();
		}
    }

    void handleServiceDisconnected() {
        Log.e(TAG, "handleServiceDisconnected()");  
        mService = null;     
        mBoundToService = false;
        mIsRunning = false;
        handleExposeFallback();
    }
    
    private boolean mLoginNextUser = false;
    private int mNextUser = -1;
    
    boolean userAuthenticated(int userId) {
    	boolean ret = false;
    	if (userId == mNextUser && mLoginNextUser)
    		ret = true;
    	
    	mNextUser = -1;
    	mLoginNextUser = false;
    	return ret;
    }
    
    void handleUnlock(int userId) {
        if (mFingerprintUnlockView != null) {
        	mFingerprintUnlockView.setVisibility(View.VISIBLE);
        }
        stop();
        if (userId != mLockPatternUtils.getCurrentUser()) {
        	try {
                ActivityManagerNative.getDefault().switchUser(userId);
                mLoginNextUser = true;
        		mNextUser = userId;
            } catch (RemoteException re) {
                Log.e(TAG, "Couldn't switch user " + re);
            }
        } else {
	        mKeyguardScreenCallback.reportUnlockAttempt(true);
	        mKeyguardScreenCallback.dismiss(true);
	    }
    } 
    
    void handleCancel() {
        if (mFingerprintUnlockView != null) {
        	mFingerprintUnlockView.setVisibility(View.INVISIBLE);
        }
        stop();
        mKeyguardScreenCallback.userActivity();
    }

    void handleReportFailedAttempt() {
    	KeyguardUpdateMonitor.getInstance(mContext).setAlternateUnlockEnabled(false);
        mKeyguardScreenCallback.reportUnlockAttempt(false);
        mKeyguardScreenCallback.showBackupSecurity();
    }
  
    void handleExposeFallback() {
        if (mFingerprintUnlockView != null) {
        	mFingerprintUnlockView.setVisibility(View.INVISIBLE);
        }
    }

    void handlePokeWakelock(int millis) {
        mKeyguardScreenCallback.userActivity();
    }

	@Override
	public void stopAndShowBackup() {
		// TODO Auto-generated method stub
		
	}

}
