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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.lang.reflect.Field;

/**
 * StatusUpdaterService
 */
public final class StatusUpdaterService extends Service implements Constants {

    static final int GRAPH_WIDTH = 276;
    static final int GRAPH_HEIGHT = 23;
    static final int GRAPH_BAR = 5;
    static final int GRAPH_BAR_SMALL = 2;
    static final int GRAPH_SPACE = 1;
    static final int GRAPH_BORDER = 2;

    static final String EXTRA_TARGET = "target"; //$NON-NLS-1$
    static final String EXTRA_STATE = "state"; //$NON-NLS-1$
    static final String EXTRA_MONITOR = "monitor"; //$NON-NLS-1$
    static final String EXTRA_HISTORY = "history"; //$NON-NLS-1$
    static final String EXTRA_RATES = "rates"; //$NON-NLS-1$
    static final String EXTRA_ICON = "icon"; //$NON-NLS-1$

    static final int TEXT_COLOR_PRIMARY = 0xde000000;//android.graphics.Color.BLACK;
    static final int TEXT_COLOR_PRIMARY_INV = android.graphics.Color.WHITE;
    static final int TEXT_COLOR_SECONDARY = 0x8a000000;//0xff666666;
    static final int TEXT_COLOR_SECONDARY_INV = 0xb3ffffff;//0xffaaaaaa;

    static final int TARGET_INFO = 0x1;
    static final int TARGET_TASK = 0x2;
    static final int TARGET_WIFI = 0x4;

    static final long INFO_TIME;
    static final long TASK_TIME;
    static final long WIFI_TIME;
    static final long HIDDEN_TIME;
    static final int HIDDEN_ICON;

    static final int INFO_ICON_HIDDEN_LEVEL = 8;
    static final int TASK_ICON_HIDDEN_LEVEL = 8;
    static final int WIFI_ICON_HIDDEN_LEVEL = 4;

    static final Field fdNotificationGroupKey = Util.getField(24, true, Notification.class, "mGroupKey");

    static {
        if (Util.SDK_VER >= 9) {
            HIDDEN_ICON = R.drawable.placeholder;
            HIDDEN_TIME = -Long.MAX_VALUE;

            INFO_TIME = -Long.MAX_VALUE + 1;
            TASK_TIME = -Long.MAX_VALUE + 2;
            WIFI_TIME = -Long.MAX_VALUE + 3;
        } else {
            HIDDEN_ICON = -1;
            HIDDEN_TIME = Long.MAX_VALUE;

            INFO_TIME = Long.MAX_VALUE - 1;
            TASK_TIME = Long.MAX_VALUE - 2;
            WIFI_TIME = Long.MAX_VALUE - 3;
        }
    }

    Notification infoNotify, taskNotify, netNotify, battNotify;

    Graph infoGraph, taskGraph;

    Graph[] extraInfoGraph;

    Handler handler = new Handler();

