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
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SearchRecentSuggestionsProvider;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Browser;
import android.provider.SearchRecentSuggestions;
import android.util.Log;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 * ClearHistoryActivity
 */
public class ClearHistoryActivity extends ListActivity implements Constants {

    private static final int MSG_CLEAN_FINISH = MSG_PRIVATE + 1;

    ProgressDialog progress;

    Handler handler = new ClearHistoryHandler(this);

    private static final class ClearHistoryHandler extends Handler {

        private WeakReference<ClearHistoryActivity> acRef;

        ClearHistoryHandler(ClearHistoryActivity ac) {
            acRef = new WeakReference<ClearHistoryActivity>(ac);
        }

        public void handleMessage(android.os.Message msg) {
            ClearHistoryActivity host = acRef.get();
            if (host == null) {
                return;
            }

            switch (msg.what) {
                case MSG_INIT_OK:

                    ArrayAdapter<HistoryItem> adapter =
                            (ArrayAdapter<HistoryItem>) host.getListView().getAdapter();

                    adapter.setNotifyOnChange(false);

                    adapter.clear();

                    ArrayList<HistoryItem> data = (ArrayList<HistoryItem>) msg.obj;

                    if (data != null) {
                        for (int i = 0, size = data.size(); i < size; i++) {
                            adapter.add(data.get(i));
                        }
                    }

                    adapter.notifyDataSetChanged();

                    sendEmptyMessage(MSG_DISMISS_PROGRESS);

                    break;
                case MSG_DISMISS_PROGRESS:

                    if (host.progress != null) {
                        Util.safeDismissDialog(host.progress);
                        host.progress = null;
                    }
                    break;
                case MSG_CLEAN_FINISH:

                    host.toggleAllSelection(false);

                    Util.shortToast(host, (String) msg.obj);
                    break;
            }
        }

        ;
    }

    ;

    OnCheckedChangeListener checkListener = new OnCheckedChangeListener() {

        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            ((HistoryItem) getListView().getItemAtPosition((Integer) buttonView.getTag())).checked =
                    isChecked;

            View v = findViewById(R.id.app_footer);

            ((TextView) v.findViewById(R.id.txt_count)).setText(String.valueOf(getSelected(getListView())
                    .size()));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Util.hookExceptionHandler(getApplicationContext());

        setContentView(R.layout.app_lst_view);
        findViewById(R.id.app_footer).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_export).setVisibility(View.GONE);

        ((TextView) findViewById(R.id.txt_count)).setText("0"); //$NON-NLS-1$

