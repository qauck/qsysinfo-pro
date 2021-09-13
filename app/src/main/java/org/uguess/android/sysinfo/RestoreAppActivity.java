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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import org.uguess.android.sysinfo.ApplicationManager.AppViewHolder;
import org.uguess.android.sysinfo.ToolMenuDialog.ActionHint;
import org.uguess.android.sysinfo.ToolMenuDialog.IActionMenuProvider;
import org.uguess.android.sysinfo.Util.EditorState;

import java.io.File;
import java.io.FileFilter;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

/**
 * RestoreAppActivity
 */
public class RestoreAppActivity extends ListActivity implements Constants, IActionMenuProvider {

    private static final int MSG_SCAN = MSG_PRIVATE + 1;
    private static final int MSG_PRE_SCAN = MSG_PRIVATE + 2;
    private static final int MSG_REMOVE = MSG_PRIVATE + 3;
    private static final int MSG_REFRESH = MSG_PRIVATE + 4;

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 100;

    private static final String PREF_KEY_DEFAULT_RESTORE_DIR = "default_restore_dir"; //$NON-NLS-1$
    private static final String PREF_KEY_APP_RESTORE_DIR = "app_restore_dir"; //$NON-NLS-1$
    private static final String PREF_KEY_SEARCH_SUB_DIR = "search_sub_dir"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_PATH = "show_path"; //$NON-NLS-1$

    private static final int TAG_SYSTEM = 1;
    private static final int TAG_USER = 2;
    private static final int TAG_ARCHIVED = 3;
    private static final int TAG_CUSTOM = 4;
    private static final int TAG_RECENT = 5;
    private static final int TAG_UNTAGGED = 6;
    private static final int TAG_USER_DEFINED = 7;

    private static final int ORDER_TYPE_NAME = 0;
    private static final int ORDER_TYPE_SIZE = 1;
    private static final int ORDER_TYPE_INSTALL = 2;
    private static final int ORDER_TYPE_DATE = 3;
    private static final int ORDER_TYPE_PATH = 4;
    private static final int ORDER_TYPE_VERSION_CODE = 100;

    ProgressDialog progress;

    private boolean skipUpdate;

    private int currentLayer;

    volatile boolean stopCheck;

    String currentPath;

    String currentTag;

    int currentType;

    ArchiveAppLoaderThread appLoaderThread;

    Handler handler = new RestoreAppHandler(this);

    private static final class RestoreAppHandler extends Handler {

        private WeakReference<RestoreAppActivity> acRef;

        RestoreAppHandler(RestoreAppActivity ac) {
            acRef = new WeakReference<RestoreAppActivity>(ac);
        }

        @Override
        public void handleMessage(Message msg) {
            final RestoreAppActivity host = acRef.get();
            if (host == null) {
                return;
            }

            RestoreListAdapter adapter;

            switch (msg.what) {
                case MSG_INIT_OK:

                    adapter = (RestoreListAdapter) host.getListAdapter();

                    ArrayList<GroupMeta> dataList = (ArrayList<GroupMeta>) msg.obj;

                    adapter.setItems(dataList);

                    // should always no selection at this stage
                    host.hideButtons();

                    sendEmptyMessage(MSG_DISMISS_PROGRESS);

                    if (host.getListView().getCount() == 0) {
                        Util.shortToast(host, R.string.no_apk_show);
                    }

                    break;
                case MSG_DISMISS_PROGRESS:

                    if (host.progress != null) {
                        Util.safeDismissDialog(host.progress);
                        host.progress = null;
                    }
                    break;
                case MSG_SCAN:

                    if (host.progress != null) {
                        host.progress.setMessage(ResUtil.getString(host, R.string.scanning, msg.obj));
                        host.progress.setProgress(host.progress.getProgress() + 1);
                    }
                    break;
                case MSG_PRE_SCAN:

                    if (host.progress != null) {
                        Util.safeDismissDialog(host.progress);
                    }

                    host.progress = new ProgressDialog(host);
                    host.progress.setMessage(ResUtil.getString(host, R.string.loading));
                    host.progress.setIndeterminate(false);
                    host.progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    host.progress.setMax(msg.arg1);
                    host.progress.setOnCancelListener(new OnCancelListener() {

                        public void onCancel(DialogInterface dialog) {
                            if (host.appLoaderThread != null) {
                                host.appLoaderThread.aborted = true;
                                host.appLoaderThread = null;
                            }
                        }
                    });
                    host.progress.show();
                    break;
                case MSG_REFRESH:

                    ((RestoreListAdapter) host.getListAdapter()).notifyDataSetChanged();
                    break;
                case MSG_TOAST:

                    Util.shortToast(host, (String) msg.obj);
                    break;
                case MSG_REMOVE:

                    adapter = (RestoreListAdapter) host.getListAdapter();

                    ArrayList<ApkInfo> removed = (ArrayList<ApkInfo>) msg.obj;

                    adapter.remove(removed);

                    if (!adapter.hasSelection()) {
                        host.hideButtons();
                    }

                    sendEmptyMessage(MSG_DISMISS_PROGRESS);

                    break;
            }
        }
    }

    ;

