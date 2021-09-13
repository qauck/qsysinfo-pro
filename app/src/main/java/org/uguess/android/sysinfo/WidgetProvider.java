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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

/**
 * WidgetProvider
 */
public final class WidgetProvider extends AppWidgetProvider {

    static final String PREF_WIDGET_STYLE_PREFIX = "widget_style_"; //$NON-NLS-1$

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(context, appWidgetManager, appWidgetIds, false, true, true, true, true);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        delete(context, appWidgetIds);
    }

    static void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
                       boolean validateSetting, boolean hasTask, boolean hasInfo, boolean hasCache,
                       boolean hasHistory) {
        if (appWidgetIds != null && appWidgetIds.length > 0) {
            SharedPreferences sp = context.getSharedPreferences(Constants.SYSINFO_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            for (int id : appWidgetIds) {
                int layout = R.layout.widget_bar;

                if (sp != null) {
                    int setting = sp.getInt(PREF_WIDGET_STYLE_PREFIX + id, 100);
                    int style = setting & 0xff;

                    switch (style) {
                        case 0:
                            layout = R.layout.widget_bar_transparent;
                            break;
                        case 25:
                            layout = R.layout.widget_bar_25;
                            break;
                        case 50:
                            layout = R.layout.widget_bar_50;
                            break;
                        case 75:
                            layout = R.layout.widget_bar_75;
                            break;
                    }

                    if (validateSetting) {
                        int types = (setting >> 8) & 0xff;
                        hasInfo = (types & WidgetConfigure.WIDGET_INFO) == WidgetConfigure.WIDGET_INFO;
                        hasTask = (types & WidgetConfigure.WIDGET_TASK) == WidgetConfigure.WIDGET_TASK;
                        hasCache = (types & WidgetConfigure.WIDGET_CACHE) == WidgetConfigure.WIDGET_CACHE;
                        hasHistory = (types & WidgetConfigure.WIDGET_HISTORY) == WidgetConfigure.WIDGET_HISTORY;
                    }
                }

                update(context, appWidgetManager, id, layout, hasTask, hasInfo, hasCache, hasHistory);
            }
        }
    }

    static void update(Context context, AppWidgetManager appWidgetManager, int appWidgetId,
                       int layout, boolean hasTask, boolean hasInfo, boolean hasCache, boolean hasHistory) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), layout);

        if (hasTask) {
            Intent it = new Intent(context, QSystemInfo.ForegroundStarterService.class);
            it.putExtra(Constants.EXTRA_SVC_CLZ_NAME, EndTaskService.class.getName());
            // dummy data to enforce creating a new pending intent
            it.setData(Uri.parse("target://EndTaskService")); //$NON-NLS-1$

            Intent fallback = new Intent(context, EndTaskService.class);
            PendingIntent pi = Util.getForegroundServicePendingIntent(context, 0, it, 0, fallback);
            rv.setOnClickPendingIntent(R.id.img_kill, pi);
        } else {
            rv.setViewVisibility(R.id.img_kill, View.GONE);
        }

        if (hasInfo) {
            Intent it = new Intent(context, QSystemInfo.class);
            it.setFlags(it.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pi = PendingIntent.getActivity(context, 0, it, 0);
            rv.setOnClickPendingIntent(R.id.img_info, pi);
        } else {
            rv.setViewVisibility(R.id.img_info, View.GONE);
        }

        if (hasCache) {
            Intent it = new Intent(context, QSystemInfo.ForegroundStarterService.class);
            it.putExtra(Constants.EXTRA_SVC_CLZ_NAME, ClearCacheService.class.getName());
            // dummy data to enforce creating a new pending intent
            it.setData(Uri.parse("target://ClearCacheService")); //$NON-NLS-1$

            Intent fallback = new Intent(context, ClearCacheService.class);
            PendingIntent pi = Util.getForegroundServicePendingIntent(context, 0, it, 0, fallback);
            rv.setOnClickPendingIntent(R.id.img_cache, pi);
        } else {
            rv.setViewVisibility(R.id.img_cache, View.GONE);
        }

        if (hasHistory) {
            Intent it = new Intent(context, ClearHistoryActivity.class);
            it.setFlags(it.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pi = PendingIntent.getActivity(context, 0, it, 0);
            rv.setOnClickPendingIntent(R.id.img_history, pi);
        } else {
            rv.setViewVisibility(R.id.img_history, View.GONE);
        }

        if (!hasTask || (!hasInfo && !hasCache && !hasHistory)) {
            rv.setViewVisibility(R.id.img_div1, View.GONE);
        }

        if (!hasInfo || (!hasCache && !hasHistory)) {
            rv.setViewVisibility(R.id.img_div2, View.GONE);
        }

        if (!hasCache || !hasHistory) {
            rv.setViewVisibility(R.id.img_div3, View.GONE);
        }

        appWidgetManager.updateAppWidget(appWidgetId, rv);
    }

    static void delete(Context context, int[] appWidgetIds) {
        if (appWidgetIds != null && appWidgetIds.length > 0) {
            SharedPreferences sp = context.getSharedPreferences(Constants.SYSINFO_MANAGER_STORE_NAME, Context.MODE_PRIVATE);
            Editor et = sp.edit();

            for (int id : appWidgetIds) {
                et.remove(PREF_WIDGET_STYLE_PREFIX + id);
            }

            et.commit();
        }
    }

    /**
     * TaskWidget
     */
    public static final class TaskWidget extends AppWidgetProvider {

        @Override
        public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
            update(context, appWidgetManager, appWidgetIds, false, true, false, false, false);
        }

        @Override
        public void onDeleted(Context context, int[] appWidgetIds) {
            delete(context, appWidgetIds);
        }
    }

    /**
     * InfoWidget
     */
    public static final class InfoWidget extends AppWidgetProvider {

        @Override
        public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
            update(context, appWidgetManager, appWidgetIds, false, false, true, false, false);
        }

        @Override
        public void onDeleted(Context context, int[] appWidgetIds) {
            delete(context, appWidgetIds);
        }
    }

    /**
     * CacheWidget
     */
    public static final class CacheWidget extends AppWidgetProvider {

        @Override
        public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
            update(context, appWidgetManager, appWidgetIds, false, false, false, true, false);
        }

        @Override
        public void onDeleted(Context context, int[] appWidgetIds) {
            delete(context, appWidgetIds);
        }
    }

    /**
     * HistoryWidget
     */
    public static final class HistoryWidget extends AppWidgetProvider {

        @Override
        public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
            update(context, appWidgetManager, appWidgetIds, false, false, false, false, true);
        }

        @Override
        public void onDeleted(Context context, int[] appWidgetIds) {
            delete(context, appWidgetIds);
        }
    }

    /**
     * Bar2xWidget
     */
    public static final class Bar2xWidget extends AppWidgetProvider {

        @Override
        public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
            update(context, appWidgetManager, appWidgetIds, true, false, false, false, false);
        }

        @Override
        public void onDeleted(Context context, int[] appWidgetIds) {
            delete(context, appWidgetIds);
        }
    }

}