        View btn = findViewById(R.id.btn_uninstall);
        btn.setBackgroundResource(R.drawable.bg_button_3);
        ((TextView) btn).setText("C"); //$NON-NLS-1$
        btn.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                doClean();
            }
        });
        btn.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                Util.shortToast(ClearHistoryActivity.this, R.string.clean);
                return true;
            }
        });

        btn = findViewById(R.id.btn_sel_all);
        btn.setBackgroundResource(R.drawable.bg_button_3);
        btn.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                toggleAllSelection(true);
            }
        });
        btn.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                Util.shortToast(ClearHistoryActivity.this, R.string.select_all);
                return true;
            }
        });

        btn = findViewById(R.id.btn_desel_all);
        btn.setBackgroundResource(R.drawable.bg_button_3);
        btn.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                toggleAllSelection(false);
            }
        });
        btn.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                Util.shortToast(ClearHistoryActivity.this, R.string.deselect_all);
                return true;
            }
        });

        ListView lstItems = getListView();

        lstItems.setFastScrollEnabled(true);

        registerForContextMenu(lstItems);

        lstItems.setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                CheckBox ckb_app = (CheckBox) view.findViewById(R.id.ckb_app);
                ckb_app.setChecked(!ckb_app.isChecked());
            }
        });

        ArrayAdapter<HistoryItem> adapter = new ArrayAdapter<HistoryItem>(this, R.layout.app_item) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view;
                TextView txt_name;
                ImageView img_type;
                CheckBox ckb_app;

                if (convertView instanceof LinearLayout) {
                    view = convertView;
                } else {
                    view = getLayoutInflater().inflate(R.layout.app_item, parent, false);
                }

                if (position >= getCount()) {
                    return view;
                }

                HistoryItem itm = getItem(position);

                txt_name = (TextView) view.findViewById(R.id.app_name);
                if (itm.label != null) {
                    txt_name.setText(itm.label);
                } else {
                    txt_name.setText(itm.pkgName);
                }

                img_type = (ImageView) view.findViewById(R.id.img_app_icon);
                if (itm.icon != null) {
                    img_type.setImageDrawable(itm.icon);
                } else {
                    try {
                        img_type.setImageDrawable(getPackageManager().getDefaultActivityIcon());
                    } catch (Exception fe) {
                        img_type.setImageDrawable(null);

                        Log.e(ClearHistoryActivity.class.getName(), fe.getLocalizedMessage());
                    }
                }

                view.findViewById(R.id.app_version).setVisibility(View.GONE);
                view.findViewById(R.id.app_size).setVisibility(View.GONE);
                view.findViewById(R.id.app_path).setVisibility(View.GONE);
                view.findViewById(R.id.app_time).setVisibility(View.GONE);
                view.findViewById(R.id.tags_pane).setVisibility(View.GONE);

                ckb_app = (CheckBox) view.findViewById(R.id.ckb_app);
                ckb_app.setVisibility(View.VISIBLE);
                ckb_app.setTag(position);
                ckb_app.setChecked(itm.checked);
                ckb_app.setOnCheckedChangeListener(checkListener);

                return view;
            }
        };

        lstItems.setAdapter(adapter);

        refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // fix: https://code.google.com/p/android/issues/detail?id=19917
        outState.putString("WORKAROUND_FOR_BUG_19917_KEY", //$NON-NLS-1$
                "WORKAROUND_FOR_BUG_19917_VALUE"); //$NON-NLS-1$

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (progress != null) {
            Util.safeDismissDialog(progress);
            progress = null;
        }
    }

    private void refresh() {
        if (progress != null) {
            Util.safeDismissDialog(progress);
        }
        progress = new ProgressDialog(this);
        progress.setMessage(ResUtil.getString(this, R.string.loading));
        progress.setIndeterminate(true);
        progress.show();

        new Thread(new Runnable() {

            public void run() {
                ArrayList<HistoryItem> data = new ArrayList<HistoryItem>();

                PackageManager pm = getPackageManager();

                ApplicationInfo ai = null;

                try {
                    ai = pm.getApplicationInfo("com.android.browser", //$NON-NLS-1$
                            0);

                    if (ai != null) {
                        HistoryItem item = new HistoryItem();

                        item.pkgName = "com.android.browser"; //$NON-NLS-1$
                        item.label = pm.getApplicationLabel(ai);
                        item.icon = pm.getApplicationIcon(ai);

                        data.add(item);
                    }
                } catch (NameNotFoundException e) {
                    // ignore
                }

                if (ai == null) {
                    try {
                        ai = pm.getApplicationInfo("com.google.android.browser", //$NON-NLS-1$
                                0);

                        if (ai != null) {
                            HistoryItem item = new HistoryItem();

                            item.pkgName = "com.google.android.browser"; //$NON-NLS-1$
                            item.label = pm.getApplicationLabel(ai);
                            item.icon = pm.getApplicationIcon(ai);

                            data.add(item);
                        }
                    } catch (NameNotFoundException e) {
                        Log.d(ClearHistoryActivity.class.getName(), "android browser application not found."); //$NON-NLS-1$
                    }
                }

                List<PackageInfo> pkgs = pm.getInstalledPackages(PackageManager.GET_PROVIDERS);

                HashMap<String, String> authMap = new HashMap<String, String>();
                HashMap<String, String> permMap = new HashMap<String, String>();

                ContentResolver cr = getContentResolver();

                for (PackageInfo pi : pkgs) {
                    // browser already handled at first entry
                    if ("com.android.browser".equals(pi.packageName) //$NON-NLS-1$
                            || "com.google.android.browser".equals(pi.packageName)) //$NON-NLS-1$
                    {
                        continue;
                    }

                    File f = new File("/data/data/" //$NON-NLS-1$
                            + pi.packageName + "/databases/suggestions.db"); //$NON-NLS-1$

                    // TODO also consider name as hitory.db, search_history.db,
                    // etc. Note even these db exists, the provide may not
                    // support the correct delete() call. So it's difficult to
                    // figure out a generic algorithm to populate the
                    // "clearable" app list.

                    if (!f.exists()) {
                        continue;
                    }

                    ProviderInfo[] pfs = pi.providers;

                    if (pfs != null) {
                        for (ProviderInfo pf : pfs) {
                            if (pf != null && pf.packageName != null && pf.authority != null) {
                                Uri uri = Uri.parse("content://" //$NON-NLS-1$
                                        + pf.authority + "/suggestions"); //$NON-NLS-1$

                                String mimeType = null;

                                try {
                                    mimeType = cr.getType(uri);
                                } catch (Exception e) {
                                    // ignore
                                }

                                if (SearchManager.SUGGEST_MIME_TYPE.equals(mimeType)
                                        || "vnd.android.cursor.dir/suggestion".equals(mimeType)) //$NON-NLS-1$
                                {
                                    authMap.put(pf.packageName, pf.authority);

                                    if (pf.readPermission != null || pf.writePermission != null) {
                                        permMap.put(pf.packageName, "read: " //$NON-NLS-1$
                                                + pf.readPermission + ", write: " //$NON-NLS-1$
                                                + pf.writePermission);
                                    }

                                    // in current android implementation, all
                                    // authorities shares the same database, so
                                    // we only need find the first valid
                                    // authority, this may change in future
                                    // android versions.
                                    break;
                                }
                            }
                        }
                    }
                }

                for (Entry<String, String> ent : authMap.entrySet()) {
                    String pkg = ent.getKey();

                    String perm = permMap.get(pkg);

                    if (perm != null) {
                        Log.d(ClearHistoryActivity.class.getName(), "History provider found for [" //$NON-NLS-1$
                                + pkg + "] but it requests extra permission: " //$NON-NLS-1$
                                + perm);
                    } else {
                        HistoryItem item = new HistoryItem();
                        item.pkgName = pkg;
                        item.authority = ent.getValue();

                        try {
                            ai = pm.getApplicationInfo(pkg, 0);

                            if (ai != null) {
                                item.label = pm.getApplicationLabel(ai);
                                item.icon = pm.getApplicationIcon(ai);
                            }
                        } catch (NameNotFoundException e) {
                            Log.d(ClearHistoryActivity.class.getName(), "Can not found application info for " //$NON-NLS-1$
                                    + pkg);
                        }

                        data.add(item);
                    }
                }

                handler.sendMessage(handler.obtainMessage(MSG_INIT_OK, data));
            }
        }).start();
    }

    void doClean() {
        final List<HistoryItem> sels = getSelected(getListView());

        if (sels == null || sels.size() == 0) {
            Util.shortToast(this, R.string.no_app_selected);
        } else {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    if (which == Dialog.BUTTON_POSITIVE) {
                        if (progress != null) {
                            Util.safeDismissDialog(progress);
                        }
                        progress = new ProgressDialog(ClearHistoryActivity.this);
                        progress.setMessage(ResUtil.getString(ClearHistoryActivity.this, R.string.cleaning));
                        progress.setIndeterminate(true);
                        progress.show();

                        new Thread(new Runnable() {

                            public void run() {
                                ContentResolver cr = getContentResolver();

                                boolean error = false;

                                for (HistoryItem hi : sels) {
                                    if ("com.android.browser".equals(hi.pkgName) //$NON-NLS-1$
                                            || "com.google.android.browser".equals(hi.pkgName)) //$NON-NLS-1$
                                    {
                                        try {
                                            Browser.clearHistory(cr);
                                            Browser.clearSearches(cr);
                                        } catch (Exception e) {
                                            error = true;

                                            Log.e(ClearHistoryActivity.class.getName(),
                                                    "Cannot clear history for android browser", //$NON-NLS-1$
                                                    e);
                                        }
                                    } else {
                                        try {
                                            new SearchRecentSuggestions(ClearHistoryActivity.this, hi.authority,
                                                    SearchRecentSuggestionsProvider.DATABASE_MODE_QUERIES).clearHistory();
                                        } catch (Exception e) {
                                            error = true;

                                            Log.e(ClearHistoryActivity.class.getName(), "Cannot clear history for " //$NON-NLS-1$
                                                    + hi.pkgName, e);
                                        }
                                    }
                                }

                                handler.sendEmptyMessage(MSG_DISMISS_PROGRESS);

                                handler.sendMessage(handler.obtainMessage(MSG_CLEAN_FINISH, ResUtil.getString(
                                        ClearHistoryActivity.this, error ? R.string.clean_history_err
                                                : R.string.clean_history_suc)));
                            }
                        }).start();
                    }
                }
            };

            Util.newAlertDialogBuilder(this).setTitle(ResUtil.getString(this, R.string.warning))
                    .setMessage(ResUtil.getString(this, R.string.clean_history_warn))
                    .setPositiveButton(ResUtil.getString(this, R.string.cont), listener)
                    .setNegativeButton(android.R.string.cancel, listener).create().show();
        }
    }

    List<HistoryItem> getSelected(ListView lstApps) {
        int count = lstApps.getCount();

        ArrayList<HistoryItem> apps = new ArrayList<HistoryItem>();

        for (int i = 0; i < count; i++) {
            HistoryItem holder = (HistoryItem) lstApps.getItemAtPosition(i);

            if (holder.checked) {
                apps.add(holder);
            }
        }

        return apps;
    }

    void toggleAllSelection(boolean selected) {
        ListView lstApps = getListView();

        int totalCount = lstApps.getCount();

        for (int i = 0; i < totalCount; i++) {
            HistoryItem holder = (HistoryItem) lstApps.getItemAtPosition(i);

            holder.checked = selected;
        }

        ((ArrayAdapter) lstApps.getAdapter()).notifyDataSetChanged();
    }

    /**
     * HistoryItem
     */
    private static final class HistoryItem {

        String pkgName;
        String authority;

        Drawable icon;
        CharSequence label;

        boolean checked;

        HistoryItem() {
        }
    }
}
