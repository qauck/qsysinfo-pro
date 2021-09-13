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

import android.Manifest.permission;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageStats;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.ClipboardManager;
import android.text.Html;
import android.text.TextUtils;
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
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import org.uguess.android.sysinfo.ToolMenuDialog.ActionHint;
import org.uguess.android.sysinfo.ToolMenuDialog.IActionMenuProvider;
import org.uguess.android.sysinfo.ToolMenuDialog.ISortMenuProvider;
import org.uguess.android.sysinfo.ToolMenuDialog.IToggleMenuProvider;
import org.uguess.android.sysinfo.ToolMenuDialog.SortHint;
import org.uguess.android.sysinfo.ToolMenuDialog.ToggleHint;
import org.uguess.android.sysinfo.Util.EditorState;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;

/**
 * ApplicationManager
 */
public class ApplicationManager extends ListActivity implements Constants,
        IActionMenuProvider, ISortMenuProvider, IToggleMenuProvider {

    private static final int MSG_COPING = MSG_PRIVATE + 1;
    private static final int MSG_COPING_ERROR = MSG_PRIVATE + 2;
    private static final int MSG_COPING_FINISHED = MSG_PRIVATE + 3;
    private static final int MSG_REFRESH_PKG_SIZE = MSG_PRIVATE + 4;
    private static final int MSG_REFRESH_PKG_LABEL = MSG_PRIVATE + 5;
    private static final int MSG_REFRESH_PKG_ICON = MSG_PRIVATE + 6;
    private static final int MSG_REFRESH_BACKUP_STATE = MSG_PRIVATE + 7;
    private static final int MSG_UPDATE = MSG_PRIVATE + 8;
    private static final int MSG_SHOW_PROGRESS = MSG_PRIVATE + 9;
    private static final int MSG_ROOT_COPY_FINISHED = MSG_PRIVATE + 10;

    private static final int APP_TYPE_ALL = 0;
    private static final int APP_TYPE_SYS = 1;
    private static final int APP_TYPE_USER = 2;
    private static final int APP_TYPE_RECENT = 3;
    private static final int APP_TYPE_UNTAGGED = 4;
    private static final int APP_TYPE_CUSTOM = 5;

    private static final int ORDER_TYPE_NAME = 0;
    private static final int ORDER_TYPE_CODE_SIZE = 1;
    private static final int ORDER_TYPE_DATA_SIZE = 2;
    private static final int ORDER_TYPE_CACHE_SIZE = 3;
    private static final int ORDER_TYPE_TOTAL_SIZE = 4;
    private static final int ORDER_TYPE_INSTALL_DATE = 5;
    private static final int ORDER_TYPE_BACKUP_STATE = 6;
    private static final int ORDER_TYPE_INSTALL_LOCATION = 7;
    private static final int ORDER_TYPE_ADMOB_STATE = 8;
    private static final int ORDER_TYPE_AUTOSTART_STATE = 9;

    private static final int REQUEST_RESTORE = REQUEST_PRIVATE + 1;

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 100;

    private static final String PREF_KEY_FILTER_APP_TYPE = "filter_app_type"; //$NON-NLS-1$
    private static final String PREF_KEY_APP_EXPORT_DIR = "app_export_dir"; //$NON-NLS-1$
    private static final String PREF_KEY_KEEP_APP_VERSION = "keep_app_version"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_BACKUP_STATE = "show_backup_state"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_ADMOB_STATE = "show_admob_state"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_INSTALL_LOCATION = "show_install_location"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_AUTOSTART_STATE = "show_autostart_state"; //$NON-NLS-1$
    private static final String PREF_KEY_SHOW_WEARABLE_STATE = "show_wearable_state"; //$NON-NLS-1$
    private static final String PREF_KEY_USE_TAG_SETTINGS = "use_tag_settings"; //$NON-NLS-1$

    private static final int ACTION_MENU = 0;
    private static final int ACTION_MANAGE = 1;
    private static final int ACTION_LAUNCH = 2;
    private static final int ACTION_SEARCH = 3;
    private static final int ACTION_DETAILS = 4;
    private static final int ACTION_TAGS = 5;
    private static final int ACTION_TOGGLE_SELECTION = 6;

    static final String KEY_RESTORE_SYS_PATH = "restore_sys_path"; //$NON-NLS-1$
    static final String KEY_RESTORE_USER_PATH = "restore_user_path"; //$NON-NLS-1$
    static final String KEY_ARCHIVE_PATH = "archive_path"; //$NON-NLS-1$

    private static final String DEFAULT_EXPORT_FOLDER = "/sdcard/backups/"; //$NON-NLS-1$

    private static final String SYS_APP = "system/"; //$NON-NLS-1$
    private static final String USER_APP = "user/"; //$NON-NLS-1$
    private static final String ARCHIVED = "archived/"; //$NON-NLS-1$

    private static final int MAX_VER_LENGTH = 64;

    static Method mdGetPackageSizeInfo, mdFreeStorageAndNotify;

    static {
        try {
            mdGetPackageSizeInfo = PackageManager.class.getMethod("getPackageSizeInfo", //$NON-NLS-1$
                    String.class, IPackageStatsObserver.class);
        } catch (Exception e) {
            Log.e(ApplicationManager.class.getName(), e.getLocalizedMessage(), e);
        }

        try {
            mdFreeStorageAndNotify = PackageManager.class.getDeclaredMethod("freeStorageAndNotify", //$NON-NLS-1$
                    long.class, IPackageDataObserver.class);
        } catch (Exception e) {
            Log.e(ApplicationManager.class.getName(), e.getLocalizedMessage(), e);
        }
    }

    volatile int currentLayer;
    volatile int currentType;
    volatile String currentTag;
    ProgressDialog progress;
    volatile boolean aborted;
    volatile boolean stopCheck;
    String versionPrefix;
    AppCache appCache;
    PkgSizeUpdaterThread sizeUpdater;
    ResourceUpdaterThread resUpdater;
    BackupStateUpdaterThread backupUpdater;

    Handler handler = new AppHandler(this);

    private static final class AppHandler extends Handler {

        private WeakReference<ApplicationManager> acRef;
        private short steps;

        AppHandler(ApplicationManager ac) {
            acRef = new WeakReference<ApplicationManager>(ac);
        }

        @Override
        public void handleMessage(Message msg) {
            ApplicationManager host = acRef.get();
            if (host == null) {
                return;
            }

            ArrayAdapter<AppInfoHolder> adapter;
            switch (msg.what) {
                case MSG_INIT_OK:
                    adapter = (ArrayAdapter<AppInfoHolder>) host.getListView().getAdapter();
                    adapter.setNotifyOnChange(false);
                    adapter.clear();

                    synchronized (host.appCache) {
                        ArrayList<AppInfoHolder> localList = host.appCache.appList;
                        for (int i = 0, size = localList.size(); i < size; i++) {
                            adapter.add(localList.get(i));
                        }
                    }

                    // should always no selection at this stage
                    host.hideButtons();
                    adapter.notifyDataSetChanged();
                    sendEmptyMessage(MSG_DISMISS_PROGRESS);

                    if (host.getListView().getCount() == 0) {
                        Util.shortToast(host, R.string.no_app_show);
                    }
                    break;
                case MSG_COPING:
                    if (host.progress != null) {
                        if (msg.arg1 == 0) {
                            // this is a main step update
                            host.progress.setMessage(ResUtil.getString(host, R.string.exporting, msg.obj));
                            host.progress.setProgress(host.progress.getProgress() + 1);
                        } else {
                            // this is a sub step update
                            host.progress.setMessage(ResUtil.getString(host, R.string.exporting,
                                    String.valueOf(msg.obj) + " (" //$NON-NLS-1$
                                            + msg.arg1 + "%)")); //$NON-NLS-1$
                        }
                    }
                    break;
                case MSG_COPING_ERROR:
                    if (msg.arg1 == 0 && host.progress != null) {
                        Util.safeDismissDialog(host.progress);
                        host.progress = null;
                    }

                    Util.shortToast(host, R.string.copy_error, msg.obj);
                    break;
                case MSG_COPING_FINISHED:
                    final List<AppInfoHolder> apps = (List<AppInfoHolder>) msg.obj;
                    if (host.progress != null) {
                        host.progress.setMessage(msg.arg2 > 0 ? ResUtil.getString(host, R.string.exported_skip,
                                msg.arg1, msg.arg2) : ResUtil.getString(host, R.string.exported, msg.arg1));
                        host.progress.setProgress(host.progress.getMax());
                        Util.safeDismissDialog(host.progress);
                        host.progress = null;
                    }

                    Util.shortToast(
                            host,
                            msg.arg2 > 0 ? ResUtil.getString(host, R.string.exported_to_skip, msg.arg1, Util
                                    .getStringOption(host, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_APP_EXPORT_DIR,
                                            DEFAULT_EXPORT_FOLDER), msg.arg2) : ResUtil.getString(host,
                                    R.string.exported_to, msg.arg1, Util.getStringOption(host,
                                            APPLICATION_MANAGER_STORE_NAME, PREF_KEY_APP_EXPORT_DIR,
                                            DEFAULT_EXPORT_FOLDER)));

                    host.toggleAllSelection(false);

                    if (host.currentLayer != LAYER_TAG
                            && getTagBooleanOption(host, APPLICATION_MANAGER_STORE_NAME,
                            PREF_KEY_SHOW_BACKUP_STATE, host.currentTag, host.currentType)) {
                        // reload backup state

                        if (host.backupUpdater != null) {
                            host.backupUpdater.aborted = true;
                        }

                        (host.backupUpdater =
                                new BackupStateUpdaterThread(host, apps, host.appCache, host.handler)).start();
                    }
                    break;
                case MSG_DISMISS_PROGRESS:
                    if (host.progress != null) {
                        Util.safeDismissDialog(host.progress);
                        host.progress = null;
                    }
                    break;
                case MSG_SHOW_PROGRESS:

                    if (host.progress != null) {
                        Util.safeDismissDialog(host.progress);
                    }

                    host.progress = new ProgressDialog(host);
                    if (msg.obj instanceof String) {
                        host.progress.setMessage((String) msg.obj);
                    }
                    host.progress.setIndeterminate(true);
                    host.progress.setCancelable(false);
                    host.progress.show();
                    break;
                case MSG_ROOT_COPY_FINISHED:
                    List<AppInfoHolder> rootApps = (List<AppInfoHolder>) msg.obj;
                    Util.shortToast(host, ResUtil.getString(host, R.string.exported_to, rootApps.size(), Util
                            .getStringOption(host, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_APP_EXPORT_DIR,
                                    DEFAULT_EXPORT_FOLDER)));
                    if (host.currentLayer != LAYER_TAG
                            && getTagBooleanOption(host, APPLICATION_MANAGER_STORE_NAME,
                            PREF_KEY_SHOW_BACKUP_STATE, host.currentTag, host.currentType)) {
                        // reload backup state

                        if (host.backupUpdater != null) {
                            host.backupUpdater.aborted = true;
                        }

                        (host.backupUpdater =
                                new BackupStateUpdaterThread(host, rootApps, host.appCache, host.handler)).start();
                    }
                    break;
                case MSG_REFRESH_PKG_SIZE:
                case MSG_REFRESH_PKG_LABEL:
                case MSG_REFRESH_BACKUP_STATE:
                    adapter = (ArrayAdapter<AppInfoHolder>) host.getListView().getAdapter();
                    steps--;

                    if (msg.arg1 == 1) {
                        adapter.setNotifyOnChange(false);
                        adapter.clear();
                        synchronized (host.appCache) {
                            ArrayList<AppInfoHolder> localList = host.appCache.appList;

                            for (int i = 0, size = localList.size(); i < size; i++) {
                                adapter.add(localList.get(i));
                            }
                        }
                    }

                    adapter.notifyDataSetChanged();
                    if (steps <= 0) {
                        host.toggleProgressBar(false);
                    }
                    break;
                case MSG_REFRESH_PKG_ICON:
                    ((ArrayAdapter<AppInfoHolder>) host.getListView().getAdapter()).notifyDataSetChanged();
                    break;
                case MSG_TOAST:
                    Util.shortToast(host, (String) msg.obj);
                    break;
                case MSG_UPDATE:
                    if (host.sizeUpdater != null) {
                        host.sizeUpdater.aborted = true;
                    }

                    if (host.resUpdater != null) {
                        host.resUpdater.aborted = true;
                    }

                    if (host.backupUpdater != null) {
                        host.backupUpdater.aborted = true;
                    }

                    host.toggleProgressBar(false);
                    host.appCache.update((ArrayList<AppInfoHolder>) msg.obj);

                    if (msg.arg1 == 1) {
                        host.appCache.reOrderApps(host, host.currentTag, host.currentType);
                    } else {
                        host.appCache.reOrderTags();
                    }

                    sendEmptyMessage(MSG_INIT_OK);
                    if (msg.arg1 == 1) {
                        steps = 0;

                        if (getTagBooleanOption(host, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SHOW_SIZE,
                                host.currentTag, host.currentType)) {
                            (host.sizeUpdater = new PkgSizeUpdaterThread(host, host.appCache, host.handler))
                                    .start();

                            steps++;
                        }

                        (host.resUpdater = new ResourceUpdaterThread(host, host.appCache, host.handler))
                                .start();
                        steps++;

                        if (getTagBooleanOption(host, APPLICATION_MANAGER_STORE_NAME,
                                PREF_KEY_SHOW_BACKUP_STATE, host.currentTag, host.currentType)) {
                            (host.backupUpdater =
                                    new BackupStateUpdaterThread(host, null, host.appCache, host.handler)).start();
                            steps++;
                        }

                        host.toggleProgressBar(true);
                    }
                    break;
                case MSG_CONTENT_READY:
                    sendEmptyMessage(MSG_DISMISS_PROGRESS);
                    Util.handleMsgSendContentReady((String) msg.obj, "Android Applications - ", //$NON-NLS-1$
                            host, msg.arg2 == 1);
                    break;
                case MSG_CHECK_FORCE_COMPRESSION:
                    sendEmptyMessage(MSG_DISMISS_PROGRESS);
                    Util.checkForceCompression(this, host, (String) msg.obj, msg.arg1, "android_applications"); //$NON-NLS-1$
                    break;
            }
        }
    }

    OnCheckedChangeListener checkListener = new OnCheckedChangeListener() {

        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            int position = (Integer) buttonView.getTag();
            if (position >= getListView().getCount()) {
                return;
            }
            ((AppInfoHolder) getListView().getItemAtPosition(position)).checked = isChecked;

            View v = findViewById(R.id.app_footer);
            ((TextView) v.findViewById(R.id.txt_count)).setText(String.valueOf(getSelectedCount(getListView())));

            if (isChecked) {
                if (v.getVisibility() != View.VISIBLE) {
                    v.setVisibility(View.VISIBLE);
                    v.startAnimation(AnimationUtils.loadAnimation(ApplicationManager.this,
                            R.anim.footer_appear));
                }
            } else if (getSelectedCount(getListView()) == 0) {
                hideButtons();
            }
        }
    };

    View.OnClickListener iconClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (currentLayer == LAYER_TAG) {
                return;
            }

            int position = (Integer) ((AppViewHolder) ((View) v.getParent()).getTag()).ckb_app.getTag();
            if (position >= getListView().getCount()) {
                return;
            }
            AppInfoHolder item = (AppInfoHolder) getListView().getItemAtPosition(position);
            handleAction(item, ACTION_TOGGLE_SELECTION, (View) v.getParent());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        versionPrefix = ResUtil.getString(this, R.string.version);
        appCache = new AppCache();

        currentType = -1;
        currentTag = null;

        setContentView(R.layout.app_lst_view);

        View btn = findViewById(R.id.btn_export);
        if (btn != null) {
            btn.setOnClickListener(new View.OnClickListener() {

                public void onClick(View v) {
                    Util.checkStoragePermission(ApplicationManager.this);
                    doExport();
                }
            });
            btn.setOnLongClickListener(new OnLongClickListener() {

                @Override
                public boolean onLongClick(View v) {
                    Util.shortToast(ApplicationManager.this, R.string.export);
                    return true;
                }
            });
        }

        btn = findViewById(R.id.btn_uninstall);
        if (btn != null) {
            btn.setOnClickListener(new View.OnClickListener() {

                public void onClick(View v) {
                    doUninstall();
                }
            });
            btn.setOnLongClickListener(new OnLongClickListener() {

                @Override
                public boolean onLongClick(View v) {
                    Util.shortToast(ApplicationManager.this, R.string.uninstall);
                    return true;
                }
            });
        }

        btn = findViewById(R.id.btn_sel_all);
        if (btn != null) {
            btn.setOnClickListener(new View.OnClickListener() {

                public void onClick(View v) {
                    showAdvancedSelection();
                }
            });
            btn.setOnLongClickListener(new OnLongClickListener() {

                @Override
                public boolean onLongClick(View v) {
                    Util.shortToast(ApplicationManager.this, R.string.select);
                    return true;
                }
            });
        }

        btn = findViewById(R.id.btn_desel_all);
        if (btn != null) {
            btn.setOnClickListener(new View.OnClickListener() {

                public void onClick(View v) {
                    toggleAllSelection(false);
                }
            });
            btn.setOnLongClickListener(new OnLongClickListener() {

                @Override
                public boolean onLongClick(View v) {
                    Util.shortToast(ApplicationManager.this, R.string.deselect_all);
                    return true;
                }
            });
        }

        ListView lstApps = getListView();
        lstApps.setFastScrollEnabled(true);
        registerForContextMenu(lstApps);

        lstApps.setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfoHolder holder = (AppInfoHolder) parent.getItemAtPosition(position);

                if (holder.appInfo == null) {
                    // handle tag item
                    loadApps(holder.versionCode == APP_TYPE_CUSTOM ? (String) holder.label : null,
                            holder.versionCode);
                } else {
                    // handle app item
                    int action =
                            Util.getIntOption(ApplicationManager.this, APPLICATION_MANAGER_STORE_NAME,
                                    PREF_KEY_DEFAULT_TAP_ACTION, ACTION_TOGGLE_SELECTION);

                    handleAction(holder, action, view);
                }
            }
        });

        ArrayAdapter<AppInfoHolder> adapter =
                new ArrayAdapter<AppInfoHolder>(ApplicationManager.this, R.layout.app_item) {

                    private Drawable sdcardImg = null;
                    private Drawable sdcardDimImg = null;

                    {
                        try {
                            sdcardImg = getResources().getDrawable(R.drawable.sdcard);
                            sdcardDimImg = getResources().getDrawable(R.drawable.sdcard_dim);
                        } catch (Exception fe) {
                            Log.e(ApplicationManager.class.getName(), fe.getLocalizedMessage());
                        }
                    }

                    public View getView(int position, View convertView,
                                        ViewGroup parent) {
                        View view;
                        if (convertView == null) {
                            view = ApplicationManager.this.getLayoutInflater().inflate(R.layout.app_item, parent,
                                    false);
                        } else {
                            view = convertView;
                        }

                        if (position >= getCount()) {
                            return view;
                        }

                        if (view.getTag() == null) {
                            view.setTag(new AppViewHolder(view));
                        }

                        AppViewHolder viewHolder = (AppViewHolder) view.getTag();
                        SharedPreferences prefStore =
                                getSharedPreferences(APPLICATION_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

                        AppInfoHolder itm = getItem(position);

                        if (itm.appInfo == null) {
                            // process tag item
                            viewHolder.txt_name.setText(itm.label);
                            viewHolder.txt_name.setTextColor(Color.WHITE);
                            viewHolder.img_type.setVisibility(View.VISIBLE);
                            viewHolder.img_type.setTag(System.currentTimeMillis());

                            Drawable iconDrawable = itm.icon == null ? null : itm.icon.get();
                            if (iconDrawable == null) {
                                // try reload icon for tags
                                iconDrawable = reloadIcon(ApplicationManager.this, itm);
                            }

                            viewHolder.img_type.setImageDrawable(iconDrawable);
                            if (Util.getBooleanOption(prefStore, PREF_KEY_SHOW_COUNT)) {
                                viewHolder.txt_ver.setVisibility(View.VISIBLE);
                                viewHolder.txt_ver.setText("( " + itm.codeSize + " )"); //$NON-NLS-1$ //$NON-NLS-2$
                            } else {
                                viewHolder.txt_ver.setVisibility(View.GONE);
                            }

                            viewHolder.txt_size.setVisibility(View.GONE);
                            viewHolder.txt_time.setVisibility(View.GONE);
                            viewHolder.ckb_app.setVisibility(View.GONE);
                            viewHolder.imgLock.setVisibility(View.GONE);
                            viewHolder.imgSdcard.setVisibility(View.GONE);
                            viewHolder.imgAdmob.setVisibility(View.GONE);
                            viewHolder.imgAuto.setVisibility(View.GONE);
                            viewHolder.imgWear.setVisibility(View.GONE);
                            viewHolder.tagPane.setVisibility(View.GONE);

                            return view;
                        }

                        // process app item
                        String lb = itm.label == null ? itm.appInfo.packageName : itm.label.toString();
                        if ((itm.appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                                && ((currentType != -1 && currentType != APP_TYPE_SYS && currentType != APP_TYPE_USER) || (currentType == -1 && Util
                                .getIntOption(prefStore, PREF_KEY_FILTER_APP_TYPE, APP_TYPE_ALL) == APP_TYPE_ALL))) {
                            lb += " *"; //$NON-NLS-1$
                        }
                        viewHolder.txt_name.setText(lb);

                        String tagKeySuffix = getTagSettingSuffixKey(prefStore, currentTag, currentType);
                        boolean showIcon = getTagBooleanOption(prefStore, PREF_KEY_SHOW_ICON, tagKeySuffix);

                        if (showIcon) {
                            viewHolder.img_type.setVisibility(View.VISIBLE);
                            viewHolder.img_type.setOnClickListener(iconClickListener);

                            updateAppIcon(viewHolder.img_type, itm, false);
                        } else {
                            viewHolder.img_type.setVisibility(View.GONE);
                        }

                        if (getTagBooleanOption(prefStore, PREF_KEY_SHOW_BACKUP_STATE, tagKeySuffix)) {
                            switch (itm.backupState) {
                                case 1:
                                    viewHolder.txt_name.setTextColor(Color.YELLOW);
                                    break;
                                case 2:
                                    viewHolder.txt_name.setTextColor(getResources().getColor(R.color.nephritis));
                                    break;
                                case 3:
                                    viewHolder.txt_name.setTextColor(getResources().getColor(R.color.peter_river));
                                    break;
                                default:
                                    viewHolder.txt_name.setTextColor(Color.WHITE);
                                    break;
                            }
                        } else {
                            viewHolder.txt_name.setTextColor(Color.WHITE);
                        }

                        viewHolder.txt_ver.setVisibility(View.VISIBLE);
                        viewHolder.txt_ver.setText(itm.version);

                        if (getTagBooleanOption(prefStore, PREF_KEY_SHOW_SIZE, tagKeySuffix)) {
                            viewHolder.txt_size.setVisibility(View.VISIBLE);

                            if (itm.size != null) {
                                viewHolder.txt_size.setText(itm.size);
                            } else {
                                viewHolder.txt_size.setText(ResUtil.getString(ApplicationManager.this,
                                        R.string.computing));
                            }
                        } else {
                            viewHolder.txt_size.setVisibility(View.GONE);
                        }

                        if (getTagBooleanOption(prefStore, PREF_KEY_SHOW_DATE, tagKeySuffix)) {
                            viewHolder.txt_time.setVisibility(View.VISIBLE);

                            if (itm.installDateString != null) {
                                viewHolder.txt_time.setText(itm.installDateString);
                            } else {
                                viewHolder.txt_time.setText(ResUtil.getString(ApplicationManager.this,
                                        R.string.unknown));
                            }
                        } else {
                            viewHolder.txt_time.setVisibility(View.GONE);
                        }

                        if (itm.tags == null) {
                            viewHolder.tagPane.setVisibility(View.GONE);
                        } else {
                            viewHolder.tagPane.setVisibility(View.VISIBLE);
                            viewHolder.tagPane.removeAllViews();

                            LayoutInflater inflator = getLayoutInflater();

                            for (String tag : itm.tags) {
                                if (!tag.equals(currentTag)) {
                                    TextView tagView =
                                            (TextView) inflator.inflate(R.layout.tag_item, viewHolder.tagPane, false);
                                    tagView.setText(tag);

                                    viewHolder.tagPane.addView(tagView);
                                }
                            }
                        }

                        // for SDK14+, we use icon to show the selection state
                        viewHolder.ckb_app.setVisibility((showIcon && Util.SDK_VER >= 14) ? View.GONE : View.VISIBLE);
                        viewHolder.ckb_app.setTag(position);
                        viewHolder.ckb_app.setChecked(itm.checked);
                        viewHolder.ckb_app.setOnCheckedChangeListener(checkListener);

                        viewHolder.imgLock.setVisibility(itm.isPrivate ? View.VISIBLE : View.GONE);

                        if (getTagBooleanOption(prefStore, PREF_KEY_SHOW_INSTALL_LOCATION, tagKeySuffix)) {
                            boolean onSD = (itm.appInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0;

                            viewHolder.imgSdcard.setVisibility((onSD || itm.supportSD) ? View.VISIBLE : View.GONE);

                            if (onSD) {
                                viewHolder.imgSdcard.setImageDrawable(sdcardImg);
                            } else {
                                viewHolder.imgSdcard.setImageDrawable(sdcardDimImg);
                            }
                        } else {
                            viewHolder.imgSdcard.setVisibility(View.GONE);
                        }

                        viewHolder.imgAdmob.setVisibility((itm.hasAD && getTagBooleanOption(prefStore,
                                PREF_KEY_SHOW_ADMOB_STATE, tagKeySuffix)) ? View.VISIBLE : View.GONE);

                        viewHolder.imgAuto.setVisibility((itm.autoStart && getTagBooleanOption(prefStore,
                                PREF_KEY_SHOW_AUTOSTART_STATE, tagKeySuffix)) ? View.VISIBLE : View.GONE);

                        viewHolder.imgWear.setVisibility((itm.supportWearable && getTagBooleanOption(prefStore,
                                PREF_KEY_SHOW_WEARABLE_STATE, tagKeySuffix)) ? View.VISIBLE : View.GONE);

                        return view;
                    }
                };

        getListView().setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        ((ArrayAdapter<AppInfoHolder>) getListView().getAdapter()).clear();
        appCache.clear();

        super.onDestroy();
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

        // from Oreo, we can only read application size info form Usage Stats
        if (Util.SDK_VER >= 26 && !Util.isUsageStatsEnabled(this)) {
            OnClickListener listener = new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Util.safeStartActivity(ApplicationManager.this, new Intent("android.settings.USAGE_ACCESS_SETTINGS"), false);
                }
            };

            Util.newAlertDialogBuilder(ApplicationManager.this).setTitle(ResUtil.getString(ApplicationManager.this, R.string.prompt))
                    .setMessage(ResUtil.getString(ApplicationManager.this, R.string.enable_usagestats_size_prompt))
                    .setPositiveButton(android.R.string.yes, listener)
                    .setNegativeButton(android.R.string.no, null).create().show();
        }

        internalStart();
    }

    @Override
    protected void onStop() {
        if (stopCheck) {
            stopCheck = false;
        }

        if (sizeUpdater != null) {
            sizeUpdater.aborted = true;
            sizeUpdater = null;
        }

        if (resUpdater != null) {
            resUpdater.aborted = true;
            resUpdater = null;
        }

        if (backupUpdater != null) {
            backupUpdater.aborted = true;
            backupUpdater = null;
        }

        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFY_EXPORT_FINISHED);

        if (progress != null) {
            Util.safeDismissDialog(progress);
            progress = null;
        }

        super.onStop();
    }

    @Override
    protected void onResume() {
        aborted = false;

        super.onResume();

        if (stopCheck) {
            stopCheck = false;

            // need re-init, since for some pad devices, the onStart() may not be
            // called as expected
            internalStart();
        }
    }

    @Override
    protected void onPause() {
        aborted = true;

        handler.removeMessages(MSG_CHECK_FORCE_COMPRESSION);
        handler.removeMessages(MSG_CONTENT_READY);

        super.onPause();
    }

    private void internalStart() {
        if (!Util.getBooleanOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_USE_TAG_VIEW)
                || currentLayer == LAYER_ITEM) {
            loadApps(currentTag, currentType);
        } else {
            loadLayer();
        }
    }

    private void loadLayer() {
        currentLayer = LAYER_TAG;
        currentType = -1;
        currentTag = null;

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
                        getSharedPreferences(APPLICATION_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

                boolean showCount = Util.getBooleanOption(prefStore, PREF_KEY_SHOW_COUNT);

                int sysAppCount = 0;
                int userAppCount = 0;

                List<ApplicationInfo> allApps = null;

                if (showCount) {
                    allApps = getPackageManager().getInstalledApplications(0);

                    List<ApplicationInfo> sysApps = filterApps(allApps, null, null, APP_TYPE_SYS);

                    if (sysApps != null) {
                        sysAppCount = sysApps.size();
                    }

                    if (allApps != null) {
                        userAppCount = allApps.size() - sysAppCount;
                    }
                }

                List<String> customTags =
                        Util.getStringList4JsonString(Util.getStringOption(prefStore, PREF_KEY_CUSTOM_TAGS,
                                null));

                String tag_untagged = ResUtil.getString(ApplicationManager.this, R.string.untagged);
                String tag_recent = ResUtil.getString(ApplicationManager.this, R.string.recent);
                String tag_user = ResUtil.getString(ApplicationManager.this, R.string.user_apps);
                String tag_sys = ResUtil.getString(ApplicationManager.this, R.string.sys_apps);
                String tag_all = ResUtil.getString(ApplicationManager.this, R.string.all_apps);

                Set<String> visTags =
                        Util.getVisibleTags(prefStore, customTags, tag_untagged, tag_recent, tag_user, tag_sys,
                                tag_all);

                ArrayList<AppInfoHolder> tags = new ArrayList<AppInfoHolder>();

                AppInfoHolder tag;

                if (visTags.contains(tag_all)) {
                    tag = new AppInfoHolder();
                    tag.label = tag_all;
                    tag.versionCode = APP_TYPE_ALL;
                    tag.codeSize = sysAppCount + userAppCount;
                    tag.icon = new SoftReference<Drawable>(getResources().getDrawable(R.drawable.tag_orange));
                    tags.add(tag);
                }

                if (visTags.contains(tag_sys)) {
                    tag = new AppInfoHolder();
                    tag.label = tag_sys;
                    tag.versionCode = APP_TYPE_SYS;
                    tag.codeSize = sysAppCount;
                    tag.icon = new SoftReference<Drawable>(getResources().getDrawable(R.drawable.tag_blue));
                    tags.add(tag);
                }

                if (visTags.contains(tag_user)) {
                    tag = new AppInfoHolder();
                    tag.label = tag_user;
                    tag.versionCode = APP_TYPE_USER;
                    tag.codeSize = userAppCount;
                    tag.icon = new SoftReference<Drawable>(getResources().getDrawable(R.drawable.tag_green));
                    tags.add(tag);
                }

                if (visTags.contains(tag_recent)) {
                    tag = new AppInfoHolder();
                    tag.label = tag_recent;
                    tag.versionCode = APP_TYPE_RECENT;
                    tag.codeSize = showCount ? filterApps(allApps, null, null, APP_TYPE_RECENT).size() : 0;
                    tag.icon = new SoftReference<Drawable>(getResources().getDrawable(R.drawable.tag_pink));
                    tags.add(tag);
                }

                if (visTags.contains(tag_untagged)) {
                    tag = new AppInfoHolder();
                    tag.label = tag_untagged;
                    tag.versionCode = APP_TYPE_UNTAGGED;
                    tag.codeSize =
                            showCount ? filterApps(allApps, null, customTags, APP_TYPE_UNTAGGED).size() : 0;
                    tag.icon = new SoftReference<Drawable>(getResources().getDrawable(R.drawable.tag_cream));
                    tags.add(tag);
                }

                if (customTags != null) {
                    customTags.retainAll(visTags);

                    for (int i = 0; i < customTags.size(); i++) {
                        String ct = customTags.get(i);

                        tag = new AppInfoHolder();
                        tag.label = ct;
                        tag.versionCode = APP_TYPE_CUSTOM;
                        tag.codeSize = showCount ? filterApps(allApps, ct, null, APP_TYPE_CUSTOM).size() : 0;
                        tag.icon = new SoftReference<Drawable>(getResources().getDrawable(R.drawable.tag_pink));
                        tags.add(tag);
                    }
                }

                handler.sendMessage(handler.obtainMessage(MSG_UPDATE, 0, 0, tags));
            }
        }, "LayerUpdater").start(); //$NON-NLS-1$
    }

    void loadApps(final String tag, int appType) {
        currentLayer = LAYER_ITEM;
        currentType = appType;
        currentTag = tag;

        if (appType == -1) {
            appType =
                    Util.getIntOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_FILTER_APP_TYPE,
                            APP_TYPE_ALL);
        }

        if (progress != null) {
            Util.safeDismissDialog(progress);
        }
        progress = new ProgressDialog(this);
        progress.setMessage(ResUtil.getString(this, R.string.loading));
        progress.setIndeterminate(true);
        progress.show();

        final int targetType = appType;

        new Thread(new Runnable() {

            public void run() {
                SharedPreferences prefStore =
                        getSharedPreferences(APPLICATION_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

                PackageManager pm = getPackageManager();

                // TODO use global setting here, may need to force load to
                // handle tag specific setting
                boolean needAdMobState = Util.getBooleanOption(prefStore, PREF_KEY_SHOW_ADMOB_STATE);
                boolean needWearableState = Util.getBooleanOption(prefStore, PREF_KEY_SHOW_WEARABLE_STATE);

                List<ApplicationInfo> allApps = pm.getInstalledApplications((needAdMobState || needWearableState)
                        ? PackageManager.GET_META_DATA : 0);

                // TODO use global setting here, may need to force load to
                // handle tag specific setting
                boolean needPermission = Util.getBooleanOption(prefStore, PREF_KEY_SHOW_AUTOSTART_STATE);

                List<String> customTags =
                        Util.getStringList4JsonString(Util.getStringOption(prefStore, PREF_KEY_CUSTOM_TAGS,
                                null));

                List<ApplicationInfo> filteredApps = filterApps(allApps, tag, customTags, targetType);

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

                ArrayList<AppInfoHolder> dataList = new ArrayList<AppInfoHolder>();

                for (int i = 0, size = filteredApps.size(); i < size; i++) {
                    ApplicationInfo info = filteredApps.get(i);

                    AppInfoHolder holder = new AppInfoHolder();
                    holder.appInfo = info;

                    try {
                        PackageInfo pi =
                                pm.getPackageInfo(info.packageName, needPermission ? PackageManager.GET_PERMISSIONS
                                        : 0);

                        holder.version = versionPrefix + " " //$NON-NLS-1$
                                + (pi.versionName == null ? String.valueOf(pi.versionCode) : pi.versionName);

                        holder.versionCode = pi.versionCode;

                        if (info.sourceDir != null) {
                            File srcFile = new File(info.sourceDir);

                            if (info.sourceDir.contains("/data/app-private") //$NON-NLS-1$
                                    || !srcFile.canRead()) {
                                holder.isPrivate = true;
                            }
                        }

                        if (info.metaData != null) {
                            if (!TextUtils.isEmpty(info.metaData.getString("ADMOB_PUBLISHER_ID"))) {
                                holder.hasAD = true;
                            }

                            if (info.metaData.getInt("com.google.android.wearable.beta.app") != 0) {
                                holder.supportWearable = true;
                            }
                        }

                        if (pi.requestedPermissions != null) {
                            for (String perm : pi.requestedPermissions) {
                                if (permission.RECEIVE_BOOT_COMPLETED.equals(perm)) {
                                    holder.autoStart = true;
                                    break;
                                }
                            }
                        }

                        int loc = Util.getInstallLocation(pi);
                        holder.supportSD = loc == 0 || loc == 2;

                        if (customTags != null) {
                            resetTags(holder, linkMap);
                        }
                    } catch (NameNotFoundException e) {
                        Log.e(ApplicationManager.class.getName(), e.getLocalizedMessage(), e);
                    }

                    dataList.add(holder);
                }

                handler.sendMessage(handler.obtainMessage(MSG_UPDATE, 1, 1, dataList));
            }
        }, "MainUpdater").start(); //$NON-NLS-1$
    }

    List<ApplicationInfo> filterApps(List<ApplicationInfo> apps, String tag, List<String> customTags,
                                     int type) {
        if (apps == null || apps.size() == 0) {
            return apps;
        }

        if (type == APP_TYPE_SYS) {
            List<ApplicationInfo> sysApps = new ArrayList<ApplicationInfo>();

            for (int i = 0, size = apps.size(); i < size; i++) {
                ApplicationInfo ai = apps.get(i);

                if (ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    sysApps.add(ai);
                }
            }

            return sysApps;
        } else if (type == APP_TYPE_USER) {
            List<ApplicationInfo> userApps = new ArrayList<ApplicationInfo>();

            for (int i = 0, size = apps.size(); i < size; i++) {
                ApplicationInfo ai = apps.get(i);

                if (ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    userApps.add(ai);
                }
            }

            return userApps;
        } else if (type == APP_TYPE_RECENT) {
            List<ApplicationInfo> recentApps = new ArrayList<ApplicationInfo>();

            int days = Util.getIntOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_RECENT_SCOPE, 3);
            long range = days * 24 * 60 * 60 * 1000L;
            long current = new Date().getTime();

            for (int i = 0, size = apps.size(); i < size; i++) {
                ApplicationInfo ai = apps.get(i);

                if (ai != null && ai.sourceDir != null) {
                    long installDate = new File(ai.sourceDir).lastModified();

                    if (current - installDate < range) {
                        recentApps.add(ai);
                    }
                }
            }

            return recentApps;
        } else if (type == APP_TYPE_UNTAGGED) {
            SharedPreferences prefStore =
                    getSharedPreferences(APPLICATION_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            Set<String> allLinks = null;

            if (customTags != null) {
                allLinks = new HashSet<String>();

                for (String ctag : customTags) {
                    Set<String> links = Util.getCustomTagLinks(prefStore, ctag);

                    if (links != null && links.size() > 0) {
                        allLinks.addAll(links);
                    }
                }
            }

            if (allLinks == null || allLinks.size() == 0) {
                return apps;
            } else {
                List<ApplicationInfo> custApps = new ArrayList<ApplicationInfo>();

                for (int i = 0, size = apps.size(); i < size; i++) {
                    ApplicationInfo ai = apps.get(i);

                    if (ai != null && !allLinks.contains(ai.packageName)) {
                        custApps.add(ai);
                    }
                }

                return custApps;
            }
        } else if (type == APP_TYPE_CUSTOM) {
            List<ApplicationInfo> custApps = new ArrayList<ApplicationInfo>();

            Set<String> links =
                    Util.getCustomTagLinks(
                            getSharedPreferences(APPLICATION_MANAGER_STORE_NAME, Context.MODE_PRIVATE), tag);

            if (links != null) {
                for (int i = 0, size = apps.size(); i < size; i++) {
                    ApplicationInfo ai = apps.get(i);

                    if (ai != null && links.contains(ai.packageName)) {
                        custApps.add(ai);
                    }
                }
            }

            return custApps;
        }

        return apps;
    }

    static void resetTags(AppInfoHolder holder, Map<String, Set<String>> linkMap) {
        ArrayList<String> tags = null;

        for (Entry<String, Set<String>> ent : linkMap.entrySet()) {
            Set<String> links = ent.getValue();

            if (links != null && links.contains(holder.appInfo.packageName)) {
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

    static int getTagIntOption(Context ctx, String storeName, String key, int defValue,
                               String tagName, int tagType) {
        SharedPreferences store = ctx.getSharedPreferences(storeName, Context.MODE_PRIVATE);

        return getTagIntOption(store, key, defValue, tagName, tagType);
    }

    static int getTagIntOption(SharedPreferences store, String key, int defValue,
                               String tagName, int tagType) {
        String suffix = getTagSettingSuffixKey(store, tagName, tagType);

        if (suffix != null) {
            String comboKey = key + suffix;

            if (store.contains(comboKey)) {
                return Util.getIntOption(store, comboKey, defValue);
            }
        }

        return Util.getIntOption(store, key, defValue);
    }

    private static void setTagIntOption(Context ctx, EditorState es, String key, int value,
                                        int defValue, String tagName, int tagType) {
        SharedPreferences store = ctx.getSharedPreferences(es.storeName, Context.MODE_PRIVATE);

        String suffix = getTagSettingSuffixKey(store, tagName, tagType);

        if (suffix != null) {
            String comboKey = key + suffix;

            // use a dummy default value to ensure the setting is always
            // written.
            Util.updateIntOption(es, comboKey, value, -1);

            return;
        }

        Util.updateIntOption(es, key, value, defValue);
    }

    static boolean getTagBooleanOption(Context ctx, String storeName, String key,
                                       String tagName, int tagType) {
        SharedPreferences store = ctx.getSharedPreferences(storeName, Context.MODE_PRIVATE);

        return getTagBooleanOption(store, key, tagName, tagType);
    }

    private static boolean getTagBooleanOption(SharedPreferences store, String key, String tagName,
                                               int tagType) {
        String suffix = getTagSettingSuffixKey(store, tagName, tagType);

        return getTagBooleanOption(store, key, suffix);
    }

    static boolean getTagBooleanOption(SharedPreferences store, String key, String suffix) {
        if (suffix != null) {
            String comboKey = key + suffix;

            if (store.contains(comboKey)) {
                return Util.getBooleanOption(store, comboKey);
            }
        }

        return Util.getBooleanOption(store, key);
    }

    private static void setTagBooleanOption(Context ctx, EditorState es, String key, boolean value,
                                            boolean defValue, String tagName, int tagType) {
        SharedPreferences store = ctx.getSharedPreferences(es.storeName, Context.MODE_PRIVATE);

        String suffix = getTagSettingSuffixKey(store, tagName, tagType);

        if (suffix != null) {
            String comboKey = key + suffix;

            // always write the setting to overwrite the global one.
            Util.updateBooleanOptionForce(es, comboKey, value);

            return;
        }

        Util.updateBooleanOption(es, key, value, defValue);
    }

    static String getTagSettingSuffixKey(SharedPreferences store, String tagName, int tagType) {
        if (Util.SDK_VER > 10 && Util.getBooleanOption(store, PREF_KEY_USE_TAG_VIEW)
                && Util.getBooleanOption(store, PREF_KEY_USE_TAG_SETTINGS)) {
            if (tagType != APP_TYPE_CUSTOM) {
                tagName = "<" + tagType + ">"; //$NON-NLS-1$ //$NON-NLS-2$
            }

            if (tagName != null) {
                return ":" + tagName; //$NON-NLS-1$
            }
        }

        return null;
    }

    private static boolean ensureSDCard() {
        String state = Environment.getExternalStorageState();

        return Environment.MEDIA_MOUNTED.equals(state);
    }

    protected static List<AppInfoHolder> getSelected(ListView lstApps) {
        int count = lstApps.getCount();

        ArrayList<AppInfoHolder> apps = new ArrayList<AppInfoHolder>();

        for (int i = 0; i < count; i++) {
            AppInfoHolder holder = (AppInfoHolder) lstApps.getItemAtPosition(i);

            if (holder.checked) {
                apps.add(holder);
            }
        }

        return apps;
    }

    int getSelectedCount(ListView lstApps) {
        int count = lstApps.getCount();

        int s = 0;

        for (int i = 0; i < count; i++) {
            AppInfoHolder holder = (AppInfoHolder) lstApps.getItemAtPosition(i);

            if (holder.checked) {
                s++;
            }
        }

        return s;
    }

    void export(final List<AppInfoHolder> apps) {
        if (apps == null || apps.isEmpty()) {
            Util.shortToast(this, R.string.no_app_selected);
            return;
        }

        if (progress != null) {
            Util.safeDismissDialog(progress);
        }
        progress = new ProgressDialog(this);
        progress.setMessage(ResUtil.getString(this, R.string.start_exporting));
        progress.setIndeterminate(false);
        progress.setCancelable(false);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(apps.size());

        progress.show();

        new Thread(new Runnable() {

            public void run() {
                String exportFolder =
                        Util.getStringOption(ApplicationManager.this, APPLICATION_MANAGER_STORE_NAME,
                                PREF_KEY_APP_EXPORT_DIR, DEFAULT_EXPORT_FOLDER);

                File output = new File(exportFolder);

                if (!output.exists()) {
                    if (!output.mkdirs()) {
                        handler.sendMessage(Message.obtain(
                                handler,
                                MSG_COPING_ERROR,
                                0,
                                0,
                                ResUtil.getString(ApplicationManager.this, R.string.error_create_folder,
                                        output.getAbsolutePath())));

                        return;
                    }
                }

                File sysoutput = new File(output, SYS_APP);

                if (!sysoutput.exists()) {
                    if (!sysoutput.mkdirs()) {
                        handler.sendMessage(Message.obtain(
                                handler,
                                MSG_COPING_ERROR,
                                0,
                                0,
                                ResUtil.getString(ApplicationManager.this, R.string.error_create_folder,
                                        sysoutput.getAbsolutePath())));

                        return;
                    }
                }

                File useroutput = new File(output, USER_APP);

                if (!useroutput.exists()) {
                    if (!useroutput.mkdirs()) {
                        handler.sendMessage(Message.obtain(
                                handler,
                                MSG_COPING_ERROR,
                                0,
                                0,
                                ResUtil.getString(ApplicationManager.this, R.string.error_create_folder,
                                        useroutput.getAbsolutePath())));

                        return;
                    }
                }

                final Map<AppInfoHolder, String> skipped = new HashMap<AppInfoHolder, String>();
                int succeed = 0;

                int stripLength =
                        Util.getBooleanOption(ApplicationManager.this, APPLICATION_MANAGER_STORE_NAME,
                                PREF_KEY_KEEP_APP_VERSION, true) ? (versionPrefix.length() + 1) : -1;

                for (int i = 0, size = apps.size(); i < size; i++) {
                    AppInfoHolder holder = apps.get(i);

                    ApplicationInfo app = holder.appInfo;

                    String src = app.sourceDir;

                    boolean toSkip = false;

                    if (src != null) {
                        File srcFile = new File(src);

                        if (src.contains("/data/app-private") //$NON-NLS-1$
                                || !srcFile.canRead()) {
                            toSkip = true;
                        }

                        if (app.packageName != null) {
                            String appName = makeFileName(holder, stripLength);

                            File targetOutput = useroutput;

                            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                                targetOutput = sysoutput;
                            }

                            File destFile = new File(targetOutput, appName);

                            if (toSkip) {
                                // record the info and skip here
                                skipped.put(holder, destFile.getAbsolutePath());

                                continue;
                            }

                            handler.sendMessage(Message.obtain(handler, MSG_COPING, 0, 0, appName));

                            try {
                                copyFile(srcFile, destFile, appName, handler);

                                succeed++;
                            } catch (Exception e) {
                                Log.e(ApplicationManager.class.getName(), e.getLocalizedMessage(), e);

                                handler.sendMessage(Message.obtain(handler, MSG_COPING_ERROR, 1, 0,
                                        e.getLocalizedMessage()));

                                continue;
                            }
                        }
                    }
                }

                handler.sendMessage(Message.obtain(handler, MSG_COPING_FINISHED, succeed, skipped.size(),
                        apps));

                if (skipped.size() > 0) {
                    runOnUiThread(new Runnable() {

                        public void run() {
                            OnClickListener listener = new OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    new Thread("RootWorker") { //$NON-NLS-1$

                                        public void run() {
                                            if (!RootUtil.rootAvailable()) {
                                                handler.sendMessage(handler.obtainMessage(MSG_TOAST,
                                                        ResUtil.getString(ApplicationManager.this, R.string.root_obtain_fail)));

                                                return;
                                            }

                                            handler.sendMessage(handler.obtainMessage(MSG_TOAST,
                                                    ResUtil.getString(ApplicationManager.this, R.string.root_obtain_succ)));

                                            List<String> commands = new ArrayList<String>();

                                            for (Entry<AppInfoHolder, String> ent : skipped.entrySet()) {
                                                AppInfoHolder holder = ent.getKey();

                                                ApplicationInfo app = holder.appInfo;

                                                if (app.sourceDir == null) {
                                                    continue;
                                                }

                                                String src = new File(app.sourceDir).getAbsolutePath();
                                                String des = ent.getValue();

                                                if (des != null && des.length() > 0) {
                                                    commands.add("cp \"" //$NON-NLS-1$
                                                            + src + "\" \"" //$NON-NLS-1$
                                                            + des + "\""); //$NON-NLS-1$
                                                }
                                            }

                                            if (commands.size() > 0) {
                                                handler.sendMessage(handler.obtainMessage(MSG_SHOW_PROGRESS,
                                                        ResUtil.getString(ApplicationManager.this, R.string.root_copying)));

                                                List<String> result =
                                                        RootUtil.runRoot(commands.toArray(new String[commands.size()]));

                                                if (result == null) {
                                                    handler.sendMessage(handler.obtainMessage(MSG_TOAST,
                                                            ResUtil.getString(ApplicationManager.this, R.string.root_copy_fail)));
                                                } else {
                                                    handler.sendMessage(handler.obtainMessage(MSG_ROOT_COPY_FINISHED,
                                                            new ArrayList<AppInfoHolder>(skipped.keySet())));
                                                }

                                                handler.sendMessage(handler.obtainMessage(MSG_DISMISS_PROGRESS));
                                            } else {
                                                handler.sendMessage(handler.obtainMessage(MSG_TOAST,
                                                        ResUtil.getString(ApplicationManager.this, R.string.root_copy_skip)));
                                            }
                                        }
                                    }.start();
                                }
                            };

                            Util.newAlertDialogBuilder(ApplicationManager.this)
                                    .setTitle(ResUtil.getString(ApplicationManager.this, R.string.prompt))
                                    .setMessage(ResUtil.getString(ApplicationManager.this, R.string.root_copy_prompt))
                                    .setPositiveButton(android.R.string.ok, listener)
                                    .setNegativeButton(android.R.string.cancel, null).create().show();
                        }
                    });
                }
            }
        }).start();
    }

    static void copyFile(File src, File dest, String appName, Handler handler) throws IOException {
        InputStream fis = new BufferedInputStream(new FileInputStream(src), 8192 * 4);
        OutputStream fos = new BufferedOutputStream(new FileOutputStream(dest), 8192 * 4);

        byte[] buf = new byte[8192 * 2];

        long writtenSize = 0;
        long totalSize = src.length();
        long segSize = 1024 * 1024 * 3; // 3M

        boolean reportSteps = totalSize > segSize * 3; // >9M
        int step = 0;

        int i;
        while ((i = fis.read(buf)) != -1) {
            fos.write(buf, 0, i);

            if (reportSteps) {
                writtenSize += i;

                if (writtenSize > segSize) {
                    step++;

                    writtenSize -= segSize;

                    handler.sendMessage(handler.obtainMessage(MSG_COPING,
                            (int) ((segSize * step) * 100 / totalSize), 0, appName));
                }
            }
        }

        fis.close();
        fos.close();
    }

    static String makeFileName(AppInfoHolder holder, int stripLength) {
        if (stripLength < 0) {
            return holder.appInfo.packageName + ".apk"; //$NON-NLS-1$
        }

        String ver = holder.version;

        ver = ver.length() > stripLength ? ver.substring(stripLength) : null;

        if (ver == null) {
            return holder.appInfo.packageName + ".apk"; //$NON-NLS-1$
        }

        int verLength = Math.min(MAX_VER_LENGTH, ver.length());

        StringBuilder fname = new StringBuilder(holder.appInfo.packageName).append('-');

        for (int i = 0; i < verLength; i++) {
            char c = ver.charAt(i);

            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || (c == '.')
                    || (c == '-')) {
                fname.append(c);
            } else {
                fname.append('_');
            }
        }

        fname.append(".apk"); //$NON-NLS-1$

        return fname.toString();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            if (currentLayer == LAYER_ITEM
                    && Util.getBooleanOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_USE_TAG_VIEW)) {
                loadLayer();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PREFERENCES && data != null) {
            EditorState es = Util.beginEditOption(this, APPLICATION_MANAGER_STORE_NAME);

            Util.updateStringOption(data, es, PREF_KEY_APP_EXPORT_DIR);

            Util.updateIntOption(data, es, PREF_KEY_FILTER_APP_TYPE, APP_TYPE_ALL);
            Util.updateIntOption(data, es, PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_NAME);
            Util.updateIntOption(data, es, PREF_KEY_SORT_DIRECTION, ORDER_ASC);
            Util.updateIntOption(data, es, PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_NAME);
            Util.updateIntOption(data, es, PREF_KEY_SECONDARY_SORT_DIRECTION, ORDER_ASC);
            Util.updateIntOption(data, es, PREF_KEY_DEFAULT_TAP_ACTION, ACTION_TOGGLE_SELECTION);

            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_SIZE);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_DATE);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_ICON);
            Util.updateBooleanOption(data, es, PREF_KEY_KEEP_APP_VERSION, true);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_BACKUP_STATE);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_ADMOB_STATE);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_AUTOSTART_STATE);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_WEARABLE_STATE);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_INSTALL_LOCATION);

            if (Util.updateBooleanOption(data, es, PREF_KEY_USE_TAG_VIEW)) {
                currentLayer = LAYER_TAG;
                currentType = -1;
                currentTag = null;
            }

            Util.updateBooleanOption(data, es, PREF_KEY_USE_TAG_SETTINGS);
            Util.updateBooleanOption(data, es, PREF_KEY_SHOW_COUNT);
            Util.updateStringOption(data, es, PREF_KEY_CUSTOM_TAGS);
            Util.updateStringOption(data, es, PREF_KEY_TAG_VISIBILITY);
            Util.updateIntOption(data, es, PREF_KEY_RECENT_SCOPE, 3);

            Util.endEditOption(es);

            ArrayList<String> removedTags = data.getStringArrayListExtra(KEY_REMOVED_TAGS);

            if (removedTags != null) {
                SharedPreferences prefStore =
                        getSharedPreferences(APPLICATION_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

                for (String tag : removedTags) {
                    Util.updateCustomTagLinks(this, prefStore, tag, null);
                }
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isTagLayer = currentLayer == LAYER_TAG;

        menu.findItem(MI_DELETE).setVisible(!isTagLayer);
        menu.findItem(MI_SHARE).setVisible(!isTagLayer);
        menu.findItem(MI_TAGS).setVisible(!isTagLayer);

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem mi = menu.add(Menu.NONE, MI_DELETE, Menu.NONE, ResUtil.getString(this, R.string.uninstall));
        mi.setIcon(android.R.drawable.ic_menu_delete);

        mi = menu.add(Menu.NONE, MI_SHARE, Menu.NONE, ResUtil.getString(this, R.string.share));
        mi.setIcon(android.R.drawable.ic_menu_share);

        mi = menu.add(Menu.NONE, MI_TAGS, Menu.NONE, ResUtil.getString(this, R.string.tags));
        mi.setIcon(android.R.drawable.ic_menu_agenda);

        mi = menu.add(Menu.NONE, MI_REVERT, Menu.NONE, ResUtil.getString(this, R.string.restore));
        mi.setIcon(android.R.drawable.ic_menu_revert);

        mi = menu.add(Menu.NONE, MI_CLEAR_CACHE, Menu.NONE, ResUtil.getString(this, R.string.clean));
        mi.setIcon(android.R.drawable.ic_menu_close_clear_cancel);

        if (Util.getSettingsIntent(getPackageManager(), "com.android.settings.UsageStats") != null) //$NON-NLS-1$
        {
            mi = menu.add(Menu.NONE, MI_USAGE_STATS, Menu.NONE, ResUtil.getString(this, R.string.usage_stats));
            mi.setIcon(android.R.drawable.ic_menu_recent_history);
        }

        mi = menu.add(Menu.NONE, MI_PREFERENCE, Menu.NONE, ResUtil.getString(this, R.string.preference));
        mi.setIcon(android.R.drawable.ic_menu_preferences);

        mi = menu.add(Menu.NONE, MI_EXIT, Menu.NONE, ResUtil.getString(this, R.string.exit));
        mi.setIcon(android.R.drawable.ic_menu_close_clear_cancel);

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
            hints.add(new ActionHint(ResUtil.getString(this, R.string.uninstall), MI_DELETE));
            hints.add(new ActionHint(ResUtil.getString(this, R.string.share), MI_SHARE));
            hints.add(new ActionHint(ResUtil.getString(this, R.string.tags), MI_TAGS));
        }

        hints.add(new ActionHint(ResUtil.getString(this, R.string.restore), MI_REVERT));

        hints.add(new ActionHint(ResUtil.getString(this, R.string.clean), MI_CLEAR_CACHE));

        if (Util.getSettingsIntent(getPackageManager(), "com.android.settings.UsageStats") != null) //$NON-NLS-1$
        {
            hints.add(new ActionHint(ResUtil.getString(this, R.string.usage_stats), MI_USAGE_STATS));
        }

        hints.add(new ActionHint(ResUtil.getString(this, R.string.preference), MI_PREFERENCE));
        hints.add(new ActionHint(ResUtil.getString(this, R.string.exit), MI_EXIT));

        return hints;
    }

    @Override
    public boolean onActionSelected(int action) {
        if (action == MI_PREFERENCE) {
            Intent it = new Intent(this, Util.getIntentProxyClz(AppSettings.class));

            SharedPreferences prefStore =
                    getSharedPreferences(APPLICATION_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            it.putExtra(PREF_KEY_FILTER_APP_TYPE,
                    Util.getIntOption(prefStore, PREF_KEY_FILTER_APP_TYPE, APP_TYPE_ALL));
            it.putExtra(PREF_KEY_APP_EXPORT_DIR,
                    Util.getStringOption(prefStore, PREF_KEY_APP_EXPORT_DIR, DEFAULT_EXPORT_FOLDER));
            it.putExtra(PREF_KEY_USE_TAG_VIEW, Util.getBooleanOption(prefStore, PREF_KEY_USE_TAG_VIEW));
            it.putExtra(PREF_KEY_USE_TAG_SETTINGS,
                    Util.getBooleanOption(prefStore, PREF_KEY_USE_TAG_SETTINGS));
            it.putExtra(PREF_KEY_SHOW_COUNT, Util.getBooleanOption(prefStore, PREF_KEY_SHOW_COUNT));
            it.putExtra(PREF_KEY_CUSTOM_TAGS, Util.getStringOption(prefStore, PREF_KEY_CUSTOM_TAGS, null));
            it.putExtra(PREF_KEY_TAG_VISIBILITY,
                    Util.getStringOption(prefStore, PREF_KEY_TAG_VISIBILITY, null));
            it.putExtra(PREF_KEY_RECENT_SCOPE, Util.getIntOption(prefStore, PREF_KEY_RECENT_SCOPE, 3));
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
            it.putExtra(PREF_KEY_SHOW_ICON, Util.getBooleanOption(prefStore, PREF_KEY_SHOW_ICON));
            it.putExtra(PREF_KEY_KEEP_APP_VERSION,
                    Util.getBooleanOption(prefStore, PREF_KEY_KEEP_APP_VERSION, true));
            it.putExtra(PREF_KEY_SHOW_BACKUP_STATE,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_BACKUP_STATE));
            it.putExtra(PREF_KEY_SHOW_ADMOB_STATE,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_ADMOB_STATE));
            it.putExtra(PREF_KEY_SHOW_AUTOSTART_STATE,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_AUTOSTART_STATE));
            it.putExtra(PREF_KEY_SHOW_WEARABLE_STATE,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_WEARABLE_STATE));
            it.putExtra(PREF_KEY_SHOW_INSTALL_LOCATION,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_INSTALL_LOCATION));
            it.putExtra(PREF_KEY_DEFAULT_TAP_ACTION,
                    Util.getIntOption(prefStore, PREF_KEY_DEFAULT_TAP_ACTION, ACTION_TOGGLE_SELECTION));

            startActivityForResult(it, REQUEST_PREFERENCES);

            return true;
        } else if (action == MI_CLEAR_CACHE) {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        doClear();
                    } else {
                        Intent it = new Intent(ApplicationManager.this, Util.getIntentProxyClz(ClearHistoryActivity.class));
                        startActivity(it);
                    }
                }
            };

            Util.newAlertDialogBuilder(this)
                    .setTitle(ResUtil.getString(this, R.string.clean))
                    .setItems(
                            new CharSequence[]{ResUtil.getString(this, R.string.cache),
                                    ResUtil.getString(this, R.string.history)}, listener).create().show();

            return true;
        } else if (action == MI_REVERT) {
            Intent it = new Intent(this, Util.getIntentProxyClz(RestoreAppActivity.class));

            SharedPreferences prefStore =
                    getSharedPreferences(APPLICATION_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            it.putExtra(KEY_RESTORE_SYS_PATH,
                    new File(Util.getStringOption(prefStore, PREF_KEY_APP_EXPORT_DIR, DEFAULT_EXPORT_FOLDER),
                            SYS_APP).getAbsolutePath());
            it.putExtra(KEY_RESTORE_USER_PATH,
                    new File(Util.getStringOption(prefStore, PREF_KEY_APP_EXPORT_DIR, DEFAULT_EXPORT_FOLDER),
                            USER_APP).getAbsolutePath());
            it.putExtra(KEY_ARCHIVE_PATH,
                    new File(Util.getStringOption(prefStore, PREF_KEY_APP_EXPORT_DIR, DEFAULT_EXPORT_FOLDER),
                            ARCHIVED).getAbsolutePath());

            startActivityForResult(it, REQUEST_RESTORE);

            return true;
        } else if (action == MI_USAGE_STATS) {
            Intent it = Util.getSettingsIntent(getPackageManager(), "com.android.settings.UsageStats"); //$NON-NLS-1$

            if (it != null) {
                Util.safeStartActivity(this, it, false);
            }

            return true;
        } else if (action == MI_SHARE) {
            doShare();

            return true;
        } else if (action == MI_DELETE) {
            doUninstall();

            return true;
        } else if (action == MI_TAGS) {
            doChangeTags(getSelected(getListView()));
        } else if (action == MI_EXIT) {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    Util.killSelf(handler, ApplicationManager.this,
                            (ActivityManager) getSystemService(ACTIVITY_SERVICE), getPackageName());
                }
            };

            Util.newAlertDialogBuilder(this).setTitle(ResUtil.getString(this, R.string.prompt))
                    .setMessage(ResUtil.getString(this, R.string.exit_prompt))
                    .setPositiveButton(android.R.string.yes, listener)
                    .setNegativeButton(android.R.string.no, null).create().show();

            return true;
        }

        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (currentLayer != LAYER_TAG) {
            int pos = ((AdapterContextMenuInfo) menuInfo).position;

            if (pos >= 0 && pos < getListView().getCount()) {
                AppInfoHolder ai = (AppInfoHolder) getListView().getItemAtPosition(pos);

                menu.setHeaderTitle(ai.label != null ? ai.label : ai.appInfo.packageName);

                menu.add(Menu.NONE, MI_MANAGE, Menu.NONE, ResUtil.getString(this, R.string.manage));
                menu.add(Menu.NONE, MI_LAUNCH, Menu.NONE, ResUtil.getString(this, R.string.run));
                menu.add(Menu.NONE, MI_SEARCH, Menu.NONE, ResUtil.getString(this, R.string.search_market));
                menu.add(Menu.NONE, MI_DETAILS, Menu.NONE, ResUtil.getString(this, R.string.details));
                menu.add(Menu.NONE, MI_TAGS, Menu.NONE, ResUtil.getString(this, R.string.tags));
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int pos = ((AdapterContextMenuInfo) item.getMenuInfo()).position;

        if (pos >= 0 && pos < getListView().getCount()) {
            AppInfoHolder ai = (AppInfoHolder) getListView().getItemAtPosition(pos);

            if (item.getItemId() == MI_MANAGE) {
                handleAction(ai, ACTION_MANAGE, null);
                return true;
            } else if (item.getItemId() == MI_LAUNCH) {
                handleAction(ai, ACTION_LAUNCH, null);
                return true;
            } else if (item.getItemId() == MI_SEARCH) {
                handleAction(ai, ACTION_SEARCH, null);
                return true;
            } else if (item.getItemId() == MI_DETAILS) {
                handleAction(ai, ACTION_DETAILS, null);
                return true;
            } else if (item.getItemId() == MI_TAGS) {
                handleAction(ai, ACTION_TAGS, null);
                return true;
            }
        }

        return false;
    }

    @Override
    public SortHint getSort(boolean primary) {
        boolean isTagLayer = currentLayer == LAYER_TAG;

        if (isTagLayer) {
            return null;
        }

        SortHint sh = new SortHint();

        sh.primary = primary;
        sh.sortByLabels =
                new String[]{ResUtil.getString(this, R.string.name),
                        ResUtil.getString(this, R.string.code_size),
                        ResUtil.getString(this, R.string.data_size),
                        ResUtil.getString(this, R.string.cache_size),
                        ResUtil.getString(this, R.string.total_size),
                        ResUtil.getString(this, R.string.installed_date),
                        ResUtil.getString(this, R.string.backup_state),
                        ResUtil.getString(this, R.string.install_location),
                        ResUtil.getString(this, R.string.admob_state),
                        ResUtil.getString(this, R.string.auto_start_state),};

        if (primary) {
            sh.sortBy =
                    getTagIntOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SORT_ORDER_TYPE,
                            ORDER_TYPE_NAME, currentTag, currentType);
            sh.ascending =
                    getTagIntOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SORT_DIRECTION, ORDER_ASC,
                            currentTag, currentType) == ORDER_ASC;
        } else {
            sh.sortBy =
                    getTagIntOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SECONDARY_SORT_ORDER_TYPE,
                            ORDER_TYPE_NAME, currentTag, currentType);
            sh.ascending =
                    getTagIntOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SECONDARY_SORT_DIRECTION,
                            ORDER_ASC, currentTag, currentType) == ORDER_ASC;
        }

        return sh;
    }

    @Override
    public void updateSort(SortHint hint) {
        EditorState es = Util.beginEditOption(this, APPLICATION_MANAGER_STORE_NAME);

        if (hint.primary) {
            setTagIntOption(this, es, PREF_KEY_SORT_ORDER_TYPE, hint.sortBy, ORDER_TYPE_NAME, currentTag,
                    currentType);
            setTagIntOption(this, es, PREF_KEY_SORT_DIRECTION, hint.ascending ? ORDER_ASC : ORDER_DESC,
                    ORDER_ASC, currentTag, currentType);
        } else {
            setTagIntOption(this, es, PREF_KEY_SECONDARY_SORT_ORDER_TYPE, hint.sortBy, ORDER_TYPE_NAME,
                    currentTag, currentType);
            setTagIntOption(this, es, PREF_KEY_SECONDARY_SORT_DIRECTION, hint.ascending ? ORDER_ASC
                    : ORDER_DESC, ORDER_ASC, currentTag, currentType);
        }

        Util.endEditOption(es);

        appCache.reOrderApps(this, currentTag, currentType);

        handler.sendMessage(handler.obtainMessage(MSG_REFRESH_PKG_LABEL, 1, 0));
    }

    @Override
    public List<ToggleHint> getToggles() {
        boolean isTagLayer = currentLayer == LAYER_TAG;

        if (isTagLayer) {
            return null;
        }

        List<ToggleHint> hints = new ArrayList<ToggleHint>();

        ToggleHint hint = new ToggleHint();
        hint.label = "SZE"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_app_size);
        hint.key = PREF_KEY_SHOW_SIZE;
        hint.value =
                getTagBooleanOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SHOW_SIZE, currentTag,
                        currentType);
        hints.add(hint);

        hint = new ToggleHint();
        hint.label = "DAT"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_app_date);
        hint.key = PREF_KEY_SHOW_DATE;
        hint.value =
                getTagBooleanOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SHOW_DATE, currentTag,
                        currentType);
        hints.add(hint);

        hint = new ToggleHint();
        hint.label = "ICN"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_app_icon);
        hint.key = PREF_KEY_SHOW_ICON;
        hint.value =
                getTagBooleanOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SHOW_ICON, currentTag,
                        currentType);
        hints.add(hint);

        hint = new ToggleHint();
        hint.label = "BKS"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_backup_state);
        hint.key = PREF_KEY_SHOW_BACKUP_STATE;
        hint.value =
                getTagBooleanOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SHOW_BACKUP_STATE,
                        currentTag, currentType);
        hints.add(hint);

        hint = new ToggleHint();
        hint.label = "ILC"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_install_location);
        hint.key = PREF_KEY_SHOW_INSTALL_LOCATION;
        hint.value =
                getTagBooleanOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SHOW_INSTALL_LOCATION,
                        currentTag, currentType);
        hints.add(hint);

        hint = new ToggleHint();
        hint.label = "ADM"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_admob_state);
        hint.key = PREF_KEY_SHOW_ADMOB_STATE;
        hint.value =
                getTagBooleanOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SHOW_ADMOB_STATE,
                        currentTag, currentType);
        hints.add(hint);

        hint = new ToggleHint();
        hint.label = "AUT"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_autostart_state);
        hint.key = PREF_KEY_SHOW_AUTOSTART_STATE;
        hint.value =
                getTagBooleanOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SHOW_AUTOSTART_STATE,
                        currentTag, currentType);
        hints.add(hint);

        hint = new ToggleHint();
        hint.label = "WER"; //$NON-NLS-1$
        hint.hint = ResUtil.getString(this, R.string.show_wearable_state);
        hint.key = PREF_KEY_SHOW_WEARABLE_STATE;
        hint.value =
                getTagBooleanOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SHOW_WEARABLE_STATE,
                        currentTag, currentType);
        hints.add(hint);

        return hints;
    }

    @Override
    public void updateToggle(ToggleHint hint) {
        EditorState es = Util.beginEditOption(this, APPLICATION_MANAGER_STORE_NAME);

        setTagBooleanOption(this, es, hint.key, hint.value, true, currentTag, currentType);

        Util.endEditOption(es);

        internalStart();
    }

    void handleAction(final AppInfoHolder ai, int action, final View view) {
        String pkgName = ai.appInfo.packageName;

        switch (action) {
            case ACTION_MENU:

                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        // bypass the 'showMenu' action offset
                        int action = which + 1;

                        handleAction(ai, action, view);
                    }
                };

                Util.newAlertDialogBuilder(this)
                        .setTitle(ai.label != null ? ai.label : pkgName)
                        .setItems(
                                new CharSequence[]{ResUtil.getString(this, R.string.manage),
                                        ResUtil.getString(this, R.string.run),
                                        ResUtil.getString(this, R.string.search_market),
                                        ResUtil.getString(this, R.string.details),
                                        ResUtil.getString(this, R.string.tags),}, listener).create().show();

                break;
            case ACTION_MANAGE:

                Util.showPackageDetails(this, pkgName);

                break;
            case ACTION_LAUNCH:

                if (!pkgName.equals(this.getPackageName())) {
                    Util.launchPackage(this, pkgName, R.string.run_failed, false);
                }

                break;
            case ACTION_SEARCH:

                Intent it = new Intent(Intent.ACTION_VIEW);

                it.setData(Uri.parse("market://search?q=pname:" + pkgName)); //$NON-NLS-1$

                it = Intent.createChooser(it, null);

                Util.safeStartActivity(this, it, false);

                break;
            case ACTION_DETAILS:

                ApplicationInfo appInfo = ai.appInfo;

                String installDate;
                String fileSize;

                if (appInfo.sourceDir != null) {
                    File f = new File(appInfo.sourceDir);
                    installDate =
                            DateUtils.formatDateTime(this, f.lastModified(), DateUtils.FORMAT_SHOW_YEAR
                                    | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
                    fileSize = Util.safeFormatFileSize(this, f.length());
                } else {
                    installDate = fileSize = ResUtil.getString(this, R.string.unknown);
                }

                StringBuffer sb = new StringBuffer().append("<small>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.pkg_name)).append(": ") //$NON-NLS-1$
                        .append(appInfo.packageName).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.pkg_issuer)).append(": ") //$NON-NLS-1$
                        .append(Util.getPackageIssuer(this, appInfo.packageName, null)).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.version_code)).append(": ") //$NON-NLS-1$
                        .append(ai.versionCode).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.target_sdk)).append(": ") //$NON-NLS-1$
                        .append(Util.getTargetSdkVersion(this, appInfo)).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.uid)).append(": ") //$NON-NLS-1$
                        .append(appInfo.uid).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.file_size)).append(": ") //$NON-NLS-1$
                        .append(fileSize).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.public_source)).append(": ") //$NON-NLS-1$
                        .append(appInfo.publicSourceDir).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.source)).append(": ") //$NON-NLS-1$
                        .append(appInfo.sourceDir).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.data)).append(": ") //$NON-NLS-1$
                        .append(appInfo.dataDir).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.installed_date)).append(": ") //$NON-NLS-1$
                        .append(installDate).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.process)).append(": ") //$NON-NLS-1$
                        .append(appInfo.processName).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.app_class)).append(": ") //$NON-NLS-1$
                        .append(appInfo.className == null ? "" //$NON-NLS-1$
                                : appInfo.className).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.task_affinity)).append(": ") //$NON-NLS-1$
                        .append(appInfo.taskAffinity).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.permission)).append(": ") //$NON-NLS-1$
                        .append(appInfo.permission == null ? "" //$NON-NLS-1$
                                : appInfo.permission).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.flags)).append(": ") //$NON-NLS-1$
                        .append(appInfo.flags).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.enabled)).append(": ") //$NON-NLS-1$
                        .append(appInfo.enabled).append("<br>") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.manage_space_ac)).append(": ") //$NON-NLS-1$
                        .append(appInfo.manageSpaceActivityName == null ? "" //$NON-NLS-1$
                                : appInfo.manageSpaceActivityName).append("</small>"); //$NON-NLS-1$

                Util.newAlertDialogBuilder(this).setTitle(ai.label == null ? appInfo.packageName : ai.label)
                        .setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setMessage(Html.fromHtml(sb.toString())).create().show();

                break;
            case ACTION_TAGS:

                List<AppInfoHolder> apps = new ArrayList<AppInfoHolder>();
                apps.add(ai);

                doChangeTags(apps);

                break;
            case ACTION_TOGGLE_SELECTION:

                if (view != null) {
                    CheckBox ckb = (CheckBox) view.findViewById(R.id.ckb_app);

                    if (ckb != null) {
                        ckb.setChecked(!ckb.isChecked());
                    }

                    ImageView iconView = (ImageView) view.findViewById(R.id.img_app_icon);
                    if (iconView != null && iconView.getVisibility() == View.VISIBLE) {
                        updateAppIcon(iconView, ai, true);
                    }
                }

                break;
        }
    }

    void updateAppIcon(ImageView iconView, AppInfoHolder item, boolean isSelection) {
        // for SDK14+, we use icon to show the selection state

        if (isSelection && Util.SDK_VER < 14) {
            // no need to update icon for selection in this case
            return;
        }

        iconView.setTag(System.currentTimeMillis());

        if (item.checked && Util.SDK_VER >= 14) {
            iconView.setImageResource(R.drawable.check_icon_1);
        } else {
            Drawable iconDrawable = item.icon == null ? null : item.icon.get();

            iconView.setImageDrawable(iconDrawable);

            if (iconDrawable == null) {
                // try reload icon for app items
                try {
                    new IconLoaderTask(ApplicationManager.this, iconView).execute(item);
                } catch (RejectedExecutionException e) {
                    // try adjust pool size and run again
                    if (Util.adjustAsyncTaskPoolSize()) {
                        try {
                            new IconLoaderTask(ApplicationManager.this, iconView).execute(item);
                        } catch (RejectedExecutionException e1) {
                            // this time we ignore
                        }
                    }
                } catch (Exception e) {
                    Log.e(ApplicationManager.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }

        // animation only for icon change by selection
        if (isSelection && Util.SDK_VER >= 14) {
            iconView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.flip));
        }
    }

    void doClear() {
        if (mdFreeStorageAndNotify == null) {
            Util.shortToast(this, R.string.clear_cache_disable);

            return;
        }

        // CLEAR_CACHE permission is only for system apps since Marshmallow
        if (Util.SDK_VER >= 23 && !Util.isSysApp(this)) {
            OnClickListener listener = new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Util.showManageApps(ApplicationManager.this);
                }
            };

            Util.newAlertDialogBuilder(this).setTitle(ResUtil.getString(this, R.string.warning))
                    .setMessage(ResUtil.getString(this, R.string.clear_cache_alt))
                    .setPositiveButton(android.R.string.yes, listener)
                    .setNegativeButton(android.R.string.no, null).create().show();

            return;
        }

        OnClickListener listener = new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                try {
                    if (progress != null) {
                        Util.safeDismissDialog(progress);
                    }
                    progress = new ProgressDialog(ApplicationManager.this);
                    progress.setMessage(ResUtil.getString(ApplicationManager.this, R.string.loading));
                    progress.setIndeterminate(true);
                    progress.show();

                    final PackageManager pm = getPackageManager();

                    final long[] oldStat = Util.getStorageState(Environment.getDataDirectory());

                    mdFreeStorageAndNotify.invoke(pm, 1000000000, new IPackageDataObserver.Stub() {

                        public void onRemoveCompleted(String packageName, boolean succeeded)
                                throws RemoteException {
                            long totalSize = (oldStat != null && oldStat.length > 0) ? oldStat[0] : 0;

                            if (totalSize > 1000000000) {
                                try {
                                    mdFreeStorageAndNotify.invoke(pm, totalSize, new Stub() {

                                        public void onRemoveCompleted(String packageName, boolean succeeded)
                                                throws RemoteException {
                                            doClearSucceeded(oldStat);
                                        }
                                    });

                                    return;
                                } catch (Exception e) {
                                    Log.e(ApplicationManager.class.getName(), e.getLocalizedMessage(), e);
                                }
                            }

                            doClearSucceeded(oldStat);
                        }
                    });
                } catch (Exception e) {
                    Log.e(ApplicationManager.class.getName(), e.getLocalizedMessage(), e);

                    Util.shortToast(ApplicationManager.this, R.string.clear_cache_fail,
                            e.getLocalizedMessage());
                }
            }
        };

        Util.newAlertDialogBuilder(this).setTitle(ResUtil.getString(this, R.string.warning))
                .setMessage(ResUtil.getString(this, R.string.clear_cache_warn))
                .setPositiveButton(android.R.string.yes, listener)
                .setNegativeButton(android.R.string.no, null).create().show();
    }

    void doClearSucceeded(long[] oldStat) {
        handler.sendEmptyMessage(MSG_DISMISS_PROGRESS);

        long gain = -1;

        if (oldStat != null && oldStat.length > 1) {
            long[] newStat = Util.getStorageState(Environment.getDataDirectory());

            if (newStat != null && newStat.length > 1) {
                gain = newStat[1] - oldStat[1];
            }
        }

        if (gain != -1) {
            handler.sendMessage(handler.obtainMessage(MSG_TOAST, ResUtil.getString(this,
                    R.string.clear_cache_finish2, Util.safeFormatFileSize(this, gain))));
        } else {
            handler.sendMessage(handler.obtainMessage(MSG_TOAST,
                    ResUtil.getString(this, R.string.clear_cache_finish)));
        }

        if (currentLayer != LAYER_TAG
                && getTagBooleanOption(ApplicationManager.this, APPLICATION_MANAGER_STORE_NAME,
                PREF_KEY_SHOW_SIZE, currentTag, currentLayer)) {
            if (sizeUpdater != null) {
                sizeUpdater.aborted = true;
            }

            (sizeUpdater = new PkgSizeUpdaterThread(ApplicationManager.this, appCache, handler)).start();
        }
    }

    void doUninstall() {
        final List<AppInfoHolder> sels = getSelected(getListView());

        if (sels == null || sels.size() == 0) {
            Util.shortToast(this, R.string.no_app_selected);
        } else {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    boolean canUninstall = false;

                    for (int i = 0, size = sels.size(); i < size; i++) {
                        ApplicationInfo app = sels.get(i).appInfo;

                        Intent it = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" //$NON-NLS-1$
                                + app.packageName));

                        if (!canUninstall) {
                            List<ResolveInfo> acts = getPackageManager().queryIntentActivities(it, 0);

                            canUninstall = acts.size() > 0;
                        }

                        if (canUninstall) {
                            startActivity(it);
                        }
                    }

                    if (!canUninstall) {
                        Util.shortToast(ApplicationManager.this, R.string.uninstall_fail);

                        Log.d(ApplicationManager.class.getName(),
                                "No activity found to handle the uninstall request."); //$NON-NLS-1$
                    } else {
                        stopCheck = true;
                    }
                }
            };

            Util.newAlertDialogBuilder(this).setTitle(ResUtil.getString(this, R.string.warning))
                    .setMessage(ResUtil.getString(this, R.string.uninstall_msg))
                    .setPositiveButton(android.R.string.ok, listener)
                    .setNegativeButton(android.R.string.cancel, null).create().show();
        }
    }

    protected void doChangeTags(final List<AppInfoHolder> apps) {
        if (apps == null || apps.size() == 0) {
            Util.shortToast(this, R.string.no_app_selected);
        } else {
            SharedPreferences prefStore =
                    getSharedPreferences(APPLICATION_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

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

                for (AppInfoHolder ai : apps) {
                    if (!links.contains(ai.appInfo.packageName)) {
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
                            getSharedPreferences(APPLICATION_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

                    for (int i = 0; i < customTags.size(); i++) {
                        String tag = customTags.get(i);

                        Set<String> links = Util.getCustomTagLinks(prefStore, tag);

                        if (toAdd.contains(tag)) {
                            // add tag
                            for (AppInfoHolder ai : apps) {
                                links.add(ai.appInfo.packageName);
                            }
                        } else if (toRemove.contains(tag)) {
                            // remove tag
                            for (AppInfoHolder ai : apps) {
                                links.remove(ai.appInfo.packageName);
                            }
                        }

                        // update
                        Util.updateCustomTagLinks(ApplicationManager.this, prefStore, tag, links);
                    }

                    // refresh
                    if (currentType == APP_TYPE_CUSTOM
                            && (toRemove.contains(currentTag) || toAdd.contains(currentTag))) {
                        loadApps(currentTag, currentType);
                    } else if (currentType == APP_TYPE_UNTAGGED && (!toAdd.isEmpty() || !toRemove.isEmpty())) {
                        loadApps(currentTag, currentType);
                    } else {
                        Map<String, Set<String>> linkMap = new HashMap<String, Set<String>>();

                        for (String tag : customTags) {
                            Set<String> links = Util.getCustomTagLinks(prefStore, tag);

                            if (links != null && links.size() > 0) {
                                linkMap.put(tag, links);
                            }
                        }

                        for (AppInfoHolder ai : apps) {
                            resetTags(ai, linkMap);
                        }

                        handler.sendEmptyMessage(MSG_REFRESH_PKG_ICON);
                    }
                }
            };

            Util.newAlertDialogBuilder(this)
                    .setTitle(ResUtil.getString(this, R.string.tags))
                    .setMultiChoiceItems(customTags.toArray(new String[customTags.size()]), states,
                            selListener).setPositiveButton(android.R.string.ok, listener)
                    .setNegativeButton(android.R.string.cancel, null).create().show();
        }
    }

    private void doShare() {
        final List<AppInfoHolder> sels = getSelected(getListView());

        if (sels == null || sels.size() == 0) {
            Util.shortToast(this, R.string.no_app_selected);
        } else {
            final boolean[] items;

            // TODO add ui for setting
            final boolean rememberShareSettigns =
                    Util.getBooleanOption(this, SYSINFO_MANAGER_STORE_NAME,
                            PREF_KEY_REMEMBER_LAST_SHARE_SETTING, true);

            if (rememberShareSettigns) {
                items =
                        Util.getBits(Util.getStringOption(this, APPLICATION_MANAGER_STORE_NAME,
                                PREF_KEY_LAST_SHARE_SETTING, null), 5, true);
            } else {
                items = new boolean[]{true, true, true, true, true};
            }

            OnMultiChoiceClickListener selListener = new OnMultiChoiceClickListener() {

                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    items[which] = isChecked;
                }
            };

            OnClickListener sendListener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    if (rememberShareSettigns) {
                        Util.setStringOption(ApplicationManager.this, APPLICATION_MANAGER_STORE_NAME,
                                PREF_KEY_LAST_SHARE_SETTING, Util.getBitsString(items));
                    }

                    OnClickListener typeListener = new OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            sendContent(items, sels, which == 0);
                        }
                    };

                    Util.newAlertDialogBuilder(ApplicationManager.this)
                            .setTitle(ResUtil.getString(ApplicationManager.this, R.string.actions))
                            .setItems(
                                    new String[]{ResUtil.getString(ApplicationManager.this, R.string.copy),
                                            ResUtil.getString(ApplicationManager.this, R.string.send),}, typeListener)
                            .create().show();
                }
            };

            Util.newAlertDialogBuilder(this)
                    .setTitle(ResUtil.getString(this, R.string.include))
                    .setMultiChoiceItems(
                            new CharSequence[]{ResUtil.getString(this, R.string.version),
                                    ResUtil.getString(this, R.string.version_code),
                                    ResUtil.getString(this, R.string.target_sdk),
                                    ResUtil.getString(this, R.string.pkg_name),
                                    ResUtil.getString(this, R.string.market_link),}, items, selListener)
                    .setPositiveButton(android.R.string.ok, sendListener)
                    .setNegativeButton(android.R.string.cancel, null).create().show();
        }
    }

    void sendContent(final boolean[] items, final List<AppInfoHolder> apps, final boolean isCopy) {
        if (progress != null) {
            Util.safeDismissDialog(progress);
        }
        progress = new ProgressDialog(this);
        progress.setMessage(ResUtil.getString(this, R.string.loading));
        progress.setIndeterminate(true);
        progress.show();

        // move this out of the thread code to avoid exception under honeycomb
        final ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        new Thread(new Runnable() {

            public void run() {
                String content = collectTextContent(items, apps);

                if (isCopy) {
                    if (cm != null && content != null) {
                        cm.setText(content);
                    }

                    handler.sendEmptyMessage(MSG_DISMISS_PROGRESS);

                    handler.sendMessage(handler.obtainMessage(MSG_TOAST,
                            ResUtil.getString(ApplicationManager.this, R.string.copied_hint)));

                    return;
                }

                if (aborted) {
                    return;
                }

                if (content != null) {
                    handler.sendMessage(handler.obtainMessage(MSG_CHECK_FORCE_COMPRESSION,
                            SysInfoManager.PLAINTEXT, 0, content));
                } else {
                    handler.sendMessage(handler.obtainMessage(MSG_CONTENT_READY, SysInfoManager.PLAINTEXT, 0,
                            content));
                }
            }
        }).start();
    }

    String collectTextContent(boolean[] items, List<AppInfoHolder> apps) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0, size = apps.size(); i < size; i++) {
            AppInfoHolder ai = apps.get(i);

            if (i > 0) {
                sb.append('\n');
            }

            sb.append(ai.label == null ? ai.appInfo.packageName : ai.label);

            if ((ai.appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                sb.append(" *"); //$NON-NLS-1$
            }

            if (items[0]) {
                sb.append(", " + ai.version); //$NON-NLS-1$
            }

            if (items[1]) {
                sb.append(", " + ai.versionCode); //$NON-NLS-1$
            }

            if (items[2]) {
                sb.append(", SDK " + Util.getTargetSdkVersion(this, ai.appInfo)); //$NON-NLS-1$
            }

            if (items[3]) {
                sb.append(", " + ai.appInfo.packageName); //$NON-NLS-1$
            }

            if (items[4]) {
                sb.append(", http://market.android.com/search?q=pname:" //$NON-NLS-1$
                        + ai.appInfo.packageName);
            }
        }

        if (sb.length() > 0) {
            return sb.toString();
        }

        return null;
    }

    void doExport() {
        final List<AppInfoHolder> sels = getSelected(getListView());

        if (sels == null || sels.size() == 0) {
            Util.shortToast(this, R.string.no_app_selected);
        } else if (!ensureSDCard()) {
            Util.shortToast(this, R.string.error_sdcard);
        } else {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    if (which == Dialog.BUTTON_POSITIVE) {
                        export(sels);
                    }
                }
            };

            Util.newAlertDialogBuilder(this)
                    .setTitle(ResUtil.getString(this, R.string.warning))
                    .setMessage(
                            ResUtil.getString(this, R.string.warning_msg, Util.getStringOption(this,
                                    APPLICATION_MANAGER_STORE_NAME, PREF_KEY_APP_EXPORT_DIR, DEFAULT_EXPORT_FOLDER)))
                    .setPositiveButton(ResUtil.getString(this, R.string.cont), listener)
                    .setNegativeButton(android.R.string.cancel, listener).create().show();
        }
    }

    void showAdvancedSelection() {
        final boolean showBackupState =
                getTagBooleanOption(this, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SHOW_BACKUP_STATE,
                        currentTag, currentType);

        OnClickListener listener = new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        toggleAllSelection(true);
                        break;
                    case 1:
                        if (showBackupState) {
                            chooseBackupState();
                        }
                        break;
                }

                // TODO should have better solution
                // enforce count update again, previous update may have sequence
                // issue, so may not have the correct count updated
                View v = findViewById(R.id.app_footer);

                ((TextView) v.findViewById(R.id.txt_count)).setText(String
                        .valueOf(getSelectedCount(getListView())));
            }
        };

        Util.newAlertDialogBuilder(this)
                .setTitle(ResUtil.getString(this, R.string.select))
                .setItems(
                        new CharSequence[]{
                                ResUtil.getString(this, R.string.all_apps),
                                showBackupState ? ResUtil.getString(this, R.string.backup_state)
                                        : Html.fromHtml("<font color=\"#848484\">" //$NON-NLS-1$
                                        + ResUtil.getString(this, R.string.backup_state) + "</font>") //$NON-NLS-1$
                        }, listener).create().show();
    }

    void chooseBackupState() {
        final boolean[] items = new boolean[5];

        OnMultiChoiceClickListener multiListener = new OnMultiChoiceClickListener() {

            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                items[which] = isChecked;
            }
        };

        OnClickListener listener = new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                selectBackupState(items);
            }
        };

        Util.newAlertDialogBuilder(this)
                .setTitle(ResUtil.getString(this, R.string.backup_state))
                .setMultiChoiceItems(
                        new CharSequence[]{ResUtil.getString(this, R.string.no_backup),
                                ResUtil.getString(this, R.string.new_), ResUtil.getString(this, R.string.latest),
                                ResUtil.getString(this, R.string.old),}, items, multiListener)
                .setPositiveButton(android.R.string.ok, listener)
                .setNegativeButton(android.R.string.cancel, null).create().show();
    }

    void selectBackupState(boolean[] states) {
        ListView lstApps = getListView();

        int totalCount = lstApps.getCount();

        for (int i = 0; i < totalCount; i++) {
            AppInfoHolder holder = (AppInfoHolder) lstApps.getItemAtPosition(i);

            if (holder.backupState >= 0 && holder.backupState <= 3) {
                holder.checked = states[holder.backupState];
            }
        }

        if (getSelectedCount(lstApps) == 0) {
            hideButtons();
        }

        ((ArrayAdapter) lstApps.getAdapter()).notifyDataSetChanged();
    }

    void toggleProgressBar(boolean show) {
        View v = findViewById(R.id.progress);

        if (v != null) {
            v.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        }
    }

    void toggleAllSelection(boolean selected) {
        ListView lstApps = getListView();

        int totalCount = lstApps.getCount();

        try {
            for (int i = 0; i < totalCount; i++) {
                AppInfoHolder holder = (AppInfoHolder) lstApps.getItemAtPosition(i);

                holder.checked = selected;
            }
        } catch (IndexOutOfBoundsException e) {
            // ignore
        }

        if (!selected) {
            hideButtons();
        }

        ((ArrayAdapter) lstApps.getAdapter()).notifyDataSetChanged();
    }

    void hideButtons() {
        View v = findViewById(R.id.app_footer);

        if (v.getVisibility() != View.GONE) {
            v.setVisibility(View.GONE);

            v.startAnimation(AnimationUtils.loadAnimation(ApplicationManager.this,
                    R.anim.footer_disappear));
        }
    }

    static Drawable reloadIcon(Context ctx, AppInfoHolder ai) {
        if (ai.appInfo == null) {
            // tag items
            int res = 0;

            switch (ai.versionCode) {
                case APP_TYPE_ALL:
                    res = R.drawable.tag_orange;
                    break;
                case APP_TYPE_SYS:
                    res = R.drawable.tag_blue;
                    break;
                case APP_TYPE_USER:
                    res = R.drawable.tag_green;
                    break;
                case APP_TYPE_RECENT:
                    res = R.drawable.tag_pink;
                    break;
                case APP_TYPE_UNTAGGED:
                    res = R.drawable.tag_cream;
                    break;
                case APP_TYPE_CUSTOM:
                    res = R.drawable.tag_pink;
                    break;
            }

            if (res != 0) {
                ai.icon = new SoftReference<Drawable>(ctx.getResources().getDrawable(res));
            } else {
                ai.icon = null;
            }
        } else {
            try {
                Drawable icon = ctx.getPackageManager().getApplicationIcon(ai.appInfo);

                if (icon != null) {
                    ai.icon = new SoftReference<Drawable>(icon);
                } else {
                    ai.icon = null;
                }
            } catch (OutOfMemoryError oom) {
                Log.e(ApplicationManager.class.getName(), "OOM when loading icon: " //$NON-NLS-1$
                        + ai.appInfo.packageName, oom);
            }
        }

        return ai.icon == null ? null : ai.icon.get();
    }

    /**
     * PackageSizeObserver
     */
    private static final class PkgSizeObserver extends IPackageStatsObserver.Stub {

        private CountDownLatch count;
        private Context ctx;
        private AppCache appCache;

        PkgSizeObserver(CountDownLatch count, Context ctx, AppCache appCache) {
            this.count = count;
            this.ctx = ctx;
            this.appCache = appCache;
        }

        void invokeGetPkgSize(String pkgName, PackageManager pm) {
            if (Util.SDK_VER >= 26) {
                // from Oreo, we have to use StorageStatsManager to get size info
                boolean statsSuccess = false;
                if (Util.isUsageStatsEnabled(ctx)) {
                    try {
                        Object storageStatsManager = ctx.getSystemService("storagestats"/*Context.STORAGE_STATS_SERVICE*/);
                        if (storageStatsManager != null) {
                            Class clz = Class.forName("android.app.usage.StorageStatsManager");
                            Class userHandleClz = Class.forName("android.os.UserHandle");
                            Method mtdQueryStatsForPackage = clz.getDeclaredMethod("queryStatsForPackage", UUID.class, String.class, userHandleClz);

                            Class stmClz = Class.forName("android.os.storage.StorageManager");
                            Field fdDeaultUUID = stmClz.getDeclaredField("UUID_DEFAULT");

                            UUID defaultUUID = (UUID) fdDeaultUUID.get(null);

                            Method mtdMyUserHandle = Process.class.getDeclaredMethod("myUserHandle");
                            Object myUserHandle = mtdMyUserHandle.invoke(null);

                            Object result = mtdQueryStatsForPackage.invoke(storageStatsManager, defaultUUID, pkgName, myUserHandle);
                            if (result != null) {
                                Class stgStatsClz = Class.forName("android.app.usage.StorageStats");
                                Method mtdGetAppBytes = stgStatsClz.getDeclaredMethod("getAppBytes");
                                Method mtdGetDataBytes = stgStatsClz.getDeclaredMethod("getDataBytes");
                                Method mtdGetCacheBytes = stgStatsClz.getDeclaredMethod("getCacheBytes");

                                long codeSize = (Long) mtdGetAppBytes.invoke(result);
                                long dataSize = (Long) mtdGetDataBytes.invoke(result);
                                long cacheSize = (Long) mtdGetCacheBytes.invoke(result);

                                updatePackageSizeInfo(pkgName, codeSize, dataSize, cacheSize);
                                statsSuccess = true;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(ApplicationManager.class.getName(), e.getLocalizedMessage(), e);
                    }
                }

                if (!statsSuccess) {
                    updatePackageSizeInfo(pkgName, 0, 0, 0);
                }

                count.countDown();
            } else if (mdGetPackageSizeInfo != null) {
                try {
                    mdGetPackageSizeInfo.invoke(pm, pkgName, this);
                } catch (Exception e) {
                    Log.e(ApplicationManager.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }

        private void updatePackageSizeInfo(String pkgName, long codeSize, long dataSize, long cacheSize) {
            AppInfoHolder holder = appCache.appLookup.get(pkgName);
            if (holder != null) {
                synchronized (appCache) {
                    holder.size = Util.safeFormatFileSize(ctx, codeSize) + " + " //$NON-NLS-1$
                            + Util.safeFormatFileSize(ctx, dataSize) + " (" //$NON-NLS-1$
                            + Util.safeFormatFileSize(ctx, cacheSize) + ')';

                    holder.codeSize = codeSize;
                    holder.dataSize = dataSize;
                    holder.cacheSize = cacheSize;
                }
            }
        }

        public void onGetStatsCompleted(PackageStats pStats, boolean succeeded) throws RemoteException {
            if (pStats != null) {
                updatePackageSizeInfo(pStats.packageName, pStats.codeSize, pStats.dataSize, pStats.cacheSize);
            }
            count.countDown();
        }

    }

    /**
     * PkgSizeUpdaterThread
     */
    private static final class PkgSizeUpdaterThread extends Thread {

        private ApplicationManager ctx;
        private AppCache appCache;
        private Handler handler;

        volatile boolean aborted;

        PkgSizeUpdaterThread(ApplicationManager ctx, AppCache appCache, Handler handler) {
            super("SizeUpdater"); //$NON-NLS-1$

            this.ctx = ctx;
            this.appCache = appCache;
            this.handler = handler;
        }

        @Override
        public void run() {
            PackageManager pm = ctx.getPackageManager();

            ArrayList<AppInfoHolder> localList = appCache.generateLocalList();

            int totalSize = localList.size();
            int secSize = 32;

            int num = totalSize / secSize;
            if (num * secSize < totalSize) {
                num++;
            }

            for (int k = 0; k < num; k++) {
                int secCount = (k + 1) * secSize > totalSize ? (totalSize - k * secSize) : secSize;

                CountDownLatch count = new CountDownLatch(secCount);

                PkgSizeObserver observer = new PkgSizeObserver(count, ctx, appCache);

                for (int i = 0; i < secCount; i++) {
                    if (aborted) {
                        return;
                    }

                    ApplicationInfo ai = localList.get(k * secSize + i).appInfo;

                    if (ai == null) {
                        return;
                    }

                    observer.invokeGetPkgSize(ai.packageName, pm);
                }

                try {
                    count.await();

                    if (k == num - 1) {
                        int type =
                                getTagIntOption(ctx, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SORT_ORDER_TYPE,
                                        ORDER_TYPE_NAME, ctx.currentTag, ctx.currentType);

                        int secondType =
                                getTagIntOption(ctx, APPLICATION_MANAGER_STORE_NAME,
                                        PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_NAME, ctx.currentTag,
                                        ctx.currentType);

                        if (type == ORDER_TYPE_CODE_SIZE || type == ORDER_TYPE_DATA_SIZE
                                || type == ORDER_TYPE_CACHE_SIZE || type == ORDER_TYPE_TOTAL_SIZE
                                || secondType == ORDER_TYPE_CODE_SIZE || secondType == ORDER_TYPE_DATA_SIZE
                                || secondType == ORDER_TYPE_CACHE_SIZE || secondType == ORDER_TYPE_TOTAL_SIZE) {
                            appCache.reOrderApps(ctx, ctx.currentTag, ctx.currentType);

                            handler.sendMessage(handler.obtainMessage(MSG_REFRESH_PKG_SIZE, 1, 0));

                            return;
                        }
                    }

                    handler.sendMessage(handler.obtainMessage(MSG_REFRESH_PKG_SIZE, 0, 0));
                } catch (InterruptedException e) {
                    Log.e(ApplicationManager.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }
    }

    /**
     * ResourceUpdaterThread
     */
    private static final class ResourceUpdaterThread extends Thread {

        private ApplicationManager ctx;
        private AppCache appCache;
        private Handler handler;

        volatile boolean aborted;

        ResourceUpdaterThread(ApplicationManager ctx, AppCache appCache, Handler handler) {
            super("ResourceUpdater"); //$NON-NLS-1$

            this.ctx = ctx;
            this.appCache = appCache;
            this.handler = handler;
        }

        public void run() {
            ApplicationInfo ai;
            AppInfoHolder holder;

            PackageManager pm = ctx.getPackageManager();

            ArrayList<AppInfoHolder> localList = appCache.generateLocalList();

            for (int i = 0, size = localList.size(); i < size; i++) {
                if (aborted) {
                    return;
                }

                ai = localList.get(i).appInfo;

                if (ai == null) {
                    return;
                }

                CharSequence label = pm.getApplicationLabel(ai);

                String installDateString = null;
                long installDate = 0;
                if (ai.sourceDir != null) {
                    File f = new File(ai.sourceDir);
                    installDate = f.lastModified();
                    installDateString =
                            DateUtils.formatDateTime(ctx, installDate, DateUtils.FORMAT_SHOW_YEAR
                                    | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
                }

                holder = appCache.appLookup.get(ai.packageName);

                if (holder != null) {
                    synchronized (appCache) {
                        holder.label = label;
                        holder.installDate = installDate;
                        holder.installDateString = installDateString;
                    }
                }
            }

            // reorder by new names
            int pSortType =
                    getTagIntOption(ctx, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SORT_ORDER_TYPE,
                            ORDER_TYPE_NAME, ctx.currentTag, ctx.currentType);
            int sSortType =
                    getTagIntOption(ctx, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SECONDARY_SORT_ORDER_TYPE,
                            ORDER_TYPE_NAME, ctx.currentTag, ctx.currentType);
            if (pSortType == ORDER_TYPE_NAME || sSortType == ORDER_TYPE_NAME
                    || pSortType == ORDER_TYPE_INSTALL_DATE || sSortType == ORDER_TYPE_INSTALL_DATE) {
                appCache.reOrderApps(ctx, ctx.currentTag, ctx.currentType);

                handler.sendMessage(handler.obtainMessage(MSG_REFRESH_PKG_LABEL, 1, 0));
            } else {
                handler.sendMessage(handler.obtainMessage(MSG_REFRESH_PKG_LABEL, 0, 0));
            }

            // ! we now dynamically reload the icon when UI requested.
            // if ( Util.getBooleanOption( ac, PREF_KEY_SHOW_ICON ) )
            // {
            // for ( int i = 0, size = localList.size( ); i < size; i++ )
            // {
            // if ( aborted )
            // {
            // return;
            // }
            //
            // ai = localList.get( i ).appInfo;
            //
            // if ( ai == null )
            // {
            // return;
            // }
            //
            // try
            // {
            // holder = appCache.appLookup.get( ai.packageName );
            //
            // if ( holder != null )
            // {
            // Drawable icon = pm.getApplicationIcon( ai );
            //
            // if ( icon != null )
            // {
            // holder.icon = new SoftReference<Drawable>( icon );
            // }
            // else
            // {
            // holder.icon = null;
            // }
            // }
            // }
            // catch ( OutOfMemoryError oom )
            // {
            // Log.e( ApplicationManager.class.getName( ),
            //								"OOM when loading icon: " //$NON-NLS-1$
            // + ai.packageName,
            // oom );
            // }
            // }
            //
            // handler.sendEmptyMessage( MSG_REFRESH_PKG_ICON );
            // }
        }
    }

    /**
     * BackupStateUpdaterThread
     */
    private static final class BackupStateUpdaterThread extends Thread {

        private ApplicationManager ctx;
        private List<AppInfoHolder> apps;
        private AppCache appCache;
        private Handler handler;

        volatile boolean aborted;

        BackupStateUpdaterThread(ApplicationManager ctx, List<AppInfoHolder> apps, AppCache appCache,
                                 Handler handler) {
            super("BackupStateUpdater"); //$NON-NLS-1$

            this.ctx = ctx;
            this.apps = apps;
            this.appCache = appCache;
            this.handler = handler;
        }

        public void run() {
            if (apps == null) {
                apps = appCache.generateLocalList();
            }

            if (apps == null || apps.size() == 0) {
                return;
            }

            String exportFolder =
                    Util.getStringOption(ctx, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_APP_EXPORT_DIR,
                            DEFAULT_EXPORT_FOLDER);

            File sysoutput = null;
            File useroutput = null;

            File output = new File(exportFolder);

            if (output.exists()) {
                sysoutput = new File(output, SYS_APP);

                if (!sysoutput.exists() || !sysoutput.isDirectory()) {
                    sysoutput = null;
                }

                useroutput = new File(output, USER_APP);

                if (!useroutput.exists() || !useroutput.isDirectory()) {
                    useroutput = null;
                }
            }

            ApplicationInfo ai;
            AppInfoHolder holder;
            PackageInfo pi;

            PackageManager pm = ctx.getPackageManager();

            APPS:
            for (int i = 0, size = apps.size(); i < size; i++) {
                if (aborted) {
                    return;
                }

                ai = apps.get(i).appInfo;

                if (ai == null) {
                    return;
                }

                holder = appCache.appLookup.get(ai.packageName);

                if (holder != null) {
                    File targetOutput = useroutput;

                    if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        targetOutput = sysoutput;
                    }

                    if (targetOutput != null && ai.packageName != null) {
                        final String nameNoVer = makeFileName(holder, -1);
                        final String nameVerPrefix = ai.packageName + '-';

                        File[] backFiles = targetOutput.listFiles(new FilenameFilter() {

                            public boolean accept(File dir, String filename) {
                                if (nameNoVer.equals(filename) || filename.startsWith(nameVerPrefix)) {
                                    return true;
                                }
                                return false;
                            }
                        });

                        if (backFiles != null) {
                            boolean hasBackup = false;

                            for (File destFile : backFiles) {
                                if (destFile.exists() && destFile.isFile()) {
                                    pi = pm.getPackageArchiveInfo(destFile.getAbsolutePath(), 0);

                                    if (pi != null && ai.packageName.equals(pi.packageName)) {
                                        synchronized (appCache) {
                                            if (pi.versionCode == holder.versionCode) {
                                                holder.backupState = 2;

                                                continue APPS;
                                            } else if (pi.versionCode > holder.versionCode) {
                                                holder.backupState = 3;

                                                continue APPS;
                                            } else {
                                                hasBackup = true;
                                            }
                                        }
                                    }
                                }
                            }

                            if (hasBackup) {
                                synchronized (appCache) {
                                    holder.backupState = 1;
                                }

                                continue APPS;
                            }
                        }
                    }

                    synchronized (appCache) {
                        holder.backupState = 0;
                    }
                }
            }

            // reorder by backup state
            if (getTagIntOption(ctx, APPLICATION_MANAGER_STORE_NAME, PREF_KEY_SORT_ORDER_TYPE,
                    ORDER_TYPE_NAME, ctx.currentTag, ctx.currentType) == ORDER_TYPE_BACKUP_STATE
                    || getTagIntOption(ctx, APPLICATION_MANAGER_STORE_NAME,
                    PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_NAME, ctx.currentTag, ctx.currentType) == ORDER_TYPE_BACKUP_STATE) {
                appCache.reOrderApps(ctx, ctx.currentTag, ctx.currentType);

                handler.sendMessage(handler.obtainMessage(MSG_REFRESH_BACKUP_STATE, 1, 0));
            } else {
                handler.sendMessage(handler.obtainMessage(MSG_REFRESH_BACKUP_STATE, 0, 0));
            }
        }
    }

    /**
     * IconLoaderTask
     */
    private static final class IconLoaderTask extends AsyncTask<AppInfoHolder, Void, Drawable> {

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
        protected Drawable doInBackground(AppInfoHolder... params) {
            if (params != null && params.length > 0) {
                AppInfoHolder ai = params[0];

                return reloadIcon(ctx, ai);
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
     * AppViewHolder
     */
    static final class AppViewHolder {

        TextView txt_name, txt_size, txt_ver, txt_time, txt_path;
        ImageView img_type, imgLock, imgSdcard, imgAdmob, imgAuto, imgWear;
        CheckBox ckb_app;
        ViewGroup tagPane;

        AppViewHolder(View view) {
            txt_name = (TextView) view.findViewById(R.id.app_name);
            txt_ver = (TextView) view.findViewById(R.id.app_version);
            txt_size = (TextView) view.findViewById(R.id.app_size);
            txt_time = (TextView) view.findViewById(R.id.app_time);
            txt_path = (TextView) view.findViewById(R.id.app_path);
            tagPane = (ViewGroup) view.findViewById(R.id.tags_pane);
            ckb_app = (CheckBox) view.findViewById(R.id.ckb_app);
            img_type = (ImageView) view.findViewById(R.id.img_app_icon);
            imgLock = (ImageView) view.findViewById(R.id.img_lock);
            imgSdcard = (ImageView) view.findViewById(R.id.img_sdcard);
            imgAdmob = (ImageView) view.findViewById(R.id.img_admob);
            imgAuto = (ImageView) view.findViewById(R.id.img_auto);
            imgWear = (ImageView) view.findViewById(R.id.img_wear);
        }
    }

    /**
     * AppInfoHolder
     */
    private static final class AppInfoHolder {

        ApplicationInfo appInfo;

        CharSequence label;

        String version;

        long installDate;

        String installDateString;

        SoftReference<Drawable> icon;

        String size;

        long codeSize, dataSize, cacheSize;

        int backupState;

        int versionCode;

        boolean isPrivate;

        boolean hasAD;

        boolean supportSD;

        boolean supportWearable;

        boolean autoStart;

        boolean checked;

        String[] tags;

        AppInfoHolder() {

        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof AppInfoHolder)) {
                return false;
            }

            AppInfoHolder that = (AppInfoHolder) o;

            if (this.appInfo != null && that.appInfo != null) {
                // app item
                return this.appInfo.packageName.equals(that.appInfo.packageName);
            } else if (this.appInfo == that.appInfo) {
                // tag item
                return this.versionCode == that.versionCode && TextUtils.equals(this.label, that.label);
            } else {
                return false;
            }
        }
    }

    /**
     * AppCache
     */
    private static final class AppCache {

        ArrayList<AppInfoHolder> appList;

        HashMap<String, AppInfoHolder> appLookup;

        AppCache() {
            appList = new ArrayList<AppInfoHolder>();
            appLookup = new HashMap<String, AppInfoHolder>();
        }

        synchronized void clear() {
            appList.clear();
            appLookup.clear();
        }

        synchronized ArrayList<AppInfoHolder> generateLocalList() {
            ArrayList<AppInfoHolder> local = new ArrayList<AppInfoHolder>();

            local.addAll(appList);

            return local;
        }

        synchronized void update(ArrayList<AppInfoHolder> apps) {
            appList.retainAll(apps);

            for (int i = 0, size = apps.size(); i < size; i++) {
                AppInfoHolder ai = apps.get(i);

                if (ai.appInfo == null) {
                    // tag item
                    int idx = appList.indexOf(ai);
                    if (idx == -1) {
                        appList.add(ai);
                    } else {
                        AppInfoHolder oai = appList.get(idx);
                        oai.codeSize = ai.codeSize;
                    }
                } else {
                    // app item
                    AppInfoHolder oai = appLookup.get(ai.appInfo.packageName);

                    if (oai == null) {
                        oai = ai;

                        appLookup.put(ai.appInfo.packageName, ai);
                    } else {
                        oai.appInfo = ai.appInfo;
                        oai.version = ai.version;
                        oai.isPrivate = ai.isPrivate;
                        oai.hasAD = ai.hasAD;
                        oai.autoStart = ai.autoStart;
                        oai.supportSD = ai.supportSD;
                        oai.supportWearable = ai.supportWearable;
                        oai.checked = ai.checked;
                        oai.versionCode = ai.versionCode;
                        oai.tags = ai.tags;
                    }

                    if (!appList.contains(oai)) {
                        appList.add(oai);
                    }
                }
            }
        }

        synchronized void reOrderApps(Context ctx, String tagName, int tagType) {
            SharedPreferences prefStore =
                    ctx.getSharedPreferences(APPLICATION_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            int type = getTagIntOption(prefStore, PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_NAME, tagName, tagType);
            int direction = getTagIntOption(prefStore, PREF_KEY_SORT_DIRECTION, ORDER_ASC, tagName, tagType);
            int secondType = getTagIntOption(prefStore, PREF_KEY_SECONDARY_SORT_ORDER_TYPE, ORDER_TYPE_NAME, tagName, tagType);
            int secondDirection = getTagIntOption(prefStore, PREF_KEY_SECONDARY_SORT_DIRECTION, ORDER_ASC, tagName, tagType);

            Collections.sort(appList, new AppComparator(type, direction, secondType, secondDirection));
        }

        synchronized void reOrderTags() {
            Collections.sort(appList, new TagComparator());
        }
    }

    /**
     * TagComparator
     */
    private static final class TagComparator implements Comparator<AppInfoHolder> {

        Collator clt = Collator.getInstance();

        TagComparator() {
        }

        public int compare(AppInfoHolder obj1, AppInfoHolder obj2) {
            if (obj1.versionCode == obj2.versionCode) {
                return clt.compare(obj1.label.toString(), obj2.label.toString());
            }
            return obj1.versionCode < obj2.versionCode ? -1 : 1;
        }
    }

    /**
     * AppComparator
     */
    private static final class AppComparator implements Comparator<AppInfoHolder> {

        int type, direction, secondType, secondDirection;

        Collator clt = Collator.getInstance();

        AppComparator(int type, int direction, int secondType, int secondDirection) {
            this.type = type;
            this.direction = direction;
            this.secondType = secondType;
            this.secondDirection = secondDirection;
        }

        public int compare(AppInfoHolder obj1, AppInfoHolder obj2) {
            if (obj1.appInfo == null || obj2.appInfo == null) {
                return 0;
            }

            int rlt = compare(clt, type, direction, obj1, obj2);

            if (rlt == 0) {
                rlt = compare(clt, secondType, secondDirection, obj1, obj2);
            }

            return rlt;
        }

        private static int compare(Collator clt, int type, int direction, AppInfoHolder obj1,
                                   AppInfoHolder obj2) {
            switch (type) {
                case ORDER_TYPE_NAME:
                    String n1 = obj1.label == null ? obj1.appInfo.packageName : obj1.label.toString();
                    String n2 = obj2.label == null ? obj2.appInfo.packageName : obj2.label.toString();
                    return clt.compare(n1, n2) * direction;
                case ORDER_TYPE_CODE_SIZE:
                    return (obj1.codeSize == obj2.codeSize ? 0 : (obj1.codeSize < obj2.codeSize ? -1 : 1))
                            * direction;
                case ORDER_TYPE_DATA_SIZE:
                    return (obj1.dataSize == obj2.dataSize ? 0 : (obj1.dataSize < obj2.dataSize ? -1 : 1))
                            * direction;
                case ORDER_TYPE_CACHE_SIZE:
                    return (obj1.cacheSize == obj2.cacheSize ? 0 : (obj1.cacheSize < obj2.cacheSize ? -1 : 1))
                            * direction;
                case ORDER_TYPE_TOTAL_SIZE:
                    long s1 = obj1.codeSize + obj1.dataSize + obj1.cacheSize;
                    long s2 = obj2.codeSize + obj2.dataSize + obj2.cacheSize;
                    return (s1 == s2 ? 0 : (s1 < s2 ? -1 : 1)) * direction;
                case ORDER_TYPE_INSTALL_DATE:
                    long d1 = obj1.installDate;
                    long d2 = obj2.installDate;
                    return (d1 == d2 ? 0 : (d1 < d2 ? -1 : 1)) * direction;
                case ORDER_TYPE_BACKUP_STATE:
                    return (obj1.backupState - obj2.backupState) * direction;
                case ORDER_TYPE_INSTALL_LOCATION:
                    int t1 = (obj1.appInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0 ? 2
                                    : (obj1.supportSD ? 1 : 0);
                    int t2 = (obj2.appInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0 ? 2
                                    : (obj2.supportSD ? 1 : 0);
                    return (t1 - t2) * direction;
                case ORDER_TYPE_ADMOB_STATE:
                    return ((obj1.hasAD ? 1 : 0) - (obj2.hasAD ? 1 : 0)) * direction;
                case ORDER_TYPE_AUTOSTART_STATE:
                    return ((obj1.autoStart ? 1 : 0) - (obj2.autoStart ? 1 : 0)) * direction;
            }
            return 0;
        }
    }

    /**
     * AppSettings
     */
    public static class AppSettings extends PreferenceActivity {

        protected String[] defaultTags;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);

            super.onCreate(savedInstanceState);

            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(this));

            PreferenceCategory pc = new PreferenceCategory(this);
            pc.setTitle(ResUtil.getString(this, R.string.preference));
            getPreferenceScreen().addPreference(pc);

            Preference perfFilter = new Preference(this);
            perfFilter.setKey(PREF_KEY_FILTER_APP_TYPE);
            perfFilter.setTitle(ResUtil.getString(this, R.string.filter_title));
            pc.addPreference(perfFilter);

            CheckBoxPreference perfShowSize = new CheckBoxPreference(this);
            perfShowSize.setKey(PREF_KEY_SHOW_SIZE);
            perfShowSize.setTitle(ResUtil.getString(this, R.string.show_app_size));
            perfShowSize.setSummary(ResUtil.getString(this, R.string.show_app_size_sum));
            pc.addPreference(perfShowSize);

            CheckBoxPreference perfShowDate = new CheckBoxPreference(this);
            perfShowDate.setKey(PREF_KEY_SHOW_DATE);
            perfShowDate.setTitle(ResUtil.getString(this, R.string.show_app_date));
            perfShowDate.setSummary(ResUtil.getString(this, R.string.show_app_date_sum));
            pc.addPreference(perfShowDate);

            CheckBoxPreference perfShowIcon = new CheckBoxPreference(this);
            perfShowIcon.setKey(PREF_KEY_SHOW_ICON);
            perfShowIcon.setTitle(ResUtil.getString(this, R.string.show_app_icon));
            perfShowIcon.setSummary(ResUtil.getString(this, R.string.show_app_icon_sum));
            pc.addPreference(perfShowIcon);

            CheckBoxPreference perfShowBackup = new CheckBoxPreference(this);
            perfShowBackup.setKey(PREF_KEY_SHOW_BACKUP_STATE);
            perfShowBackup.setTitle(ResUtil.getString(this, R.string.show_backup_state));
            perfShowBackup.setSummary(ResUtil.getString(this, R.string.show_backup_state_sum));
            pc.addPreference(perfShowBackup);

            CheckBoxPreference perfLocation = new CheckBoxPreference(this);
            perfLocation.setKey(PREF_KEY_SHOW_INSTALL_LOCATION);
            perfLocation.setTitle(ResUtil.getString(this, R.string.show_install_location));
            perfLocation.setSummary(ResUtil.getString(this, R.string.show_install_location_sum));
            pc.addPreference(perfLocation);

            CheckBoxPreference perfShowAdmob = new CheckBoxPreference(this);
            perfShowAdmob.setKey(PREF_KEY_SHOW_ADMOB_STATE);
            perfShowAdmob.setTitle(ResUtil.getString(this, R.string.show_admob_state));
            perfShowAdmob.setSummary(ResUtil.getString(this, R.string.show_admob_state_sum));
            pc.addPreference(perfShowAdmob);

            CheckBoxPreference perfShowAutoStart = new CheckBoxPreference(this);
            perfShowAutoStart.setKey(PREF_KEY_SHOW_AUTOSTART_STATE);
            perfShowAutoStart.setTitle(ResUtil.getString(this, R.string.show_autostart_state));
            perfShowAutoStart.setSummary(ResUtil.getString(this, R.string.show_autostart_state_sum));
            pc.addPreference(perfShowAutoStart);

            CheckBoxPreference perfShowWearableStart = new CheckBoxPreference(this);
            perfShowWearableStart.setKey(PREF_KEY_SHOW_WEARABLE_STATE);
            perfShowWearableStart.setTitle(ResUtil.getString(this, R.string.show_wearable_state));
            perfShowWearableStart.setSummary(ResUtil.getString(this, R.string.show_wearable_state_sum));
            pc.addPreference(perfShowWearableStart);

            Preference perfDefaultAction = new Preference(this);
            perfDefaultAction.setKey(PREF_KEY_DEFAULT_TAP_ACTION);
            perfDefaultAction.setTitle(ResUtil.getString(this, R.string.default_tap_action));
            pc.addPreference(perfDefaultAction);

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

            CheckBoxPreference perfTagSettings = new CheckBoxPreference(this);
            perfTagSettings.setKey(PREF_KEY_USE_TAG_SETTINGS);
            perfTagSettings.setTitle(ResUtil.getString(this, R.string.enable_tag_setting));
            perfTagSettings.setSummary(ResUtil.getString(this, R.string.enable_tag_setting_sum));
            pc.addPreference(perfTagSettings);

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
            pc.setKey(PREF_CATEGORY_BACKUP);
            pc.setTitle(ResUtil.getString(this, R.string.backup));
            getPreferenceScreen().addPreference(pc);

            Preference perfExportDir = new Preference(this);
            perfExportDir.setKey(PREF_KEY_APP_EXPORT_DIR);
            perfExportDir.setTitle(ResUtil.getString(this, R.string.export_dir));
            pc.addPreference(perfExportDir);

            CheckBoxPreference perfKeepVer = new CheckBoxPreference(this);
            perfKeepVer.setKey(PREF_KEY_KEEP_APP_VERSION);
            perfKeepVer.setTitle(ResUtil.getString(this, R.string.keep_version));
            perfKeepVer.setSummary(ResUtil.getString(this, R.string.keep_version_sum));
            pc.addPreference(perfKeepVer);

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

            refreshBackupFolder();
            refreshBooleanOption(PREF_KEY_KEEP_APP_VERSION, true);
            refreshAppType();
            refreshDefaultAction();
            refreshBooleanOption(PREF_KEY_USE_TAG_VIEW, true);
            refreshBooleanOption(PREF_KEY_USE_TAG_SETTINGS, true);
            refreshBooleanOption(PREF_KEY_SHOW_COUNT, true);
            refreshSortType(PREF_KEY_SORT_ORDER_TYPE);
            refreshSortDirection(PREF_KEY_SORT_DIRECTION);
            refreshSortType(PREF_KEY_SECONDARY_SORT_ORDER_TYPE);
            refreshSortDirection(PREF_KEY_SECONDARY_SORT_DIRECTION);
            refreshBooleanOption(PREF_KEY_SHOW_SIZE, true);
            refreshBooleanOption(PREF_KEY_SHOW_DATE, true);
            refreshBooleanOption(PREF_KEY_SHOW_ICON, true);
            refreshBooleanOption(PREF_KEY_SHOW_BACKUP_STATE, true);
            refreshBooleanOption(PREF_KEY_SHOW_INSTALL_LOCATION, true);
            refreshBooleanOption(PREF_KEY_SHOW_ADMOB_STATE, true);
            refreshBooleanOption(PREF_KEY_SHOW_AUTOSTART_STATE, true);
            refreshBooleanOption(PREF_KEY_SHOW_WEARABLE_STATE, true);

            defaultTags =
                    new String[]{ResUtil.getString(this, R.string.untagged),
                            ResUtil.getString(this, R.string.recent),
                            ResUtil.getString(this, R.string.user_apps),
                            ResUtil.getString(this, R.string.sys_apps),
                            ResUtil.getString(this, R.string.all_apps),};

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

        void refreshBooleanOption(String key, boolean defValue) {
            boolean val = getIntent().getBooleanExtra(key, defValue);

            ((CheckBoxPreference) findPreference(key)).setChecked(val);
        }

        void refreshRecentRange() {
            int days = getIntent().getIntExtra(PREF_KEY_RECENT_SCOPE, 3);

            findPreference(PREF_KEY_RECENT_SCOPE).setSummary(
                    days == 1 ? ResUtil.getString(this, R.string.past_day) : ResUtil.getString(this,
                            R.string.past_days, days));
        }

        void refreshBackupFolder() {
            findPreference(PREF_KEY_APP_EXPORT_DIR).setSummary(
                    getIntent().getStringExtra(PREF_KEY_APP_EXPORT_DIR));
        }

        void refreshAppType() {
            int type = getIntent().getIntExtra(PREF_KEY_FILTER_APP_TYPE, APP_TYPE_ALL);

            int res = R.string.all_apps;
            if (type == APP_TYPE_SYS) {
                res = R.string.sys_apps;
            } else if (type == APP_TYPE_USER) {
                res = R.string.user_apps;
            }

            findPreference(PREF_KEY_FILTER_APP_TYPE).setSummary(ResUtil.getString(this, res));
        }

        void refreshDefaultAction() {
            int type = getIntent().getIntExtra(PREF_KEY_DEFAULT_TAP_ACTION, ACTION_TOGGLE_SELECTION);

            String label = null;
            switch (type) {
                case ACTION_MANAGE:
                    label = ResUtil.getString(this, R.string.manage);
                    break;
                case ACTION_LAUNCH:
                    label = ResUtil.getString(this, R.string.run);
                    break;
                case ACTION_SEARCH:
                    label = ResUtil.getString(this, R.string.search_market);
                    break;
                case ACTION_DETAILS:
                    label = ResUtil.getString(this, R.string.details);
                    break;
                case ACTION_TAGS:
                    label = ResUtil.getString(this, R.string.tags);
                    break;
                case ACTION_TOGGLE_SELECTION:
                    label = ResUtil.getString(this, R.string.toggle_selection);
                    break;
                case ACTION_MENU:
                    label = ResUtil.getString(this, R.string.show_menu);
                    break;
            }

            findPreference(PREF_KEY_DEFAULT_TAP_ACTION).setSummary(label);
        }

        void refreshSortType(String key) {
            int type = getIntent().getIntExtra(key, ORDER_TYPE_NAME);

            String label = null;
            switch (type) {
                case ORDER_TYPE_NAME:
                    label = ResUtil.getString(this, R.string.name);
                    break;
                case ORDER_TYPE_CODE_SIZE:
                    label = ResUtil.getString(this, R.string.code_size);
                    break;
                case ORDER_TYPE_DATA_SIZE:
                    label = ResUtil.getString(this, R.string.data_size);
                    break;
                case ORDER_TYPE_CACHE_SIZE:
                    label = ResUtil.getString(this, R.string.cache_size);
                    break;
                case ORDER_TYPE_TOTAL_SIZE:
                    label = ResUtil.getString(this, R.string.total_size);
                    break;
                case ORDER_TYPE_INSTALL_DATE:
                    label = ResUtil.getString(this, R.string.installed_date);
                    break;
                case ORDER_TYPE_BACKUP_STATE:
                    label = ResUtil.getString(this, R.string.backup_state);
                    break;
                case ORDER_TYPE_INSTALL_LOCATION:
                    label = ResUtil.getString(this, R.string.install_location);
                    break;
                case ORDER_TYPE_ADMOB_STATE:
                    label = ResUtil.getString(this, R.string.admob_state);
                    break;
                case ORDER_TYPE_AUTOSTART_STATE:
                    label = ResUtil.getString(this, R.string.auto_start_state);
                    break;
            }

            findPreference(key).setSummary(label);
        }

        void refreshSortDirection(String key) {
            int type = getIntent().getIntExtra(key, ORDER_ASC);

            String label = ResUtil.getString(this, type == ORDER_ASC ? R.string.ascending : R.string.descending);
            findPreference(key).setSummary(label);
        }

        protected void editCustomTags(Context ctx, Intent it, Preference prefCustomTags,
                                      Preference prefTagVisibility, String[] defaultTags) {
            Util.editCustomTags(ctx, it, prefCustomTags, prefTagVisibility, defaultTags);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            final Intent it = getIntent();

            final String prefKey = preference.getKey();

            if (PREF_KEY_APP_EXPORT_DIR.equals(prefKey)) {
                final EditText txt = new EditText(this);
                txt.setText(it.getStringExtra(PREF_KEY_APP_EXPORT_DIR));

                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        String path = txt.getText().toString();

                        if (path != null) {
                            path = path.trim();

                            if (path.length() == 0) {
                                path = null;
                            }
                        }

                        if (path == null) {
                            path = DEFAULT_EXPORT_FOLDER;
                        }

                        it.putExtra(PREF_KEY_APP_EXPORT_DIR, path);

                        dialog.dismiss();

                        refreshBackupFolder();
                    }
                };

                new AlertDialog.Builder(this).setTitle(ResUtil.getString(this, R.string.export_dir))
                        .setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, null).setView(txt).create().show();

                return true;
            } else if (PREF_KEY_KEEP_APP_VERSION.equals(prefKey)) {
                it.putExtra(PREF_KEY_KEEP_APP_VERSION,
                        ((CheckBoxPreference) findPreference(PREF_KEY_KEEP_APP_VERSION)).isChecked());

                return true;
            } else if (PREF_KEY_USE_TAG_VIEW.equals(prefKey)) {
                it.putExtra(PREF_KEY_USE_TAG_VIEW,
                        ((CheckBoxPreference) findPreference(PREF_KEY_USE_TAG_VIEW)).isChecked());

                return true;
            } else if (PREF_KEY_USE_TAG_SETTINGS.equals(prefKey)) {
                it.putExtra(PREF_KEY_USE_TAG_SETTINGS,
                        ((CheckBoxPreference) findPreference(PREF_KEY_USE_TAG_SETTINGS)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_COUNT.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_COUNT,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_COUNT)).isChecked());

                return true;
            } else if (PREF_KEY_TAG_VISIBILITY.equals(prefKey)) {
                Util.editTagVisibility(this, it, preference, defaultTags);

                return true;
            } else if (PREF_KEY_CUSTOM_TAGS.equals(prefKey)) {
                editCustomTags(this, it, preference, findPreference(PREF_KEY_TAG_VISIBILITY),
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

                        txtInfo.setText(days == 1 ? ResUtil.getString(AppSettings.this, R.string.past_day)
                                : ResUtil.getString(AppSettings.this, R.string.past_days, days));
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

                Util.newAlertDialogBuilder(this).setTitle(ResUtil.getString(this, R.string.recent_range))
                        .setView(v).setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, null).create().show();

                return true;
            } else if (PREF_KEY_FILTER_APP_TYPE.equals(prefKey)) {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        it.putExtra(PREF_KEY_FILTER_APP_TYPE, which);
                        dialog.dismiss();

                        refreshAppType();
                    }
                };

                Util.newAlertDialogBuilder(this)
                        .setTitle(ResUtil.getString(this, R.string.filter_title))
                        .setSingleChoiceItems(
                                new CharSequence[]{ResUtil.getString(this, R.string.all_apps),
                                        ResUtil.getString(this, R.string.sys_apps),
                                        ResUtil.getString(this, R.string.user_apps)},
                                it.getIntExtra(PREF_KEY_FILTER_APP_TYPE, APP_TYPE_ALL), listener).create().show();

                return true;
            } else if (PREF_KEY_SHOW_SIZE.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_SIZE,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_SIZE)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_DATE.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_DATE,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_DATE)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_ICON.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_ICON,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_ICON)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_BACKUP_STATE.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_BACKUP_STATE,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_BACKUP_STATE)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_INSTALL_LOCATION.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_INSTALL_LOCATION,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_INSTALL_LOCATION)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_ADMOB_STATE.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_ADMOB_STATE,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_ADMOB_STATE)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_AUTOSTART_STATE.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_AUTOSTART_STATE,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_AUTOSTART_STATE)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_WEARABLE_STATE.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_WEARABLE_STATE,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_WEARABLE_STATE)).isChecked());

                return true;
            } else if (PREF_KEY_DEFAULT_TAP_ACTION.equals(prefKey)) {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        it.putExtra(PREF_KEY_DEFAULT_TAP_ACTION, which);
                        dialog.dismiss();

                        refreshDefaultAction();
                    }
                };

                Util.newAlertDialogBuilder(this)
                        .setTitle(ResUtil.getString(this, R.string.default_tap_action))
                        //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setSingleChoiceItems(
                                new String[]{ResUtil.getString(this, R.string.show_menu),
                                        ResUtil.getString(this, R.string.manage),
                                        ResUtil.getString(this, R.string.run),
                                        ResUtil.getString(this, R.string.search_market),
                                        ResUtil.getString(this, R.string.details),
                                        ResUtil.getString(this, R.string.tags),
                                        ResUtil.getString(this, R.string.toggle_selection),},
                                it.getIntExtra(PREF_KEY_DEFAULT_TAP_ACTION, ACTION_TOGGLE_SELECTION), listener)
                        .create().show();

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

                Util.newAlertDialogBuilder(this)
                        .setTitle(ResUtil.getString(this, R.string.sort_type))
                        //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setSingleChoiceItems(
                                new String[]{ResUtil.getString(this, R.string.name),
                                        ResUtil.getString(this, R.string.code_size),
                                        ResUtil.getString(this, R.string.data_size),
                                        ResUtil.getString(this, R.string.cache_size),
                                        ResUtil.getString(this, R.string.total_size),
                                        ResUtil.getString(this, R.string.installed_date),
                                        ResUtil.getString(this, R.string.backup_state),
                                        ResUtil.getString(this, R.string.install_location),
                                        ResUtil.getString(this, R.string.admob_state),
                                        ResUtil.getString(this, R.string.auto_start_state)},
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

                Util.newAlertDialogBuilder(this)
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
