/********************************************************************************
 * (C) Copyright 2000-2021, by Shawn Q.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 ********************************************************************************/

package org.uguess.android.sysinfo;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * WidgetConfigure
 */
public final class WidgetConfigure extends Activity {

    static final int WIDGET_INFO = 0x1;
    static final int WIDGET_TASK = 0x2;
    static final int WIDGET_CACHE = 0x4;
    static final int WIDGET_HISTORY = 0x8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED);
        setContentView(R.layout.widget_config);
        ((TextView) findViewById(R.id.txt_background)).setText(ResUtil.getString(this,
                R.string.background));
        int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        final int targetId = widgetId;
        final int[] style = new int[]{100, 0};

        Spinner spStyle = (Spinner) findViewById(R.id.sp_transparency);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{
                        ResUtil.getString(this, R.string.opaque),
                        "25% " + ResUtil.getString(this, R.string.transparency), //$NON-NLS-1$
                        "50% " + ResUtil.getString(this, R.string.transparency), //$NON-NLS-1$
                        "75% " + ResUtil.getString(this, R.string.transparency), //$NON-NLS-1$
                        ResUtil.getString(this, R.string.transparent),});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStyle.setAdapter(adapter);
        spStyle.setOnItemSelectedListener(new OnItemSelectedListener() {

            public void onItemSelected(android.widget.AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                style[0] = 100 - arg2 * 25;
            }

            public void onNothingSelected(android.widget.AdapterView<?> arg0) {
            }
        });
        spStyle.setSelection(0);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(WidgetConfigure.this);
        AppWidgetProviderInfo pi = appWidgetManager.getAppWidgetInfo(targetId);

        if (pi != null && pi.provider.getClassName().endsWith("Bar2xWidget")) //$NON-NLS-1$
        {
            findViewById(R.id.types_pane).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.txt_hint)).setText(ResUtil.getString(this,
                    R.string.select_types_hint));

            final CheckBox ckbInfo = (CheckBox) findViewById(R.id.ckb_info);
            final CheckBox ckbTask = (CheckBox) findViewById(R.id.ckb_task);
            final CheckBox ckbCache = (CheckBox) findViewById(R.id.ckb_cache);
            final CheckBox ckbHistory = (CheckBox) findViewById(R.id.ckb_history);

            ckbInfo.setChecked(true);
            ckbTask.setChecked(true);
            ckbCache.setChecked(true);

            style[1] = WIDGET_INFO | WIDGET_TASK | WIDGET_CACHE;
            OnCheckedChangeListener listener = new OnCheckedChangeListener() {

                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    int checked = 0;

                    if (ckbInfo.isChecked()) {
                        checked++;
                    }
                    if (ckbTask.isChecked()) {
                        checked++;
                    }
                    if (ckbCache.isChecked()) {
                        checked++;
                    }
                    if (ckbHistory.isChecked()) {
                        checked++;
                    }

                    if (checked > 3) {
                        if (buttonView != ckbHistory && ckbHistory.isChecked()) {
                            ckbHistory.setChecked(false);
                        } else if (buttonView != ckbCache && ckbCache.isChecked()) {
                            ckbCache.setChecked(false);
                        } else if (buttonView != ckbTask && ckbTask.isChecked()) {
                            ckbTask.setChecked(false);
                        } else if (buttonView != ckbInfo && ckbInfo.isChecked()) {
                            ckbInfo.setChecked(false);
                        }
                    } else if (checked < 3) {
                        if (buttonView != ckbInfo && !ckbInfo.isChecked()) {
                            ckbInfo.setChecked(true);
                        } else if (buttonView != ckbTask && !ckbTask.isChecked()) {
                            ckbTask.setChecked(true);
                        } else if (buttonView != ckbCache && !ckbCache.isChecked()) {
                            ckbCache.setChecked(true);
                        } else if (buttonView != ckbHistory && !ckbHistory.isChecked()) {
                            ckbHistory.setChecked(true);
                        }
                    } else {
                        int types = 0;

                        if (ckbInfo.isChecked()) {
                            types |= WIDGET_INFO;
                        }
                        if (ckbTask.isChecked()) {
                            types |= WIDGET_TASK;
                        }
                        if (ckbCache.isChecked()) {
                            types |= WIDGET_CACHE;
                        }
                        if (ckbHistory.isChecked()) {
                            types |= WIDGET_HISTORY;
                        }

                        style[1] = types;
                    }
                }
            };

            ckbInfo.setOnCheckedChangeListener(listener);
            ckbTask.setOnCheckedChangeListener(listener);
            ckbCache.setOnCheckedChangeListener(listener);
            ckbHistory.setOnCheckedChangeListener(listener);
        }

        Button btnSave = (Button) findViewById(R.id.btn_save);
        btnSave.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                SharedPreferences sp = getSharedPreferences(Constants.SYSINFO_MANAGER_STORE_NAME, Context.MODE_PRIVATE);
                Editor et = sp.edit();
                et.putInt(WidgetProvider.PREF_WIDGET_STYLE_PREFIX + targetId, ((style[1] & 0xff) << 8)
                        | (style[0] & 0xff));
                et.commit();

                int layout = R.layout.widget_bar;

                switch (style[0]) {
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

                boolean hasTask = true;
                boolean hasInfo = true;
                boolean hasCache = true;
                boolean hasHistory = true;

                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(WidgetConfigure.this);
                AppWidgetProviderInfo pi = appWidgetManager.getAppWidgetInfo(targetId);

                if (pi != null) {
                    String name = pi.provider.getClassName();

                    if (name.endsWith("TaskWidget")) //$NON-NLS-1$
                    {
                        hasInfo = false;
                        hasCache = false;
                        hasHistory = false;
                    } else if (name.endsWith("InfoWidget")) //$NON-NLS-1$
                    {
                        hasTask = false;
                        hasCache = false;
                        hasHistory = false;
                    } else if (name.endsWith("CacheWidget")) //$NON-NLS-1$
                    {
                        hasInfo = false;
                        hasTask = false;
                        hasHistory = false;
                    } else if (name.endsWith("HistoryWidget")) //$NON-NLS-1$
                    {
                        hasInfo = false;
                        hasTask = false;
                        hasCache = false;
                    } else if (name.endsWith("Bar2xWidget")) //$NON-NLS-1$
                    {
                        int types = style[1] & 0xff;
                        hasInfo = (types & WIDGET_INFO) == WIDGET_INFO;
                        hasTask = (types & WIDGET_TASK) == WIDGET_TASK;
                        hasCache = (types & WIDGET_CACHE) == WIDGET_CACHE;
                        hasHistory = (types & WIDGET_HISTORY) == WIDGET_HISTORY;
                    }
                }

                WidgetProvider.update(WidgetConfigure.this, appWidgetManager, targetId, layout, hasTask,
                        hasInfo, hasCache, hasHistory);

                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, targetId);
                setResult(RESULT_OK, resultValue);

                finish();
            }
        });

        Button btnCancel = (Button) findViewById(R.id.btn_cancel);
        btnCancel.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // fix: https://code.google.com/p/android/issues/detail?id=19917
        outState.putString("WORKAROUND_FOR_BUG_19917_KEY", //$NON-NLS-1$
                "WORKAROUND_FOR_BUG_19917_VALUE"); //$NON-NLS-1$

        super.onSaveInstanceState(outState);
    }
}
