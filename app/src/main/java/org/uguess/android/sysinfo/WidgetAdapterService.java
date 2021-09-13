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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

/**
 * WidgetAdapterService
 */
public final class WidgetAdapterService extends Service {

    static final String EXTRA_TOKEN = "tk"; //$NON-NLS-1$
    static final String EXTRA_TARGET = "tp"; //$NON-NLS-1$

    static final int TARGET_INFO = 1;
    static final int TARGET_TASK = 2;
    static final int TARGET_CACHE = 3;
    static final int TARGET_HISTORY = 4;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Util.hookExceptionHandler(getApplicationContext());
    }

    @Override
    public void onStart(Intent intent, int startId) {
        adaptService(this, intent);
        stopSelfResult(startId);
    }

    static void adaptService(Context ctx, Intent intent) {
        String token = intent.getStringExtra(EXTRA_TOKEN);
        int target = intent.getIntExtra(EXTRA_TARGET, 0);

        boolean identified = false;

        if (!TextUtils.isEmpty(token)) {
            if (Util.isDebugPackage(ctx) || ctx.getPackageManager().checkSignatures(token, ctx.getPackageName()) == 0) {
                Intent it;

                switch (target) {
                    case TARGET_INFO:
                        identified = true;

                        it = new Intent(ctx, QSystemInfo.class);
                        it.setFlags(it.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        ctx.startActivity(it);
                        break;
                    case TARGET_TASK:
                        identified = true;

                        ctx.startService(new Intent(ctx, EndTaskService.class));
                        break;
                    case TARGET_CACHE:
                        identified = true;

                        ctx.startService(new Intent(ctx, ClearCacheService.class));
                        break;
                    case TARGET_HISTORY:
                        identified = true;

                        it = new Intent(ctx, ClearHistoryActivity.class);
                        it.setFlags(it.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        ctx.startActivity(it);
                        break;
                }
            }
        }

        if (!identified) {
            Util.shortToast(ctx, R.string.service_request_error);

            Log.e(WidgetAdapterService.class.getName(), "Bad request from: " //$NON-NLS-1$
                    + token + ", target: " //$NON-NLS-1$
                    + target);
        }
    }
}
