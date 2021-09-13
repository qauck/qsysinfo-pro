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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.os.Bundle;

/**
 * ShortcutAdapterActivity
 */
public final class ShortcutAdapterActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Util.hookExceptionHandler(getApplicationContext());
        WidgetAdapterService.adaptService(this, getIntent());
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // fix: https://code.google.com/p/android/issues/detail?id=19917
        outState.putString("WORKAROUND_FOR_BUG_19917_KEY", //$NON-NLS-1$
                "WORKAROUND_FOR_BUG_19917_VALUE"); //$NON-NLS-1$

        super.onSaveInstanceState(outState);
    }

    /**
     * ShortcutConfigure
     */
    public static final class ShortcutConfigure extends Activity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            OnClickListener listener = new OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent shortcutIntent =
                            new Intent(getApplicationContext(), ShortcutAdapterActivity.class);
                    shortcutIntent.putExtra(WidgetAdapterService.EXTRA_TOKEN, getPackageName());

                    ShortcutIconResource iconResource = null;
                    String label = null;

                    switch (which) {
                        case 0:
                            shortcutIntent.putExtra(WidgetAdapterService.EXTRA_TARGET,
                                    WidgetAdapterService.TARGET_INFO);
                            iconResource =
                                    ShortcutIconResource.fromContext(getApplicationContext(),
                                            R.drawable.shortcut_i);
                            label = ResUtil.getString(ShortcutConfigure.this, R.string.info_widget_name);
                            break;
                        case 1:
                            shortcutIntent.putExtra(WidgetAdapterService.EXTRA_TARGET,
                                    WidgetAdapterService.TARGET_TASK);
                            iconResource =
                                    ShortcutIconResource.fromContext(getApplicationContext(),
                                            R.drawable.shortcut_x);
                            label = ResUtil.getString(ShortcutConfigure.this, R.string.task_widget_name);
                            break;
                        case 2:
                            shortcutIntent.putExtra(WidgetAdapterService.EXTRA_TARGET,
                                    WidgetAdapterService.TARGET_CACHE);
                            iconResource =
                                    ShortcutIconResource.fromContext(getApplicationContext(),
                                            R.drawable.shortcut_c);
                            label = ResUtil.getString(ShortcutConfigure.this, R.string.cache_widget_name);
                            break;
                        case 3:
                            shortcutIntent.putExtra(WidgetAdapterService.EXTRA_TARGET,
                                    WidgetAdapterService.TARGET_HISTORY);
                            iconResource =
                                    ShortcutIconResource.fromContext(getApplicationContext(),
                                            R.drawable.shortcut_h);
                            label = ResUtil.getString(ShortcutConfigure.this, R.string.history_widget_name);
                            break;
                    }

                    Intent intent = new Intent();
                    intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
                    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, label);
                    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
                    setResult(RESULT_OK, intent);

                    dialog.dismiss();

                    finish();
                }
            };

            OnCancelListener dismissListener = new OnCancelListener() {

                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            };

            new AlertDialog.Builder(this)
                    .setTitle(ResUtil.getString(this, R.string.choose_shortcut))
                    .setItems(
                            new String[]{ResUtil.getString(this, R.string.info_widget_name),
                                    ResUtil.getString(this, R.string.task_widget_name),
                                    ResUtil.getString(this, R.string.cache_widget_name),
                                    ResUtil.getString(this, R.string.history_widget_name)}, listener)
                    .setOnCancelListener(dismissListener).create().show();
        }

        @Override
        protected void onSaveInstanceState(Bundle outState) {
            // fix: https://code.google.com/p/android/issues/detail?id=19917
            outState.putString("WORKAROUND_FOR_BUG_19917_KEY", //$NON-NLS-1$
                    "WORKAROUND_FOR_BUG_19917_VALUE"); //$NON-NLS-1$

            super.onSaveInstanceState(outState);
        }
    }

}
