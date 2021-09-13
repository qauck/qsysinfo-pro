/********************************************************************************
 * (C) Copyright 2000-2021, by Shawn Q.
 * <p/>
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 ********************************************************************************/

package org.uguess.android.sysinfo;

import android.app.Dialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import java.lang.reflect.Method;

/**
 * ClearCacheService
 */
public final class ClearCacheService extends Service {

    static Method mdFreeStorageAndNotify;

    static {
        try {
            mdFreeStorageAndNotify = PackageManager.class.getDeclaredMethod("freeStorageAndNotify", //$NON-NLS-1$
                    long.class, IPackageDataObserver.class);
        } catch (Exception e) {
            Log.e(ClearCacheService.class.getName(), e.getLocalizedMessage(), e);
        }
    }

    @Override
    public void onCreate() {
        Util.hookExceptionHandler(getApplicationContext());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onStart(Intent intent, final int startId) {
        if (mdFreeStorageAndNotify == null) {
            Util.shortToast(this, R.string.clear_cache_disable);
            stopSelfResult(startId);
            return;
        }

        // CLEAR_CACHE permission is only for system apps since Marshmallow
        if (Util.SDK_VER >= 23 && !Util.isSysApp(this)) {
            if (Util.SDK_VER >= 23 && !Util.isSystemOpEnabled(this, "android:system_alert_window" /*AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW*/)) {
                Util.longToast(this, ResUtil.getString(this, R.string.clear_cache_alt2));
                Util.showManageApps(ClearCacheService.this);
                stopSelfResult(startId);
            } else {
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            Util.showManageApps(ClearCacheService.this);
                        }
                        stopSelfResult(startId);
                    }
                };

                Dialog dlg = Util.newAlertDialogBuilder(this).setTitle(ResUtil.getString(this, R.string.warning))
                        .setMessage(ResUtil.getString(this, R.string.clear_cache_alt))
                        .setPositiveButton(android.R.string.yes, listener)
                        .setNegativeButton(android.R.string.no, listener).create();

                WindowManager.LayoutParams lp =
                        new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                                Util.SDK_VER < 26 ? WindowManager.LayoutParams.TYPE_SYSTEM_ALERT : (2038 /*TYPE_APPLICATION_OVERLAY = FIRST_SYSTEM_WINDOW + 38*/),
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.LEFT;
                lp.x = 0;
                lp.y = (int) (100 * ClearCacheService.this.getResources().getDisplayMetrics().density);
                lp.alpha = 1;
                lp.windowAnimations = android.R.style.Animation_Translucent;

                dlg.getWindow().setAttributes(lp);
                dlg.show();
            }

            return;
        }

        try {
            final Handler handler = new Handler();

            final PackageManager pm = getPackageManager();

            Util.shortToast(this, R.string.start_clear_cache);

            final long[] oldStat = Util.getStorageState(Environment.getDataDirectory());

            mdFreeStorageAndNotify.invoke(pm, 1000000000, new IPackageDataObserver.Stub() {

                public void onRemoveCompleted(String packageName, boolean succeeded) throws RemoteException {
                    long totalSize = (oldStat != null && oldStat.length > 0) ? oldStat[0] : 0;

                    if (totalSize > 1000000000) {
                        try {
                            mdFreeStorageAndNotify.invoke(pm, totalSize, new Stub() {

                                public void onRemoveCompleted(String packageName, boolean succeeded)
                                        throws RemoteException {
                                    handler.post(new Runnable() {

                                        public void run() {
                                            doClearSucceeded(ClearCacheService.this, oldStat);

                                            stopSelfResult(startId);
                                        }
                                    });
                                }
                            });

                            return;
                        } catch (Exception e) {
                            Log.e(ClearCacheService.class.getName(), e.getLocalizedMessage(), e);
                        }
                    }

                    handler.post(new Runnable() {

                        public void run() {
                            doClearSucceeded(ClearCacheService.this, oldStat);

                            stopSelfResult(startId);
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(ClearCacheService.class.getName(), e.getLocalizedMessage(), e);
            Util.shortToast(this, R.string.clear_cache_fail, e.getLocalizedMessage());
        }
    }

    static void doClearSucceeded(Context ctx, long[] oldStat) {
        long gain = -1;

        if (oldStat != null && oldStat.length > 1) {
            long[] newStat = Util.getStorageState(Environment.getDataDirectory());

            if (newStat != null && newStat.length > 1) {
                gain = newStat[1] - oldStat[1];
            }
        }

        if (gain != -1) {
            Util.shortToast(ctx, R.string.clear_cache_finish2, Util.safeFormatFileSize(ctx, gain));
        } else {
            Util.shortToast(ctx, R.string.clear_cache_finish);
        }
    }

}