    OnCheckedChangeListener checkListener = new OnCheckedChangeListener() {

        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            RestoreListAdapter adapter = (RestoreListAdapter) getListAdapter();

            adapter.getApkInfo((Integer) buttonView.getTag()).checked = isChecked;

            View v = findViewById(R.id.app_footer);

            ((TextView) v.findViewById(R.id.txt_count)).setText(String.valueOf(adapter.getSelection()
                    .size()));

            if (isChecked) {
                if (v.getVisibility() != View.VISIBLE) {
                    v.setVisibility(View.VISIBLE);

                    v.startAnimation(AnimationUtils.loadAnimation(RestoreAppActivity.this,
                            R.anim.footer_appear));
                }
            } else if (!adapter.hasSelection()) {
                hideButtons();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currentPath = null;
        currentTag = null;
        currentType = 0;

        setContentView(R.layout.app_lst_view);

        View btn = findViewById(R.id.btn_export);
        btn.setBackgroundResource(R.drawable.bg_button_2);
        ((TextView) btn).setText("R"); //$NON-NLS-1$
        btn.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                doRestore();
            }
        });
        btn.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                Util.shortToast(RestoreAppActivity.this, R.string.restore);
                return true;
            }
        });

        btn = findViewById(R.id.btn_uninstall);
        btn.setBackgroundResource(R.drawable.bg_button_2);
        ((TextView) btn).setText("D"); //$NON-NLS-1$
        btn.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                RestoreListAdapter adapter = (RestoreListAdapter) getListAdapter();

                doDelete(adapter.getSelection());
            }
        });
        btn.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                Util.shortToast(RestoreAppActivity.this, R.string.delete_file);
                return true;
            }
        });

        btn = findViewById(R.id.btn_sel_all);
        btn.setBackgroundResource(R.drawable.bg_button_2);
        btn.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                showAdvancedSelection();
            }
        });
        btn.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                Util.shortToast(RestoreAppActivity.this, R.string.select);
                return true;
            }
        });

        btn = findViewById(R.id.btn_desel_all);
        btn.setBackgroundResource(R.drawable.bg_button_2);
        btn.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                toggleAllSelection(false);
            }
        });
        btn.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                Util.shortToast(RestoreAppActivity.this, R.string.deselect_all);
                return true;
            }
        });

        ListView lstApps = getListView();

        lstApps.setFastScrollEnabled(true);

        registerForContextMenu(lstApps);

        lstApps.setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                RestoreListAdapter adapter = (RestoreListAdapter) getListAdapter();

                ApkInfo ai = adapter.getApkInfo(position);

                if (ai == null) {
                    // handle group header
                    adapter.toggleGroup(position);
                } else if (ai.file == null) {
                    // handle tag
                    if (isExplicitPathNeeded(ai.pkgName, ai.versionCode)) {
                        Util.shortToast(RestoreAppActivity.this, R.string.error_locate_path);
                    } else {
                        loadApps(ai.versionCode, ai.pkgName,
                                ai.versionCode == TAG_USER_DEFINED ? ai.label.toString() : null);
                    }
                } else {
                    // handle standard or group items
                    CheckBox ckb_app = (CheckBox) view.findViewById(R.id.ckb_app);
                    ckb_app.setChecked(!ckb_app.isChecked());

                    ImageView iconView = (ImageView) view.findViewById(R.id.img_app_icon);
                    if (iconView != null && iconView.getVisibility() == View.VISIBLE) {
                        adapter.updateAppIcon(iconView, ai, true);
                    }
                }
            }
        });

        setListAdapter(new RestoreListAdapter(this, checkListener));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // fix: https://code.google.com/p/android/issues/detail?id=19917
        outState.putString("WORKAROUND_FOR_BUG_19917_KEY", //$NON-NLS-1$
                "WORKAROUND_FOR_BUG_19917_VALUE"); //$NON-NLS-1$

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Util.checkStoragePermission(this);

        internalStart();
    }

    @Override
    protected void onStop() {
        if (stopCheck) {
            stopCheck = false;
        }

        if (appLoaderThread != null) {
            appLoaderThread.aborted = true;
            appLoaderThread = null;
        }

        if (progress != null) {
            Util.safeDismissDialog(progress);
            progress = null;
        }

        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (stopCheck) {
            stopCheck = false;

            // need re-init, since for some pad devices, the onStart() may not be
            // called as expected
            internalStart();
        }
    }

    private void internalStart() {
        if (skipUpdate) {
            skipUpdate = false;
        } else {
            if (!Util.getBooleanOption(this, RESTORE_MANAGER_STORE_NAME, PREF_KEY_USE_TAG_VIEW)
                    || currentLayer == LAYER_ITEM) {
                loadApps(currentType, currentPath, currentTag);
            } else {
                loadLayer();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            if (currentLayer == LAYER_ITEM
                    && Util.getBooleanOption(this, RESTORE_MANAGER_STORE_NAME, PREF_KEY_USE_TAG_VIEW)) {
                loadLayer();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PREFERENCES && data != null) {
            skipUpdate = true;

            SharedPreferences prefStore =
                    getSharedPreferences(RESTORE_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            ArrayList<String> removedTags = data.getStringArrayListExtra(KEY_REMOVED_TAGS);

            if (removedTags != null) {
                for (String tag : removedTags) {
                    Util.updateCustomTagLinks(this, prefStore, tag, null);
                }
            }

            EditorState es = Util.beginEditOption(this, RESTORE_MANAGER_STORE_NAME);

            if (Util.updateStringOption(data, es, PREF_KEY_APP_RESTORE_DIR)) {
                skipUpdate = false;
            }

            if (Util.updateBooleanOption(data, es, PREF_KEY_USE_TAG_VIEW)) {
                skipUpdate = false;

                currentLayer = LAYER_TAG;
                currentPath = null;
                currentTag = null;
                currentType = 0;
            }

            if (Util.updateBooleanOption(data, es, PREF_KEY_SHOW_COUNT)) {
                if (currentLayer == LAYER_TAG) {
                    skipUpdate = false;
                }
            }

            if (Util.updateStringOption(data, es, PREF_KEY_CUSTOM_TAGS)) {
                if (currentLayer == LAYER_TAG) {
                    skipUpdate = false;
                } else if (currentLayer == LAYER_ITEM && removedTags != null) {
                    skipUpdate = false;
                }
            }

            if (Util.updateStringOption(data, es, PREF_KEY_TAG_VISIBILITY)) {
                if (currentLayer == LAYER_TAG) {
                    skipUpdate = false;
                }
            }

            if (Util.updateIntOption(data, es, PREF_KEY_RECENT_SCOPE, 3)) {
                if (currentLayer == LAYER_TAG || (currentLayer == LAYER_ITEM && currentType == TAG_RECENT)) {
                    skipUpdate = false;
                }
            }

            if (Util.updateBooleanOption(data, es, PREF_KEY_SEARCH_SUB_DIR)) {
                skipUpdate = false;
            }

            Util.updateIntOption(data, es, PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_NAME);
            Util.updateIntOption(data, es, PREF_KEY_SORT_DIRECTION, ORDER_ASC);
            Util.updateIntOption(data, es, PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_NAME);
            Util.updateIntOption(data, es, PREF_KEY_SECONDARY_SORT_DIRECTION, ORDER_ASC);

            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_SIZE);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_DATE);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_PATH);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_ICON);

            Util.endEditOption(es);

            if (skipUpdate && currentLayer == LAYER_ITEM) {
                ((RestoreListAdapter) getListAdapter()).sort(
                        Util.getIntOption(prefStore, PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_NAME),
                        Util.getIntOption(prefStore, PREF_KEY_SORT_DIRECTION, ORDER_ASC),
                        Util.getIntOption(prefStore, PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_NAME),
                        Util.getIntOption(prefStore, PREF_KEY_SECONDARY_SORT_DIRECTION, ORDER_ASC));
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isTagLayer = currentLayer == LAYER_TAG;

        menu.findItem(MI_DELETE).setVisible(!isTagLayer);
        menu.findItem(MI_ARCHIVE).setVisible(
                !isTagLayer && isArchiveEnabled(ApplicationManager.KEY_ARCHIVE_PATH));
        menu.findItem(MI_TO_SYSTEM).setVisible(
                !isTagLayer && isArchiveEnabled(ApplicationManager.KEY_RESTORE_SYS_PATH));
        menu.findItem(MI_TO_USER).setVisible(
                !isTagLayer && isArchiveEnabled(ApplicationManager.KEY_RESTORE_USER_PATH));
        menu.findItem(MI_TAGS).setVisible(!isTagLayer);

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem mi =
                menu.add(Menu.NONE, MI_DELETE, Menu.NONE, ResUtil.getString(this, R.string.delete_file));
        mi.setIcon(android.R.drawable.ic_menu_delete);

        mi = menu.add(Menu.NONE, MI_ARCHIVE, Menu.NONE, ResUtil.getString(this, R.string.archive));
        mi.setIcon(android.R.drawable.ic_menu_save);

        mi =
                menu.add(Menu.NONE, MI_TO_SYSTEM, Menu.NONE,
                        ResUtil.getString(this, R.string.move_to_system));
        mi.setIcon(android.R.drawable.ic_menu_save);

        mi = menu.add(Menu.NONE, MI_TO_USER, Menu.NONE, ResUtil.getString(this, R.string.move_to_user));
        mi.setIcon(android.R.drawable.ic_menu_save);

        mi = menu.add(Menu.NONE, MI_TAGS, Menu.NONE, ResUtil.getString(this, R.string.tags));
        mi.setIcon(android.R.drawable.ic_menu_agenda);

        mi =
                menu.add(Menu.NONE, MI_PREFERENCE, Menu.NONE, ResUtil.getString(this, R.string.preference));
        mi.setIcon(android.R.drawable.ic_menu_preferences);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return onActionSelected(item.getItemId());
    }

    @Override
    public List<ActionHint> getActions() {
        boolean isTagLayer = currentLayer == LAYER_TAG;

        List<ActionHint> hints = new ArrayList<ActionHint>();

        if (!isTagLayer) {
            hints.add(new ActionHint(ResUtil.getString(this, R.string.delete_file), MI_DELETE));

            if (isArchiveEnabled(ApplicationManager.KEY_ARCHIVE_PATH)) {
                hints.add(new ActionHint(ResUtil.getString(this, R.string.archive), MI_ARCHIVE));
            }

            if (isArchiveEnabled(ApplicationManager.KEY_RESTORE_SYS_PATH)) {
                hints.add(new ActionHint(ResUtil.getString(this, R.string.move_to_system), MI_TO_SYSTEM));
            }

            if (isArchiveEnabled(ApplicationManager.KEY_RESTORE_USER_PATH)) {
                hints.add(new ActionHint(ResUtil.getString(this, R.string.move_to_user), MI_TO_USER));
            }

            hints.add(new ActionHint(ResUtil.getString(this, R.string.tags), MI_TAGS));
        }

        hints.add(new ActionHint(ResUtil.getString(this, R.string.preference), MI_PREFERENCE));

        return hints;
    }

    @Override
    public boolean onActionSelected(int action) {
        if (action == MI_PREFERENCE) {
            Intent it = new Intent(this, Util.getIntentProxyClz(RestoreAppSettings.class));

            SharedPreferences prefStore =
                    getSharedPreferences(RESTORE_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            it.putExtra(PREF_KEY_DEFAULT_RESTORE_DIR,
                    getIntent().getStringExtra(ApplicationManager.KEY_RESTORE_USER_PATH));
            it.putExtra(PREF_KEY_APP_RESTORE_DIR,
                    Util.getStringOption(prefStore, PREF_KEY_APP_RESTORE_DIR, null));
            it.putExtra(PREF_KEY_USE_TAG_VIEW, Util.getBooleanOption(prefStore, PREF_KEY_USE_TAG_VIEW));
            it.putExtra(PREF_KEY_SHOW_COUNT, Util.getBooleanOption(prefStore, PREF_KEY_SHOW_COUNT));
            it.putExtra(PREF_KEY_CUSTOM_TAGS, Util.getStringOption(prefStore, PREF_KEY_CUSTOM_TAGS, null));
            it.putExtra(PREF_KEY_TAG_VISIBILITY,
                    Util.getStringOption(prefStore, PREF_KEY_TAG_VISIBILITY, null));
            it.putExtra(PREF_KEY_RECENT_SCOPE, Util.getIntOption(prefStore, PREF_KEY_RECENT_SCOPE, 3));
            it.putExtra(PREF_KEY_SEARCH_SUB_DIR,
                    Util.getBooleanOption(prefStore, PREF_KEY_SEARCH_SUB_DIR));
            it.putExtra(PREF_KEY_SORT_ORDER_TYPE,
                    Util.getIntOption(prefStore, PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_NAME));
            it.putExtra(PREF_KEY_SORT_DIRECTION,
                    Util.getIntOption(prefStore, PREF_KEY_SORT_DIRECTION, ORDER_ASC));
            it.putExtra(PREF_KEY_SECONDARY_SORT_ORDER_TYPE,
                    Util.getIntOption(prefStore, PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_NAME));
            it.putExtra(PREF_KEY_SECONDARY_SORT_DIRECTION,
                    Util.getIntOption(prefStore, PREF_KEY_SECONDARY_SORT_DIRECTION, ORDER_ASC));
            it.putExtra(PREF_KEY_SHOW_SIZE, Util.getBooleanOption(prefStore, PREF_KEY_SHOW_SIZE));
            it.putExtra(PREF_KEY_SHOW_DATE, Util.getBooleanOption(prefStore, PREF_KEY_SHOW_DATE));
            it.putExtra(PREF_KEY_SHOW_PATH, Util.getBooleanOption(prefStore, PREF_KEY_SHOW_PATH));
            it.putExtra(PREF_KEY_SHOW_ICON, Util.getBooleanOption(prefStore, PREF_KEY_SHOW_ICON));

            startActivityForResult(it, REQUEST_PREFERENCES);

            return true;
        } else if (action == MI_DELETE) {
            RestoreListAdapter adapter = (RestoreListAdapter) getListAdapter();

            doDelete(adapter.getSelection());

            return true;
        } else if (action == MI_ARCHIVE) {
            RestoreListAdapter adapter = (RestoreListAdapter) getListAdapter();

            doArchive(adapter.getSelection(), ApplicationManager.KEY_ARCHIVE_PATH);

            return true;
        } else if (action == MI_TO_SYSTEM) {
            RestoreListAdapter adapter = (RestoreListAdapter) getListAdapter();

            doArchive(adapter.getSelection(), ApplicationManager.KEY_RESTORE_SYS_PATH);

            return true;
        } else if (action == MI_TO_USER) {
            RestoreListAdapter adapter = (RestoreListAdapter) getListAdapter();

            doArchive(adapter.getSelection(), ApplicationManager.KEY_RESTORE_USER_PATH);

            return true;
        } else if (action == MI_TAGS) {
            RestoreListAdapter adapter = (RestoreListAdapter) getListAdapter();

            doChangeTags(adapter.getSelection());

            return true;
        }

        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (currentLayer != LAYER_TAG) {
            int pos = ((AdapterContextMenuInfo) menuInfo).position;
            RestoreListAdapter adapter = (RestoreListAdapter) getListAdapter();
            ApkInfo ai = adapter.getApkInfo(pos);

            if (ai == null) {
                return;
            }

            menu.setHeaderTitle(ai.label != null ? ai.label : ai.file.getName());

            menu.add(Menu.NONE, MI_DELETE, Menu.NONE, ResUtil.getString(this, R.string.delete_file));

            if (isArchiveEnabled(ApplicationManager.KEY_ARCHIVE_PATH)) {
                menu.add(Menu.NONE, MI_ARCHIVE, Menu.NONE, ResUtil.getString(this, R.string.archive));
            }

            menu.add(Menu.NONE, MI_SEARCH, Menu.NONE, ResUtil.getString(this, R.string.search_market));

            if (ai != null) {
                menu.add(Menu.NONE, MI_DETAILS, Menu.NONE, ResUtil.getString(this, R.string.details));
            }

            menu.add(Menu.NONE, MI_TAGS, Menu.NONE, ResUtil.getString(this, R.string.tags));
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int pos = ((AdapterContextMenuInfo) item.getMenuInfo()).position;

        final ListView lstApps = getListView();

        if (pos >= 0 && pos < lstApps.getCount()) {
            RestoreListAdapter adapter = (RestoreListAdapter) getListAdapter();

            List<ApkInfo> apks = adapter.getGroupApkInfo(pos);

            if (item.getItemId() == MI_DELETE) {
                doDelete(apks);

                return true;
            } else if (item.getItemId() == MI_ARCHIVE) {
                doArchive(apks, ApplicationManager.KEY_ARCHIVE_PATH);

                return true;
            } else if (item.getItemId() == MI_SEARCH && apks.size() > 0) {
                Intent it = new Intent(Intent.ACTION_VIEW);

                it.setData(Uri.parse("market://search?q=pname:" + apks.get(0).pkgName)); //$NON-NLS-1$

                it = Intent.createChooser(it, null);

                Util.safeStartActivity(this, it, false);

                return true;
            } else if (item.getItemId() == MI_DETAILS && apks.size() == 1) {
                ApkInfo ai = apks.get(0);

                StringBuffer sb =
                        new StringBuffer()
                                .append("<small>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.pkg_name))
                                .append(": ") //$NON-NLS-1$
                                .append(ai.pkgName)
                                .append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.pkg_issuer))
                                .append(": ") //$NON-NLS-1$
                                .append(Util.getPackageIssuer(this, ai.pkgName, ai.file.getAbsolutePath()))
                                .append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.version_code))
                                .append(": ") //$NON-NLS-1$
                                .append(ai.versionCode)
                                .append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.file_size))
                                .append(": ") //$NON-NLS-1$
                                .append(
                                        ai.sizeString != null ? ai.sizeString : ResUtil.getString(this,
                                                R.string.unknown)).append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.file_date))
                                .append(": ") //$NON-NLS-1$
                                .append(
                                        DateUtils.formatDateTime(this, ai.file.lastModified(),
                                                DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE
                                                        | DateUtils.FORMAT_SHOW_TIME)).append("<br>") //$NON-NLS-1$
                                .append(ResUtil.getString(this, R.string.file_path)).append(": ") //$NON-NLS-1$
                                .append(ai.file.getAbsolutePath()).append("</small>"); //$NON-NLS-1$

                new AlertDialog.Builder(this).setTitle(ai.label == null ? ai.file.getName() : ai.label)
                        //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setMessage(Html.fromHtml(sb.toString())).create().show();

                return true;
            } else if (item.getItemId() == MI_TAGS) {
                doChangeTags(apks);

                return true;
            }
        }

        return false;
    }

    static ArrayList<File> getApkFiles(final boolean recursive, final Set<String> pkgFilter,
                                       final FileFilter fileFilter, File... parents) {
        final ArrayList<File> files = new ArrayList<File>();

        if (parents != null) {
            FileFilter filter = new FileFilter() {

                public boolean accept(File f) {
                    String fname = f.getName();

                    if (f.isFile() && fname.toLowerCase(Locale.ENGLISH).endsWith(".apk")) //$NON-NLS-1$
                    {
                        if (pkgFilter != null) {
                            String pkgName;

                            int idx = fname.indexOf('-');

                            if (idx == -1) {
                                idx = fname.lastIndexOf('.');
                            }

                            pkgName = fname.substring(0, idx);

                            if (pkgFilter.contains(pkgName)) {
                                files.add(f);
                            }
                        } else if (fileFilter != null) {
                            if (fileFilter.accept(f)) {
                                files.add(f);
                            }
                        } else {
                            files.add(f);
                        }
                    } else if (recursive && f.isDirectory()) {
                        try {
                            // try skip links
                            if (f.getAbsolutePath().equals(f.getCanonicalPath())) {
                                f.listFiles(this);
                            }
                        } catch (Exception e) {
                            Log.e(RestoreAppActivity.class.getName(), e.getLocalizedMessage(), e);
                        }
                    }
                    return false;
                }
            };

            for (File pa : parents) {
                pa.listFiles(filter);
            }
        }

        return files;
    }

    static void computedApkFileCount(final Map<String, Integer> result,
                                     final Map<String, Set<String>> pkgFilterMap, final String id, String path,
                                     final String[] extraId, final FileFilter[] extraFileFilter, final boolean recursive) {
        if (path != null) {
            File folder = new File(path);

            if (folder.exists() && folder.isDirectory()) {
                folder.listFiles(new FileFilter() {

                    public boolean accept(File f) {
                        String fname = f.getName();

                        if (f.isFile() && fname.toLowerCase(Locale.ENGLISH).endsWith(".apk")) //$NON-NLS-1$
                        {
                            // calculate path count
                            incrementCount(result, id);

                            // calculate tag count
                            if (pkgFilterMap.size() > 0) {
                                String pkgName;

                                int idx = fname.indexOf('-');

                                if (idx == -1) {
                                    idx = fname.lastIndexOf('.');
                                }

                                pkgName = fname.substring(0, idx);

                                for (Entry<String, Set<String>> ent : pkgFilterMap.entrySet()) {
                                    Set<String> pkgFilter = ent.getValue();

                                    if (pkgFilter != null && pkgFilter.contains(pkgName)) {
                                        incrementCount(result, ent.getKey());
                                    }
                                }
                            }

                            // calculate extra tag count
                            if (extraId != null && extraFileFilter != null) {
                                for (int i = 0; i < extraId.length; i++) {
                                    if (extraFileFilter[i].accept(f)) {
                                        incrementCount(result, extraId[i]);
                                    }
                                }
                            }
                        } else if (recursive && f.isDirectory()) {
                            try {
                                // try skip links
                                if (f.getAbsolutePath().equals(f.getCanonicalPath())) {
                                    f.listFiles(this);
                                }
                            } catch (Exception e) {
                                Log.e(RestoreAppActivity.class.getName(), e.getLocalizedMessage(), e);
                            }
                        }
                        return false;
                    }
                });
            }
        }
    }

    static int saftGetCount(Map<String, Integer> result, String key) {
        Integer count = result.get(key);

        if (count == null) {
            return 0;
        } else {
            return count;
        }
    }

    static void incrementCount(Map<String, Integer> result, String key) {
        Integer count = result.get(key);

        if (count == null) {
            result.put(key, 1);
        } else {
            result.put(key, count + 1);
        }
    }

    static void resetTags(ApkInfo holder, Map<String, Set<String>> linkMap) {
        ArrayList<String> tags = null;

        for (Entry<String, Set<String>> ent : linkMap.entrySet()) {
            Set<String> links = ent.getValue();

            if (links != null && links.contains(holder.pkgName)) {
                if (tags == null) {
                    tags = new ArrayList<String>();
                }

                tags.add(ent.getKey());
            }
        }

        if (tags != null) {
            holder.tags = tags.toArray(new String[tags.size()]);
        } else {
            holder.tags = null;
        }
    }

    private void loadLayer() {
        currentLayer = LAYER_TAG;
        currentPath = null;
        currentTag = null;
        currentType = 0;

        ((RestoreListAdapter) getListAdapter()).setCurrentTag(currentTag);

        if (progress != null) {
            Util.safeDismissDialog(progress);
        }
        progress = new ProgressDialog(this);
        progress.setMessage(ResUtil.getString(this, R.string.loading));
        progress.setIndeterminate(true);
        progress.show();

        new Thread(new Runnable() {

            public void run() {
                SharedPreferences prefStore =
                        getSharedPreferences(RESTORE_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

                boolean searchSubFolder = Util.getBooleanOption(prefStore, PREF_KEY_SEARCH_SUB_DIR);

                boolean showCount = Util.getBooleanOption(prefStore, PREF_KEY_SHOW_COUNT);

                List<String> customTags =
                        Util.getStringList4JsonString(Util.getStringOption(prefStore, PREF_KEY_CUSTOM_TAGS,
                                null));

                String tag_untagged = ResUtil.getString(RestoreAppActivity.this, R.string.untagged);
                String tag_recent = ResUtil.getString(RestoreAppActivity.this, R.string.recent);
                String tag_custom = ResUtil.getString(RestoreAppActivity.this, R.string.custom);
                String tag_archived = ResUtil.getString(RestoreAppActivity.this, R.string.archived);
                String tag_user = ResUtil.getString(RestoreAppActivity.this, R.string.user_apps);
                String tag_sys = ResUtil.getString(RestoreAppActivity.this, R.string.sys_apps);

                Set<String> visTags =
                        Util.getVisibleTags(prefStore, customTags, tag_untagged, tag_recent, tag_custom,
                                tag_archived, tag_user, tag_sys);

                if (customTags != null) {
                    customTags.retainAll(visTags);
                }

                String sysPath = getIntent().getStringExtra(ApplicationManager.KEY_RESTORE_SYS_PATH);
                String userPath = getIntent().getStringExtra(ApplicationManager.KEY_RESTORE_USER_PATH);
                String archivePath = getIntent().getStringExtra(ApplicationManager.KEY_ARCHIVE_PATH);

                Map<String, Integer> counts = null;
                Map<String, Set<String>> linkMap = null;

                String[] extraTags = null;
                FileFilter[] extraFileFilters = null;

                if (showCount) {
                    counts = new HashMap<String, Integer>();
                    linkMap = new HashMap<String, Set<String>>();

                    if (customTags != null) {
                        for (String tag : customTags) {
                            Set<String> links = Util.getCustomTagLinks(prefStore, tag);

                            if (links != null && links.size() > 0) {
                                linkMap.put(tag, links);
                            }
                        }
                    }

                    extraTags = new String[]{tag_recent, tag_untagged};
                    extraFileFilters =
                            new FileFilter[]{createRecentRangeFilter(), createUntaggedFilter(customTags)};

                    computedApkFileCount(counts, linkMap, tag_sys, sysPath, extraTags, extraFileFilters,
                            searchSubFolder);

                    computedApkFileCount(counts, linkMap, tag_user, userPath, extraTags, extraFileFilters,
                            searchSubFolder);

                    computedApkFileCount(counts, linkMap, tag_archived, archivePath, extraTags,
                            extraFileFilters, searchSubFolder);
                }

                ArrayList<GroupMeta> tags = new ArrayList<GroupMeta>();

                ApkInfo tag;
                GroupMeta gm;

                if (visTags.contains(tag_sys)) {
                    tag = new ApkInfo();
                    tag.label = tag_sys;
                    tag.pkgName = sysPath;
                    tag.versionCode = TAG_SYSTEM;
                    tag.size = showCount ? saftGetCount(counts, tag_sys) : 0;
                    tag.icon = new SoftReference<Drawable>(getResources().getDrawable(R.drawable.tag_blue));

                    gm = new GroupMeta();
                    gm.isGroup = false;
                    gm.firstChild = tag;
                    tags.add(gm);
                }

                if (visTags.contains(tag_user)) {
                    tag = new ApkInfo();
                    tag.label = tag_user;
                    tag.pkgName = userPath;
                    tag.versionCode = TAG_USER;
                    tag.size = showCount ? saftGetCount(counts, tag_user) : 0;
                    tag.icon = new SoftReference<Drawable>(getResources().getDrawable(R.drawable.tag_green));

                    gm = new GroupMeta();
                    gm.isGroup = false;
                    gm.firstChild = tag;
                    tags.add(gm);
                }

                if (visTags.contains(tag_archived)) {
                    tag = new ApkInfo();
                    tag.label = tag_archived;
                    tag.pkgName = archivePath;
                    tag.versionCode = TAG_ARCHIVED;
                    tag.size = showCount ? saftGetCount(counts, tag_archived) : 0;
                    tag.icon = new SoftReference<Drawable>(getResources().getDrawable(R.drawable.tag_orange));

                    gm = new GroupMeta();
                    gm.isGroup = false;
                    gm.firstChild = tag;
                    tags.add(gm);
                }

                if (visTags.contains(tag_custom)) {
                    String customPath = Util.getStringOption(prefStore, PREF_KEY_APP_RESTORE_DIR, null);

                    if (customPath != null && !customPath.equals(sysPath) && !customPath.equals(userPath)
                            && !customPath.equals(archivePath)) {
                        if (showCount) {
                            computedApkFileCount(counts, linkMap, tag_custom, customPath, extraTags,
                                    extraFileFilters, searchSubFolder);
                        }

                        tag = new ApkInfo();
                        tag.label = tag_custom;
                        tag.pkgName = customPath;
                        tag.versionCode = TAG_CUSTOM;
                        tag.size = showCount ? saftGetCount(counts, tag_custom) : 0;
                        tag.icon = new SoftReference<Drawable>(getResources().getDrawable(R.drawable.tag_pink));

                        gm = new GroupMeta();
                        gm.isGroup = false;
                        gm.firstChild = tag;
                        tags.add(gm);
                    }
                }

                if (visTags.contains(tag_recent)) {
                    tag = new ApkInfo();
                    tag.label = tag_recent;
                    tag.versionCode = TAG_RECENT;
                    tag.size = showCount ? saftGetCount(counts, tag_recent) : 0;
                    tag.icon = new SoftReference<Drawable>(getResources().getDrawable(R.drawable.tag_pink));

                    gm = new GroupMeta();
                    gm.isGroup = false;
                    gm.firstChild = tag;
                    tags.add(gm);
                }

                if (visTags.contains(tag_untagged)) {
                    tag = new ApkInfo();
                    tag.label = tag_untagged;
                    tag.versionCode = TAG_UNTAGGED;
                    tag.size = showCount ? saftGetCount(counts, tag_untagged) : 0;
                    tag.icon = new SoftReference<Drawable>(getResources().getDrawable(R.drawable.tag_cream));

                    gm = new GroupMeta();
                    gm.isGroup = false;
                    gm.firstChild = tag;
                    tags.add(gm);
                }

                if (customTags != null) {
                    for (int i = 0; i < customTags.size(); i++) {
                        String ct = customTags.get(i);

                        tag = new ApkInfo();
                        tag.label = ct;
                        tag.versionCode = TAG_USER_DEFINED;
                        tag.size = showCount ? saftGetCount(counts, ct) : 0;
                        tag.icon = new SoftReference<Drawable>(getResources().getDrawable(R.drawable.tag_pink));

                        gm = new GroupMeta();
                        gm.isGroup = false;
                        gm.firstChild = tag;
                        tags.add(gm);
                    }
                }

                Collections.sort(tags, new TagComparator());

                handler.sendMessage(handler.obtainMessage(MSG_INIT_OK, tags));
            }
        }).start();
    }

    void loadApps(int tagType, String appPath, String tag) {
        currentLayer = LAYER_ITEM;
        currentPath = appPath;
        currentTag = tag;
        currentType = tagType;

        ((RestoreListAdapter) getListAdapter()).setCurrentTag(currentTag);

        if (isExplicitPathNeeded(appPath, tagType)) {
            appPath =
                    Util.getStringOption(this, RESTORE_MANAGER_STORE_NAME, PREF_KEY_APP_RESTORE_DIR, null);

            if (appPath == null) {
                appPath = getIntent().getStringExtra(ApplicationManager.KEY_RESTORE_USER_PATH);
            }

            if (appPath == null) {
                handler.sendEmptyMessage(MSG_INIT_OK);
                return;
            }
        }

        if (appPath != null) {
            File appFolder = new File(appPath);

            if (!appFolder.exists() || !appFolder.isDirectory()) {
                handler.sendEmptyMessage(MSG_INIT_OK);
                return;
            }
        }

        if (progress != null) {
            Util.safeDismissDialog(progress);
        }
        progress = new ProgressDialog(this);
        progress.setMessage(ResUtil.getString(this, R.string.loading));
        progress.setIndeterminate(true);
        progress.show();

        if (appLoaderThread != null) {
            appLoaderThread.aborted = true;
        }

        if (appPath != null) {
            // load from specific folder
            (appLoaderThread = new ArchiveAppLoaderThread(this, handler, null, null, new File(appPath)))
                    .start();
        } else {
            // load by tag or specific type
            Set<String> pkgFilter = null;
            FileFilter fileFilter = null;

            if (tagType == TAG_USER_DEFINED) {
                // tag filter
                pkgFilter =
                        Util.getCustomTagLinks(
                                getSharedPreferences(RESTORE_MANAGER_STORE_NAME, Context.MODE_PRIVATE), tag);
                if (pkgFilter != null && pkgFilter.size() == 0) {
                    pkgFilter = null;
                }
            } else if (tagType == TAG_RECENT) {
                // recent range filter
                fileFilter = createRecentRangeFilter();
            } else if (tagType == TAG_UNTAGGED) {
                // untagged filter
                List<String> customTags =
                        Util.getStringList4JsonString(Util.getStringOption(
                                getSharedPreferences(RESTORE_MANAGER_STORE_NAME, Context.MODE_PRIVATE),
                                PREF_KEY_CUSTOM_TAGS, null));

                fileFilter = createUntaggedFilter(customTags);
            }

            if (pkgFilter == null && fileFilter == null) {
                handler.sendEmptyMessage(MSG_INIT_OK);
            } else {
                List<File> folders = new ArrayList<File>();

                String sysPath = getIntent().getStringExtra(ApplicationManager.KEY_RESTORE_SYS_PATH);
                String userPath = getIntent().getStringExtra(ApplicationManager.KEY_RESTORE_USER_PATH);
                String archivePath = getIntent().getStringExtra(ApplicationManager.KEY_ARCHIVE_PATH);

                folders.add(new File(sysPath));
                folders.add(new File(userPath));
                folders.add(new File(archivePath));

                String customPath =
                        Util.getStringOption(RestoreAppActivity.this, RESTORE_MANAGER_STORE_NAME,
                                PREF_KEY_APP_RESTORE_DIR, null);

                if (customPath != null && !customPath.equals(sysPath) && !customPath.equals(userPath)
                        && !customPath.equals(archivePath)) {
                    folders.add(new File(customPath));
                }

                (appLoaderThread =
                        new ArchiveAppLoaderThread(this, handler, pkgFilter, fileFilter,
                                folders.toArray(new File[folders.size()]))).start();
            }
        }
    }

    void hideButtons() {
        View v = findViewById(R.id.app_footer);

        if (v.getVisibility() != View.GONE) {
            v.setVisibility(View.GONE);

            v.startAnimation(AnimationUtils.loadAnimation(RestoreAppActivity.this,
                    R.anim.footer_disappear));
        }
    }

    FileFilter createRecentRangeFilter() {
        int days = Util.getIntOption(this, RESTORE_MANAGER_STORE_NAME, PREF_KEY_RECENT_SCOPE, 3);

        long range = days * 24 * 60 * 60 * 1000L;
        long current = new Date().getTime();

        final long checkPoint = current - range;

        return new FileFilter() {

            public boolean accept(File pathname) {
                long installDate = pathname.lastModified();

                return installDate > checkPoint;
            }
        };
    }

    FileFilter createUntaggedFilter(List<String> customTags) {
        Set<String> allLinks = null;

        if (customTags != null) {
            allLinks = new HashSet<String>();

            SharedPreferences prefStore =
                    getSharedPreferences(RESTORE_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            for (String ctag : customTags) {
                Set<String> links = Util.getCustomTagLinks(prefStore, ctag);

                if (links != null && links.size() > 0) {
                    allLinks.addAll(links);
                }
            }
        }

        final Set<String> existLinks = allLinks;

        return new FileFilter() {

            public boolean accept(File f) {
                if (existLinks == null || existLinks.size() == 0) {
                    return true;
                }

                String fname = f.getName();

                String pkgName;

                int idx = fname.indexOf('-');

                if (idx == -1) {
                    idx = fname.lastIndexOf('.');
                }

                pkgName = fname.substring(0, idx);

                return !existLinks.contains(pkgName);
            }
        };
    }

    static boolean isExplicitPathNeeded(String path, int tagType) {
        return path == null && tagType != TAG_USER_DEFINED && tagType != TAG_RECENT
                && tagType != TAG_UNTAGGED;
    }

    private boolean isArchiveEnabled(String targetPathKey) {
        String appPath = currentPath;

        if (isExplicitPathNeeded(currentPath, currentType)) {
            appPath =
                    Util.getStringOption(this, RESTORE_MANAGER_STORE_NAME, PREF_KEY_APP_RESTORE_DIR, null);

            if (appPath == null) {
                appPath = getIntent().getStringExtra(ApplicationManager.KEY_RESTORE_USER_PATH);
            }
        }

        return appPath != null && !appPath.equals(getIntent().getStringExtra(targetPathKey));
    }

    private void doChangeTags(final List<ApkInfo> apps) {
        if (apps == null || apps.size() == 0) {
            Util.shortToast(this, R.string.no_apk_selected);
        } else {
            SharedPreferences prefStore =
                    getSharedPreferences(RESTORE_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            final List<String> customTags =
                    Util.getStringList4JsonString(Util.getStringOption(prefStore, PREF_KEY_CUSTOM_TAGS, null));

            if (customTags == null || customTags.size() == 0) {
                Util.shortToast(this, R.string.no_cust_tags);

                return;
            }

            Collections.sort(customTags, new StringComparator());

            boolean[] states = new boolean[customTags.size()];

            // load init state
            for (int i = 0; i < customTags.size(); i++) {
                String tag = customTags.get(i);

                Set<String> links = Util.getCustomTagLinks(prefStore, tag);

                boolean checked = true;

                for (ApkInfo ai : apps) {
                    if (!links.contains(ai.pkgName)) {
                        checked = false;
                        break;
                    }
                }

                states[i] = checked;
            }

            final Set<String> toAdd = new HashSet<String>();
            final Set<String> toRemove = new HashSet<String>();

            OnMultiChoiceClickListener selListener = new OnMultiChoiceClickListener() {

                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    String tag = customTags.get(which);

                    if (isChecked) {
                        toAdd.add(tag);
                        toRemove.remove(tag);
                    } else {
                        toAdd.remove(tag);
                        toRemove.add(tag);
                    }
                }
            };

            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    SharedPreferences prefStore =
                            getSharedPreferences(RESTORE_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

                    for (int i = 0; i < customTags.size(); i++) {
                        String tag = customTags.get(i);

                        Set<String> links = Util.getCustomTagLinks(prefStore, tag);

                        if (toAdd.contains(tag)) {
                            // add tag
                            for (ApkInfo ai : apps) {
                                links.add(ai.pkgName);
                            }
                        } else if (toRemove.contains(tag)) {
                            // remove tag
                            for (ApkInfo ai : apps) {
                                links.remove(ai.pkgName);
                            }
                        }

                        // update
                        Util.updateCustomTagLinks(RestoreAppActivity.this, prefStore, tag, links);
                    }

                    // refresh
                    if (currentType == TAG_USER_DEFINED
                            && (toRemove.contains(currentTag) || toAdd.contains(currentTag))) {
                        loadApps(currentType, currentPath, currentTag);
                    } else if (currentType == TAG_UNTAGGED && (!toAdd.isEmpty() || !toRemove.isEmpty())) {
                        loadApps(currentType, currentPath, currentTag);
                    } else {
                        Map<String, Set<String>> linkMap = new HashMap<String, Set<String>>();

                        for (String tag : customTags) {
                            Set<String> links = Util.getCustomTagLinks(prefStore, tag);

                            if (links != null && links.size() > 0) {
                                linkMap.put(tag, links);
                            }
                        }

                        for (ApkInfo ai : apps) {
                            resetTags(ai, linkMap);
                        }

                        handler.sendEmptyMessage(MSG_REFRESH);
                    }
                }
            };

            new AlertDialog.Builder(this)
                    .setTitle(ResUtil.getString(this, R.string.tags))
                    .setMultiChoiceItems(customTags.toArray(new String[customTags.size()]), states,
                            selListener).setPositiveButton(android.R.string.ok, listener)
                    .setNegativeButton(android.R.string.cancel, null).create().show();
        }
    }

    void doDelete(final List<ApkInfo> apks) {
        if (apks == null || apks.size() == 0) {
            Util.shortToast(this, R.string.no_apk_selected);
            return;
        }

        OnClickListener listener = new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                if (progress != null) {
                    Util.safeDismissDialog(progress);
                }
                progress = new ProgressDialog(RestoreAppActivity.this);
                progress.setMessage(ResUtil.getString(RestoreAppActivity.this, R.string.deleting));
                progress.setIndeterminate(true);
                progress.show();

                new Thread(new Runnable() {

                    public void run() {
                        ArrayList<ApkInfo> removed = new ArrayList<ApkInfo>();

                        for (int i = 0, size = apks.size(); i < size; i++) {
                            ApkInfo ai = apks.get(i);

                            boolean deleted = ai.file.delete();

                            if (deleted) {
                                removed.add(ai);
                            } else {
                                handler.sendMessage(handler.obtainMessage(
                                        MSG_TOAST,
                                        ResUtil.getString(RestoreAppActivity.this, R.string.delete_file_failed,
                                                ai.file.getAbsolutePath())));
                            }
                        }

                        handler.sendMessage(handler.obtainMessage(MSG_REMOVE, removed));
                    }
                }, "DeleteWorker").start(); //$NON-NLS-1$
            }
        };

        StringBuilder sb = new StringBuilder();
        for (int i = 0, size = apks.size(); i < size; i++) {
            ApkInfo ai = apks.get(i);

            sb.append(ai.file.getName()).append('\n');
        }

        new AlertDialog.Builder(this).setTitle(ResUtil.getString(this, R.string.warning))
                .setMessage(ResUtil.getString(this, R.string.delete_file_warn, sb.toString()))
                .setPositiveButton(android.R.string.yes, listener)
                .setNegativeButton(android.R.string.no, null).create().show();
    }

    private void doArchive(final List<ApkInfo> apks, String targetPathKey) {
        if (apks == null || apks.size() == 0) {
            Util.shortToast(this, R.string.no_apk_selected);
            return;
        }

        final String archivePath = getIntent().getStringExtra(targetPathKey);

        if (archivePath == null) {
            Util.shortToast(this, R.string.no_archive_path);
        } else if (apks.get(0).file.getAbsolutePath().startsWith(archivePath)) {
            Util.shortToast(this, R.string.dup_archived);
        } else {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    if (progress != null) {
                        Util.safeDismissDialog(progress);
                    }
                    progress = new ProgressDialog(RestoreAppActivity.this);
                    progress.setMessage(ResUtil.getString(RestoreAppActivity.this, R.string.archiving));
                    progress.setIndeterminate(true);
                    progress.show();

                    new Thread(new Runnable() {

                        public void run() {
                            File f = new File(archivePath);

                            if (!f.exists()) {
                                if (!f.mkdirs()) {
                                    handler.sendMessage(handler.obtainMessage(MSG_TOAST, ResUtil.getString(
                                            RestoreAppActivity.this, R.string.fail_create_archive_folder,
                                            f.getAbsolutePath())));

                                    handler.sendEmptyMessage(MSG_DISMISS_PROGRESS);

                                    return;
                                }
                            }

                            ArrayList<ApkInfo> removed = new ArrayList<ApkInfo>();

                            for (int i = 0, size = apks.size(); i < size; i++) {
                                ApkInfo ai = apks.get(i);

                                boolean moved = ai.file.renameTo(new File(f, ai.file.getName()));

                                if (moved) {
                                    removed.add(ai);
                                } else {
                                    handler.sendMessage(handler.obtainMessage(
                                            MSG_TOAST,
                                            ResUtil.getString(RestoreAppActivity.this, R.string.archive_fail,
                                                    ai.file.getAbsolutePath())));
                                }
                            }

                            handler.sendMessage(handler.obtainMessage(MSG_REMOVE, removed));
                        }
                    }, "ArchiveWorker").start(); //$NON-NLS-1$
                }
            };

            new AlertDialog.Builder(this).setTitle(ResUtil.getString(this, R.string.warning))
                    .setMessage(ResUtil.getString(this, R.string.archive_warning, archivePath))
                    .setPositiveButton(android.R.string.yes, listener)
                    .setNegativeButton(android.R.string.no, null).create().show();
        }

    }

    void doRestore() {
        RestoreListAdapter adapter = (RestoreListAdapter) getListAdapter();

        final ArrayList<ApkInfo> apps = adapter.getSelection();

        if (apps == null || apps.size() == 0) {
            Util.shortToast(this, R.string.no_apk_selected);
            return;
        }

        OnClickListener listener = new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                boolean canInstall = false;

                for (int i = 0, size = apps.size(); i < size; i++) {
                    ApkInfo app = apps.get(i);

                    Intent it = new Intent(Intent.ACTION_VIEW);

                    it.setDataAndType(Uri.fromFile(app.file), "application/vnd.android.package-archive"); //$NON-NLS-1$

                    if (!canInstall) {
                        List<ResolveInfo> acts = getPackageManager().queryIntentActivities(it, 0);

                        canInstall = acts.size() > 0;
                    }

                    if (canInstall) {
                        startActivity(it);
                    }
                }

                if (!canInstall) {
                    Util.shortToast(RestoreAppActivity.this, R.string.install_fail);

                    Log.d(RestoreAppActivity.class.getName(),
                            "No activity found to handle the install request."); //$NON-NLS-1$
                } else {
                    stopCheck = true;
                }
            }
        };

        new AlertDialog.Builder(this).setTitle(ResUtil.getString(this, R.string.warning))
                .setMessage(ResUtil.getString(this, R.string.restore_warn, apps.size()))
                .setPositiveButton(android.R.string.yes, listener)
                .setNegativeButton(android.R.string.no, null).create().show();
    }

    void showAdvancedSelection() {
        OnClickListener listener = new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        toggleAllSelection(true);
                        break;
                    case 1:
                        chooseInstallState();
                        break;
                }

                // TODO should have better solution
                // enforce count update again, previous update may have sequence
                // issue, so may not have the correct count updated
                View v = findViewById(R.id.app_footer);

                ((TextView) v.findViewById(R.id.txt_count)).setText(String
                        .valueOf(((RestoreListAdapter) getListAdapter()).getSelection().size()));
            }
        };

        new AlertDialog.Builder(this)
                .setTitle(ResUtil.getString(this, R.string.select))
                .setItems(
                        new CharSequence[]{ResUtil.getString(this, R.string.all_apps),
                                ResUtil.getString(this, R.string.installation)}, listener).create().show();
    }

    void chooseInstallState() {
        final boolean[] items = new boolean[5];

        OnMultiChoiceClickListener multiListener = new OnMultiChoiceClickListener() {

            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                items[which] = isChecked;
            }
        };

        OnClickListener listener = new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                selectInstallState(items);
            }
        };

        new AlertDialog.Builder(this)
                .setTitle(ResUtil.getString(this, R.string.installation))
                .setMultiChoiceItems(
                        new CharSequence[]{ResUtil.getString(this, R.string.not_installed),
                                ResUtil.getString(this, R.string.new_), ResUtil.getString(this, R.string.latest),
                                ResUtil.getString(this, R.string.old),}, items, multiListener)
                .setPositiveButton(android.R.string.ok, listener)
                .setNegativeButton(android.R.string.cancel, null).create().show();
    }

    void selectInstallState(boolean[] states) {
        RestoreListAdapter adapter = (RestoreListAdapter) getListAdapter();

        adapter.selectInstallState(states);

        if (!adapter.hasSelection()) {
            hideButtons();
        }
    }

    void toggleAllSelection(boolean selected) {
        RestoreListAdapter adapter = (RestoreListAdapter) getListAdapter();

        adapter.toggleAllChecked(selected);

        if (!selected) {
            hideButtons();
        }
    }

    /**
     * ArchiveAppLoaderThread
     */
    private static final class ArchiveAppLoaderThread extends Thread {

        private Context ctx;
        private Handler handler;
        private File[] appFolders;
        private Set<String> pkgFilter;
        private FileFilter fileFilter;

        volatile boolean aborted;

        ArchiveAppLoaderThread(Context ctx, Handler handler, Set<String> pkgFilter,
                               FileFilter fileFilter, File... appFolders) {
            super("ArchiveAppLoader"); //$NON-NLS-1$

            this.ctx = ctx;
            this.handler = handler;
            this.pkgFilter = pkgFilter;
            this.fileFilter = fileFilter;
            this.appFolders = appFolders;
        }

        public void run() {
            ArrayList<File> files = new ArrayList<File>();

            SharedPreferences prefStore =
                    ctx.getSharedPreferences(RESTORE_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            if (appFolders != null) {
                files.addAll(getApkFiles(Util.getBooleanOption(prefStore, PREF_KEY_SEARCH_SUB_DIR),
                        pkgFilter, fileFilter, appFolders));
            }

            ArrayList<GroupMeta> dataList = new ArrayList<GroupMeta>();

            HashMap<String, GroupMeta> groupCache = new HashMap<String, GroupMeta>();

            if (files.size() > 0) {
                handler.sendMessage(handler.obtainMessage(MSG_PRE_SCAN, files.size(), 0));

                List<String> customTags =
                        Util.getStringList4JsonString(Util.getStringOption(prefStore, PREF_KEY_CUSTOM_TAGS,
                                null));

                Map<String, Set<String>> linkMap = null;

                if (customTags != null) {
                    linkMap = new HashMap<String, Set<String>>();

                    for (String tag : customTags) {
                        Set<String> links = Util.getCustomTagLinks(prefStore, tag);

                        if (links != null && links.size() > 0) {
                            linkMap.put(tag, links);
                        }
                    }
                }

                PackageManager pm = ctx.getPackageManager();

                PackageInfo pi;

                for (int i = 0, size = files.size(); i < size; i++) {
                    if (aborted) {
                        break;
                    }

                    File f = files.get(i);

                    handler.sendMessage(handler.obtainMessage(MSG_SCAN, "...")); //$NON-NLS-1$

                    pi = pm.getPackageArchiveInfo(f.getAbsolutePath(), 0);

                    if (pi != null) {
                        ApkInfo holder = new ApkInfo();

                        holder.file = f;
                        holder.pkgName = pi.packageName;
                        holder.size = f.length();
                        holder.sizeString = Util.safeFormatFileSize(ctx, holder.size);
                        holder.version =
                                pi.versionName == null ? String.valueOf(pi.versionCode) : pi.versionName;
                        holder.versionCode = pi.versionCode;

                        if (pi.packageName != null) {
                            try {
                                PackageInfo ipi = pm.getPackageInfo(pi.packageName, 0);

                                holder.version =
                                        ResUtil
                                                .getString(ctx, R.string.installed_ver,
                                                        pi.versionName == null ? String.valueOf(pi.versionCode)
                                                                : pi.versionName,
                                                        ipi.versionName == null ? String.valueOf(ipi.versionCode)
                                                                : ipi.versionName);

                                if (ipi.versionCode < pi.versionCode) {
                                    holder.installed = 1;
                                } else if (ipi.versionCode == pi.versionCode) {
                                    holder.installed = 2;
                                } else {
                                    holder.installed = 3;
                                }
                            } catch (NameNotFoundException e) {
                                // ignore
                            }

                            if (customTags != null) {
                                resetTags(holder, linkMap);
                            }
                        }

                        if (pi.applicationInfo != null) {
                            if (pi.applicationInfo.publicSourceDir == null) {
                                pi.applicationInfo.publicSourceDir = f.getAbsolutePath();
                            }

                            holder.label = pm.getApplicationLabel(pi.applicationInfo);

                            // ! we now dynamically reload the icon when UI
                            // requested.
                            // try
                            // {
                            // Drawable icon = pm.getApplicationIcon(
                            // pi.applicationInfo );
                            //
                            // if ( icon != null )
                            // {
                            // holder.icon = new SoftReference<Drawable>( icon
                            // );
                            // }
                            // else
                            // {
                            // holder.icon = null;
                            // }
                            // }
                            // catch ( OutOfMemoryError oom )
                            // {
                            // Log.e( RestoreAppActivity.class.getName( ),
                            //										"OOM when loading icon: " //$NON-NLS-1$
                            // + pi.packageName,
                            // oom );
                            // }
                        }

                        GroupMeta gm = groupCache.get(holder.pkgName);

                        if (gm == null) {
                            gm = new GroupMeta();
                            gm.isGroup = false;
                            gm.firstChild = holder;

                            groupCache.put(holder.pkgName, gm);

                            dataList.add(gm);
                        } else {
                            if (gm.children == null) {
                                gm.children = new ArrayList<ApkInfo>();
                                gm.children.add(gm.firstChild);
                            }

                            gm.isGroup = true;
                            gm.children.add(holder);
                        }
                    }
                }

                RestoreListAdapter.sort(dataList,
                        Util.getIntOption(prefStore, PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_NAME),
                        Util.getIntOption(prefStore, PREF_KEY_SORT_DIRECTION, ORDER_ASC),
                        Util.getIntOption(prefStore, PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_NAME),
                        Util.getIntOption(prefStore, PREF_KEY_SECONDARY_SORT_DIRECTION, ORDER_ASC));
            }

            handler.sendMessage(handler.obtainMessage(MSG_INIT_OK, dataList));
        }
    }

    /**
     * IconLoaderTask
     */
    private static final class IconLoaderTask extends AsyncTask<ApkInfo, Void, Drawable> {

        private WeakReference<ImageView> refView;
        private Context ctx;
        private long stamp;

        IconLoaderTask(Context ctx, ImageView view) {
            this.ctx = ctx;
            this.refView = new WeakReference<ImageView>(view);

            Long obj = (Long) view.getTag();
            if (obj != null) {
                stamp = obj;
            }
        }

        @Override
        protected Drawable doInBackground(ApkInfo... params) {
            if (params != null && params.length > 0) {
                ApkInfo ai = params[0];

                return RestoreListAdapter.reloadIcon(ctx, ai);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Drawable result) {
            if (refView != null) {
                ImageView view = refView.get();
                if (view != null) {
                    refView.clear();

                    Long obj = (Long) view.getTag();
                    if (obj != null && obj == stamp) {
                        view.setImageDrawable(result);
                        view.startAnimation(AnimationUtils.loadAnimation(ctx, R.anim.fade_in_half));
                    }
                }
            }

            refView = null;
            ctx = null;
        }

    }

    /**
     * ApkInfo
     */
    private static final class ApkInfo {

        File file;
        CharSequence label;
        String pkgName;
        String version;
        int versionCode;
        String sizeString;
        long size;
        SoftReference<Drawable> icon;
        int installed;
        boolean checked;
        String[] tags;

        ApkInfo() {

        }
    }

    /**
     * TagComparator
     */
    private static final class TagComparator implements Comparator<GroupMeta> {

        Collator clt = Collator.getInstance();

        TagComparator() {

        }

        public int compare(GroupMeta obj1, GroupMeta obj2) {
            return compareApk(obj1.firstChild, obj2.firstChild);
        }

        private int compareApk(ApkInfo obj1, ApkInfo obj2) {
            if (obj1.versionCode == obj2.versionCode) {
                return clt.compare(obj1.label.toString(), obj2.label.toString());
            }
            return obj1.versionCode < obj2.versionCode ? -1 : 1;
        }
    }

    /**
     * ApkComparator
     */
    private static final class ApkComparator implements Comparator<ApkInfo> {

        int type, direction, secondType, secondDirection;

        Collator clt = Collator.getInstance();

        ApkComparator(int type, int direction, int secondType, int secondDirection) {
            this.type = type;
            this.direction = direction;
            this.secondType = secondType;
            this.secondDirection = secondDirection;
        }

        public int compare(ApkInfo obj1, ApkInfo obj2) {
            // always compare by version_code in desc order first
            int rlt = compare(clt, ORDER_TYPE_VERSION_CODE, ORDER_DESC, obj1, obj2);

            if (rlt == 0) {
                rlt = compare(clt, type, direction, obj1, obj2);
            }

            if (rlt == 0) {
                rlt = compare(clt, secondType, secondDirection, obj1, obj2);
            }

            return rlt;
        }

        static int compare(Collator clt, int type, int direction, ApkInfo obj1, ApkInfo obj2) {
            switch (type) {
                case ORDER_TYPE_NAME:

                    String lb1 = obj1.label == null ? obj1.file.getName() : obj1.label.toString();
                    String lb2 = obj2.label == null ? obj2.file.getName() : obj2.label.toString();

                    return clt.compare(lb1, lb2) * direction;

                case ORDER_TYPE_SIZE:

                    return (obj1.size == obj2.size ? 0 : (obj1.size < obj2.size ? -1 : 1)) * direction;

                case ORDER_TYPE_INSTALL:

                    return (obj1.installed - obj2.installed) * direction;

                case ORDER_TYPE_DATE:

                    long d1 = obj1.file.lastModified();
                    long d2 = obj2.file.lastModified();

                    return (d1 == d2 ? 0 : (d1 < d2 ? -1 : 1)) * direction;

                case ORDER_TYPE_PATH:

                    return obj1.file.compareTo(obj2.file) * direction;

                case ORDER_TYPE_VERSION_CODE:

                    return (obj1.versionCode - obj2.versionCode) * direction;
            }

            return 0;
        }
    }

    /**
     * GroupMeta
     */
    private static final class GroupMeta {

        boolean isGroup;
        ApkInfo firstChild;
        ArrayList<ApkInfo> children;

        GroupMeta() {

        }
    }

    /**
     * GroupComparator
     */
    private static final class GroupComparator implements Comparator<GroupMeta> {

        int type, direction, secondType, secondDirection;

        Collator clt = Collator.getInstance();

        GroupComparator(int type, int direction, int secondType, int secondDirection) {
            this.type = type;
            this.direction = direction;
            this.secondType = secondType;
            this.secondDirection = secondDirection;
        }

        public int compare(GroupMeta obj1, GroupMeta obj2) {
            int rlt = compare(clt, type, direction, obj1, obj2);

            if (rlt == 0) {
                rlt = compare(clt, secondType, secondDirection, obj1, obj2);
            }

            return rlt;
        }

        private static int compare(Collator clt, int type, int direction, GroupMeta obj1, GroupMeta obj2) {
            ApkInfo apk1 = obj1.firstChild;
            ApkInfo apk2 = obj2.firstChild;

            return ApkComparator.compare(clt, type, direction, apk1, apk2);
        }
    }

    /**
     * RestoreListAdapter
     */
    private static final class RestoreListAdapter extends BaseAdapter {

        private static final long GROUP_OFFSET_MASK = 0x00000000FFFFFFFFL;
        private static final long IN_GROUP_POSITION_MASK = 0x7FFFFFFF00000000L;
        private static final long EXPAND_STATE_MASK = 0x8000000000000000L;
        private static final long COLLAPSE_STATE_MASK = 0x7FFFFFFFFFFFFFFFL;

        private Context ctx;
        private String versionPrefix;

        private OnCheckedChangeListener checkListener;

        private ArrayList<GroupMeta> metas;
        private ArrayList<Long> items;

        private String currentTag;

        RestoreListAdapter(Context ctx, OnCheckedChangeListener listener) {
            this.ctx = ctx;
            this.checkListener = listener;

            versionPrefix = ResUtil.getString(ctx, R.string.version);
        }

        private void rebuildItems() {
            if (metas != null) {
                int size = metas.size();

                items = new ArrayList<Long>(size);

                for (int i = 0; i < size; i++) {
                    items.add(i & GROUP_OFFSET_MASK);
                }

                notifyDataSetChanged();
            }
        }

        void setItems(ArrayList<GroupMeta> metas) {
            if (metas == null) {
                metas = new ArrayList<GroupMeta>();
            }

            this.metas = metas;

            rebuildItems();
        }

        ApkInfo getApkInfo(int position) {
            long item = items.get(position);

            int offset = (int) (item & GROUP_OFFSET_MASK);

            GroupMeta aMeta = metas.get(offset);

            if (aMeta.isGroup) {
                int groupOffset = (int) ((item & IN_GROUP_POSITION_MASK) >> 32);

                return groupOffset == 0 ? null : aMeta.children.get(groupOffset - 1);
            }

            return aMeta.firstChild;
        }

        List<ApkInfo> getGroupApkInfo(int position) {
            long item = items.get(position);

            int offset = (int) (item & GROUP_OFFSET_MASK);

            GroupMeta aMeta = metas.get(offset);

            ArrayList<ApkInfo> apks = new ArrayList<ApkInfo>();

            if (aMeta.isGroup) {
                int groupOffset = (int) ((item & IN_GROUP_POSITION_MASK) >> 32);

                if (groupOffset == 0) {
                    apks.addAll(aMeta.children);
                } else {
                    apks.add(aMeta.children.get(groupOffset - 1));
                }
            } else {
                apks.add(aMeta.firstChild);
            }

            return apks;
        }

        void setCurrentTag(String tag) {
            this.currentTag = tag;
        }

        /**
         * @param states ["not installed", "new", "latest", "old"]
         */
        void selectInstallState(boolean[] states) {
            for (GroupMeta gm : metas) {
                if (gm.isGroup) {
                    for (ApkInfo ai : gm.children) {
                        if (ai.installed >= 0 && ai.installed <= 3) {
                            ai.checked = states[ai.installed];
                        }
                    }
                } else {
                    ApkInfo ai = gm.firstChild;

                    if (ai.installed >= 0 && ai.installed <= 3) {
                        ai.checked = states[ai.installed];
                    }
                }
            }

            notifyDataSetChanged();
        }

        void toggleAllChecked(boolean checked) {
            for (GroupMeta gm : metas) {
                if (gm.isGroup) {
                    for (ApkInfo ai : gm.children) {
                        ai.checked = checked;
                    }
                } else {
                    gm.firstChild.checked = checked;
                }
            }

            notifyDataSetChanged();
        }

        boolean doRemove(ApkInfo apk) {
            boolean removed = false;

            if (apk != null) {
                META:
                for (int i = 0, size = metas.size(); i < size; i++) {
                    GroupMeta gm = metas.get(i);

                    if (gm.isGroup) {
                        for (ApkInfo ai : gm.children) {
                            if (ai == apk) {
                                if (gm.children.size() <= 1) {
                                    metas.remove(i);
                                } else {
                                    gm.children.remove(ai);
                                    gm.firstChild = gm.children.get(0);

                                    if (gm.children.size() == 1) {
                                        gm.isGroup = false;
                                        gm.children = null;
                                    }
                                }

                                removed = true;
                                break META;
                            }
                        }
                    } else if (gm.firstChild == apk) {
                        metas.remove(i);
                        removed = true;
                        break;
                    }
                }
            }

            return removed;
        }

        // void remove( ApkInfo apk )
        // {
        // if ( doRemove( apk ) )
        // {
        // rebuildItems( );
        // }
        // }

        void remove(ArrayList<ApkInfo> apks) {
            if (apks != null) {
                boolean removed = false;

                for (ApkInfo ai : apks) {
                    if (doRemove(ai)) {
                        removed = true;
                    }
                }

                if (removed) {
                    rebuildItems();
                }
            }
        }

        void toggleGroup(int position) {
            long item = items.get(position);

            int offset = (int) (item & GROUP_OFFSET_MASK);

            GroupMeta aMeta = metas.get(offset);

            if (aMeta.isGroup) {
                boolean collapsed = (item >> 63) == 0;

                items.set(position, collapsed ? (EXPAND_STATE_MASK | item) : (COLLAPSE_STATE_MASK & item));

                if (collapsed) {
                    // expand now
                    for (int i = 1, size = aMeta.children.size(); i <= size; i++) {
                        items.add(position + i, (((long) i << 32) & IN_GROUP_POSITION_MASK)
                                | (offset & GROUP_OFFSET_MASK));
                    }
                } else {
                    // collapse now
                    for (int i = 0, size = aMeta.children.size(); i < size; i++) {
                        items.remove(position + 1);
                    }
                }

                notifyDataSetChanged();
            }
        }

        ArrayList<ApkInfo> getSelection() {
            int count = metas == null ? 0 : metas.size();

            ArrayList<ApkInfo> apps = new ArrayList<ApkInfo>();

            for (int i = 0; i < count; i++) {
                GroupMeta gm = metas.get(i);

                if (gm.isGroup) {
                    for (ApkInfo ai : gm.children) {
                        if (ai.checked) {
                            apps.add(ai);
                        }
                    }
                } else if (gm.firstChild.checked) {
                    apps.add(gm.firstChild);
                }
            }

            return apps;
        }

        boolean hasSelection() {
            int count = metas == null ? 0 : metas.size();

            for (int i = 0; i < count; i++) {
                GroupMeta gm = metas.get(i);

                if (gm.isGroup) {
                    for (ApkInfo ai : gm.children) {
                        if (ai.checked) {
                            return true;
                        }
                    }
                } else if (gm.firstChild.checked) {
                    return true;
                }
            }

            return false;
        }

        void sort(int type, int direction, int secondType, int secondDirection) {
            sort(metas, type, direction, secondType, secondDirection);

            rebuildItems();
        }

        static void sort(ArrayList<GroupMeta> data, int type, int direction, int secondType,
                         int secondDirection) {
            if (data != null) {
                ApkComparator apkComparator =
                        new ApkComparator(type, direction, secondType, secondDirection);

                for (GroupMeta gm : data) {
                    if (gm.isGroup) {
                        Collections.sort(gm.children, apkComparator);
                        gm.firstChild = gm.children.get(0);
                    }
                }

                Collections.sort(data, new GroupComparator(type, direction, secondType, secondDirection));
            }
        }

        public int getCount() {
            if (items == null) {
                return 0;
            }
            return items.size();
        }

        public Object getItem(int position) {
            return items.get(position);
        }

        public long getItemId(int position) {
            return items.get(position);
        }

        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= items.size()) {
                return Adapter.IGNORE_ITEM_VIEW_TYPE;
            }

            long item = items.get(position);

            int offset = (int) (item & GROUP_OFFSET_MASK);

            GroupMeta aMeta = metas.get(offset);

            if (aMeta.isGroup) {
                int groupOffset = (int) ((item & IN_GROUP_POSITION_MASK) >> 32);

                return groupOffset == 0 ? 1 : 2;
            }

            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v = null;

            if (position >= items.size()) {
                return v;
            }

            long item = items.get(position);

            int offset = (int) (item & GROUP_OFFSET_MASK);

            GroupMeta aMeta = metas.get(offset);

            if (aMeta.isGroup) {
                int groupOffset = (int) ((item & IN_GROUP_POSITION_MASK) >> 32);

                if (groupOffset == 0) {
                    // group header
                    if (convertView instanceof RelativeLayout) {
                        v = convertView;
                    } else {
                        v =
                                ((LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(
                                        R.layout.app_group, parent, false);
                    }

                    TextView header = (TextView) v.findViewById(R.id.txt_head);

                    ApkInfo itm = aMeta.firstChild;

                    if (itm.label != null) {
                        header.setText(itm.label + " ( " //$NON-NLS-1$
                                + aMeta.children.size() + " )"); //$NON-NLS-1$
                    } else {
                        header.setText(itm.file.getName() + " ( " //$NON-NLS-1$
                                + aMeta.children.size() + " )"); //$NON-NLS-1$
                    }

                    ImageView expander = (ImageView) v.findViewById(R.id.expander);

                    boolean collapsed = (item >> 63) == 0;

                    expander.setImageResource(collapsed ? R.drawable.expander_collapse
                            : R.drawable.expander_expand);
                } else {
                    // group items
                    v = getItemView(position, aMeta.children.get(groupOffset - 1), convertView, parent);
                    v.setBackgroundResource(R.drawable.item_background);
                }
            } else {
                v = getItemView(position, aMeta.firstChild, convertView, parent);
                v.setBackgroundDrawable(null);
            }

            return v;
        }

        static Drawable reloadIcon(Context ctx, ApkInfo ai) {
            if (ai.file == null) {
                // tag items
                int res = 0;

                switch (ai.versionCode) {
                    case TAG_SYSTEM:
                        res = R.drawable.tag_blue;
                        break;
                    case TAG_USER:
                        res = R.drawable.tag_green;
                        break;
                    case TAG_ARCHIVED:
                        res = R.drawable.tag_orange;
                        break;
                    case TAG_CUSTOM:
                    case TAG_RECENT:
                    case TAG_USER_DEFINED:
                        res = R.drawable.tag_pink;
                        break;
                    case TAG_UNTAGGED:
                        res = R.drawable.tag_cream;
                        break;
                }

                if (res != 0) {
                    ai.icon = new SoftReference<Drawable>(ctx.getResources().getDrawable(res));
                } else {
                    ai.icon = null;
                }
            } else {
                // apk items
                PackageManager pm = ctx.getPackageManager();

                PackageInfo pi = pm.getPackageArchiveInfo(ai.file.getAbsolutePath(), 0);

                if (pi != null && pi.applicationInfo != null) {
                    if (pi.applicationInfo.publicSourceDir == null) {
                        pi.applicationInfo.publicSourceDir = ai.file.getAbsolutePath();
                    }

                    try {
                        Drawable icon = pm.getApplicationIcon(pi.applicationInfo);

                        if (icon != null) {
                            ai.icon = new SoftReference<Drawable>(icon);
                        } else {
                            ai.icon = null;
                        }
                    } catch (OutOfMemoryError oom) {
                        Log.e(RestoreAppActivity.class.getName(), "OOM when loading icon: " //$NON-NLS-1$
                                + pi.packageName, oom);
                    }
                }
            }

            return ai.icon == null ? null : ai.icon.get();
        }

        private View getItemView(int position, ApkInfo ai, View convertView, ViewGroup parent) {
            View view;

            if (convertView instanceof LinearLayout) {
                view = convertView;
            } else {
                view =
                        ((LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(
                                R.layout.app_item, parent, false);
            }

            if (view.getTag() == null) {
                view.setTag(new AppViewHolder(view));
            }

            AppViewHolder viewHolder = (AppViewHolder) view.getTag();

            if (ai.label != null) {
                viewHolder.txt_name.setText(ai.label);
            } else {
                viewHolder.txt_name.setText(ai.file.getName());
            }

            SharedPreferences prefStore =
                    ctx.getSharedPreferences(RESTORE_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            boolean showIcon = Util.getBooleanOption(prefStore, PREF_KEY_SHOW_ICON);

            if (ai.file == null || showIcon) {
                viewHolder.img_type.setVisibility(View.VISIBLE);

                updateAppIcon(viewHolder.img_type, ai, false);
            } else {
                viewHolder.img_type.setVisibility(View.GONE);
            }

            if (ai.file == null) {
                // should be tag item
                viewHolder.txt_name.setTextColor(Color.WHITE);

                if (Util.getBooleanOption(prefStore, PREF_KEY_SHOW_COUNT)) {
                    viewHolder.txt_ver.setVisibility(View.VISIBLE);
                    viewHolder.txt_ver.setText("( " + ai.size + " )"); //$NON-NLS-1$ //$NON-NLS-2$
                } else {
                    viewHolder.txt_ver.setVisibility(View.GONE);
                }

                viewHolder.txt_size.setVisibility(View.GONE);
                viewHolder.txt_path.setVisibility(View.GONE);
                viewHolder.txt_time.setVisibility(View.GONE);
                viewHolder.ckb_app.setVisibility(View.GONE);
                viewHolder.tagPane.setVisibility(View.GONE);

                return view;
            }

            // process as file
            switch (ai.installed) {
                case 1:
                    viewHolder.txt_name.setTextColor(Color.YELLOW);
                    break;
                case 2:
                    viewHolder.txt_name.setTextColor(ctx.getResources().getColor(R.color.nephritis));
                    break;
                case 3:
                    viewHolder.txt_name.setTextColor(ctx.getResources().getColor(R.color.peter_river));
                    break;
                default:
                    viewHolder.txt_name.setTextColor(Color.WHITE);
                    break;
            }

            viewHolder.txt_ver.setVisibility(View.VISIBLE);
            if (ai.version != null) {
                viewHolder.txt_ver.setText(versionPrefix + " " + ai.version); //$NON-NLS-1$
            } else {
                viewHolder.txt_ver.setText(""); //$NON-NLS-1$
            }

            if (Util.getBooleanOption(prefStore, PREF_KEY_SHOW_SIZE)) {
                viewHolder.txt_size.setVisibility(View.VISIBLE);

                if (ai.sizeString != null) {
                    viewHolder.txt_size.setText(ai.sizeString);
                } else {
                    viewHolder.txt_size.setText(ResUtil.getString(ctx, R.string.unknown));
                }
            } else {
                viewHolder.txt_size.setVisibility(View.GONE);
            }

            if (Util.getBooleanOption(prefStore, PREF_KEY_SHOW_PATH)) {
                viewHolder.txt_path.setVisibility(View.VISIBLE);

                viewHolder.txt_path.setText(ai.file.getAbsolutePath());
            } else {
                viewHolder.txt_path.setVisibility(View.GONE);
            }

            if (Util.getBooleanOption(prefStore, PREF_KEY_SHOW_DATE)) {
                viewHolder.txt_time.setVisibility(View.VISIBLE);

                viewHolder.txt_time.setText(DateUtils.formatDateTime(ctx, ai.file.lastModified(),
                        DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME));
            } else {
                viewHolder.txt_time.setVisibility(View.GONE);
            }

            if (ai.tags == null) {
                viewHolder.tagPane.setVisibility(View.GONE);
            } else {
                viewHolder.tagPane.setVisibility(View.VISIBLE);

                viewHolder.tagPane.removeAllViews();

                LayoutInflater inflator =
                        (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                for (String tag : ai.tags) {
                    if (!tag.equals(currentTag)) {
                        TextView tagView =
                                (TextView) inflator.inflate(R.layout.tag_item, viewHolder.tagPane, false);
                        tagView.setText(tag);

                        viewHolder.tagPane.addView(tagView);
                    }
                }
            }

            viewHolder.ckb_app.setVisibility((showIcon && Util.SDK_VER >= 14) ? View.GONE : View.VISIBLE);
            viewHolder.ckb_app.setTag(position);
            viewHolder.ckb_app.setChecked(ai.checked);
            viewHolder.ckb_app.setOnCheckedChangeListener(checkListener);

            return view;
        }

        void updateAppIcon(ImageView iconView, ApkInfo item, boolean isSelection) {
            // for SDK14+, we use icon to show the selection state

            if (isSelection && Util.SDK_VER < 14) {
                // no need to update icon for selection in this case
                return;
            }

            iconView.setTag(System.currentTimeMillis());

            if (item.checked && Util.SDK_VER >= 14) {
                iconView.setImageResource(R.drawable.check_icon_2);
            } else {
                Drawable iconDrawable = item.icon == null ? null : item.icon.get();

                iconView.setImageDrawable(iconDrawable);

                if (iconDrawable == null) {
                    // try reload icon for app items
                    try {
                        new IconLoaderTask(ctx, iconView).execute(item);
                    } catch (RejectedExecutionException e) {
                        // try adjust pool size and run again
                        if (Util.adjustAsyncTaskPoolSize()) {
                            try {
                                new IconLoaderTask(ctx, iconView).execute(item);
                            } catch (RejectedExecutionException e1) {
                                // this time we ignore
                            }
                        }
                    }
                }
            }

            // animation only for icon change by selection
            if (isSelection && Util.SDK_VER >= 14) {
                iconView.startAnimation(AnimationUtils.loadAnimation(ctx, R.anim.flip));
            }
        }
    }

    /**
     * RestoreAppSettings
     */
    public static class RestoreAppSettings extends PreferenceActivity {

        private String[] defaultTags;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);

            super.onCreate(savedInstanceState);

            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(this));

            PreferenceCategory pc = new PreferenceCategory(this);
            pc.setTitle(ResUtil.getString(this, R.string.preference));
            getPreferenceScreen().addPreference(pc);

            Preference perfRestoreFolder = new Preference(this);
            perfRestoreFolder.setKey(PREF_KEY_APP_RESTORE_DIR);
            perfRestoreFolder.setTitle(ResUtil.getString(this, R.string.scan_dir));
            pc.addPreference(perfRestoreFolder);

            CheckBoxPreference perfSubDir = new CheckBoxPreference(this);
            perfSubDir.setKey(PREF_KEY_SEARCH_SUB_DIR);
            perfSubDir.setTitle(ResUtil.getString(this, R.string.search_subdir));
            perfSubDir.setSummary(ResUtil.getString(this, R.string.search_subdir_sum));
            pc.addPreference(perfSubDir);

            CheckBoxPreference perfShowSize = new CheckBoxPreference(this);
            perfShowSize.setKey(PREF_KEY_SHOW_SIZE);
            perfShowSize.setTitle(ResUtil.getString(this, R.string.show_file_size));
            perfShowSize.setSummary(ResUtil.getString(this, R.string.show_file_size_sum));
            pc.addPreference(perfShowSize);

            CheckBoxPreference perfShowDate = new CheckBoxPreference(this);
            perfShowDate.setKey(PREF_KEY_SHOW_DATE);
            perfShowDate.setTitle(ResUtil.getString(this, R.string.show_file_date));
            perfShowDate.setSummary(ResUtil.getString(this, R.string.show_file_date_sum));
            pc.addPreference(perfShowDate);

            CheckBoxPreference perfShowPath = new CheckBoxPreference(this);
            perfShowPath.setKey(PREF_KEY_SHOW_PATH);
            perfShowPath.setTitle(ResUtil.getString(this, R.string.show_file_path));
            perfShowPath.setSummary(ResUtil.getString(this, R.string.show_file_path_sum));
            pc.addPreference(perfShowPath);

            CheckBoxPreference perfShowIcon = new CheckBoxPreference(this);
            perfShowIcon.setKey(PREF_KEY_SHOW_ICON);
            perfShowIcon.setTitle(ResUtil.getString(this, R.string.show_file_icon));
            perfShowIcon.setSummary(ResUtil.getString(this, R.string.show_file_icon_sum));
            pc.addPreference(perfShowIcon);

            pc = new PreferenceCategory(this);
            pc.setTitle(ResUtil.getString(this, R.string.tags));
            getPreferenceScreen().addPreference(pc);

            CheckBoxPreference perfTagView = new CheckBoxPreference(this);
            perfTagView.setKey(PREF_KEY_USE_TAG_VIEW);
            perfTagView.setTitle(ResUtil.getString(this, R.string.enable_tag_view));
            perfTagView.setSummary(ResUtil.getString(this, R.string.enable_tag_view_sum));
            pc.addPreference(perfTagView);

            CheckBoxPreference perfShowCount = new CheckBoxPreference(this);
            perfShowCount.setKey(PREF_KEY_SHOW_COUNT);
            perfShowCount.setTitle(ResUtil.getString(this, R.string.show_file_count));
            perfShowCount.setSummary(ResUtil.getString(this, R.string.show_file_count_sum));
            pc.addPreference(perfShowCount);

            Preference perfTagVisibility = new Preference(this);
            perfTagVisibility.setKey(PREF_KEY_TAG_VISIBILITY);
            perfTagVisibility.setTitle(ResUtil.getString(this, R.string.toggle_tags));
            pc.addPreference(perfTagVisibility);

            Preference perfManageTag = new Preference(this);
            perfManageTag.setKey(PREF_KEY_CUSTOM_TAGS);
            perfManageTag.setTitle(ResUtil.getString(this, R.string.manage_tags));
            pc.addPreference(perfManageTag);

            Preference perfRecent = new Preference(this);
            perfRecent.setKey(PREF_KEY_RECENT_SCOPE);
            perfRecent.setTitle(ResUtil.getString(this, R.string.recent_range));
            pc.addPreference(perfRecent);

            pc = new PreferenceCategory(this);
            pc.setTitle(ResUtil.getString(this, R.string.sort));
            getPreferenceScreen().addPreference(pc);

            Preference perfSortType = new Preference(this);
            perfSortType.setKey(PREF_KEY_SORT_ORDER_TYPE);
            perfSortType.setTitle(ResUtil.getString(this, R.string.prime_sort_type));
            pc.addPreference(perfSortType);

            Preference perfSortDirection = new Preference(this);
            perfSortDirection.setKey(PREF_KEY_SORT_DIRECTION);
            perfSortDirection.setTitle(ResUtil.getString(this, R.string.prime_sort_direction));
            pc.addPreference(perfSortDirection);

            Preference perfSecSortType = new Preference(this);
            perfSecSortType.setKey(PREF_KEY_SECONDARY_SORT_ORDER_TYPE);
            perfSecSortType.setTitle(ResUtil.getString(this, R.string.second_sort_type));
            pc.addPreference(perfSecSortType);

            Preference perfSecSortDirection = new Preference(this);
            perfSecSortDirection.setKey(PREF_KEY_SECONDARY_SORT_DIRECTION);
            perfSecSortDirection.setTitle(ResUtil.getString(this, R.string.second_sort_direction));
            pc.addPreference(perfSecSortDirection);

            refreshRestoreFolder();
            refreshBooleanOption(PREF_KEY_USE_TAG_VIEW);
            refreshBooleanOption(PREF_KEY_SEARCH_SUB_DIR);
            refreshBooleanOption(PREF_KEY_SHOW_COUNT);
            refreshSortType(PREF_KEY_SORT_ORDER_TYPE);
            refreshSortDirection(PREF_KEY_SORT_DIRECTION);
            refreshSortType(PREF_KEY_SECONDARY_SORT_ORDER_TYPE);
            refreshSortDirection(PREF_KEY_SECONDARY_SORT_DIRECTION);
            refreshBooleanOption(PREF_KEY_SHOW_SIZE);
            refreshBooleanOption(PREF_KEY_SHOW_DATE);
            refreshBooleanOption(PREF_KEY_SHOW_PATH);
            refreshBooleanOption(PREF_KEY_SHOW_ICON);

            defaultTags =
                    new String[]{ResUtil.getString(this, R.string.untagged),
                            ResUtil.getString(this, R.string.recent), ResUtil.getString(this, R.string.custom),
                            ResUtil.getString(this, R.string.archived),
                            ResUtil.getString(this, R.string.user_apps),
                            ResUtil.getString(this, R.string.sys_apps),};

            Util.refreshCustomTags(this, getIntent(), perfManageTag);
            Util.refreshTagVisibility(this, getIntent(), perfTagVisibility, defaultTags);
            refreshRecentRange();

            setResult(RESULT_OK, getIntent());
        }

        @Override
        protected void onSaveInstanceState(Bundle outState) {
            // fix: https://code.google.com/p/android/issues/detail?id=19917
            outState.putString("WORKAROUND_FOR_BUG_19917_KEY", //$NON-NLS-1$
                    "WORKAROUND_FOR_BUG_19917_VALUE"); //$NON-NLS-1$

            super.onSaveInstanceState(outState);
        }

        void refreshRestoreFolder() {
            String path = getIntent().getStringExtra(PREF_KEY_APP_RESTORE_DIR);
            if (path == null) {
                path = getIntent().getStringExtra(PREF_KEY_DEFAULT_RESTORE_DIR);
            }

            findPreference(PREF_KEY_APP_RESTORE_DIR).setSummary(path);
        }

        void refreshBooleanOption(String key) {
            boolean val = getIntent().getBooleanExtra(key, true);

            ((CheckBoxPreference) findPreference(key)).setChecked(val);
        }

        void refreshRecentRange() {
            int days = getIntent().getIntExtra(PREF_KEY_RECENT_SCOPE, 3);

            findPreference(PREF_KEY_RECENT_SCOPE).setSummary(
                    days == 1 ? ResUtil.getString(this, R.string.past_day) : ResUtil.getString(this,
                            R.string.past_days, days));
        }

        void refreshSortType(String key) {
            int type = getIntent().getIntExtra(key, ORDER_TYPE_NAME);

            String label = null;
            switch (type) {
                case ORDER_TYPE_NAME:
                    label = ResUtil.getString(this, R.string.name);
                    break;
                case ORDER_TYPE_SIZE:
                    label = ResUtil.getString(this, R.string.file_size);
                    break;
                case ORDER_TYPE_INSTALL:
                    label = ResUtil.getString(this, R.string.installation);
                    break;
                case ORDER_TYPE_DATE:
                    label = ResUtil.getString(this, R.string.file_date);
                    break;
                case ORDER_TYPE_PATH:
                    label = ResUtil.getString(this, R.string.file_path);
                    break;
            }

            findPreference(key).setSummary(label);
        }

        void refreshSortDirection(String key) {
            int type = getIntent().getIntExtra(key, ORDER_ASC);

            String label =
                    ResUtil.getString(this, type == ORDER_ASC ? R.string.ascending : R.string.descending);

            findPreference(key).setSummary(label);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            final Intent it = getIntent();

            final String prefKey = preference.getKey();

            if (PREF_KEY_APP_RESTORE_DIR.equals(prefKey)) {
                final EditText txt = new EditText(this);
                final String defaultPath = it.getStringExtra(PREF_KEY_DEFAULT_RESTORE_DIR);

                String path = it.getStringExtra(PREF_KEY_APP_RESTORE_DIR);
                if (path == null) {
                    path = defaultPath;
                }
                txt.setText(path);

                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        String path = txt.getText().toString();

                        if (path != null) {
                            path = path.trim();

                            if (path.length() == 0) {
                                path = null;
                            }
                        }

                        it.putExtra(PREF_KEY_APP_RESTORE_DIR, path);

                        dialog.dismiss();

                        refreshRestoreFolder();
                    }
                };

                new AlertDialog.Builder(this).setTitle(ResUtil.getString(this, R.string.scan_dir))
                        .setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, null).setView(txt).create().show();

                return true;
            } else if (PREF_KEY_USE_TAG_VIEW.equals(prefKey)) {
                it.putExtra(PREF_KEY_USE_TAG_VIEW,
                        ((CheckBoxPreference) findPreference(PREF_KEY_USE_TAG_VIEW)).isChecked());

                return true;
            } else if (PREF_KEY_SEARCH_SUB_DIR.equals(prefKey)) {
                it.putExtra(PREF_KEY_SEARCH_SUB_DIR,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SEARCH_SUB_DIR)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_COUNT.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_COUNT,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_COUNT)).isChecked());

                return true;
            } else if (PREF_KEY_TAG_VISIBILITY.equals(prefKey)) {
                Util.editTagVisibility(this, it, preference, defaultTags);

                return true;
            } else if (PREF_KEY_CUSTOM_TAGS.equals(prefKey)) {
                Util.editCustomTags(this, it, preference, findPreference(PREF_KEY_TAG_VISIBILITY),
                        defaultTags);

                return true;
            } else if (PREF_KEY_RECENT_SCOPE.equals(prefKey)) {
                View v = getLayoutInflater().inflate(R.layout.scope, null);

                final TextView txtInfo = (TextView) v.findViewById(R.id.txt_info);
                final SeekBar bar = (SeekBar) v.findViewById(R.id.bar_scope);
                bar.setMax(MAX_DAYS - 1);

                bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }

                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        int days = progress + 1;

                        txtInfo.setText(days == 1 ? ResUtil.getString(RestoreAppSettings.this,
                                R.string.past_day) : ResUtil.getString(RestoreAppSettings.this, R.string.past_days,
                                days));
                    }
                });

                int days = it.getIntExtra(PREF_KEY_RECENT_SCOPE, 3);

                if (days < MIN_DAYS) {
                    days = MIN_DAYS;
                }

                if (days > MAX_DAYS) {
                    days = MAX_DAYS;
                }

                bar.setProgress(days - 1);

                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        int days = bar.getProgress() + 1;

                        it.putExtra(PREF_KEY_RECENT_SCOPE, days);

                        refreshRecentRange();
                    }
                };

                new AlertDialog.Builder(this).setTitle(ResUtil.getString(this, R.string.recent_range))
                        .setView(v).setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, null).create().show();

                return true;
            } else if (PREF_KEY_SHOW_SIZE.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_SIZE,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_SIZE)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_DATE.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_DATE,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_DATE)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_PATH.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_PATH,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_PATH)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_ICON.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_ICON,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_ICON)).isChecked());

                return true;
            } else if (PREF_KEY_SORT_ORDER_TYPE.equals(prefKey)
                    || PREF_KEY_SECONDARY_SORT_ORDER_TYPE.equals(prefKey)) {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        it.putExtra(prefKey, which);

                        dialog.dismiss();

                        refreshSortType(prefKey);
                    }
                };

                new AlertDialog.Builder(this)
                        .setTitle(ResUtil.getString(this, R.string.sort_type))
                        //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setSingleChoiceItems(
                                new String[]{ResUtil.getString(this, R.string.name),
                                        ResUtil.getString(this, R.string.file_size),
                                        ResUtil.getString(this, R.string.installation),
                                        ResUtil.getString(this, R.string.file_date),
                                        ResUtil.getString(this, R.string.file_path),},
                                it.getIntExtra(prefKey, ORDER_TYPE_NAME), listener).create().show();

                return true;
            } else if (PREF_KEY_SORT_DIRECTION.equals(prefKey)
                    || PREF_KEY_SECONDARY_SORT_DIRECTION.equals(prefKey)) {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        it.putExtra(prefKey, which == 0 ? ORDER_ASC : ORDER_DESC);

                        dialog.dismiss();

                        refreshSortDirection(prefKey);
                    }
                };

                new AlertDialog.Builder(this)
                        .setTitle(ResUtil.getString(this, R.string.sort_direction))
                        //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setSingleChoiceItems(
                                new String[]{ResUtil.getString(this, R.string.ascending),
                                        ResUtil.getString(this, R.string.descending),},
                                it.getIntExtra(prefKey, ORDER_ASC) == ORDER_ASC ? 0 : 1, listener).create().show();

                return true;
            }

            return false;
        }
    }

}