    UpdaterRunnable updater = new UpdaterRunnable();

    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                if (updater.showCpu || updater.showMem || updater.showWifi) {
                    handler.post(updater);
                }
            } else {
                updater.reset();
                handler.removeCallbacks(updater);
            }
        }
    };

    boolean battReceiverRegistered = false;

    BroadcastReceiver battReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int level = intent.getIntExtra("level", 0); //$NON-NLS-1$
                int scale = intent.getIntExtra("scale", 100); //$NON-NLS-1$

                int percent = level * 100 / scale;

                String lStr = String.valueOf(percent) + '%';

                int health = intent.getIntExtra("health", //$NON-NLS-1$
                        BatteryManager.BATTERY_HEALTH_UNKNOWN);

                String hStr = ResUtil.getString(ctx, R.string.unknown);

                switch (health) {
                    case BatteryManager.BATTERY_HEALTH_GOOD:
                        hStr = ResUtil.getString(ctx, R.string.good);
                        break;
                    case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                        hStr = ResUtil.getString(ctx, R.string.over_heat);
                        break;
                    case BatteryManager.BATTERY_HEALTH_DEAD:
                        hStr = ResUtil.getString(ctx, R.string.dead);
                        break;
                    case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                        hStr = ResUtil.getString(ctx, R.string.over_voltage);
                        break;
                    case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                        hStr = ResUtil.getString(ctx, R.string.failure);
                        break;
                }

                int status = intent.getIntExtra("status", //$NON-NLS-1$
                        BatteryManager.BATTERY_STATUS_UNKNOWN);

                String sStr = ResUtil.getString(ctx, R.string.unknown);

                switch (status) {
                    case BatteryManager.BATTERY_STATUS_CHARGING:
                        sStr = ResUtil.getString(ctx, R.string.charging);
                        break;
                    case BatteryManager.BATTERY_STATUS_DISCHARGING:
                        sStr = ResUtil.getString(ctx, R.string.discharging);
                        break;
                    case BatteryManager.BATTERY_STATUS_FULL:
                        sStr = ResUtil.getString(ctx, R.string.full);
                        break;
                    case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                        sStr = ResUtil.getString(ctx, R.string.not_charging);
                        break;
                }

                String tStr = intent.getStringExtra("technology"); //$NON-NLS-1$

                int voltage = intent.getIntExtra("voltage", 0); //$NON-NLS-1$

                String vStr = String.valueOf(voltage) + "mV"; //$NON-NLS-1$

                int temperature = intent.getIntExtra("temperature", 0); //$NON-NLS-1$

                int tens = temperature / 10;

                String ct = Integer.toString(tens) + "." //$NON-NLS-1$
                        + (temperature - 10 * tens);

                tens = temperature * 18 / 100;

                String ft = Integer.toString(tens + 32) + "." //$NON-NLS-1$
                        + (temperature * 18 - 100 * tens);

                String tpStr = ct + "\u00B0C/" //$NON-NLS-1$
                        + ft + "\u00B0F"; //$NON-NLS-1$

                int plugged = intent.getIntExtra("plugged", 0); //$NON-NLS-1$

                String pStr = ResUtil.getString(ctx, R.string.unknown);

                switch (plugged) {
                    case 0:
                        pStr = ResUtil.getString(ctx, R.string.unplugged);
                        break;
                    case BatteryManager.BATTERY_PLUGGED_AC:
                        pStr = "AC"; //$NON-NLS-1$
                        break;
                    case BatteryManager.BATTERY_PLUGGED_USB:
                        pStr = "USB"; //$NON-NLS-1$
                        break;
                    case BatteryManager.BATTERY_PLUGGED_AC | BatteryManager.BATTERY_PLUGGED_USB:
                        pStr = "AC USB"; //$NON-NLS-1$
                        break;
                }

                int battIconLevel = R.drawable.battery_unknown;

                if (health != BatteryManager.BATTERY_HEALTH_UNKNOWN) {
                    if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                        if (percent < 15) {
                            battIconLevel = R.drawable.battery_charge_10;
                        } else if (percent < 30) {
                            battIconLevel = R.drawable.battery_charge_20;
                        } else if (percent < 50) {
                            battIconLevel = R.drawable.battery_charge_40;
                        } else if (percent < 70) {
                            battIconLevel = R.drawable.battery_charge_60;
                        } else if (percent < 90) {
                            battIconLevel = R.drawable.battery_charge_80;
                        } else {
                            battIconLevel = R.drawable.battery_charge_100;
                        }
                    } else {
                        if (percent < 5) {
                            battIconLevel = R.drawable.battery_0;
                        } else if (percent < 15) {
                            battIconLevel = R.drawable.battery_10;
                        } else if (percent < 30) {
                            battIconLevel = R.drawable.battery_20;
                        } else if (percent < 50) {
                            battIconLevel = R.drawable.battery_40;
                        } else if (percent < 70) {
                            battIconLevel = R.drawable.battery_60;
                        } else if (percent < 90) {
                            battIconLevel = R.drawable.battery_80;
                        } else {
                            battIconLevel = R.drawable.battery_100;
                        }
                    }
                }

                boolean invTitleColor = false;

                SharedPreferences sp =
                        ctx.getSharedPreferences(SYSINFO_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

                if (sp != null) {
                    invTitleColor =
                            sp.getBoolean(PREF_KEY_INVERSE_NOTIFY_TITLE_COLOR,
                                    Util.INVERT_TITLE_COLOR_DEFAULT_SETTING);
                }

                updateEvent(battNotify, ctx, battIconLevel,
                        ResUtil.getString(ctx, R.string.battery_level) + ": " //$NON-NLS-1$
                                + lStr + " (" //$NON-NLS-1$
                                + sStr + ")", //$NON-NLS-1$
                        hStr + ", " //$NON-NLS-1$
                                + tStr + ", " //$NON-NLS-1$
                                + vStr + ", " //$NON-NLS-1$
                                + tpStr + ", " //$NON-NLS-1$
                                + pStr,
                        null, 0, 0, battNotify.contentIntent, 0, invTitleColor);

                try {
                    ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).notify(
                            NOTIFY_BATTERY_UPDATE, battNotify);
                } catch (Exception e) {
                    Log.e(StatusUpdaterService.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }

    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Util.hookExceptionHandler(getApplicationContext());

        IntentFilter itf = new IntentFilter(Intent.ACTION_SCREEN_ON);
        itf.addAction(Intent.ACTION_SCREEN_OFF);

        registerReceiver(receiver, itf);

        boolean useLegacyIcon = false;
        SharedPreferences sp = getSharedPreferences(SYSINFO_MANAGER_STORE_NAME, Context.MODE_PRIVATE);
        if (sp != null) {
            useLegacyIcon = sp.getBoolean(PREF_KEY_USE_LEGACY_ICON, false);
        }

        infoNotify = createInfoNotification(getApplicationContext(), useLegacyIcon);
        taskNotify = createTaskNotification(getApplicationContext(), useLegacyIcon);
        netNotify = createNetNotification(getApplicationContext(), useLegacyIcon);
        battNotify = createBatteryNotification(getApplicationContext());
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);

        updater.reset();
        handler.removeCallbacks(updater);

        SharedPreferences sp =
                getSharedPreferences(SYSINFO_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

        if (sp == null || intent == null) {
            stopSelfResult(startId);

            return;
        }

        boolean highPriority = sp.getBoolean(PREF_KEY_HIGH_PRIORITY, true);
        boolean invTitleColor =
                sp.getBoolean(PREF_KEY_INVERSE_NOTIFY_TITLE_COLOR,
                        Util.INVERT_TITLE_COLOR_DEFAULT_SETTING);
        boolean showSsid = sp.getBoolean(PREF_KEY_SHOW_WIFI_SSID, true);

        boolean disableAllIcon = sp.getBoolean(PREF_KEY_DISABLE_ALL_ICON, false);
        boolean showBattery = sp.getBoolean(PREF_KEY_SHOW_BATTERY_INFO, true) && !disableAllIcon;
        boolean useLegacyIcon = sp.getBoolean(PREF_KEY_USE_LEGACY_ICON, false);

        if (showBattery) {
            if (battReceiverRegistered) {
                // re-register the receiver to honor the setting updates
                unregisterReceiver(battReceiver);
                registerReceiver(battReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            } else {
                registerReceiver(battReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                battReceiverRegistered = true;
            }
        } else if (battReceiverRegistered) {
            unregisterReceiver(battReceiver);
            battReceiverRegistered = false;

            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIFY_BATTERY_UPDATE);
        }

        int target = intent.getIntExtra(EXTRA_TARGET, 0);

        if (target != 0) {
            boolean enabled = intent.getBooleanExtra(EXTRA_STATE, true);
            boolean monitor = intent.getBooleanExtra(EXTRA_MONITOR, true);
            boolean history = intent.getBooleanExtra(EXTRA_HISTORY, true);
            boolean showIcon = intent.getBooleanExtra(EXTRA_ICON, true);

            if (target == TARGET_INFO) {
                long infoDelay = getDelay(sp, PREF_KEY_REFRESH_INTERVAL_CPU);

                update(startId, TARGET_INFO, highPriority, invTitleColor, enabled, infoDelay, monitor,
                        history, showIcon, false, 0, false, false, false, false, 0,
                        false, false, false, false, useLegacyIcon);
            } else if (target == TARGET_TASK) {
                long taskDelay = getDelay(sp, PREF_KEY_REFRESH_INTERVAL_MEM);

                update(startId, TARGET_TASK, highPriority, invTitleColor, false, 0, false, false, false,
                        enabled, taskDelay, monitor, history, showIcon, false, 0, false, false, false,
                        false, useLegacyIcon);
            } else if (target == TARGET_WIFI) {
                boolean showRates = intent.getBooleanExtra(EXTRA_RATES, false);

                long wifiDelay = getDelay(sp, PREF_KEY_REFRESH_INTERVAL_NET);

                update(startId, TARGET_WIFI, highPriority, invTitleColor, false, 0, false, false, false,
                        false, 0, false, false, false, enabled, wifiDelay, showRates, showSsid,
                        showIcon, false, useLegacyIcon);
            } else if (target == (TARGET_INFO | TARGET_TASK | TARGET_WIFI)) {
                boolean showRates = intent.getBooleanExtra(EXTRA_RATES, false);

                long infoDelay = getDelay(sp, PREF_KEY_REFRESH_INTERVAL_CPU);
                long taskDelay = getDelay(sp, PREF_KEY_REFRESH_INTERVAL_MEM);
                long wifiDelay = getDelay(sp, PREF_KEY_REFRESH_INTERVAL_NET);

                update(startId, TARGET_INFO | TARGET_TASK | TARGET_WIFI, highPriority, invTitleColor,
                        enabled, infoDelay, monitor, history, showIcon, enabled, taskDelay, monitor, history,
                        showIcon, enabled, wifiDelay, showRates, showSsid, showIcon, showBattery, useLegacyIcon);
            }
        } else {
            boolean showInfo = sp.getBoolean(PREF_KEY_SHOW_INFO_ICON, true) && !disableAllIcon;
            boolean showCpu = sp.getBoolean(PREF_KEY_SHOW_CPU_MONITOR, true);
            boolean showCpuHistory = sp.getBoolean(PREF_KEY_SHOW_CPU_HISTORY, true);
            boolean showInfoIcon = sp.getBoolean(PREF_KEY_SHOW_BAR_ICON_INFO, true);
            boolean showTask = sp.getBoolean(PREF_KEY_SHOW_TASK_ICON, true) && !disableAllIcon;
            boolean showMem = sp.getBoolean(PREF_KEY_SHOW_MEM_MONITOR, true);
            boolean showMemHistory = sp.getBoolean(PREF_KEY_SHOW_MEM_HISTORY, true);
            boolean showTaskIcon = sp.getBoolean(PREF_KEY_SHOW_BAR_ICON_TASK, true);
            boolean showWifi = sp.getBoolean(PREF_KEY_SHOW_WIFI_ACTIVITY, true) && !disableAllIcon;
            boolean showRates = sp.getBoolean(PREF_KEY_SHOW_WIFI_RATES, true);
            boolean showWifiIcon = sp.getBoolean(PREF_KEY_SHOW_BAR_ICON_WIFI, true);

            long infoDelay = getDelay(sp, PREF_KEY_REFRESH_INTERVAL_CPU);
            long taskDelay = getDelay(sp, PREF_KEY_REFRESH_INTERVAL_MEM);
            long wifiDelay = getDelay(sp, PREF_KEY_REFRESH_INTERVAL_NET);

            update(startId, TARGET_INFO | TARGET_TASK | TARGET_WIFI, highPriority, invTitleColor,
                    showInfo, infoDelay, showCpu, showCpuHistory, showInfoIcon, showTask, taskDelay, showMem,
                    showMemHistory, showTaskIcon, showWifi, wifiDelay, showRates, showSsid, showWifiIcon,
                    showBattery, useLegacyIcon);
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);

        if (battReceiverRegistered) {
            unregisterReceiver(battReceiver);
            battReceiverRegistered = false;
        }

        handler.removeCallbacks(updater);

        if (updater.lastHighPriority != 0) {
            updater.lastHighPriority = 0;

            Util.stopForeground(StatusUpdaterService.this);
        }

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFY_INFO_UPDATE);
        nm.cancel(NOTIFY_TASK_UPDATE);
        nm.cancel(NOTIFY_NET_UPDATE);
        nm.cancel(NOTIFY_BATTERY_UPDATE);

        if (infoGraph != null) {
            infoGraph.release();
            infoGraph = null;
        }

        if (extraInfoGraph != null) {
            for (Graph g : extraInfoGraph) {
                if (g != null) {
                    g.release();
                }
            }

            extraInfoGraph = null;
        }

        if (taskGraph != null) {
            taskGraph.release();
            taskGraph = null;
        }

        super.onDestroy();
    }

    private void update(int serviceId, int target, boolean highPriority, boolean invTitleColor,
                        boolean showInfo, long infoDelay, boolean showCpu, boolean showCpuHistory,
                        boolean showInfoIcon, boolean showTask, long taskDelay, boolean showMem,
                        boolean showMemHistory, boolean showTaskIcon, boolean showWifi, long wifiDelay,
                        boolean showRates, boolean showSsid, boolean showWifiIcon, boolean showBattery,
                        boolean useLegacyIcon) {
        updater.invTitleColor = invTitleColor;
        updater.useLegacyIcon = useLegacyIcon;

        if ((target & TARGET_INFO) == TARGET_INFO) {
            updater.showCpu = false;
            updater.infoDelay = infoDelay;

            if (showInfo) {
                if (showCpu) {
                    updater.showCpu = true;
                    updater.showCpuHistory = showCpuHistory;
                    updater.showCpuIcon = showInfoIcon;
                } else {
                    if (updater.lastHighPriority == TARGET_INFO) {
                        updater.lastHighPriority = 0;

                        Util.stopForeground(StatusUpdaterService.this);
                    }

                    infoNotify.iconLevel = 0;
                    if (Util.SDK_VER >= 21) {
                        infoNotify.icon = getCpuIcon(useLegacyIcon);
                        if (!showInfoIcon) {
                            infoNotify.iconLevel = INFO_ICON_HIDDEN_LEVEL;
                        }
                    } else {
                        infoNotify.icon = showInfoIcon ? getCpuIcon(useLegacyIcon) : HIDDEN_ICON;
                    }
                    infoNotify.when = showInfoIcon ? INFO_TIME : HIDDEN_TIME;

                    if (Util.SDK_VER >= 16) {
                        Util.setNotificationPriority(infoNotify, showInfoIcon ? 0 : -2);
                    }

                    updateEvent(infoNotify, StatusUpdaterService.this, R.drawable.icon,
                            ResUtil.getString(this, ProUtil.getId_appName()),
                            ResUtil.getString(this, R.string.info_icon_hint),
                            null, 0, 0, infoNotify.contentIntent, 0, invTitleColor);

                    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(
                            NOTIFY_INFO_UPDATE, infoNotify);
                }
            } else {
                if (updater.lastHighPriority == TARGET_INFO) {
                    updater.lastHighPriority = 0;

                    Util.stopForeground(StatusUpdaterService.this);
                }

                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                        .cancel(NOTIFY_INFO_UPDATE);
            }

            if (!updater.showCpu) {
                if (infoGraph != null) {
                    infoGraph.release();
                    infoGraph = null;
                }

                if (extraInfoGraph != null) {
                    for (Graph g : extraInfoGraph) {
                        if (g != null) {
                            g.release();
                        }
                    }

                    extraInfoGraph = null;
                }
            }
        }

        if ((target & TARGET_TASK) == TARGET_TASK) {
            updater.showMem = false;
            updater.taskDelay = taskDelay;

            if (showTask) {
                if (showMem) {
                    updater.showMem = true;
                    updater.showMemHistory = showMemHistory;
                    updater.showMemIcon = showTaskIcon;
                } else {
                    if (updater.lastHighPriority == TARGET_TASK) {
                        updater.lastHighPriority = 0;

                        Util.stopForeground(StatusUpdaterService.this);
                    }

                    taskNotify.iconLevel = 0;
                    if (Util.SDK_VER >= 21) {
                        taskNotify.icon = getMemIcon(useLegacyIcon);
                        if (!showTaskIcon) {
                            taskNotify.iconLevel = TASK_ICON_HIDDEN_LEVEL;
                        }
                    } else {
                        taskNotify.icon = showTaskIcon ? getMemIcon(useLegacyIcon) : HIDDEN_ICON;
                    }
                    taskNotify.when = showTaskIcon ? TASK_TIME : HIDDEN_TIME;

                    if (Util.SDK_VER >= 16) {
                        Util.setNotificationPriority(taskNotify, showTaskIcon ? 0 : -2);
                    }

                    updateEvent(taskNotify, StatusUpdaterService.this, R.drawable.end,
                            ResUtil.getString(this, R.string.task_widget_name),
                            ResUtil.getString(this, R.string.task_icon_hint),
                            null, 0, 0, taskNotify.contentIntent, 0, invTitleColor);

                    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(
                            NOTIFY_TASK_UPDATE, taskNotify);
                }
            } else {
                if (updater.lastHighPriority == TARGET_TASK) {
                    updater.lastHighPriority = 0;

                    Util.stopForeground(StatusUpdaterService.this);
                }

                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                        .cancel(NOTIFY_TASK_UPDATE);
            }

            if (!updater.showMem && taskGraph != null) {
                taskGraph.release();
                taskGraph = null;
            }
        }

        if ((target & TARGET_WIFI) == TARGET_WIFI) {
            updater.showWifi = showWifi;
            updater.showRates = showRates;
            updater.showSsid = showSsid;
            updater.showWifiIcon = showWifiIcon;
            updater.wifiDelay = wifiDelay;

            if (!showWifi) {
                if (updater.lastHighPriority == TARGET_WIFI) {
                    updater.lastHighPriority = 0;

                    Util.stopForeground(StatusUpdaterService.this);
                }

                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                        .cancel(NOTIFY_NET_UPDATE);
            }
        }

        if (updater.showCpu || updater.showMem || updater.showWifi) {
            if (highPriority) {
                if (updater.showCpu && updater.lastHighPriority == 0) {
                    infoNotify.iconLevel = 1;

                    Util.startForeground(StatusUpdaterService.this, NOTIFY_INFO_UPDATE, infoNotify);

                    updater.lastHighPriority = TARGET_INFO;
                }

                if (updater.showMem && updater.lastHighPriority == 0) {
                    taskNotify.iconLevel = 1;

                    Util.startForeground(StatusUpdaterService.this, NOTIFY_TASK_UPDATE, taskNotify);

                    updater.lastHighPriority = TARGET_TASK;
                }

                if (updater.showWifi && updater.lastHighPriority == 0) {
                    netNotify.iconLevel = 0;

                    Util.startForeground(StatusUpdaterService.this, NOTIFY_NET_UPDATE, netNotify);

                    updater.lastHighPriority = TARGET_WIFI;
                }
            } else if (updater.lastHighPriority != 0) {
                updater.lastHighPriority = 0;

                Util.stopForeground(StatusUpdaterService.this);
            }

            updater.prepare();

            handler.post(updater);
        } else if (target == (TARGET_INFO | TARGET_TASK | TARGET_WIFI)) {
            if (!showBattery) {
                stopSelfResult(serviceId);
            }
        }
    }

    static long getDelay(SharedPreferences sp, String prefKey) {
        long delay = 0;

        int interval = sp.getInt(prefKey, REFRESH_LOW);

        switch (interval) {
            case REFRESH_HIGH:
                delay = 1000;
                break;
            case REFRESH_NORMAL:
                delay = 2000;
                break;
            case REFRESH_LOW:
                delay = 4000;
                break;
            case REFRESH_HIGHER:
                delay = 500;
                break;
        }

        return delay;
    }

    static int getCpuIcon(boolean useLegacyIcon) {
        if (Util.SDK_VER >= 21) {
            return useLegacyIcon ? R.drawable.cpu : R.drawable.cpu_v21;
        }
        return R.drawable.cpu;
    }

    static int getMemIcon(boolean useLegacyIcon) {
        if (Util.SDK_VER >= 21) {
            return useLegacyIcon ? R.drawable.mem : R.drawable.mem_v21;
        }
        return R.drawable.mem;
    }

    static int getWifiIcon(boolean useLegacyIcon) {
        if (Util.SDK_VER >= 21) {
            return useLegacyIcon ? R.drawable.wifi : R.drawable.wifi_v21;
        }
        return R.drawable.wifi;
    }

    private static Notification createInfoNotification(Context ctx, boolean useLegacyIcon) {
        Intent it = new Intent(ctx, QSystemInfo.class);
        it.setFlags(it.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(ctx, 0, it, 0);

        Notification nc = new Notification(getCpuIcon(useLegacyIcon), null, INFO_TIME);

        nc.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

        if (Util.SDK_VER >= 26) {
            Util.setDefaultNotificationChannel(nc, ctx);
        }

        Util.setLatestEventInfo(nc, ctx, ResUtil.getString(ctx, ProUtil.getId_appName()),
                ResUtil.getString(ctx, R.string.info_icon_hint), pi);

        if (Util.SDK_VER >= 24) {
            Util.safeSet(fdNotificationGroupKey, nc, "info_icon");
        }

        return nc;
    }

    private static Notification createTaskNotification(Context ctx, boolean useLegacyIcon) {
        Intent it = new Intent(ctx, EndTaskService.class);

        PendingIntent pi = PendingIntent.getService(ctx, 0, it, 0);

        Notification nc = new Notification(getMemIcon(useLegacyIcon), null, TASK_TIME);

        nc.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

        if (Util.SDK_VER >= 26) {
            Util.setDefaultNotificationChannel(nc, ctx);
        }

        Util.setLatestEventInfo(nc, ctx, ResUtil.getString(ctx, R.string.task_widget_name),
                ResUtil.getString(ctx, R.string.task_icon_hint), pi);

        if (Util.SDK_VER >= 24) {
            Util.safeSet(fdNotificationGroupKey, nc, "task_icon");
        }
        return nc;
    }

    private static Notification createNetNotification(Context ctx, boolean useLegacyIcon) {
        Intent it = new Intent(ctx, QSystemInfo.class);
        it.setFlags(it.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        it.putExtra(QSystemInfo.EXTRA_TARGET_TAB, 4);
        // dummy data to enforce creating a new pending intent
        it.setData(Uri.parse("target://4")); //$NON-NLS-1$

        PendingIntent pi = PendingIntent.getActivity(ctx, 0, it, 0);

        Notification nc = new Notification(getWifiIcon(useLegacyIcon), null, WIFI_TIME);

        nc.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

        if (Util.SDK_VER >= 26) {
            Util.setDefaultNotificationChannel(nc, ctx);
        }

        Util.setLatestEventInfo(nc, ctx, "Wi-Fi", //$NON-NLS-1$
                ResUtil.getString(ctx, R.string.net_icon_hint), pi);

        if (Util.SDK_VER >= 24) {
            Util.safeSet(fdNotificationGroupKey, nc, "net_icon");
        }
        return nc;
    }

    private static Notification createBatteryNotification(Context ctx) {
        Intent it = new Intent(ctx, QSystemInfo.class);
        it.setFlags(it.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        it.putExtra(QSystemInfo.EXTRA_TARGET_TAB, 1);
        // dummy data to enforce creating a new pending intent
        it.setData(Uri.parse("target://1")); //$NON-NLS-1$

        PendingIntent pi = PendingIntent.getActivity(ctx, 0, it, 0);

        Notification nc = new Notification(HIDDEN_ICON, null, HIDDEN_TIME);

        nc.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

        if (Util.SDK_VER >= 26) {
            Util.setDefaultNotificationChannel(nc, ctx);
        }

        Util.setLatestEventInfo(nc, ctx, ResUtil.getString(ctx, R.string.battery_level), "", pi); //$NON-NLS-1$

        if (Util.SDK_VER >= 16) {
            Util.setNotificationPriority(nc, -2);
        }

        if (Util.SDK_VER >= 24) {
            Util.safeSet(fdNotificationGroupKey, nc, "batt_icon");
        }
        return nc;
    }

    static void updateEvent(Notification nc, Context ctx, int viewIcon, CharSequence title,
                            CharSequence text, Graph[] graph, int[] percent, int color,
                            PendingIntent contentIntent, int popTarget, boolean invTitleColor) {
        RemoteViews contentView = new RemoteViews(ctx.getPackageName(), invTitleColor ? R.layout.history_graph : R.layout.history_graph_light);

        if (viewIcon != 0) {
            contentView.setImageViewResource(R.id.icon, viewIcon);
        }

        if (title != null) {
            contentView.setTextViewText(R.id.title, title);
            contentView.setTextColor(R.id.title, invTitleColor ? TEXT_COLOR_PRIMARY_INV : TEXT_COLOR_PRIMARY);
        }

        if (text != null) {
            contentView.setViewVisibility(R.id.text, View.VISIBLE);
            contentView.setTextViewText(R.id.text, text);
            contentView.setTextColor(R.id.text, invTitleColor ? TEXT_COLOR_SECONDARY_INV : TEXT_COLOR_SECONDARY);
        } else {
            contentView.setViewVisibility(R.id.text, View.GONE);
        }

        updateGraph(contentView, graph, percent, color, 0, R.id.graph);
        updateGraph(contentView, graph, percent, color, 1, R.id.graph1);
        updateGraph(contentView, graph, percent, color, 2, R.id.graph2);
        updateGraph(contentView, graph, percent, color, 3, R.id.graph3);
        updateGraph(contentView, graph, percent, color, 4, R.id.graph4);
        updateGraph(contentView, graph, percent, color, 5, R.id.graph5);
        updateGraph(contentView, graph, percent, color, 6, R.id.graph6);
        updateGraph(contentView, graph, percent, color, 7, R.id.graph7);

        if (popTarget != 0 && Util.SDK_VER >= 12) {
            contentView.setImageViewResource(R.id.pop, invTitleColor ? R.drawable.arrow_light : R.drawable.arrow);
            contentView.setViewVisibility(R.id.pop, View.VISIBLE);
            contentView.setOnClickPendingIntent(
                    R.id.pop,
                    PendingIntent.getService(
                            ctx.getApplicationContext(),
                            0,
                            new Intent(ctx.getApplicationContext(), PopService.class).putExtra(EXTRA_TARGET,
                                    popTarget).setData(Uri.parse("target://" //$NON-NLS-1$
                                    + popTarget)), 0));

        } else {
            contentView.setViewVisibility(R.id.pop, View.GONE);
        }

        nc.contentView = contentView;
        nc.contentIntent = contentIntent;
    }

    private static void updateGraph(RemoteViews contentView, Graph[] graph, int[] percent, int color,
                                    int idx, int viewId) {
        if (graph != null && graph.length > idx && graph[idx] != null) {
            graph[idx].update(percent[idx], color);
            contentView.setViewVisibility(viewId, View.VISIBLE);
            contentView.setImageViewBitmap(viewId, graph[idx].bmp);
        } else {
            contentView.setViewVisibility(viewId, View.GONE);
            if (Util.SDK_VER >= 16) {
                // otherwise will throw NPE
                contentView.setImageViewBitmap(viewId, null);
            }
        }
    }

    static void updateEvent(Notification nc, Context ctx, int viewIcon, CharSequence title,
                            CharSequence text, Graph graph, int percent, int color,
                            PendingIntent contentIntent, int popTarget, boolean invTitleColor) {
        updateEvent(nc, ctx, viewIcon, title, text, graph == null ? null
                        : new Graph[]{graph}, graph == null ? null : new int[]{percent}, color, contentIntent,
                popTarget, invTitleColor);
    }

    /**
     * Graph
     */
    static final class Graph {

        Bitmap bmp;

        private Canvas canv;
        private Paint pt;
        private int width, height, bar, space, border, lastPos;

        Graph(int width, int height, int bar, int space, int border) {
            this.width = width;
            this.height = height;
            this.bar = bar;
            this.space = space;
            this.border = border;

            int count = (width - 2 - 2 * border) / (bar + space);

            lastPos = (count - 1) * (bar + space) + border + 1;

            bmp = Bitmap.createBitmap(width, height, Config.ARGB_8888);
            canv = new Canvas(bmp);

            pt = new Paint();
            pt.setAntiAlias(false);

            pt.setColor(android.graphics.Color.BLACK);
            pt.setStyle(Style.FILL);

            canv.drawRect(0, 0, width, height, pt);
        }

        void update(int data, int color) {
            if (bmp == null || bmp.isRecycled()) {
                return;
            }

            int top = (100 - data) * (height - border * 2 - 2) / 100 + border + 1;

            canv.drawBitmap(bmp, -(bar + space), 0, pt);

            pt.setStyle(Style.FILL);
            pt.setColor(android.graphics.Color.BLACK);
            canv.drawRect(lastPos, 0, width, height, pt);
            canv.drawRect(0, 0, border, height, pt);

            pt.setColor(color);
            canv.drawRect(lastPos, top, lastPos + bar, height - border - 1, pt);

            pt.setStyle(Style.STROKE);
            pt.setColor(0xff696969);
            canv.drawLine(0, 0, width, 0, pt);
            canv.drawLine(0, 0, 0, height, pt);

            pt.setColor(0xffe3e3e3);
            canv.drawLine(width - 1, 0, width - 1, height, pt);
            canv.drawLine(0, height - 1, width, height - 1, pt);
        }

        void release() {
            if (bmp != null && !bmp.isRecycled()) {
                bmp.recycle();
                bmp = null;
            }
        }
    }

    /**
     * UpdaterRunnable
     */
    private final class UpdaterRunnable implements Runnable {

        boolean showCpu;
        boolean showCpuHistory;
        boolean showCpuIcon;
        boolean showMem;
        boolean showMemHistory;
        boolean showMemIcon;
        boolean showWifi;
        boolean showRates;
        boolean showWifiIcon;
        boolean invTitleColor;
        boolean showSsid;
        boolean useLegacyIcon;

        long delay;
        long infoDelay, taskDelay, wifiDelay;
        int totalRun, currentRun;

        long[] lastLoad;
        long[][] lastAllLoad;
        long lastRx, lastTx;

        CpuState cpuObj = new CpuState();

        int lastHighPriority;

        UpdaterRunnable() {

        }

        void prepare() {
            long minDelay = infoDelay != 0 ? infoDelay : (taskDelay != 0 ? taskDelay : wifiDelay);

            if (taskDelay != 0 && taskDelay < minDelay) {
                minDelay = taskDelay;
            }

            if (wifiDelay != 0 && wifiDelay < minDelay) {
                minDelay = wifiDelay;
            }

            long maxDelay = infoDelay != 0 ? infoDelay : (taskDelay != 0 ? taskDelay : wifiDelay);

            if (taskDelay != 0 && taskDelay > maxDelay) {
                maxDelay = taskDelay;
            }

            if (wifiDelay != 0 && wifiDelay > maxDelay) {
                maxDelay = wifiDelay;
            }

            totalRun = minDelay == 0 ? 0 : (int) (maxDelay / minDelay);
            currentRun = 0;
            delay = minDelay;
        }

        void reset() {
            lastLoad = null;
            lastAllLoad = null;
            lastRx = 0;
            lastTx = 0;

            cpuObj.reset();
        }

        private boolean canRun(long targetDelay) {
            if (targetDelay > 0) {
                return ((delay * currentRun) % targetDelay) == 0;
            }
            return false;
        }

        int getRealGraphWidth(DisplayMetrics dm) {
            int usable = (int) (dm.widthPixels / dm.density - 8);
            return Math.min(GRAPH_WIDTH, usable);
        }

        public void run() {
            if (showCpu && canRun(infoDelay)) {
                int cl = 1;
                int pl = 0;

                long[] load = ProcessManager.readCpuLoad();

                if (lastLoad != null && load != null) {
                    long totaldelta = load[0] + load[1] - (lastLoad[0] + lastLoad[1]);

                    if (totaldelta != 0) {
                        long workdelta = load[0] - lastLoad[0];

                        cl = (int) (workdelta * 100 / totaldelta);

                        if (cl < 0) {
                            cl = 0;
                        }

                        if (cl > 100) {
                            cl = 100;
                        }

                        pl = cl;

                        cl = (cl / 15) + 1;
                    }
                }

                lastLoad = load;

                StringBuilder cpuState = new StringBuilder();

                cpuState.append(ResUtil.getString(StatusUpdaterService.this, R.string.cpu_usage));
                cpuState.append(": ").append(pl).append('%'); //$NON-NLS-1$

                String[] cs = cpuObj.getMips();
                if (cs != null && cs.length > 0) {
                    if (cs.length == 1) {
                        if (cs[0] != null) {
                            cpuState.append(" (").append(cs[0]).append(')'); //$NON-NLS-1$
                        }
                    } else {
                        cpuState.append(" ("); //$NON-NLS-1$

                        for (int i = 0; i < cs.length; i++) {
                            if (i > 0) {
                                cpuState.append(", "); //$NON-NLS-1$
                            }

                            cpuState.append(cs[i] != null ? cs[i] : "zZ"); //$NON-NLS-1$
                        }

                        cpuState.append(')');
                    }
                }

                infoNotify.iconLevel = cl;
                if (Util.SDK_VER >= 21) {
                    infoNotify.icon = getCpuIcon(useLegacyIcon);
                    if (!showCpuIcon) {
                        infoNotify.iconLevel = INFO_ICON_HIDDEN_LEVEL;
                    }
                } else {
                    infoNotify.icon = showCpuIcon ? getCpuIcon(useLegacyIcon) : HIDDEN_ICON;
                }
                infoNotify.when = showCpuIcon ? INFO_TIME : HIDDEN_TIME;

                if (Util.SDK_VER >= 16) {
                    Util.setNotificationPriority(infoNotify, showCpuIcon ? 0 : -2);
                }

                if (showCpuHistory) {
                    int pc = Runtime.getRuntime().availableProcessors();

                    if (pc > 1) {
                        long[][] allLoad = ProcessManager.readAllCpuLoad(pc);

                        int[] percents = new int[pc];

                        if (lastAllLoad != null && allLoad != null) {
                            for (int i = 0; i < pc; i++) {
                                if (allLoad[i] == null || i >= lastAllLoad.length || lastAllLoad[i] == null) {
                                    continue;
                                }

                                long totaldelta =
                                        allLoad[i][0] + allLoad[i][1] - (lastAllLoad[i][0] + lastAllLoad[i][1]);

                                if (totaldelta != 0) {
                                    long workdelta = allLoad[i][0] - lastAllLoad[i][0];

                                    percents[i] = (int) (workdelta * 100 / totaldelta);

                                    if (percents[i] < 0) {
                                        percents[i] = 0;
                                    }

                                    if (percents[i] > 100) {
                                        percents[i] = 100;
                                    }
                                }
                            }
                        }

                        lastAllLoad = allLoad;

                        if (extraInfoGraph == null || extraInfoGraph.length != pc) {
                            if (extraInfoGraph != null) {
                                for (Graph g : extraInfoGraph) {
                                    g.release();
                                }
                            }

                            extraInfoGraph = new Graph[pc];

                            DisplayMetrics dm = getResources().getDisplayMetrics();
                            float scale = dm.density;

                            int width = (int) ((getRealGraphWidth(dm) - pc * 2 - 2) * scale / pc);

                            for (int i = 0; i < pc; i++) {
                                extraInfoGraph[i] =
                                        new Graph(width, (int) (GRAPH_HEIGHT * scale), (int) ((pc > 4 ? GRAPH_BAR_SMALL
                                                : GRAPH_BAR) * scale), (int) (GRAPH_SPACE * scale),
                                                (int) (GRAPH_BORDER * scale));
                            }
                        }

                        updateEvent(infoNotify, StatusUpdaterService.this, R.drawable.icon, cpuState,
                                null, extraInfoGraph, percents, 0xff52b652, infoNotify.contentIntent,
                                TARGET_INFO, invTitleColor);
                    } else {
                        if (infoGraph == null) {
                            DisplayMetrics dm = getResources().getDisplayMetrics();
                            float scale = dm.density;
                            infoGraph =
                                    new Graph((int) (getRealGraphWidth(dm) * scale), (int) (GRAPH_HEIGHT * scale),
                                            (int) (GRAPH_BAR * scale), (int) (GRAPH_SPACE * scale),
                                            (int) (GRAPH_BORDER * scale));
                        }

                        updateEvent(infoNotify, StatusUpdaterService.this, R.drawable.icon, cpuState,
                                null, infoGraph, pl, 0xff52b652, infoNotify.contentIntent,
                                TARGET_INFO, invTitleColor);
                    }
                } else {
                    if (infoGraph != null) {
                        infoGraph.release();
                        infoGraph = null;
                    }

                    if (extraInfoGraph != null) {
                        for (Graph g : extraInfoGraph) {
                            if (g != null) {
                                g.release();
                            }
                        }

                        extraInfoGraph = null;
                    }

                    updateEvent(infoNotify, StatusUpdaterService.this, R.drawable.icon, cpuState,
                            ResUtil.getString(StatusUpdaterService.this, R.string.info_icon_hint),
                            null, 0, 0, infoNotify.contentIntent, TARGET_INFO, invTitleColor);
                }

                try {
                    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(
                            NOTIFY_INFO_UPDATE, infoNotify);
                } catch (Exception e) {
                    Log.e(StatusUpdaterService.class.getName(), e.getLocalizedMessage(), e);
                }
            }

            if (showMem && canRun(taskDelay)) {
                int cl = 1;
                int pl = 0;
                String size = ResUtil.getString(StatusUpdaterService.this, R.string.unknown);

                long[] mem = Util.getMemState(StatusUpdaterService.this);

                if (mem != null && mem[0] > 0) {
                    long used = mem[0] - mem[2];

                    if (used < 0) {
                        used = 0;
                    }

                    size = Util.safeFormatFileSize(StatusUpdaterService.this, used);

                    cl = (int) (used * 100 / mem[0]);

                    if (cl < 0) {
                        cl = 0;
                    }

                    if (cl > 100) {
                        cl = 100;
                    }

                    pl = cl;

                    cl = (cl / 15) + 1;
                }

                taskNotify.iconLevel = cl;
                if (Util.SDK_VER >= 21) {
                    taskNotify.icon = getMemIcon(useLegacyIcon);
                    if (!showMemIcon) {
                        taskNotify.iconLevel = TASK_ICON_HIDDEN_LEVEL;
                    }
                } else {
                    taskNotify.icon = showMemIcon ? getMemIcon(useLegacyIcon) : HIDDEN_ICON;
                }
                taskNotify.when = showMemIcon ? TASK_TIME : HIDDEN_TIME;

                if (Util.SDK_VER >= 16) {
                    Util.setNotificationPriority(taskNotify, showMemIcon ? 0 : -2);
                }

                if (showMemHistory) {
                    if (taskGraph == null) {
                        DisplayMetrics dm = getResources().getDisplayMetrics();
                        float scale = dm.density;
                        taskGraph =
                                new Graph((int) (getRealGraphWidth(dm) * scale), (int) (GRAPH_HEIGHT * scale),
                                        (int) (GRAPH_BAR * scale), (int) (GRAPH_SPACE * scale),
                                        (int) (GRAPH_BORDER * scale));
                    }

                    updateEvent(taskNotify, StatusUpdaterService.this, R.drawable.end,
                            ResUtil.getString(StatusUpdaterService.this, R.string.memory_usage) + ": " //$NON-NLS-1$
                                    + pl + "% (" //$NON-NLS-1$
                                    + size + ')',
                            null, taskGraph, pl, 0xff5275c6, taskNotify.contentIntent,
                            TARGET_TASK, invTitleColor);
                } else {
                    if (taskGraph != null) {
                        taskGraph.release();
                        taskGraph = null;
                    }

                    updateEvent(taskNotify, StatusUpdaterService.this, R.drawable.end,
                            ResUtil.getString(StatusUpdaterService.this, R.string.memory_usage) + ": " //$NON-NLS-1$
                                    + pl + "% (" //$NON-NLS-1$
                                    + size + ')',
                            ResUtil.getString(StatusUpdaterService.this, R.string.task_icon_hint),
                            null, 0, 0, taskNotify.contentIntent, TARGET_TASK, invTitleColor);
                }

                try {
                    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(
                            NOTIFY_TASK_UPDATE, taskNotify);
                } catch (Exception e) {
                    Log.e(StatusUpdaterService.class.getName(), e.getLocalizedMessage(), e);
                }
            }

            if (showWifi && canRun(wifiDelay)) {
                boolean cancelNet = true;

                ConnectivityManager cm =
                        (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

                if (cm != null) {
                    NetworkInfo nif = cm.getActiveNetworkInfo();

                    if (nif != null && nif.isConnected()) {
                        String netDev = null;
                        String netType = nif.getSubtypeName();
                        boolean forceHideIcon = false;

                        nif = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                        if (nif != null && nif.isConnected()) {
                            netDev = Util.getActiveWifiDevice();
                            netType = "Wi-Fi"; //$NON-NLS-1$

                            if (showSsid) {
                                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                                WifiInfo wi = wm.getConnectionInfo();

                                if (wi != null && wi.getSSID() != null) {
                                    netType = wi.getSSID();
                                }
                            }
                        } else {
                            netDev = Util.getActiveCellDevice();
                            forceHideIcon = true;
                        }

                        if (netDev != null) {
                            long rx = Util.getRxBytes(netDev);
                            long tx = Util.getTxBytes(netDev);

                            if (rx > lastRx && tx > lastTx) {
                                netNotify.iconLevel = 3;
                            } else if (tx > lastTx) {
                                netNotify.iconLevel = 2;
                            } else if (rx > lastRx) {
                                netNotify.iconLevel = 1;
                            } else {
                                netNotify.iconLevel = 0;
                            }

                            long drx = 0;
                            long dtx = 0;

                            if (showRates && wifiDelay > 0) {
                                if (lastRx != 0) {
                                    drx = rx - lastRx;

                                    if (drx > 0) {
                                        drx = drx * 1000 / wifiDelay;
                                    } else {
                                        drx = 0;
                                    }
                                }

                                if (lastTx != 0) {
                                    dtx = tx - lastTx;

                                    if (dtx > 0) {
                                        dtx = dtx * 1000 / wifiDelay;
                                    } else {
                                        dtx = 0;
                                    }
                                }
                            }

                            lastRx = rx;
                            lastTx = tx;

                            String hint;

                            if (showRates) {
                                hint = ResUtil.getString(StatusUpdaterService.this, R.string.rates) + ": Tx: " //$NON-NLS-1$
                                        + Util.safeFormatFileSize(StatusUpdaterService.this, dtx) + "/s, Rx: " //$NON-NLS-1$
                                        + Util.safeFormatFileSize(StatusUpdaterService.this, drx) + "/s"; //$NON-NLS-1$
                            } else {
                                hint = ResUtil.getString(StatusUpdaterService.this, R.string.net_icon_hint);
                            }

                            if (forceHideIcon) {
                                if (Util.SDK_VER >= 21) {
                                    netNotify.icon = getWifiIcon(useLegacyIcon);
                                    netNotify.iconLevel = WIFI_ICON_HIDDEN_LEVEL;
                                } else {
                                    netNotify.icon = HIDDEN_ICON;
                                }
                                netNotify.when = HIDDEN_TIME;
                            } else {
                                if (Util.SDK_VER >= 21) {
                                    netNotify.icon = getWifiIcon(useLegacyIcon);
                                    if (!showWifiIcon) {
                                        netNotify.iconLevel = WIFI_ICON_HIDDEN_LEVEL;
                                    }
                                } else {
                                    netNotify.icon = showWifiIcon ? getWifiIcon(useLegacyIcon) : HIDDEN_ICON;
                                }
                                netNotify.when = showWifiIcon ? WIFI_TIME : HIDDEN_TIME;
                            }

                            if (Util.SDK_VER >= 16) {
                                Util.setNotificationPriority(netNotify, (showWifiIcon && !forceHideIcon) ? 0 : -2);
                            }

                            updateEvent(netNotify, StatusUpdaterService.this,
                                    forceHideIcon ? ("EDGE".equalsIgnoreCase(netType) ? R.drawable.mobile_edge_noop //$NON-NLS-1$
                                            : R.drawable.mobile_noop) : R.drawable.wifi_noop, (TextUtils.isEmpty(netType) ? "" //$NON-NLS-1$
                                            : netType) + " Tx: " //$NON-NLS-1$
                                            + Util.safeFormatFileSize(StatusUpdaterService.this, lastTx) + ", Rx: " //$NON-NLS-1$
                                            + Util.safeFormatFileSize(StatusUpdaterService.this, lastRx),
                                    hint, null, 0, 0, netNotify.contentIntent, TARGET_WIFI, invTitleColor);

                            try {
                                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(
                                        NOTIFY_NET_UPDATE, netNotify);
                            } catch (Exception e) {
                                Log.e(StatusUpdaterService.class.getName(), e.getLocalizedMessage(), e);
                            }

                            cancelNet = false;
                        }
                    }
                }

                if (cancelNet) {
                    lastRx = 0;
                    lastTx = 0;

                    if (lastHighPriority == TARGET_WIFI) {
                        lastHighPriority = 0;

                        Util.stopForeground(StatusUpdaterService.this);
                    }

                    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                            .cancel(NOTIFY_NET_UPDATE);
                }
            }

            if (delay > 0) {
                handler.postDelayed(this, delay);

                currentRun++;

                if (currentRun >= totalRun) {
                    currentRun -= totalRun;
                }
            }
        }
    }
}
