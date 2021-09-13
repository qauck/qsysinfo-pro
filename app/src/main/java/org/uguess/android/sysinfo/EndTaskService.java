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

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import org.uguess.android.sysinfo.ProcessManager.ProcessInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * EndTaskService
 */
public final class EndTaskService extends Service {

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

        List<ProcessInfo> raps = ProcessManager.getRunningProcessInfo(this, true, false);

        if (raps == null) {
            return;
        }

        String self = getPackageName();
        int killed = 0;
        int ignored = 0;
        String name;

        SharedPreferences sp =
                getSharedPreferences(Constants.PROCESS_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

        ArrayList<String> ignoreList = ProcessManager.getIgnoreList(sp);

        boolean useGuard = ProcessManager.guardEnabled(sp);
        ArrayList<String> guardList = useGuard ? ProcessManager.getGuardList(sp) : null;

        final ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        final long oldMem = getAvailableMem(am);

        for (int i = 0, size = raps.size(); i < size; i++) {
            ProcessInfo rap = raps.get(i);

            name = rap.processName;

            int killType = Util.killable(name, self, ignoreList, useGuard, guardList);

            if (killType == -1) {
                continue;
            }

            if (killType == 1) {
                ignored++;
            } else if (rap.pkgList != null) {
                for (String pkg : rap.pkgList) {
                    if (pkg != null) {
                        int subKillType = Util.killable(pkg, self, ignoreList, useGuard, guardList);

                        if (subKillType == 0) {
                            am.restartPackage(pkg);
                            killed++;
                        }
                    }
                }
            }
        }

        final int bKilled = killed;
        final int bIgnored = ignored;

        new Handler().postDelayed(new Runnable() {

            public void run() {
                long gain = 0;

                if (oldMem != -1) {
                    long newMem = getAvailableMem(am);

                    if (newMem != -1) {
                        gain = newMem - oldMem;
                    }

                    if (gain < 0) {
                        gain = 0;
                    }
                }

                Util.shortToast(EndTaskService.this,
                        bKilled > 1 ? R.string.kill_info2 : R.string.kill_info, bKilled, bIgnored,
                        Util.safeFormatFileSize(EndTaskService.this, gain));

                stopSelfResult(startId);
            }
        }, 300);
    }

    static long getAvailableMem(ActivityManager am) {
        long mem = -1;

        try {
            MemoryInfo mi = new MemoryInfo();
            am.getMemoryInfo(mi);
            mem = mi.availMem;
        } catch (Exception e) {
            Log.d(EndTaskService.class.getName(), e.getLocalizedMessage(), e);
        }

        return mem;
    }
}
