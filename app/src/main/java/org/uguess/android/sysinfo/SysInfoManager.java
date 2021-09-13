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
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import org.uguess.android.sysinfo.ProcessManager.ProcessInfo;
import org.uguess.android.sysinfo.PropertiesViewer.GLPropertiesViewerStub;
import org.uguess.android.sysinfo.ToolMenuDialog.ActionHint;
import org.uguess.android.sysinfo.ToolMenuDialog.IActionMenuProvider;
import org.uguess.android.sysinfo.Util.EditorState;
import org.uguess.android.sysinfo.WidgetProvider.Bar2xWidget;
import org.uguess.android.sysinfo.WidgetProvider.CacheWidget;
import org.uguess.android.sysinfo.WidgetProvider.HistoryWidget;
import org.uguess.android.sysinfo.WidgetProvider.InfoWidget;
import org.uguess.android.sysinfo.WidgetProvider.TaskWidget;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SysInfoManager
 */
public class SysInfoManager extends PreferenceActivity implements Constants,
        IActionMenuProvider {

    protected static final String PREF_KEY_WIDGET_DISABLED = "widget_disabled"; //$NON-NLS-1$
    protected static final String PREF_KEY_EXPORT_PREFERENCES = "export_prefs"; //$NON-NLS-1$
    protected static final String PREF_KEY_IMPORT_PREFERENCES = "import_prefs"; //$NON-NLS-1$
    protected static final String PREF_KEY_INSTALL_WIDGET_PACK = "install_widget_pack"; //$NON-NLS-1$

    private static final String KEY_INFO_PREF_SCREEN = "INFO_PREF_SCREEN"; //$NON-NLS-1$
    private static final String KEY_TASK_PREF_SCREEN = "TASK_PREF_SCREEN"; //$NON-NLS-1$
    private static final String KEY_WIFI_PREF_SCREEN = "WIFI_PREF_SCREEN"; //$NON-NLS-1$
    private static final String KEY_BATT_PREF_SCREEN = "BATT_PREF_SCREEN"; //$NON-NLS-1$

    private static final int RESULT_PREFERENCES_IMPORTED = 99;

    private static final String KEY_SD_STORAGE = "sd_storage"; //$NON-NLS-1$
    private static final String KEY_SECONDARY_SD_STORAGE = "secondary_sd_storage"; //$NON-NLS-1$
    private static final String KEY_A2SD_STORAGE = "app2sd_storage"; //$NON-NLS-1$
    private static final String KEY_INTERNAL_STORAGE = "internal_storage"; //$NON-NLS-1$
    private static final String KEY_SYSTEM_STORAGE = "system_storage"; //$NON-NLS-1$
    private static final String KEY_CACHE_STORAGE = "cache_storage"; //$NON-NLS-1$
    private static final String KEY_MEMORY = "memory"; //$NON-NLS-1$
    private static final String KEY_PROCESSOR = "processor"; //$NON-NLS-1$
    private static final String KEY_NETWORK = "net_address"; //$NON-NLS-1$
    private static final String KEY_BATTERY = "battery_level"; //$NON-NLS-1$
    private static final String KEY_SENSORS = "sensors"; //$NON-NLS-1$
    private static final String KEY_INPUT = "input"; //$NON-NLS-1$
    private static final String KEY_USB = "usb"; //$NON-NLS-1$
    private static final String KEY_REFRESH = "refresh_status"; //$NON-NLS-1$
    private static final String KEY_VIEW_LOGS = "view_logs"; //$NON-NLS-1$
    private static final String KEY_VIEW_PROPS = "view_props"; //$NON-NLS-1$
    private static final String KEY_SEND_REPORT = "send_report"; //$NON-NLS-1$
    private static final String KEY_MORE_INFO = "more_info"; //$NON-NLS-1$

    private static final int BASIC_INFO = 0;
    private static final int APPLICATIONS = 1;
    private static final int PROCESSES = 2;
    private static final int NETSTATES = 3;
    private static final int DMESG_LOG = 4;
    private static final int LOGCAT_LOG = 5;

    private static final int WIDGET_BAR = 0;
    private static final int WIDGET_INFO = 1;
    private static final int WIDGET_TASK = 2;
    private static final int WIDGET_CACHE = 3;
    private static final int WIDGET_HISTORY = 4;
    private static final int WIDGET_BAR_2x = 5;

    ProgressDialog progress;

    volatile boolean aborted;

    private CpuState cpuObj = new CpuState();

    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int level = intent.getIntExtra("level", 0); //$NON-NLS-1$
                int scale = intent.getIntExtra("scale", 100); //$NON-NLS-1$

                String lStr = String.valueOf(level * 100 / scale) + '%';

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

                findPreference(KEY_BATTERY).setSummary(lStr + " (" //$NON-NLS-1$
                        + sStr + ")"); //$NON-NLS-1$
            }
        }
    };

    Handler handler = new InfoHandler(this);

    private static final class InfoHandler extends Handler {

        private WeakReference<SysInfoManager> acRef;

        InfoHandler(SysInfoManager ac) {
            acRef = new WeakReference<SysInfoManager>(ac);
        }

        public void handleMessage(Message msg) {
            SysInfoManager host = acRef.get();
            if (host == null) {
                return;
            }

            switch (msg.what) {
                case MSG_CONTENT_READY:

                    sendEmptyMessage(MSG_DISMISS_PROGRESS);

                    Util.handleMsgSendContentReady((String) msg.obj, "Android System Report - ", //$NON-NLS-1$
                            host, msg.arg2 == 1);

                    break;
                case MSG_CHECK_FORCE_COMPRESSION:

                    sendEmptyMessage(MSG_DISMISS_PROGRESS);

                    Util.checkForceCompression(this, host, (String) msg.obj, msg.arg1, "android_report"); //$NON-NLS-1$

                    break;
                case MSG_DISMISS_PROGRESS:

                    if (host.progress != null) {
                        Util.safeDismissDialog(host.progress);
                        host.progress = null;
                    }
                    break;
                case MSG_TOAST:

                    Util.longToast(host, (String) msg.obj);
                    break;
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceScreen psc = getPreferenceManager().createPreferenceScreen(this);
        setPreferenceScreen(psc);

        Preference perf = new Preference(this);
        perf.setKey(KEY_SD_STORAGE);
        perf.setTitle(ResUtil.getString(this, R.string.sd_storage));
        psc.addPreference(perf);

        if (getSecondarySDStorageState() != null) {
            perf = new Preference(this);
            perf.setKey(KEY_SECONDARY_SD_STORAGE);
            perf.setTitle(ResUtil.getString(this, R.string.secondary_sd_storage));
            psc.addPreference(perf);
        }

        perf = new Preference(this);
        perf.setKey(KEY_A2SD_STORAGE);
        perf.setTitle(ResUtil.getString(this, R.string.a2sd_storage));
        psc.addPreference(perf);

        perf = new Preference(this);
        perf.setKey(KEY_INTERNAL_STORAGE);
        perf.setTitle(ResUtil.getString(this, R.string.internal_storage));
        psc.addPreference(perf);

        perf = new Preference(this);
        perf.setKey(KEY_SYSTEM_STORAGE);
        perf.setTitle(ResUtil.getString(this, R.string.system_storage));
        psc.addPreference(perf);

        perf = new Preference(this);
        perf.setKey(KEY_CACHE_STORAGE);
        perf.setTitle(ResUtil.getString(this, R.string.cache_storage));
        psc.addPreference(perf);

        perf = new Preference(this);
        perf.setKey(KEY_MEMORY);
        perf.setTitle(ResUtil.getString(this, R.string.memory));
        psc.addPreference(perf);

        perf = new Preference(this);
        perf.setKey(KEY_PROCESSOR);
        perf.setTitle(ResUtil.getString(this, R.string.processor));
        psc.addPreference(perf);

        perf = new Preference(this);
        perf.setKey(KEY_NETWORK);
        perf.setTitle(ResUtil.getString(this, R.string.net_address));
        psc.addPreference(perf);

        perf = new Preference(this);
        perf.setKey(KEY_BATTERY);
        perf.setTitle(ResUtil.getString(this, R.string.battery_level));
        perf.setSummary(ResUtil.getString(this, R.string.retrieving));
        psc.addPreference(perf);

        perf = new Preference(this);
        perf.setKey(KEY_SENSORS);
        perf.setTitle(ResUtil.getString(this, R.string.sensors));
        psc.addPreference(perf);

        if (InputInfoActivity.isAvailable()) {
            perf = new Preference(this);
            perf.setKey(KEY_INPUT);
            perf.setTitle(ResUtil.getString(this, R.string.input_devices));
            psc.addPreference(perf);
        }

        if (UsbInfoActivity.isAvailable(this)) {
            perf = new Preference(this);
            perf.setKey(KEY_USB);
            perf.setTitle(ResUtil.getString(this, R.string.usb_devices));
            psc.addPreference(perf);
        }

        PreferenceCategory pc = new PreferenceCategory(this);
        pc.setTitle(ResUtil.getString(this, R.string.actions));
        psc.addPreference(pc);

        perf = new Preference(this);
        perf.setKey(KEY_REFRESH);
        perf.setTitle(ResUtil.getString(this, R.string.refresh));
        pc.addPreference(perf);

        perf = new Preference(this);
        perf.setKey(KEY_VIEW_LOGS);
        perf.setTitle(ResUtil.getString(this, R.string.view_logs));
        pc.addPreference(perf);

        perf = new Preference(this);
        perf.setKey(KEY_VIEW_PROPS);
        perf.setTitle(ResUtil.getString(this, R.string.view_props));
        pc.addPreference(perf);

        perf = new Preference(this);
        perf.setKey(KEY_SEND_REPORT);
        perf.setTitle(ResUtil.getString(this, R.string.send_report));
        pc.addPreference(perf);

        Intent it = getAboutSettingsIntent();

        if (it != null) {
            perf = new Preference(this);
            perf.setKey(KEY_MORE_INFO);
            perf.setTitle(ResUtil.getString(this, R.string.more_info));
            pc.addPreference(perf);
        }

        if (CpuState.needGpuInfo()) {
            it = new Intent(this, GLPropertiesViewerStub.class);
            it.putExtra(PropertiesViewer.EXTRA_NO_DETAILS, true);
            startActivityForResult(it, REQUEST_GPU_INFO);

            PropertiesViewer.overridePendingTransition(this, 0, 0);
        }
    }

    @Override
    protected void onDestroy() {
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
    protected void onStop() {
        super.onStop();

        if (progress != null) {
            Util.safeDismissDialog(progress);
            progress = null;
        }
    }

    @Override
    protected void onResume() {
        aborted = false;

        super.onResume();

        registerReceiver(mBatteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        updateInfo();

        if (cpuObj.needSecondRun()) {
            handler.postDelayed(new Runnable() {

                public void run() {
                    if (aborted) {
                        return;
                    }

                    findPreference(KEY_PROCESSOR).setSummary(getCpuInfo());
                }
            }, 1000);
        }
    }

    @Override
    protected void onPause() {
        aborted = true;

        handler.removeMessages(MSG_CHECK_FORCE_COMPRESSION);
        handler.removeMessages(MSG_CONTENT_READY);

        unregisterReceiver(mBatteryInfoReceiver);

        super.onPause();
    }

    private void updateInfo() {
        findPreference(KEY_PROCESSOR).setSummary(getCpuInfo());

        String[] mi = getMemInfo();
        findPreference(KEY_MEMORY).setSummary(
                mi == null ? ResUtil.getString(this, R.string.info_not_available) : (ResUtil.getString(
                        this, R.string.storage_summary, mi[0], mi[2]) + ResUtil.getString(this,
                        R.string.idle_info, mi[1])));

        Preference pref = findPreference(KEY_SD_STORAGE);
        String[] si = getExternalStorageInfo();
        pref.setSummary(si == null ? ResUtil.getString(this, R.string.info_not_available) : ResUtil
                .getString(this, R.string.storage_summary, si[0], si[1]));
        pref.setEnabled(si != null);

        pref = findPreference(KEY_SECONDARY_SD_STORAGE);
        if (pref != null) {
            si = getSecondarySDStorageInfo();
            pref.setSummary(si == null ? ResUtil.getString(this, R.string.info_not_available) : ResUtil
                    .getString(this, R.string.storage_summary, si[0], si[1]));
            pref.setEnabled(si != null);
        }

        pref = findPreference(KEY_A2SD_STORAGE);
        si = getA2SDStorageInfo();
        pref.setSummary(si == null ? ResUtil.getString(this, R.string.info_not_available) : ResUtil
                .getString(this, R.string.storage_summary, si[0], si[1]));
        pref.setEnabled(si != null);

        pref = findPreference(KEY_INTERNAL_STORAGE);
        si = getInternalStorageInfo();
        pref.setSummary(si == null ? ResUtil.getString(this, R.string.info_not_available) : ResUtil
                .getString(this, R.string.storage_summary, si[0], si[1]));
        pref.setEnabled(si != null);

        pref = findPreference(KEY_SYSTEM_STORAGE);
        si = getSystemStorageInfo();
        pref.setSummary(si == null ? ResUtil.getString(this, R.string.info_not_available) : ResUtil
                .getString(this, R.string.storage_summary, si[0], si[1]));
        pref.setEnabled(si != null);

        pref = findPreference(KEY_CACHE_STORAGE);
        si = getCacheStorageInfo();
        pref.setSummary(si == null ? ResUtil.getString(this, R.string.info_not_available) : ResUtil
                .getString(this, R.string.storage_summary, si[0], si[1]));
        pref.setEnabled(si != null);

        pref = findPreference(KEY_NETWORK);
        String nInfo = Util.getNetAddressInfo();
        pref.setSummary(nInfo == null ? ResUtil.getString(this, R.string.info_not_available) : nInfo);
        pref.setEnabled(nInfo != null);

        pref = findPreference(KEY_SENSORS);
        int s = getSensorState();
        pref.setSummary(getSensorInfo(s));
        pref.setEnabled(s > 0);

        pref = findPreference(KEY_INPUT);
        if (pref != null) {
            int count = InputInfoActivity.getInputDeviceCount();

            pref.setSummary(count == 1 ? ResUtil.getString(this, R.string.input_device_info, count)
                    : ResUtil.getString(this, R.string.input_device_info2, count));
            pref.setEnabled(count > 0);
        }

        pref = findPreference(KEY_USB);
        if (pref != null) {
            int acCount = UsbInfoActivity.getAccessoryCount(this);
            int dvCount = UsbInfoActivity.getDeviceCount(this);

            pref.setSummary(ResUtil.getString(this, R.string.usb_device_info, acCount, dvCount));
            pref.setEnabled(acCount > 0 || dvCount > 0);
        }

        // int[] gs = getGpsState( );
        //		findPreference( "gps" ).setSummary( getGpsInfo( gs ) ); //$NON-NLS-1$
        //		findPreference( "gps" ).setEnabled( gs != null ); //$NON-NLS-1$
    }

    private String[] getMemInfo() {
        long[] state = Util.getMemState(this);

        if (state == null) {
            return null;
        }

        String[] mem = new String[state.length];

        for (int i = 0, size = mem.length; i < size; i++) {
            if (state[i] == -1) {
                mem[i] = ResUtil.getString(this, R.string.info_not_available);
            } else {
                mem[i] = Util.safeFormatFileSize(this, state[i]);
            }
        }

        return mem;
    }

    String getCpuInfo() {
        String model = CpuState.getProcessor();
        String[] stat = cpuObj.getMips();

        if (stat != null && stat.length > 0) {
            if (stat.length == 1) {
                if (stat[0] == null) {
                    return model;
                } else {
                    return model + "  " + stat[0]; //$NON-NLS-1$
                }
            } else {
                StringBuilder freq = new StringBuilder();

                freq.append(model).append('\n');

                for (int i = 0; i < stat.length; i++) {
                    freq.append(i).append(": ") //$NON-NLS-1$
                            .append(stat[i] != null ? stat[i] : "zZ") //$NON-NLS-1$
                            .append("  "); //$NON-NLS-1$
                }

                return freq.toString();
            }
        } else if (model != null) {
            return model;
        }

        return ResUtil.getString(this, R.string.info_not_available);
    }

    private String getSensorInfo(int state) {
        if (state == -1) {
            return ResUtil.getString(this, R.string.info_not_available);
        }

        if (state == 1) {
            return ResUtil.getString(this, R.string.sensor_info, state);
        } else {
            return ResUtil.getString(this, R.string.sensor_info2, state);
        }
    }

    private int getSensorState() {
        SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (sm != null) {
            List<Sensor> ss = sm.getSensorList(Sensor.TYPE_ALL);

            int c = 0;

            if (ss != null) {
                c = ss.size();
            }

            return c;
        }

        return -1;
    }

    // private String getGpsInfo( int[] state )
    // {
    // if ( state == null )
    // {
    // return getString( R.string.info_not_available );
    // }
    //
    // if ( state[1] == -1 )
    // {
    // return getString( R.string.disabled );
    // }
    //
    //		return getString( R.string.enabled ) + " (" //$NON-NLS-1$
    // + state[0]
    // + '/'
    // + state[1]
    // + ')';
    // }

    // private int[] getGpsState( )
    // {
    // LocationManager lm = (LocationManager) getSystemService(
    // Context.LOCATION_SERVICE );
    //
    // if ( lm != null )
    // {
    // boolean gpsEnabled = false;
    //
    // String allowedProviders = Settings.Secure.getString( getContentResolver(
    // ),
    // Settings.Secure.LOCATION_PROVIDERS_ALLOWED );
    //
    // if ( allowedProviders != null )
    // {
    // gpsEnabled = ( allowedProviders.equals( LocationManager.GPS_PROVIDER )
    //						|| allowedProviders.contains( "," //$NON-NLS-1$
    // + LocationManager.GPS_PROVIDER
    //								+ "," ) //$NON-NLS-1$
    // || allowedProviders.startsWith( LocationManager.GPS_PROVIDER
    //								+ "," ) || allowedProviders.endsWith( "," //$NON-NLS-1$ //$NON-NLS-2$
    // + LocationManager.GPS_PROVIDER ) );
    // }
    //
    // if ( gpsEnabled )
    // {
    // GpsStatus gs = lm.getGpsStatus( null );
    //
    // Iterable<GpsSatellite> sats = gs.getSatellites( );
    //
    // int c = 0;
    //
    // if ( sats != null )
    // {
    // Iterator<GpsSatellite> itr = sats.iterator( );
    //
    // if ( itr != null )
    // {
    // while ( itr.hasNext( ) )
    // {
    // itr.next( );
    // c++;
    // }
    // }
    // }
    //
    // return new int[]{
    // c, gs.getMaxSatellites( )
    // };
    // }
    //
    // return new int[]{
    // 0, -1
    // };
    // }
    //
    // return null;
    // }

    private String[] getExternalStorageInfo() {
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)
                || Environment.MEDIA_MOUNTED.equals(state)) {
            return getStorageInfo(Environment.getExternalStorageDirectory());
        }

        return null;
    }

    private String[] getA2SDStorageInfo() {
        return getStorageInfo(getA2SDStorageState());
    }

    private long[] getA2SDStorageState() {
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)
                || Environment.MEDIA_MOUNTED.equals(state)) {
            // here we just guess if it's app2sd enabled, this should work for
            // most app2sd enabled roms, but may not all.

            File f = new File("/dev/block/mmcblk0p2"); //$NON-NLS-1$

            if (f.exists()) {
                BufferedReader reader = null;
                String mountPoint = null;

                try {
                    reader =
                            new BufferedReader(new InputStreamReader(new FileInputStream(F_MOUNT_INFO)), 1024);

                    String line;

                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("/dev/block/mmcblk0p2 ")) //$NON-NLS-1$
                        {
                            // 21==length of the above string
                            int idx = line.indexOf(' ', 21);

                            if (idx != -1) {
                                mountPoint = line.substring(21, idx).trim();
                            }

                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                            reader = null;
                        } catch (IOException ie) {
                            Log.e(SysInfoManager.class.getName(), ie.getLocalizedMessage(), ie);
                        }
                    }
                }

                if (mountPoint != null) {
                    f = new File(mountPoint);

                    if (f.exists() && f.isDirectory()) {
                        return Util.getStorageState(f);
                    }
                }
            }
        }

        return getSystemA2SDStorageState();
    }

    /**
     * This checks the built-in app2sd storage info supported since Froyo
     */
    private long[] getSystemA2SDStorageState() {
        final PackageManager pm = getPackageManager();
        List<ApplicationInfo> allApps = null;

        try {
            allApps = pm.getInstalledApplications(0);
        } catch (Exception e) {
            Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
        }

        if (allApps == null) {
            return null;
        }

        long total = 0;
        long free = 0;

        for (int i = 0, size = allApps.size(); i < size; i++) {
            ApplicationInfo info = allApps.get(i);

            if (info != null && (info.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0) {
                String src = info.sourceDir;

                if (src != null) {
                    File srcFile = new File(src);

                    if (srcFile.canRead()) {
                        try {
                            StatFs stat = new StatFs(srcFile.getAbsolutePath());
                            long blockSize = stat.getBlockSize();

                            total += stat.getBlockCount() * blockSize;
                            free += stat.getAvailableBlocks() * blockSize;
                        } catch (Exception e) {
                            Log.e(SysInfoManager.class.getName(), "Cannot access path: " //$NON-NLS-1$
                                    + srcFile.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }

        if (total > 0) {
            long[] info = new long[2];
            info[0] = total;
            info[1] = free;

            return info;
        }

        return null;
    }

    private String[] getSecondarySDStorageInfo() {
        Object[] state = getSecondarySDStorageState();

        if (state != null) {
            return getStorageInfo((long[]) state[0]);
        }
        return null;
    }

    /**
     * @return [[storageSize], storagePath]
     */
    private Object[] getSecondarySDStorageState() {
        String osHintPath = null;
        Map<String, String> env = System.getenv();
        if (env != null) {
            osHintPath = env.get("SECONDARY_STORAGE"); //$NON-NLS-1$
        }

        String dfHintPath = null;
        if (Util.SDK_VER >= 23) {
            dfHintPath = getSecondarySDPathFromDF();
        }

        String[] possibilePath = new String[]{
                osHintPath, dfHintPath,
                "/sdcard/sd", //$NON-NLS-1$
                "/sdcard/external_sd", //$NON-NLS-1$
                "/mnt/extSdCard", //$NON-NLS-1$
                "/data/sdext2", //$NON-NLS-1$
                "/emmc", //$NON-NLS-1$
                "/mnt/flash", //$NON-NLS-1$
                "/mnt/external1", //$NON-NLS-1$
                "/storage/sdcard-disk0", //$NON-NLS-1$
                "/storage/sdcard1", //$NON-NLS-1$
                "/removable/microsd", //$NON-NLS-1$
                "/Removable/MicroSD", //$NON-NLS-1$
                "/mnt/sdcard/_ExternalSD" //$NON-NLS-1$
        };

        long[] state = null;
        String sdPath = null;

        for (String path : possibilePath) {
            if (path == null) {
                continue;
            }

            state = getSecondarySDStorageState(path);

            if (state != null) {
                sdPath = path;
                break;
            }
        }

        if (state != null) {
            return new Object[]{state, sdPath};
        }

        return null;
    }

    private String getSecondarySDPathFromDF() {

        // the sd path format in DF looks like this:
        // Old DF (for SDK 23)
        // Filesystem               Size     Used     Free   Blksize
        // /storage/emulated      541.3M   100.8M   440.5M   4096
        // /storage/0AF7-1614      98.4M     1.0K    98.4M   512  (mounted as portable storage)
        // /mnt/expand/4bd8e06b-7e48-4462-aa9c-bacb4431a808    81.7M    84.0K    81.6M   4096  (mounted as internal storage)

        // New DF (for SDK N+)
        // Filesystem           1K-blocks    Used Available Use% Mounted on
        // /dev/fuse               554336  130948    423388  24% /storage/emulated
        // /dev/fuse               101312     192    101120   1% /storage/362E-14E8  (mounted as portable storage)
        // /dev/block/dm-0          83628      96     83532   1% /mnt/expand/20d24b30-c0d4-4c81-99fa-e1ac2b368fd7  (mounted as internal storage)

        try {
            Process proc = Runtime.getRuntime().exec("df"); //$NON-NLS-1$

            List<String> lines = Util.readRawTextLines(proc.getInputStream(), 2048);
            if (lines != null && lines.size() > 1) {
                String header = lines.get(0);
                boolean isNewDF = header.contains("Mounted on");

                // new df, path in the end
                for (int i = 1; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    // new df, path is in the end, otherwise it's in the beginning
                    if (isNewDF) {
                        int idx = line.lastIndexOf(' ');
                        if (idx != -1) {
                            line = line.substring(idx + 1);
                        }
                    }
                    if (line.startsWith("/mnt/expand/") || (
                            line.startsWith("/storage/")
                                    && !line.startsWith("/storage/emulated")
                                    && !line.startsWith("/storage/self")
                    )) {
                        int idx = line.indexOf(' ');
                        if (idx != -1) {
                            // old df
                            return line.substring(0, idx);
                        } else {
                            // new df
                            return line;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
        }

        return null;
    }

    private long[] getSecondarySDStorageState(String path) {
        long[] state = null;

        File f = new File(path);

        if (f.exists() && f.isDirectory() && f.canRead()) {
            state = Util.getStorageState(f);

            if (state != null) {
                String ess = Environment.getExternalStorageState();

                if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(ess)
                        || Environment.MEDIA_MOUNTED.equals(ess)) {
                    File exFile = Environment.getExternalStorageDirectory();

                    long[] exState = Util.getStorageState(exFile);

                    if (exState != null) {
                        if (exState[0] == state[0] && exState[1] == state[1]) {
                            // this is a poor guess for if total blocks and
                            // available blocks are the same then we think they
                            // are the same partition.
                            state = null;
                        }
                    }
                }
            }
        }

        return state;
    }

    private String[] getInternalStorageInfo() {
        return getStorageInfo(Environment.getDataDirectory());
    }

    private String[] getSystemStorageInfo() {
        return getStorageInfo(Environment.getRootDirectory());
    }

    private String[] getCacheStorageInfo() {
        return getStorageInfo(Environment.getDownloadCacheDirectory());
    }

    private String[] getStorageInfo(File path) {
        return getStorageInfo(Util.getStorageState(path));
    }

    private String[] getStorageInfo(long[] state) {
        if (state == null) {
            return null;
        }

        String[] result = new String[state.length];

        for (int i = 0, size = result.length; i < size; i++) {
            result[i] = Util.safeFormatFileSize(this, state[i]);
        }

        return result;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String prefKey = preference.getKey();

        if (KEY_VIEW_PROPS.equals(prefKey)) {
            Intent it = new Intent(this, Util.getIntentProxyClz(PropertiesViewer.class));
            startActivityForResult(it, REQUEST_INVOKE);

            return true;
        } else if (KEY_NETWORK.equals(prefKey)) {
            Intent it = new Intent(this, NetworkInfoActivity.class);
            startActivityForResult(it, REQUEST_INVOKE);

            return true;
        } else if (KEY_PROCESSOR.equals(prefKey)) {
            Intent it = new Intent(this, CpuInfoActivity.class);
            startActivityForResult(it, REQUEST_INVOKE);

            return true;
        } else if (KEY_MEMORY.equals(prefKey)) {
            Intent it = new Intent(this, MemInfoActivity.class);
            startActivityForResult(it, REQUEST_INVOKE);

            return true;
        } else if (KEY_SD_STORAGE.equals(prefKey)) {
            String ms = Environment.getExternalStorageState();

            if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(ms) || Environment.MEDIA_MOUNTED.equals(ms)) {
                File ext = Environment.getExternalStorageDirectory();
                long[] state = Util.getStorageState(ext);

                Intent it = new Intent(this, StorageInfoActivity.class);
                it.putExtra(StorageInfoActivity.EXTRA_STATE, state);
                it.putExtra(StorageInfoActivity.EXTRA_PRIMARY_SD, true);
                if (ext != null) {
                    it.putExtra(StorageInfoActivity.EXTRA_SD_PATH, ext.getAbsolutePath());
                }
                startActivityForResult(it, REQUEST_INVOKE);
            }

            return true;
        } else if (KEY_SECONDARY_SD_STORAGE.equals(prefKey)) {
            Object[] state = getSecondarySDStorageState();

            Intent it = new Intent(this, StorageInfoActivity.class);
            if (state != null) {
                it.putExtra(StorageInfoActivity.EXTRA_STATE, (long[]) state[0]);
                it.putExtra(StorageInfoActivity.EXTRA_SD_PATH, (String) state[1]);
            }
            startActivityForResult(it, REQUEST_INVOKE);

            return true;
        } else if (KEY_A2SD_STORAGE.equals(prefKey)) {
            long[] state = getA2SDStorageState();

            Intent it = new Intent(this, StorageInfoActivity.class);
            it.putExtra(StorageInfoActivity.EXTRA_STATE, state);
            startActivityForResult(it, REQUEST_INVOKE);

            return true;
        } else if (KEY_SYSTEM_STORAGE.equals(prefKey)) {
            long[] state = Util.getStorageState(Environment.getRootDirectory());

            Intent it = new Intent(this, StorageInfoActivity.class);
            it.putExtra(StorageInfoActivity.EXTRA_STATE, state);
            startActivityForResult(it, REQUEST_INVOKE);

            return true;
        } else if (KEY_INTERNAL_STORAGE.equals(prefKey)) {
            long[] state = Util.getStorageState(Environment.getDataDirectory());

            Intent it = new Intent(this, StorageInfoActivity.class);
            it.putExtra(StorageInfoActivity.EXTRA_STATE, state);
            it.putExtra(StorageInfoActivity.EXTRA_ACTION_LABEL, ResUtil.getString(this, R.string.details));
            it.putExtra(StorageInfoActivity.EXTRA_ACTION_CLASS,
                    "com.android.settings.Settings$StorageSettingsActivity"); //$NON-NLS-1$

            startActivityForResult(it, REQUEST_INVOKE);

            return true;
        } else if (KEY_CACHE_STORAGE.equals(prefKey)) {
            long[] state = Util.getStorageState(Environment.getDownloadCacheDirectory());

            Intent it = new Intent(this, StorageInfoActivity.class);
            it.putExtra(StorageInfoActivity.EXTRA_STATE, state);
            startActivityForResult(it, REQUEST_INVOKE);

            return true;
        } else if (KEY_BATTERY.equals(prefKey)) {
            Intent it = new Intent(this, BatteryInfoActivity.class);
            startActivityForResult(it, REQUEST_INVOKE);

            return true;
        } else if (KEY_SENSORS.equals(prefKey)) {
            Intent it = new Intent(this, SensorInfoActivity.class);
            startActivityForResult(it, REQUEST_INVOKE);

            return true;
        } else if (KEY_INPUT.equals(prefKey)) {
            Intent it = new Intent(this, InputInfoActivity.class);
            startActivityForResult(it, REQUEST_INVOKE);

            return true;
        } else if (KEY_USB.equals(prefKey)) {
            Intent it = new Intent(this, UsbInfoActivity.class);
            startActivityForResult(it, REQUEST_INVOKE);

            return true;
        }
        //		else if ( "gps".equals( prefKey ) ) //$NON-NLS-1$
        // {
        // int[] gs = getGpsState( );
        //
        // if ( gs != null )
        // {
        // if ( gs[1] == -1 )
        // {
        //					Intent it = new Intent( "android.settings.LOCATION_SOURCE_SETTINGS" ); //$NON-NLS-1$
        // startActivityForResult( it, REQUEST_INVOKE );
        // }
        // else
        // {
        // Intent it = new Intent( this, GpsInfoActivity.class );
        // startActivityForResult( it, REQUEST_INVOKE );
        // }
        // }
        //
        // return true;
        // }
        else if (KEY_REFRESH.equals(prefKey)) {
            updateInfo();
            return true;
        } else if (KEY_VIEW_LOGS.equals(prefKey)) {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    if (which == 0) {
                        showLog(true);
                    } else {
                        showLog(false);
                    }
                }
            };

            Util.newAlertDialogBuilder(this).setTitle(ResUtil.getString(this, R.string.view_logs))
                    .setItems(new CharSequence[]{"Dmesg", "Logcat" //$NON-NLS-1$ //$NON-NLS-2$
                    }, listener).create().show();
            return true;
        } else if (KEY_SEND_REPORT.equals(prefKey)) {
            final boolean[] items;

            // TODO add ui for setting
            final boolean rememberShareSettigns =
                    Util.getBooleanOption(this, SYSINFO_MANAGER_STORE_NAME,
                            PREF_KEY_REMEMBER_LAST_SHARE_SETTING, true);

            if (rememberShareSettigns) {
                items =
                        Util.getBits(Util.getStringOption(this, SYSINFO_MANAGER_STORE_NAME,
                                PREF_KEY_LAST_SHARE_SETTING, null), 6, true);
            } else {
                items = new boolean[]{true, true, true, true, true, true};
            }

            OnMultiChoiceClickListener selListener = new OnMultiChoiceClickListener() {

                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    items[which] = isChecked;
                }
            };

            OnClickListener sendListener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    boolean hasContent = false;

                    for (boolean b : items) {
                        if (b) {
                            hasContent = true;
                            break;
                        }
                    }

                    if (!hasContent) {
                        Util.shortToast(SysInfoManager.this, R.string.no_report_item);

                        return;
                    }

                    if (rememberShareSettigns) {
                        Util.setStringOption(SysInfoManager.this, SYSINFO_MANAGER_STORE_NAME,
                                PREF_KEY_LAST_SHARE_SETTING, Util.getBitsString(items));
                    }

                    final FormatArrayAdapter adapter =
                            new FormatArrayAdapter(SysInfoManager.this, R.layout.send_item, new FormatItem[]{
                                    new FormatItem(ResUtil.getString(SysInfoManager.this, R.string.plain_text)),
                                    new FormatItem(ResUtil.getString(SysInfoManager.this, R.string.html)),});

                    OnClickListener listener = new OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            FormatItem fi = adapter.getItem(which);

                            sendReport(items, which, fi.compressed);
                        }
                    };

                    Util.newAlertDialogBuilder(SysInfoManager.this)
                            .setTitle(ResUtil.getString(SysInfoManager.this, R.string.send_report))
                            .setAdapter(adapter, listener).setInverseBackgroundForced(true).create().show();
                }
            };

            Util.newAlertDialogBuilder(this)
                    .setTitle(ResUtil.getString(this, R.string.send_report))
                    .setMultiChoiceItems(
                            new CharSequence[]{ResUtil.getString(this, R.string.tab_info),
                                    ResUtil.getString(this, R.string.tab_apps),
                                    ResUtil.getString(this, R.string.tab_procs),
                                    ResUtil.getString(this, R.string.tab_netstat),
                                    "Dmesg " + ResUtil.getString(this, R.string.log), //$NON-NLS-1$
                                    "Logcat " + ResUtil.getString(this, R.string.log) //$NON-NLS-1$
                            }, items, selListener).setPositiveButton(android.R.string.ok, sendListener)
                    .setNegativeButton(android.R.string.cancel, null).create().show();

            return true;
        } else if (KEY_MORE_INFO.equals(prefKey)) {
            Intent it = getAboutSettingsIntent();

            if (it != null) {
                Util.safeStartActivity(this, it, false);
            } else {
                Log.d(SysInfoManager.class.getName(), "Failed to resolve activity for DeviceInfoSettings"); //$NON-NLS-1$
            }
            return true;
        }

        return false;
    }

    private Intent getAboutSettingsIntent() {
        Intent it = new Intent(Intent.ACTION_VIEW);

        // try the htc specific settings first to avoid some broken manifest
        // issue on certain htc models
        it.setClassName("com.android.settings", //$NON-NLS-1$
                "com.android.settings.framework.aboutphone.HtcAboutPhoneSettings"); //$NON-NLS-1$

        List<ResolveInfo> acts = getPackageManager().queryIntentActivities(it, 0);

        if (acts.size() > 0) {
            return it;
        } else {
            // try the standard settings
            it.setClassName("com.android.settings", //$NON-NLS-1$
                    "com.android.settings.DeviceInfoSettings"); //$NON-NLS-1$

            acts = getPackageManager().queryIntentActivities(it, 0);

            if (acts.size() > 0) {
                return it;
            } else {
                // try the 3.1 fragment style
                it.setClassName("com.android.settings", //$NON-NLS-1$
                        "com.android.settings.Settings$DeviceInfoSettingsActivity"); //$NON-NLS-1$

                acts = getPackageManager().queryIntentActivities(it, 0);

                if (acts.size() > 0) {
                    return it;
                } else {
                    // try google tv settings
                    it.setClassName("com.google.tv.settings", //$NON-NLS-1$
                            "com.google.tv.settings.AboutBoxSettings"); //$NON-NLS-1$

                    acts = getPackageManager().queryIntentActivities(it, 0);

                    if (acts.size() > 0) {
                        return it;
                    }
//                    else{
//                        // try android wear settings
//                        it.setClassName("com.google.android.apps.wearable.settings", //$NON-NLS-1$
//                                "com.google.android.clockwork.settings.SettingsActivity"); //$NON-NLS-1$
//
//                        acts = getPackageManager().queryIntentActivities(it, 0);
//
//                        if (acts.size() > 0) {
//                            return it;
//                        }
//                    }
                }
            }
        }

        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PREFERENCES) {
            if (resultCode == RESULT_OK && data != null) {
                EditorState es = Util.beginEditOption(this, SYSINFO_MANAGER_STORE_NAME);

                Util.updateBooleanOption(data, es, PREF_KEY_SHOW_INFO_ICON);
                Util.updateBooleanOption(data, es, PREF_KEY_SHOW_TASK_ICON);
                Util.updateBooleanOption(data, es, PREF_KEY_SHOW_CPU_MONITOR);
                Util.updateBooleanOption(data, es, PREF_KEY_SHOW_MEM_MONITOR);
                Util.updateBooleanOption(data, es, PREF_KEY_SHOW_CPU_HISTORY);
                Util.updateBooleanOption(data, es, PREF_KEY_SHOW_MEM_HISTORY);
                Util.updateBooleanOption(data, es, PREF_KEY_SHOW_WIFI_ACTIVITY);
                Util.updateBooleanOption(data, es, PREF_KEY_SHOW_WIFI_RATES);
                Util.updateBooleanOption(data, es, PREF_KEY_SHOW_WIFI_SSID);

                boolean resetInfoIcon = Util.updateBooleanOption(data, es, PREF_KEY_SHOW_BAR_ICON_INFO);
                boolean resetTaskIcon = Util.updateBooleanOption(data, es, PREF_KEY_SHOW_BAR_ICON_TASK);
                boolean resetWifiIcon = Util.updateBooleanOption(data, es, PREF_KEY_SHOW_BAR_ICON_WIFI);
                boolean resetAllIcon = Util.updateBooleanOption(data, es, PREF_KEY_DISABLE_ALL_ICON, false);
                boolean useLegacyIcon = Util.updateBooleanOption(data, es, PREF_KEY_USE_LEGACY_ICON, false);
                boolean needResetService = resetInfoIcon || resetTaskIcon || resetWifiIcon || resetAllIcon || useLegacyIcon;

                Util.updateBooleanOption(data, es, PREF_KEY_SHOW_BATTERY_INFO);
                Util.updateBooleanOption(data, es, PREF_KEY_INVERSE_NOTIFY_TITLE_COLOR,
                        Util.INVERT_TITLE_COLOR_DEFAULT_SETTING);
                Util.updateBooleanOption(data, es, PREF_KEY_HIGH_PRIORITY);
                Util.updateIntOption(data, es, PREF_KEY_REFRESH_INTERVAL_CPU, REFRESH_LOW);
                Util.updateIntOption(data, es, PREF_KEY_REFRESH_INTERVAL_MEM, REFRESH_LOW);
                Util.updateIntOption(data, es, PREF_KEY_REFRESH_INTERVAL_NET, REFRESH_LOW);
                Util.updateBooleanOption(data, es, PREF_KEY_PERSISTENT_ICON);
                Util.updateBooleanOption(data, es, PREF_KEY_AUTO_START_ICON, false);
                Util.updateStringOption(data, es, PREF_KEY_DEFAULT_EMAIL);
                Util.updateIntOption(data, es, PREF_KEY_DEFAULT_TAB, 0);
                Util.updateStringOption(data, es, PREF_KEY_WIDGET_DISABLED);

                boolean localeChanged = Util.updateStringOption(data, es, PREF_KEY_USER_LOCALE);

                Util.endEditOption(es);

                if (localeChanged) {
                    ResUtil.configureResources(this, getResources().getConfiguration());

                    finish();

                    Intent it =
                            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                                    .setClass(this, Util.getIntentProxyClz(QSystemInfo.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);

                    PropertiesViewer.overridePendingTransition(this, 0, 0);

                    startActivity(it);
                }

                // force refresh the icon states
                if (needResetService && Util.SDK_VER >= 16) {
                    // only stop service when icon visibility settings changes
                    stopService(new Intent(this, StatusUpdaterService.class));
                }
                startService(new Intent(this, StatusUpdaterService.class));
            } else if (resultCode == RESULT_PREFERENCES_IMPORTED) {
                // force refresh the icon states
                if (Util.SDK_VER >= 16) {
                    // always stop service to reload icon visibility settings
                    stopService(new Intent(this, StatusUpdaterService.class));
                }
                startService(new Intent(this, StatusUpdaterService.class));
            }
        } else if (requestCode == REQUEST_SCAN_RESULT) {
            String result = null;

            if (resultCode == RESULT_OK && data != null) {
                result = data.getStringExtra("SCAN_RESULT"); //$NON-NLS-1$
            }

            showUnlock(result);
        } else if (requestCode == REQUEST_GPU_INFO) {
            if (data != null) {
                Util.finishStub(this, data.getIntExtra(EXTRA_PID, 0));

                CpuState.setGpuInfo(data.getStringExtra(PropertiesViewer.EXTRA_GLVENDOR),
                        data.getStringExtra(PropertiesViewer.EXTRA_GLRENDERER));

                findPreference(KEY_PROCESSOR).setSummary(getCpuInfo());
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem mi = menu.add(Menu.NONE, MI_ABOUT, Menu.NONE, ResUtil.getString(this, R.string.about));
        mi.setIcon(android.R.drawable.ic_menu_info_details);

        mi = menu.add(Menu.NONE, MI_HELP, Menu.NONE, ResUtil.getString(this, R.string.help));
        mi.setIcon(android.R.drawable.ic_menu_help);

        mi = menu.add(Menu.NONE, MI_PRIVACY, Menu.NONE, ResUtil.getString(this, R.string.privacy));
        mi.setIcon(android.R.drawable.ic_menu_compass);

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
        List<ActionHint> hints = new ArrayList<ActionHint>();
        hints.add(new ActionHint(ResUtil.getString(this, R.string.about), MI_ABOUT));
        hints.add(new ActionHint(ResUtil.getString(this, R.string.help), MI_HELP));
        hints.add(new ActionHint(ResUtil.getString(this, R.string.privacy), MI_PRIVACY));
        hints.add(new ActionHint(ResUtil.getString(this, R.string.preference), MI_PREFERENCE));
        hints.add(new ActionHint(ResUtil.getString(this, R.string.exit), MI_EXIT));
        return hints;
    }

    @Override
    public boolean onActionSelected(int action) {
        if (action == MI_PREFERENCE) {
            Intent it = new Intent(this, Util.getIntentProxyClz(InfoSettings.class));

            SharedPreferences prefStore =
                    getSharedPreferences(SYSINFO_MANAGER_STORE_NAME, Context.MODE_PRIVATE);

            it.putExtra(PREF_KEY_SHOW_INFO_ICON,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_INFO_ICON));
            it.putExtra(PREF_KEY_SHOW_TASK_ICON,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_TASK_ICON));
            it.putExtra(PREF_KEY_SHOW_CPU_MONITOR,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_CPU_MONITOR));
            it.putExtra(PREF_KEY_SHOW_MEM_MONITOR,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_MEM_MONITOR));
            it.putExtra(PREF_KEY_SHOW_CPU_HISTORY,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_CPU_HISTORY));
            it.putExtra(PREF_KEY_SHOW_MEM_HISTORY,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_MEM_HISTORY));
            it.putExtra(PREF_KEY_SHOW_WIFI_ACTIVITY,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_WIFI_ACTIVITY));
            it.putExtra(PREF_KEY_SHOW_WIFI_RATES,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_WIFI_RATES));
            it.putExtra(PREF_KEY_SHOW_WIFI_SSID,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_WIFI_SSID));
            it.putExtra(PREF_KEY_SHOW_BAR_ICON_INFO,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_BAR_ICON_INFO));
            it.putExtra(PREF_KEY_SHOW_BAR_ICON_TASK,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_BAR_ICON_TASK));
            it.putExtra(PREF_KEY_SHOW_BAR_ICON_WIFI,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_BAR_ICON_WIFI));
            it.putExtra(PREF_KEY_SHOW_BATTERY_INFO,
                    Util.getBooleanOption(prefStore, PREF_KEY_SHOW_BATTERY_INFO));
            it.putExtra(PREF_KEY_INVERSE_NOTIFY_TITLE_COLOR, Util.getBooleanOption(prefStore,
                    PREF_KEY_INVERSE_NOTIFY_TITLE_COLOR, Util.INVERT_TITLE_COLOR_DEFAULT_SETTING));
            it.putExtra(PREF_KEY_HIGH_PRIORITY, Util.getBooleanOption(prefStore, PREF_KEY_HIGH_PRIORITY));
            it.putExtra(PREF_KEY_REFRESH_INTERVAL_CPU,
                    Util.getIntOption(prefStore, PREF_KEY_REFRESH_INTERVAL_CPU, REFRESH_LOW));
            it.putExtra(PREF_KEY_REFRESH_INTERVAL_MEM,
                    Util.getIntOption(prefStore, PREF_KEY_REFRESH_INTERVAL_MEM, REFRESH_LOW));
            it.putExtra(PREF_KEY_REFRESH_INTERVAL_NET,
                    Util.getIntOption(prefStore, PREF_KEY_REFRESH_INTERVAL_NET, REFRESH_LOW));
            it.putExtra(PREF_KEY_PERSISTENT_ICON,
                    Util.getBooleanOption(prefStore, PREF_KEY_PERSISTENT_ICON));
            it.putExtra(PREF_KEY_AUTO_START_ICON,
                    Util.getBooleanOption(prefStore, PREF_KEY_AUTO_START_ICON, false));
            it.putExtra(PREF_KEY_DISABLE_ALL_ICON,
                    Util.getBooleanOption(prefStore, PREF_KEY_DISABLE_ALL_ICON, false));
            it.putExtra(PREF_KEY_USE_LEGACY_ICON,
                    Util.getBooleanOption(prefStore, PREF_KEY_USE_LEGACY_ICON, false));
            it.putExtra(PREF_KEY_DEFAULT_EMAIL,
                    Util.getStringOption(prefStore, PREF_KEY_DEFAULT_EMAIL, null));
            it.putExtra(PREF_KEY_DEFAULT_TAB, Util.getIntOption(prefStore, PREF_KEY_DEFAULT_TAB, 0));
            it.putExtra(PREF_KEY_WIDGET_DISABLED,
                    Util.getStringOption(prefStore, PREF_KEY_WIDGET_DISABLED, null));
            it.putExtra(PREF_KEY_USER_LOCALE, Util.getStringOption(prefStore, PREF_KEY_USER_LOCALE, null));

            startActivityForResult(it, REQUEST_PREFERENCES);

            return true;
        } else if (action == MI_UNLOCK) {
            showUnlock(null);

            return true;
        } else if (action == MI_HELP) {
            Intent it = new Intent(Intent.ACTION_VIEW);

            String target = "https://github.com/qauck/qsysinfo/wiki/FeaturesTextOnly"; //$NON-NLS-1$

            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo info = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            if (info != null && info.isConnected()) {
                target = "https://github.com/qauck/qsysinfo/wiki/Features"; //$NON-NLS-1$
            }

            it.setData(Uri.parse(target));

            it = Intent.createChooser(it, null);

            Util.safeStartActivity(this, it, false);

            return true;
        } else if (action == MI_PRIVACY) {
            Intent it = new Intent(Intent.ACTION_VIEW);

            String target = "http://qsysinfo.appspot.com/privacy.jsp"; //$NON-NLS-1$
            it.setData(Uri.parse(target));

            it = Intent.createChooser(it, null);

            Util.safeStartActivity(this, it, false);

            return true;
        } else if (action == MI_ABOUT) {
            showAbout();
            return true;
        } else if (action == MI_EXIT) {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    Util.killSelf(handler, SysInfoManager.this,
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

    protected void showAbout() {
        ScrollView sv = new ScrollView(this);

        TextView txt = new TextView(this);
        txt.setGravity(Gravity.CENTER_HORIZONTAL);
        txt.setTextAppearance(this, android.R.style.TextAppearance_Medium);

        sv.addView(txt);

        txt.setText(Html.fromHtml(ResUtil.getString(this, R.string.about_msg_verified_s,
                Util.getVersionName(getPackageManager(), getPackageName()), null)));

        txt.setMovementMethod(LinkMovementMethod.getInstance());

        new AlertDialog.Builder(this).setTitle(ResUtil.getString(this, ProUtil.getId_appName()))
                .setIcon(R.drawable.icon_m).setView(sv)
                .setNegativeButton(ResUtil.getString(this, R.string.close), null).create().show();
    }

    private void showUnlock(String data) {
        AtomicReference<ProgressDialog> pref = new AtomicReference(progress);
        progress = pref.get();
    }

    void showLog(boolean dmesg) {
        Intent it = new Intent(this, Util.getIntentProxyClz(LogViewer.class));
        it.putExtra(LogViewer.DMESG_MODE, dmesg);

        startActivityForResult(it, REQUEST_INVOKE);
    }

    void sendReport(final boolean[] items, final int format, final boolean compressed) {
        if (progress != null) {
            Util.safeDismissDialog(progress);
        }
        progress = new ProgressDialog(this);
        progress.setMessage(ResUtil.getString(this, R.string.loading));
        progress.setIndeterminate(true);
        progress.show();

        new Thread(new Runnable() {

            public void run() {
                String content = null;

                switch (format) {
                    case PLAINTEXT:
                        content = generateTextReport(items);
                        break;
                    case HTML:
                        content = generateHtmlReport(items);
                        break;
                }

                if (content != null && compressed) {
                    content =
                            Util.createCompressedContent(handler, SysInfoManager.this, content, format,
                                    "android_report"); //$NON-NLS-1$
                }

                if (aborted) {
                    return;
                }

                if (content != null && !compressed) {
                    handler.sendMessage(handler.obtainMessage(MSG_CHECK_FORCE_COMPRESSION, format,
                            compressed ? 1 : 0, content));
                } else {
                    handler.sendMessage(handler.obtainMessage(MSG_CONTENT_READY, format, compressed ? 1 : 0,
                            content));
                }
            }
        }).start();
    }

    String generateTextReport(boolean[] items) {
        StringBuffer sb = new StringBuffer();

        Util.createTextHeader(this, sb, "Android System Report - " //$NON-NLS-1$
                + new Date().toLocaleString());

        if (items[BASIC_INFO]) {
            sb.append(ResUtil.getString(this, R.string.tab_info)).append('\n');
            sb.append(HEADER_SPLIT);

            sb.append("* ") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.sd_storage)).append("\n\t"); //$NON-NLS-1$

            String[] info = getExternalStorageInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[1]));
            }
            sb.append("\n\n"); //$NON-NLS-1$

            sb.append("* ") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.secondary_sd_storage)).append("\n\t"); //$NON-NLS-1$

            info = getSecondarySDStorageInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[1]));
            }
            sb.append("\n\n"); //$NON-NLS-1$

            sb.append("* ") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.a2sd_storage)).append("\n\t"); //$NON-NLS-1$

            info = getA2SDStorageInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[1]));
            }
            sb.append("\n\n"); //$NON-NLS-1$

            sb.append("* ") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.internal_storage)).append("\n\t"); //$NON-NLS-1$

            info = getInternalStorageInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[1]));
            }
            sb.append("\n\n"); //$NON-NLS-1$

            sb.append("* ") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.system_storage)).append("\n\t"); //$NON-NLS-1$

            info = getSystemStorageInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[1]));
            }
            sb.append("\n\n"); //$NON-NLS-1$

            sb.append("* ") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.cache_storage)).append("\n\t"); //$NON-NLS-1$

            info = getCacheStorageInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[1]));
            }
            sb.append("\n\n"); //$NON-NLS-1$

            sb.append("* ") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.memory)).append("\n\t"); //$NON-NLS-1$

            info = getMemInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[2])
                        + ResUtil.getString(this, R.string.idle_info, info[1]));
            }
            sb.append("\n\n"); //$NON-NLS-1$

            sb.append("* ") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.processor)).append("\n\t") //$NON-NLS-1$
                    .append(getCpuInfo()).append("\n\n"); //$NON-NLS-1$

            String nInfo = Util.getNetAddressInfo();
            sb.append("* ") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.net_address)).append("\n\t") //$NON-NLS-1$
                    .append(nInfo == null ? ResUtil.getString(this, R.string.info_not_available) : nInfo)
                    .append("\n\n"); //$NON-NLS-1$

            sb.append('\n');

            try {
                File f = new File(CpuState.F_SCALE_FREQ);
                if (f.exists()) {
                    sb.append(ResUtil.getString(this, R.string.sc_freq));

                    Util.readRawText(sb, new FileInputStream(f), 32);
                } else {
                    sb.append(ResUtil.getString(this, R.string.no_sc_freq_info)).append('\n');
                }

                sb.append('\n');
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            try {
                File f = new File(CpuState.F_CPU_INFO);
                if (f.exists()) {
                    Util.readRawText(sb, new FileInputStream(f), 1024);
                } else {
                    sb.append(ResUtil.getString(this, R.string.no_cpu_info)).append('\n');
                }

                sb.append('\n');
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            try {
                File f = new File(F_MEM_INFO);
                if (f.exists()) {
                    Util.readRawText(sb, new FileInputStream(f), 1024);
                } else {
                    sb.append(ResUtil.getString(this, R.string.no_mem_info)).append('\n');
                }

                sb.append('\n');
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            try {
                File f = new File(F_MOUNT_INFO);
                if (f.exists()) {
                    Util.readRawText(sb, new FileInputStream(f), 2048);
                } else {
                    sb.append(ResUtil.getString(this, R.string.no_mount_info)).append('\n');
                }

                sb.append('\n');
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            try {
                Process proc = Runtime.getRuntime().exec("df"); //$NON-NLS-1$

                Util.readRawText(sb, proc.getInputStream(), 2048);

                sb.append('\n');
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        if (items[APPLICATIONS]) {
            sb.append(ResUtil.getString(this, R.string.tab_apps)).append('\n');
            sb.append(HEADER_SPLIT);

            PackageManager pm = getPackageManager();
            List<PackageInfo> pkgs = pm.getInstalledPackages(0);

            if (pkgs != null) {
                for (int i = 0, size = pkgs.size(); i < size; i++) {
                    PackageInfo pkg = pkgs.get(i);

                    sb.append(pkg.packageName).append(" <") //$NON-NLS-1$
                            .append(pkg.versionName).append(" (") //$NON-NLS-1$
                            .append(pkg.versionCode).append(")>"); //$NON-NLS-1$

                    if (pkg.applicationInfo != null) {
                        sb.append("\t: ") //$NON-NLS-1$
                                .append(pm.getApplicationLabel(pkg.applicationInfo)).append(" | ") //$NON-NLS-1$
                                .append(pkg.applicationInfo.flags).append(" | ") //$NON-NLS-1$
                                .append(pkg.applicationInfo.sourceDir);
                    }

                    sb.append('\n');
                }
            }

            sb.append('\n');
        }

        if (items[PROCESSES]) {
            sb.append(ResUtil.getString(this, R.string.tab_procs)).append('\n');
            sb.append(HEADER_SPLIT);

            List<ProcessInfo> procs = ProcessManager.getRunningProcessInfo(this, false, false);

            if (procs != null) {
                PackageManager pm = getPackageManager();

                for (int i = 0, size = procs.size(); i < size; i++) {
                    ProcessInfo proc = procs.get(i);

                    sb.append('<').append(getImportance(proc)).append("> [") //$NON-NLS-1$
                            .append(proc.pid).append("]\t:\t"); //$NON-NLS-1$

                    sb.append(proc.processName);

                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(proc.processName, 0);

                        if (ai != null) {
                            CharSequence label = pm.getApplicationLabel(ai);

                            if (label != null && !label.equals(proc.processName)) {
                                sb.append(" ( ") //$NON-NLS-1$
                                        .append(label).append(" )"); //$NON-NLS-1$
                            }
                        }
                    } catch (NameNotFoundException e) {
                        // ignore this error
                    }

                    sb.append('\n');
                }
            }

            sb.append('\n');
        }

        if (items[NETSTATES]) {
            sb.append(ResUtil.getString(this, R.string.tab_netstat)).append('\n');
            sb.append(HEADER_SPLIT);

            try {
                Util.readRawText(sb, new FileInputStream("/proc/net/tcp"), 2048); //$NON-NLS-1$
                sb.append('\n');
                Util.readRawText(sb, new FileInputStream("/proc/net/udp"), 2048); //$NON-NLS-1$
                sb.append('\n');
                Util.readRawText(sb, new FileInputStream("/proc/net/tcp6"), 2048); //$NON-NLS-1$
                sb.append('\n');
                Util.readRawText(sb, new FileInputStream("/proc/net/udp6"), 2048); //$NON-NLS-1$
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            sb.append('\n');
        }

        if (items[DMESG_LOG]) {
            sb.append("Dmesg " + ResUtil.getString(this, R.string.log)).append('\n'); //$NON-NLS-1$
            sb.append(HEADER_SPLIT);

            try {
                Process proc = Runtime.getRuntime().exec("dmesg"); //$NON-NLS-1$

                Util.readRawText(sb, proc.getInputStream(), 8192);
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            sb.append('\n');
        }

        if (items[LOGCAT_LOG]) {
            sb.append("Logcat " + ResUtil.getString(this, R.string.log)).append('\n'); //$NON-NLS-1$
            sb.append(HEADER_SPLIT);

            try {
                Process proc = Runtime.getRuntime().exec("logcat -d -v time *:V"); //$NON-NLS-1$

                Util.readRawText(sb, proc.getInputStream(), 8192);
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            sb.append('\n');
        }

        return sb.toString();
    }

    String generateHtmlReport(boolean[] items) {
        StringBuffer sb = new StringBuffer();

        Util.createHtmlHeader(this, sb, Util.escapeHtml("Android System Report - " + new Date().toLocaleString())); //$NON-NLS-1$

        if (items[BASIC_INFO]) {
            sb.append(openHeaderRow).append(ResUtil.getString(this, R.string.tab_info))
                    .append(closeHeaderRow);

            sb.append(openRow).append(ResUtil.getString(this, R.string.sd_storage)).append(nextColumn4);

            String[] info = getExternalStorageInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[1]));
            }
            sb.append(closeRow);

            sb.append(openRow).append(ResUtil.getString(this, R.string.secondary_sd_storage))
                    .append(nextColumn4);

            info = getSecondarySDStorageInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[1]));
            }
            sb.append(closeRow);

            sb.append(openRow).append(ResUtil.getString(this, R.string.a2sd_storage)).append(nextColumn4);

            info = getA2SDStorageInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[1]));
            }
            sb.append(closeRow);

            sb.append(openRow).append(ResUtil.getString(this, R.string.internal_storage))
                    .append(nextColumn4);

            info = getInternalStorageInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[1]));
            }
            sb.append(closeRow);

            sb.append(openRow).append(ResUtil.getString(this, R.string.system_storage))
                    .append(nextColumn4);

            info = getSystemStorageInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[1]));
            }
            sb.append(closeRow);

            sb.append(openRow).append(ResUtil.getString(this, R.string.cache_storage))
                    .append(nextColumn4);

            info = getCacheStorageInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[1]));
            }
            sb.append(closeRow);

            sb.append(openRow).append(ResUtil.getString(this, R.string.memory)).append(nextColumn4);

            info = getMemInfo();
            if (info == null) {
                sb.append(ResUtil.getString(this, R.string.info_not_available));
            } else {
                sb.append(ResUtil.getString(this, R.string.storage_summary, info[0], info[2])
                        + ResUtil.getString(this, R.string.idle_info, info[1]));
            }
            sb.append(closeRow);

            sb.append(openRow).append(ResUtil.getString(this, R.string.processor)).append(nextColumn4)
                    .append(Util.escapeHtml(getCpuInfo())).append(closeRow);

            String nInfo = Util.getNetAddressInfo();
            sb.append(openRow).append(ResUtil.getString(this, R.string.net_address)).append(nextColumn4)
                    .append(nInfo == null ? ResUtil.getString(this, R.string.info_not_available) : nInfo)
                    .append(closeRow);

            sb.append(emptyRow);

            try {
                File f = new File(CpuState.F_SCALE_FREQ);
                if (f.exists()) {
                    sb.append(openFullRow).append(ResUtil.getString(this, R.string.sc_freq));

                    Util.readRawText(sb, new FileInputStream(f), 32);

                    sb.append(closeRow);
                } else {
                    sb.append(openFullRow).append(ResUtil.getString(this, R.string.no_sc_freq_info))
                            .append(closeRow);
                }

                sb.append(emptyRow);
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            try {
                File f = new File(CpuState.F_CPU_INFO);
                if (f.exists()) {
                    Util.readRawHTML(sb, new FileInputStream(f), 1024);
                } else {
                    sb.append(openFullRow).append(ResUtil.getString(this, R.string.no_cpu_info))
                            .append(closeRow);
                }

                sb.append(emptyRow);
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            try {
                File f = new File(F_MEM_INFO);
                if (f.exists()) {
                    Util.readRawHTML(sb, new FileInputStream(f), 1024);
                } else {
                    sb.append(openFullRow).append(ResUtil.getString(this, R.string.no_mem_info))
                            .append(closeRow);
                }

                sb.append(emptyRow);
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            try {
                File f = new File(F_MOUNT_INFO);
                if (f.exists()) {
                    Util.readRawHTML(sb, new FileInputStream(f), 2048);
                } else {
                    sb.append(openFullRow).append(ResUtil.getString(this, R.string.no_mount_info))
                            .append(closeRow);
                }

                sb.append(emptyRow);
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            try {
                Process proc = Runtime.getRuntime().exec("df"); //$NON-NLS-1$

                Util.readRawHTML(sb, proc.getInputStream(), 2048);

                sb.append(emptyRow);
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        if (items[APPLICATIONS]) {
            sb.append(openHeaderRow).append(ResUtil.getString(this, R.string.tab_apps))
                    .append(closeHeaderRow);

            sb.append(openTitleRow).append("<b>") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.pkg_name)).append("</b>") //$NON-NLS-1$
                    .append(nextColumn).append("<b>") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.version)).append("</b>") //$NON-NLS-1$
                    .append(nextColumn).append("<b>") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.app_label)).append("</b>") //$NON-NLS-1$
                    .append(nextColumn).append("<b>") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.flags)).append("</b>") //$NON-NLS-1$
                    .append(nextColumn).append("<b>") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.source)).append("</b>") //$NON-NLS-1$
                    .append(closeRow);

            PackageManager pm = getPackageManager();
            List<PackageInfo> pkgs = pm.getInstalledPackages(0);

            if (pkgs != null) {
                for (int i = 0, size = pkgs.size(); i < size; i++) {
                    PackageInfo pkg = pkgs.get(i);

                    sb.append(openRow).append(Util.escapeHtml(pkg.packageName)).append(nextColumn)
                            .append(Util.escapeHtml(pkg.versionName)).append(" (") //$NON-NLS-1$
                            .append(pkg.versionCode).append(')');

                    if (pkg.applicationInfo != null) {
                        sb.append(nextColumn)
                                .append(Util.escapeHtml(pm.getApplicationLabel(pkg.applicationInfo).toString()))
                                .append(nextColumn).append(pkg.applicationInfo.flags).append(nextColumn)
                                .append(Util.escapeHtml(pkg.applicationInfo.sourceDir));
                    }

                    sb.append(closeRow);
                }
            }

            sb.append(emptyRow);
        }

        if (items[PROCESSES]) {
            sb.append(openHeaderRow).append(ResUtil.getString(this, R.string.tab_procs))
                    .append(closeHeaderRow);

            sb.append(openTitleRow).append("<b>") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.importance)).append("</b>") //$NON-NLS-1$
                    .append(nextColumn).append("<b>") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.pid)).append("</b>") //$NON-NLS-1$
                    .append(nextColumn).append("<b>") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.proc_name)).append("</b>") //$NON-NLS-1$
                    .append(nextColumn).append("<b>") //$NON-NLS-1$
                    .append(ResUtil.getString(this, R.string.app_label)).append("</b>") //$NON-NLS-1$
                    .append(closeRow);

            List<ProcessInfo> procs = ProcessManager.getRunningProcessInfo(this, false, false);

            if (procs != null) {
                PackageManager pm = getPackageManager();

                for (int i = 0, size = procs.size(); i < size; i++) {
                    ProcessInfo proc = procs.get(i);

                    sb.append(openRow).append(getImportance(proc)).append(nextColumn).append(proc.pid)
                            .append(nextColumn).append(Util.escapeHtml(proc.processName));

                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(proc.processName, 0);

                        if (ai != null) {
                            CharSequence label = pm.getApplicationLabel(ai);

                            if (label != null && !label.equals(proc.processName)) {
                                sb.append(nextColumn).append(Util.escapeHtml(label.toString()));
                            }
                        }
                    } catch (NameNotFoundException e) {
                        // ignore this error
                    }

                    sb.append(closeRow);
                }
            }

            sb.append(emptyRow);
        }

        if (items[NETSTATES]) {
            sb.append(openHeaderRow).append(ResUtil.getString(this, R.string.tab_netstat))
                    .append(closeHeaderRow);

            try {
                Util.readRawHTML(sb, new FileInputStream("/proc/net/tcp"), 2048); //$NON-NLS-1$
                sb.append(emptyRow);
                Util.readRawHTML(sb, new FileInputStream("/proc/net/udp"), 2048); //$NON-NLS-1$
                sb.append(emptyRow);
                Util.readRawHTML(sb, new FileInputStream("/proc/net/tcp6"), 2048); //$NON-NLS-1$
                sb.append(emptyRow);
                Util.readRawHTML(sb, new FileInputStream("/proc/net/udp6"), 2048); //$NON-NLS-1$
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            sb.append(emptyRow);
        }

        if (items[DMESG_LOG]) {
            sb.append(openHeaderRow).append("Dmesg " //$NON-NLS-1$
                    + ResUtil.getString(this, R.string.log)).append(closeHeaderRow);

            try {
                Process proc = Runtime.getRuntime().exec("dmesg"); //$NON-NLS-1$

                Util.readRawHTML(sb, proc.getInputStream(), 8192);
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            sb.append(emptyRow);
        }

        if (items[LOGCAT_LOG]) {
            sb.append(openHeaderRow).append("Logcat " //$NON-NLS-1$
                    + ResUtil.getString(this, R.string.log)).append(closeHeaderRow);

            try {
                Process proc = Runtime.getRuntime().exec("logcat -d -v time *:V"); //$NON-NLS-1$

                Util.readRawHTML(sb, proc.getInputStream(), 8192);
            } catch (Exception e) {
                Log.e(SysInfoManager.class.getName(), e.getLocalizedMessage(), e);
            }

            sb.append(emptyRow);
        }

        sb.append("</table></font></body></html>"); //$NON-NLS-1$

        return sb.toString();
    }

    private String getImportance(ProcessInfo proc) {
        String impt = "Unknown"; //$NON-NLS-1$

        switch (proc.importance) {
            case RunningAppProcessInfo.IMPORTANCE_EMPTY:
                impt = "Empty"; //$NON-NLS-1$
                break;
            case RunningAppProcessInfo.IMPORTANCE_BACKGROUND:
                impt = "Background"; //$NON-NLS-1$
                break;
            case RunningAppProcessInfo.IMPORTANCE_SERVICE:
                impt = "Service"; //$NON-NLS-1$
                break;
            case 170: // RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE:
                impt = "CantSaveState"; //$NON-NLS-1$
                break;
            case RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE:
                impt = "Perceptible"; //$NON-NLS-1$
                break;
            case RunningAppProcessInfo.IMPORTANCE_VISIBLE:
                impt = "Visible"; //$NON-NLS-1$
                break;
            case RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
                impt = "Foreground"; //$NON-NLS-1$
                break;
            case 50: // RunningAppProcessInfo.IMPORTANCE_PERSISTENT:
                impt = "Persistent"; //$NON-NLS-1$
                break;
        }

        return impt;
    }

    /**
     * FormatItem
     */
    static final class FormatItem {

        String format;
        boolean compressed;

        FormatItem(String format) {
            this.format = format;
            this.compressed = false;
        }
    }

    /**
     * FormatArrayAdapter
     */
    static final class FormatArrayAdapter extends ArrayAdapter<FormatItem> {

        Context ctx;
        int colorPrimary, colorSecondary, colorSecondaryDisable;

        FormatArrayAdapter(Context ctx, int textViewResourceId, FormatItem[] objects) {
            super(ctx, textViewResourceId, objects);

            this.ctx = ctx;

            if (Util.SDK_VER > 10) {
                colorPrimary = 0xffffffff;
                colorSecondary = 0xffaaaaaa;
                colorSecondaryDisable = 0xff666666;
            } else {
                colorPrimary = 0xff000000;
                colorSecondary = 0xff444444;
                colorSecondaryDisable = 0xff888888;
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;

            if (convertView == null) {
                view =
                        ((LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(
                                R.layout.send_item, parent, false);
            } else {
                view = convertView;
            }

            final FormatItem fi = getItem(position);

            TextView txt_format = (TextView) view.findViewById(R.id.txt_format);
            txt_format.setText(fi.format);
            txt_format.setTextColor(colorPrimary);

            final TextView txt_hint = (TextView) view.findViewById(R.id.txt_hint);
            txt_hint.setText(ResUtil.getString(ctx, R.string.compressed));
            txt_hint.setTextColor(fi.compressed ? colorSecondary : colorSecondaryDisable);

            View hintArea = view.findViewById(R.id.ll_compress);

            hintArea.setOnClickListener(new View.OnClickListener() {

                public void onClick(View v) {
                    fi.compressed = !fi.compressed;

                    txt_hint.setTextColor(fi.compressed ? colorSecondary : colorSecondaryDisable);
                }
            });

            return view;
        }
    }

    /**
     * InfoSettings
     */
    public static class InfoSettings extends PreferenceActivity {

        private static final int MSG_EXT_NLS = MSG_PRIVATE + 1;

        ProgressDialog progress;

        Map<String, String> extNLS;

        Set<String> builtinNLS;

        Handler handler = new InfoSettingsHandler(this);

        private static final class InfoSettingsHandler extends Handler {

            private WeakReference<InfoSettings> acRef;

            InfoSettingsHandler(InfoSettings ac) {
                acRef = new WeakReference<InfoSettings>(ac);
            }

            @Override
            public void handleMessage(Message msg) {
                InfoSettings host = acRef.get();
                if (host == null) {
                    return;
                }

                switch (msg.what) {
                    case MSG_DISMISS_PROGRESS:

                        if (host.progress != null) {
                            Util.safeDismissDialog(host.progress);
                            host.progress = null;
                        }
                        break;
                    case MSG_TOAST:

                        Util.longToast(host, (String) msg.obj);
                        break;
                    case MSG_EXT_NLS:

                        host.extNLS = (Map<String, String>) msg.obj;

                        if (host.missingExtNLS()) {
                            host.showExtNLSHint();
                        }

                        break;
                }
            }
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);

            super.onCreate(savedInstanceState);

            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(this));

            PreferenceCategory pc = new PreferenceCategory(this);
            pc.setKey(PREF_CATEGORY_DEFAULT);
            pc.setTitle(ResUtil.getString(this, R.string.preference));
            getPreferenceScreen().addPreference(pc);

            Preference perfTab = new Preference(this);
            perfTab.setKey(PREF_KEY_DEFAULT_TAB);
            perfTab.setTitle(ResUtil.getString(this, R.string.default_tab));
            pc.addPreference(perfTab);

            Preference perfEmail = new Preference(this);
            perfEmail.setKey(PREF_KEY_DEFAULT_EMAIL);
            perfEmail.setTitle(ResUtil.getString(this, R.string.default_email));
            pc.addPreference(perfEmail);

            int widgetState = getWidgetState();

            if (widgetState < 0) {
                Preference perfWidget = new Preference(this);
                perfWidget.setKey(PREF_KEY_INSTALL_WIDGET_PACK);
                perfWidget.setTitle(ResUtil.getString(this, R.string.install_widget_pack));
                perfWidget.setSummary(ResUtil.getString(this, R.string.install_widget_pack_sum));
                pc.addPreference(perfWidget);
            } else {
                Preference perfWidget = new Preference(this);
                perfWidget.setKey(PREF_KEY_WIDGET_DISABLED);
                perfWidget.setTitle(ResUtil.getString(this, R.string.configure_widgets));
                perfWidget.setSummary(ResUtil.getString(this, R.string.configure_widgets_sum));
                perfWidget.setEnabled(widgetState == 0);
                pc.addPreference(perfWidget);
            }

            Preference perfLang = new Preference(this);
            perfLang.setKey(PREF_KEY_USER_LOCALE);
            perfLang.setTitle(ResUtil.getString(this, R.string.language));
            pc.addPreference(perfLang);

            pc = new PreferenceCategory(this);
            pc.setKey(PREF_CATEGORY_NOTIFICATIONS);
            pc.setTitle(ResUtil.getString(this, R.string.notifications));
            getPreferenceScreen().addPreference(pc);

            if (Util.SDK_VER >= 21) {
                CheckBoxPreference prefUseLegacyIcon = new CheckBoxPreference(this);
                prefUseLegacyIcon.setKey(PREF_KEY_USE_LEGACY_ICON);
                prefUseLegacyIcon.setTitle(ResUtil.getString(this, R.string.use_legacy_icon));
                prefUseLegacyIcon.setSummary(ResUtil.getString(this, R.string.use_legacy_icon_sum));
                pc.addPreference(prefUseLegacyIcon);
            }

            CheckBoxPreference prefDisableAll = new CheckBoxPreference(this);
            prefDisableAll.setKey(PREF_KEY_DISABLE_ALL_ICON);
            prefDisableAll.setTitle(ResUtil.getString(this, R.string.disable_all_icon));
            pc.addPreference(prefDisableAll);

            PreferenceScreen psInfo = getPreferenceManager().createPreferenceScreen(this);
            psInfo.setKey(KEY_INFO_PREF_SCREEN);
            psInfo.setTitle(ResUtil.getString(this, R.string.info_notify));
            psInfo.setSummary(ResUtil.getString(this, R.string.info_notify_sum));
            pc.addPreference(psInfo);

            CheckBoxPreference prefInfo = new CheckBoxPreference(this);
            prefInfo.setKey(PREF_KEY_SHOW_INFO_ICON);
            prefInfo.setTitle(ResUtil.getString(this, R.string.show_info_icon));
            prefInfo.setSummary(ResUtil.getString(this, R.string.show_info_icon_sum));
            psInfo.addPreference(prefInfo);

            CheckBoxPreference prefInfoBarIcon = new CheckBoxPreference(this);
            prefInfoBarIcon.setKey(PREF_KEY_SHOW_BAR_ICON_INFO);
            prefInfoBarIcon.setTitle(ResUtil.getString(this, R.string.show_status_icon));
            prefInfoBarIcon.setSummary(ResUtil.getString(this, R.string.show_status_icon_sum));
            psInfo.addPreference(prefInfoBarIcon);

            CheckBoxPreference prefCpuMon = new CheckBoxPreference(this);
            prefCpuMon.setKey(PREF_KEY_SHOW_CPU_MONITOR);
            prefCpuMon.setTitle(ResUtil.getString(this, R.string.show_cpu_usage));
            prefCpuMon.setSummary(ResUtil.getString(this, R.string.show_cpu_icon_sum));
            psInfo.addPreference(prefCpuMon);

            CheckBoxPreference prefCpuHistory = new CheckBoxPreference(this);
            prefCpuHistory.setKey(PREF_KEY_SHOW_CPU_HISTORY);
            prefCpuHistory.setTitle(ResUtil.getString(this, R.string.show_cpu_history));
            prefCpuHistory.setSummary(ResUtil.getString(this, R.string.show_cpu_history_sum));
            psInfo.addPreference(prefCpuHistory);

            Preference perfCpuInterval = new Preference(this);
            perfCpuInterval.setKey(PREF_KEY_REFRESH_INTERVAL_CPU);
            perfCpuInterval.setTitle(ResUtil.getString(this, R.string.update_speed));
            psInfo.addPreference(perfCpuInterval);

            PreferenceScreen psTask = getPreferenceManager().createPreferenceScreen(this);
            psTask.setKey(KEY_TASK_PREF_SCREEN);
            psTask.setTitle(ResUtil.getString(this, R.string.task_notify));
            psTask.setSummary(ResUtil.getString(this, R.string.task_notify_sum));
            pc.addPreference(psTask);

            CheckBoxPreference prefTask = new CheckBoxPreference(this);
            prefTask.setKey(PREF_KEY_SHOW_TASK_ICON);
            prefTask.setTitle(ResUtil.getString(this, R.string.show_task_icon));
            prefTask.setSummary(ResUtil.getString(this, R.string.show_task_icon_sum));
            psTask.addPreference(prefTask);

            CheckBoxPreference prefTaskBarIcon = new CheckBoxPreference(this);
            prefTaskBarIcon.setKey(PREF_KEY_SHOW_BAR_ICON_TASK);
            prefTaskBarIcon.setTitle(ResUtil.getString(this, R.string.show_status_icon));
            prefTaskBarIcon.setSummary(ResUtil.getString(this, R.string.show_status_icon_sum));
            psTask.addPreference(prefTaskBarIcon);

            CheckBoxPreference prefMemMon = new CheckBoxPreference(this);
            prefMemMon.setKey(PREF_KEY_SHOW_MEM_MONITOR);
            prefMemMon.setTitle(ResUtil.getString(this, R.string.show_memory_usage));
            prefMemMon.setSummary(ResUtil.getString(this, R.string.show_mem_icon_sum));
            psTask.addPreference(prefMemMon);

            CheckBoxPreference prefMemHistory = new CheckBoxPreference(this);
            prefMemHistory.setKey(PREF_KEY_SHOW_MEM_HISTORY);
            prefMemHistory.setTitle(ResUtil.getString(this, R.string.show_mem_history));
            prefMemHistory.setSummary(ResUtil.getString(this, R.string.show_mem_history_sum));
            psTask.addPreference(prefMemHistory);

            Preference perfTaskInterval = new Preference(this);
            perfTaskInterval.setKey(PREF_KEY_REFRESH_INTERVAL_MEM);
            perfTaskInterval.setTitle(ResUtil.getString(this, R.string.update_speed));
            psTask.addPreference(perfTaskInterval);

            PreferenceScreen psWifi = getPreferenceManager().createPreferenceScreen(this);
            psWifi.setKey(KEY_WIFI_PREF_SCREEN);
            psWifi.setTitle(ResUtil.getString(this, R.string.wifi_notify));
            psWifi.setSummary(ResUtil.getString(this, R.string.wifi_notify_sum));
            pc.addPreference(psWifi);

            CheckBoxPreference prefWifi = new CheckBoxPreference(this);
            prefWifi.setKey(PREF_KEY_SHOW_WIFI_ACTIVITY);
            prefWifi.setTitle(ResUtil.getString(this, R.string.show_wifi_act));
            prefWifi.setSummary(ResUtil.getString(this, R.string.show_wifi_act_sum));
            psWifi.addPreference(prefWifi);

            CheckBoxPreference prefWifiBarIcon = new CheckBoxPreference(this);
            prefWifiBarIcon.setKey(PREF_KEY_SHOW_BAR_ICON_WIFI);
            prefWifiBarIcon.setTitle(ResUtil.getString(this, R.string.show_status_icon));
            prefWifiBarIcon.setSummary(ResUtil.getString(this, R.string.show_status_icon_sum));
            psWifi.addPreference(prefWifiBarIcon);

            CheckBoxPreference prefRates = new CheckBoxPreference(this);
            prefRates.setKey(PREF_KEY_SHOW_WIFI_RATES);
            prefRates.setTitle(ResUtil.getString(this, R.string.show_rates));
            prefRates.setSummary(ResUtil.getString(this, R.string.show_rates_sum));
            psWifi.addPreference(prefRates);

            CheckBoxPreference prefSsid = new CheckBoxPreference(this);
            prefSsid.setKey(PREF_KEY_SHOW_WIFI_SSID);
            prefSsid.setTitle(ResUtil.getString(this, R.string.show_ssid));
            prefSsid.setSummary(ResUtil.getString(this, R.string.show_ssid_sum));
            psWifi.addPreference(prefSsid);

            Preference perfWifiInterval = new Preference(this);
            perfWifiInterval.setKey(PREF_KEY_REFRESH_INTERVAL_NET);
            perfWifiInterval.setTitle(ResUtil.getString(this, R.string.update_speed));
            psWifi.addPreference(perfWifiInterval);

            PreferenceScreen psBatt = getPreferenceManager().createPreferenceScreen(this);
            psBatt.setKey(KEY_BATT_PREF_SCREEN);
            psBatt.setTitle(ResUtil.getString(this, R.string.batt_notify));
            psBatt.setSummary(ResUtil.getString(this, R.string.batt_notify_sum));
            pc.addPreference(psBatt);

            CheckBoxPreference prefBatt = new CheckBoxPreference(this);
            prefBatt.setKey(PREF_KEY_SHOW_BATTERY_INFO);
            prefBatt.setTitle(ResUtil.getString(this, R.string.show_batt_info));
            prefBatt.setSummary(ResUtil.getString(this, R.string.show_batt_info_sum));
            psBatt.addPreference(prefBatt);

            CheckBoxPreference prefPersist = new CheckBoxPreference(this);
            prefPersist.setKey(PREF_KEY_PERSISTENT_ICON);
            prefPersist.setTitle(ResUtil.getString(this, R.string.persist_icons));
            prefPersist.setSummary(ResUtil.getString(this, R.string.persist_icons_sum));
            pc.addPreference(prefPersist);

            CheckBoxPreference prefAuto = new CheckBoxPreference(this);
            prefAuto.setKey(PREF_KEY_AUTO_START_ICON);
            prefAuto.setTitle(ResUtil.getString(this, R.string.auto_start));
            prefAuto.setSummary(ResUtil.getString(this, R.string.auto_start_sum));
            pc.addPreference(prefAuto);

            CheckBoxPreference prefInvColor = new CheckBoxPreference(this);
            prefInvColor.setKey(PREF_KEY_INVERSE_NOTIFY_TITLE_COLOR);
            prefInvColor.setTitle(ResUtil.getString(this, R.string.inverse_title_color));
            prefInvColor.setSummary(ResUtil.getString(this, R.string.inverse_title_color_sum));
            pc.addPreference(prefInvColor);

            CheckBoxPreference prefHigh = new CheckBoxPreference(this);
            prefHigh.setKey(PREF_KEY_HIGH_PRIORITY);
            prefHigh.setTitle(ResUtil.getString(this, R.string.high_priority));
            prefHigh.setSummary(ResUtil.getString(this, R.string.high_priority_sum));
            pc.addPreference(prefHigh);

            pc = new PreferenceCategory(this);
            pc.setTitle(ResUtil.getString(this, R.string.backup));
            getPreferenceScreen().addPreference(pc);

            Preference perfExport = new Preference(this);
            perfExport.setKey(PREF_KEY_EXPORT_PREFERENCES);
            perfExport.setTitle(ResUtil.getString(this, R.string.export_pref));
            pc.addPreference(perfExport);

            Preference perfImport = new Preference(this);
            perfImport.setKey(PREF_KEY_IMPORT_PREFERENCES);
            perfImport.setTitle(ResUtil.getString(this, R.string.import_pref));
            pc.addPreference(perfImport);

            refreshEmail();
            refreshTab();
            refreshLanguage();

            if (Util.SDK_VER >= 21) {
                refreshBooleanOption(PREF_KEY_USE_LEGACY_ICON, false);
            }
            refreshBooleanOption(PREF_KEY_DISABLE_ALL_ICON, false);
            refreshBooleanOption(PREF_KEY_SHOW_INFO_ICON, true);
            refreshBooleanOption(PREF_KEY_SHOW_TASK_ICON, true);
            refreshBooleanOption(PREF_KEY_SHOW_CPU_MONITOR, true);
            refreshBooleanOption(PREF_KEY_SHOW_MEM_MONITOR, true);
            refreshBooleanOption(PREF_KEY_SHOW_CPU_HISTORY, true);
            refreshBooleanOption(PREF_KEY_SHOW_MEM_HISTORY, true);
            refreshBooleanOption(PREF_KEY_SHOW_WIFI_ACTIVITY, true);
            refreshBooleanOption(PREF_KEY_SHOW_WIFI_RATES, true);
            refreshBooleanOption(PREF_KEY_SHOW_WIFI_SSID, true);
            refreshBooleanOption(PREF_KEY_SHOW_BAR_ICON_INFO, true);
            refreshBooleanOption(PREF_KEY_SHOW_BAR_ICON_TASK, true);
            refreshBooleanOption(PREF_KEY_SHOW_BAR_ICON_WIFI, true);
            refreshBooleanOption(PREF_KEY_SHOW_BATTERY_INFO, true);
            refreshBooleanOption(PREF_KEY_PERSISTENT_ICON, true);
            refreshBooleanOption(PREF_KEY_AUTO_START_ICON, false);
            refreshBooleanOption(PREF_KEY_INVERSE_NOTIFY_TITLE_COLOR,
                    Util.INVERT_TITLE_COLOR_DEFAULT_SETTING);
            refreshBooleanOption(PREF_KEY_HIGH_PRIORITY, false);

            prefCpuMon.setEnabled(prefInfo.isChecked());
            prefCpuHistory.setEnabled(prefInfo.isChecked() && prefCpuMon.isChecked());
            prefInfoBarIcon.setEnabled(prefInfo.isChecked());

            prefMemMon.setEnabled(prefTask.isChecked());
            prefMemHistory.setEnabled(prefTask.isChecked() && prefMemMon.isChecked());
            prefTaskBarIcon.setEnabled(prefTask.isChecked());

            prefRates.setEnabled(prefWifi.isChecked());
            prefWifiBarIcon.setEnabled(prefWifi.isChecked());

            boolean disableAllIcons = prefDisableAll.isChecked();
            prefAuto.setEnabled(prefPersist.isChecked() && !disableAllIcons);
            if (!prefPersist.isChecked()) {
                prefAuto.setChecked(false);
                getIntent().putExtra(PREF_KEY_AUTO_START_ICON, false);
            }

            prefPersist.setEnabled(!disableAllIcons);
            prefInvColor.setEnabled(!disableAllIcons);
            prefHigh.setEnabled(!disableAllIcons);

            psInfo.setEnabled(!disableAllIcons);
            psTask.setEnabled(!disableAllIcons);
            psWifi.setEnabled(!disableAllIcons);
            psBatt.setEnabled(!disableAllIcons);

            refreshInterval(PREF_KEY_REFRESH_INTERVAL_CPU, PREF_KEY_SHOW_INFO_ICON,
                    PREF_KEY_SHOW_CPU_MONITOR);
            refreshInterval(PREF_KEY_REFRESH_INTERVAL_MEM, PREF_KEY_SHOW_TASK_ICON,
                    PREF_KEY_SHOW_MEM_MONITOR);
            refreshInterval(PREF_KEY_REFRESH_INTERVAL_NET, PREF_KEY_SHOW_WIFI_ACTIVITY);

            prepareBuiltinNLS();

            probeExternalNLS();

            setResult(RESULT_OK, getIntent());
        }

        @Override
        protected void onSaveInstanceState(Bundle outState) {
            // fix: https://code.google.com/p/android/issues/detail?id=19917
            outState.putString("WORKAROUND_FOR_BUG_19917_KEY", //$NON-NLS-1$
                    "WORKAROUND_FOR_BUG_19917_VALUE"); //$NON-NLS-1$

            super.onSaveInstanceState(outState);
        }

        void showExtNLSHint() {
            OnClickListener listener = new OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    Intent it = new Intent(Intent.ACTION_VIEW);

                    it.setData(Uri.parse("market://search?q=pub:\"Shawn Q.\"")); //$NON-NLS-1$

                    it = Intent.createChooser(it, null);

                    Util.safeStartActivity(InfoSettings.this, it, false);

                }
            };
            new AlertDialog.Builder(this).setTitle(ResUtil.getString(this, R.string.prompt))
                    .setMessage(ResUtil.getString(this, R.string.ext_nl_hint))
                    .setPositiveButton(android.R.string.ok, listener)
                    .setNegativeButton(android.R.string.cancel, null).create().show();
        }

        boolean missingExtNLS() {
            String userLocale = getIntent().getStringExtra(PREF_KEY_USER_LOCALE);

            if (userLocale != null && !builtinNLS.contains(userLocale) && extNLS != null
                    && !extNLS.containsKey(userLocale)) {
                return true;
            }
            return false;
        }

        private void prepareBuiltinNLS() {
            builtinNLS = new HashSet<String>();

            //builtinNLS.add( "bg" ); //$NON-NLS-1$
            //builtinNLS.add( "da" ); //$NON-NLS-1$
//            builtinNLS.add("de"); //$NON-NLS-1$
            builtinNLS.add("en"); //$NON-NLS-1$
            //builtinNLS.add( "es" ); //$NON-NLS-1$
//            builtinNLS.add("fr"); //$NON-NLS-1$
            //builtinNLS.add( "in" ); //$NON-NLS-1$
            //builtinNLS.add( "it" ); //$NON-NLS-1$
            //builtinNLS.add( "iw" ); //$NON-NLS-1$
//            builtinNLS.add("ja"); //$NON-NLS-1$
            //builtinNLS.add( "ko" ); //$NON-NLS-1$
            //builtinNLS.add( "nl" ); //$NON-NLS-1$
            //builtinNLS.add( "pl" ); //$NON-NLS-1$
            //builtinNLS.add( "pt" ); //$NON-NLS-1$
            //builtinNLS.add( "ro" ); //$NON-NLS-1$
//            builtinNLS.add("ru"); //$NON-NLS-1$
            builtinNLS.add("zh_CN"); //$NON-NLS-1$
            builtinNLS.add("zh_TW"); //$NON-NLS-1$
        }

        private void probeExternalNLS() {
            final Object mutex = new Object();

            if (progress != null) {
                progress.dismiss();
            }
            progress = new ProgressDialog(this);
            progress.setIndeterminate(true);
            progress.setCancelable(true);
            progress.setMessage(ResUtil.getString(this, R.string.loading));
            progress.setOnCancelListener(new OnCancelListener() {

                @Override
                public void onCancel(DialogInterface dialog) {
                    synchronized (mutex) {
                        mutex.notifyAll();
                    }
                }
            });
            progress.show();

            new Thread("ExtNLSChecker") { //$NON-NLS-1$

                public void run() {
                    try {
                        Map<String, String> nls = new HashMap<String, String>();

                        ResUtil.getNLPackages(InfoSettings.this, nls, mutex);

                        handler.sendMessage(handler.obtainMessage(MSG_EXT_NLS, nls));
                    } catch (Exception e) {
                        handler.sendMessage(handler.obtainMessage(MSG_TOAST, e.getLocalizedMessage()));
                    } finally {
                        handler.sendMessage(handler.obtainMessage(MSG_DISMISS_PROGRESS));
                    }
                }
            }.start();
        }

        private int getWidgetState() {
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(getPackageName(), 0);

                if (ai != null && (ai.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0) {
                    // on SD, continue check widget pack state
                    try {
                        PackageInfo pi = pm.getPackageInfo("org.uguess.android.sysinfo.pro.widgets", //$NON-NLS-1$
                                0);

                        if (pi != null) {
                            // get widget pack
                            return 1;
                        }
                    } catch (NameNotFoundException e) {
                        // ignore
                    }

                    return -1;
                }
            } catch (NameNotFoundException e) {
                // ignore
            }

            return 0;
        }

        private static String toTitleCase(String s) {
            if (s == null || s.length() == 0) {
                return s;
            }

            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }

        static int[] getWidgetIds(String names) {
            if (names != null) {
                String[] ss = names.split(","); //$NON-NLS-1$

                if (ss != null && ss.length > 0) {
                    int[] id = new int[ss.length];
                    int idx = 0;

                    for (String s : ss) {
                        if (s.equals(WidgetProvider.class.getSimpleName())) {
                            id[idx] = WIDGET_BAR;
                        } else if (s.equals(InfoWidget.class.getSimpleName())) {
                            id[idx] = WIDGET_INFO;
                        } else if (s.equals(TaskWidget.class.getSimpleName())) {
                            id[idx] = WIDGET_TASK;
                        } else if (s.equals(CacheWidget.class.getSimpleName())) {
                            id[idx] = WIDGET_CACHE;
                        } else if (s.equals(HistoryWidget.class.getSimpleName())) {
                            id[idx] = WIDGET_HISTORY;
                        } else if (s.equals(Bar2xWidget.class.getSimpleName())) {
                            id[idx] = WIDGET_BAR_2x;
                        }

                        idx++;
                    }

                    return id;
                }
            }

            return null;
        }

        static String getWidgetName(int id) {
            Class<?> clz = getWidgetClass(id);

            if (clz != null) {
                return clz.getSimpleName();
            }

            return null;
        }

        static Class<?> getWidgetClass(int id) {
            switch (id) {
                case WIDGET_BAR:
                    return WidgetProvider.class;
                case WIDGET_INFO:
                    return InfoWidget.class;
                case WIDGET_TASK:
                    return TaskWidget.class;
                case WIDGET_CACHE:
                    return CacheWidget.class;
                case WIDGET_HISTORY:
                    return HistoryWidget.class;
                case WIDGET_BAR_2x:
                    return Bar2xWidget.class;
            }

            return null;
        }

        void refreshBooleanOption(String key, boolean defValue) {
            boolean val = getIntent().getBooleanExtra(key, defValue);

            ((CheckBoxPreference) findPreference(key)).setChecked(val);
        }

        void refreshInterval(String prefKey, String... dependencies) {
            int interval = getIntent().getIntExtra(prefKey, REFRESH_NORMAL);

            CharSequence label = ResUtil.getString(this, R.string.normal);
            switch (interval) {
                case REFRESH_HIGH:
                    label = ResUtil.getString(this, R.string.high);
                    break;
                case REFRESH_LOW:
                    label = ResUtil.getString(this, R.string.low);
                    break;
                case REFRESH_PAUSED:
                    label = ResUtil.getString(this, R.string.paused);
                    break;
                case REFRESH_HIGHER:
                    label = ResUtil.getString(this, R.string.higher);
                    break;
            }

            findPreference(prefKey).setSummary(label);

            boolean enabled = true;

            if (dependencies != null) {
                for (String key : dependencies) {
                    if (!((CheckBoxPreference) findPreference(key)).isChecked()) {
                        enabled = false;
                        break;
                    }
                }
            }

            findPreference(prefKey).setEnabled(enabled);
        }

        void refreshEmail() {
            String email = getIntent().getStringExtra(PREF_KEY_DEFAULT_EMAIL);

            if (email == null) {
                findPreference(PREF_KEY_DEFAULT_EMAIL).setSummary(ResUtil.getString(this, R.string.none));
            } else {
                findPreference(PREF_KEY_DEFAULT_EMAIL).setSummary(email);
            }
        }

        void refreshTab() {
            int tab = getIntent().getIntExtra(PREF_KEY_DEFAULT_TAB, 0);

            CharSequence label = ResUtil.getString(this, R.string.last_active);
            switch (tab) {
                case 1:
                    label = ResUtil.getString(this, R.string.tab_info);
                    break;
                case 2:
                    label = ResUtil.getString(this, R.string.tab_apps);
                    break;
                case 3:
                    label = ResUtil.getString(this, R.string.tab_procs);
                    break;
                case 4:
                    label = ResUtil.getString(this, R.string.tab_netstat);
                    break;
            }

            findPreference(PREF_KEY_DEFAULT_TAB).setSummary(label);
        }

        void refreshLanguage() {
            String userLocale = getIntent().getStringExtra(PREF_KEY_USER_LOCALE);

            if (userLocale != null) {
                Locale lc = Util.string2Locale(userLocale);

                if (lc != null) {
                    findPreference(PREF_KEY_USER_LOCALE).setSummary(toTitleCase(lc.getDisplayName(lc)));

                    return;
                }
            }

            findPreference(PREF_KEY_USER_LOCALE).setSummary(
                    toTitleCase(ResUtil.getString(this, R.string.default_)));
        }

        void editInterval(final Intent it, final String prefKey, final String... dependencies) {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    int interval = which == 0 ? REFRESH_HIGHER : (which - 1);

                    it.putExtra(prefKey, interval);

                    dialog.dismiss();

                    refreshInterval(prefKey, dependencies);
                }
            };

            int interval = it.getIntExtra(prefKey, REFRESH_LOW);

            new AlertDialog.Builder(this)
                    .setTitle(ResUtil.getString(this, R.string.update_speed))
                    //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                    .setSingleChoiceItems(
                            new CharSequence[]{
                                    Html.fromHtml(ResUtil.getString(this, R.string.higher)
                                            + "<br><small><small><font color=\"#ff0000\">" //$NON-NLS-1$
                                            + ResUtil.getString(this, R.string.higher_warn)
                                            + "</font></small></small>&nbsp;"), //$NON-NLS-1$
                                    ResUtil.getString(this, R.string.high), ResUtil.getString(this, R.string.normal),
                                    ResUtil.getString(this, R.string.low),},
                            interval == REFRESH_HIGHER ? 0 : (interval + 1), listener).create().show();

        }

        private void doImportExport(final boolean isImport) {
            OnClickListener listener = new OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    if (progress != null) {
                        Util.safeDismissDialog(progress);
                    }
                    progress = new ProgressDialog(InfoSettings.this);
                    progress.setIndeterminate(true);
                    progress.setCancelable(false);
                    progress.setMessage(isImport ? ResUtil.getString(InfoSettings.this, R.string.importing)
                            : ResUtil.getString(InfoSettings.this, R.string.exporting, "")); //$NON-NLS-1$
                    progress.show();

                    new Thread() {

                        public void run() {
                            String exPath = "/sdcard/qsysinfopro"; //$NON-NLS-1$

                            File ex = Environment.getExternalStorageDirectory();

                            if (ex != null) {
                                exPath = new File(ex, "qsysinfopro").getAbsolutePath(); //$NON-NLS-1$
                            }

                            if (isImport) {
                                Util.importPreferences(InfoSettings.this, handler, exPath);

                                setResult(RESULT_PREFERENCES_IMPORTED);

                                finish();
                            } else {
                                Util.exportPreferences(InfoSettings.this, handler, exPath);
                            }

                            handler.sendEmptyMessage(MSG_DISMISS_PROGRESS);
                        }
                    }.start();
                }
            };

            Util.newAlertDialogBuilder(this)
                    .setTitle(ResUtil.getString(this, R.string.warning))
                    .setPositiveButton(android.R.string.ok, listener)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setMessage(
                            ResUtil.getString(this, isImport ? R.string.import_pref_warn
                                    : R.string.export_pref_warn)).create().show();
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            final Intent it = getIntent();

            String prefKey = preference.getKey();

            CheckBoxPreference prefInfo = (CheckBoxPreference) findPreference(PREF_KEY_SHOW_INFO_ICON);
            CheckBoxPreference prefTask = (CheckBoxPreference) findPreference(PREF_KEY_SHOW_TASK_ICON);
            CheckBoxPreference prefCpuMon =
                    (CheckBoxPreference) findPreference(PREF_KEY_SHOW_CPU_MONITOR);
            CheckBoxPreference prefMemMon =
                    (CheckBoxPreference) findPreference(PREF_KEY_SHOW_MEM_MONITOR);
            CheckBoxPreference prefCpuHistory =
                    (CheckBoxPreference) findPreference(PREF_KEY_SHOW_CPU_HISTORY);
            CheckBoxPreference prefMemHistory =
                    (CheckBoxPreference) findPreference(PREF_KEY_SHOW_MEM_HISTORY);
            CheckBoxPreference prefWifi =
                    (CheckBoxPreference) findPreference(PREF_KEY_SHOW_WIFI_ACTIVITY);
            CheckBoxPreference prefWifiRates =
                    (CheckBoxPreference) findPreference(PREF_KEY_SHOW_WIFI_RATES);
            CheckBoxPreference prefWifiSsid =
                    (CheckBoxPreference) findPreference(PREF_KEY_SHOW_WIFI_SSID);
            CheckBoxPreference prefInfoBarIcon =
                    (CheckBoxPreference) findPreference(PREF_KEY_SHOW_BAR_ICON_INFO);
            CheckBoxPreference prefTaskBarIcon =
                    (CheckBoxPreference) findPreference(PREF_KEY_SHOW_BAR_ICON_TASK);
            CheckBoxPreference prefWifiBarIcon =
                    (CheckBoxPreference) findPreference(PREF_KEY_SHOW_BAR_ICON_WIFI);

            if (PREF_KEY_SHOW_INFO_ICON.equals(prefKey)) {
                boolean enabled = prefInfo.isChecked();

                it.putExtra(PREF_KEY_SHOW_INFO_ICON, enabled);

                prefCpuMon.setEnabled(enabled);
                prefCpuHistory.setEnabled(enabled && prefCpuMon.isChecked());
                prefInfoBarIcon.setEnabled(enabled);

                refreshInterval(PREF_KEY_REFRESH_INTERVAL_CPU, PREF_KEY_SHOW_INFO_ICON,
                        PREF_KEY_SHOW_CPU_MONITOR);

                startService(new Intent(this, StatusUpdaterService.class)
                        .putExtra(StatusUpdaterService.EXTRA_TARGET, StatusUpdaterService.TARGET_INFO)
                        .putExtra(StatusUpdaterService.EXTRA_STATE, enabled)
                        .putExtra(StatusUpdaterService.EXTRA_MONITOR, prefCpuMon.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_HISTORY, prefCpuHistory.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_ICON, prefInfoBarIcon.isChecked()));

                return true;
            } else if (PREF_KEY_SHOW_TASK_ICON.equals(prefKey)) {
                boolean enabled = prefTask.isChecked();

                it.putExtra(PREF_KEY_SHOW_TASK_ICON, enabled);

                prefMemMon.setEnabled(enabled);
                prefMemHistory.setEnabled(enabled && prefMemMon.isChecked());
                prefTaskBarIcon.setEnabled(enabled);

                refreshInterval(PREF_KEY_REFRESH_INTERVAL_MEM, PREF_KEY_SHOW_TASK_ICON,
                        PREF_KEY_SHOW_MEM_MONITOR);

                startService(new Intent(this, StatusUpdaterService.class)
                        .putExtra(StatusUpdaterService.EXTRA_TARGET, StatusUpdaterService.TARGET_TASK)
                        .putExtra(StatusUpdaterService.EXTRA_STATE, enabled)
                        .putExtra(StatusUpdaterService.EXTRA_MONITOR, prefMemMon.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_HISTORY, prefMemHistory.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_ICON, prefTaskBarIcon.isChecked()));

                return true;
            } else if (PREF_KEY_SHOW_CPU_MONITOR.equals(prefKey)) {
                boolean enabled = prefCpuMon.isChecked();

                it.putExtra(PREF_KEY_SHOW_CPU_MONITOR, enabled);

                prefCpuHistory.setEnabled(enabled);

                refreshInterval(PREF_KEY_REFRESH_INTERVAL_CPU, PREF_KEY_SHOW_INFO_ICON,
                        PREF_KEY_SHOW_CPU_MONITOR);

                startService(new Intent(this, StatusUpdaterService.class)
                        .putExtra(StatusUpdaterService.EXTRA_TARGET, StatusUpdaterService.TARGET_INFO)
                        .putExtra(StatusUpdaterService.EXTRA_STATE, prefInfo.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_MONITOR, enabled)
                        .putExtra(StatusUpdaterService.EXTRA_HISTORY, prefCpuHistory.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_ICON, prefInfoBarIcon.isChecked()));

                return true;
            } else if (PREF_KEY_SHOW_MEM_MONITOR.equals(prefKey)) {
                boolean enabled = prefMemMon.isChecked();

                it.putExtra(PREF_KEY_SHOW_MEM_MONITOR, enabled);

                prefMemHistory.setEnabled(enabled);

                refreshInterval(PREF_KEY_REFRESH_INTERVAL_MEM, PREF_KEY_SHOW_TASK_ICON,
                        PREF_KEY_SHOW_MEM_MONITOR);

                startService(new Intent(this, StatusUpdaterService.class)
                        .putExtra(StatusUpdaterService.EXTRA_TARGET, StatusUpdaterService.TARGET_TASK)
                        .putExtra(StatusUpdaterService.EXTRA_STATE, prefTask.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_MONITOR, enabled)
                        .putExtra(StatusUpdaterService.EXTRA_HISTORY, prefMemHistory.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_ICON, prefTaskBarIcon.isChecked()));

                return true;
            } else if (PREF_KEY_SHOW_CPU_HISTORY.equals(prefKey)) {
                boolean enabled = prefCpuHistory.isChecked();

                it.putExtra(PREF_KEY_SHOW_CPU_HISTORY, enabled);

                startService(new Intent(this, StatusUpdaterService.class)
                        .putExtra(StatusUpdaterService.EXTRA_TARGET, StatusUpdaterService.TARGET_INFO)
                        .putExtra(StatusUpdaterService.EXTRA_STATE, prefInfo.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_MONITOR, prefCpuMon.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_HISTORY, enabled)
                        .putExtra(StatusUpdaterService.EXTRA_ICON, prefInfoBarIcon.isChecked()));

                return true;
            } else if (PREF_KEY_SHOW_MEM_HISTORY.equals(prefKey)) {
                boolean enabled = prefMemHistory.isChecked();

                it.putExtra(PREF_KEY_SHOW_MEM_HISTORY, enabled);

                startService(new Intent(this, StatusUpdaterService.class)
                        .putExtra(StatusUpdaterService.EXTRA_TARGET, StatusUpdaterService.TARGET_TASK)
                        .putExtra(StatusUpdaterService.EXTRA_STATE, prefTask.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_MONITOR, prefMemMon.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_HISTORY, enabled)
                        .putExtra(StatusUpdaterService.EXTRA_ICON, prefTaskBarIcon.isChecked()));

                return true;
            } else if (PREF_KEY_SHOW_WIFI_ACTIVITY.equals(prefKey)) {
                boolean enabled = prefWifi.isChecked();

                it.putExtra(PREF_KEY_SHOW_WIFI_ACTIVITY, enabled);

                prefWifiRates.setEnabled(enabled);
                prefWifiSsid.setEnabled(enabled);
                prefWifiBarIcon.setEnabled(enabled);

                refreshInterval(PREF_KEY_REFRESH_INTERVAL_NET, PREF_KEY_SHOW_WIFI_ACTIVITY);

                startService(new Intent(this, StatusUpdaterService.class)
                        .putExtra(StatusUpdaterService.EXTRA_TARGET, StatusUpdaterService.TARGET_WIFI)
                        .putExtra(StatusUpdaterService.EXTRA_STATE, enabled)
                        .putExtra(StatusUpdaterService.EXTRA_RATES, prefWifiRates.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_ICON, prefWifiBarIcon.isChecked()));

                return true;
            } else if (PREF_KEY_SHOW_WIFI_SSID.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_WIFI_SSID,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_WIFI_SSID)).isChecked());

                return true;
            } else if (PREF_KEY_SHOW_WIFI_RATES.equals(prefKey)) {
                boolean enabled = prefWifiRates.isChecked();

                it.putExtra(PREF_KEY_SHOW_WIFI_RATES, enabled);

                startService(new Intent(this, StatusUpdaterService.class)
                        .putExtra(StatusUpdaterService.EXTRA_TARGET, StatusUpdaterService.TARGET_WIFI)
                        .putExtra(StatusUpdaterService.EXTRA_STATE, prefWifi.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_RATES, enabled)
                        .putExtra(StatusUpdaterService.EXTRA_ICON, prefWifiBarIcon.isChecked()));

                return true;
            } else if (PREF_KEY_SHOW_BAR_ICON_INFO.equals(prefKey)) {
                boolean enabled = prefInfoBarIcon.isChecked();

                it.putExtra(PREF_KEY_SHOW_BAR_ICON_INFO, enabled);

                startService(new Intent(this, StatusUpdaterService.class)
                        .putExtra(StatusUpdaterService.EXTRA_TARGET, StatusUpdaterService.TARGET_INFO)
                        .putExtra(StatusUpdaterService.EXTRA_STATE, prefInfo.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_MONITOR, prefCpuMon.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_HISTORY, prefCpuHistory.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_ICON, enabled));

                return true;
            } else if (PREF_KEY_SHOW_BAR_ICON_TASK.equals(prefKey)) {
                boolean enabled = prefTaskBarIcon.isChecked();

                it.putExtra(PREF_KEY_SHOW_BAR_ICON_TASK, enabled);

                startService(new Intent(this, StatusUpdaterService.class)
                        .putExtra(StatusUpdaterService.EXTRA_TARGET, StatusUpdaterService.TARGET_TASK)
                        .putExtra(StatusUpdaterService.EXTRA_STATE, prefTask.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_MONITOR, prefMemMon.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_HISTORY, prefMemHistory.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_ICON, enabled));

                return true;
            } else if (PREF_KEY_SHOW_BAR_ICON_WIFI.equals(prefKey)) {
                boolean enabled = prefWifiBarIcon.isChecked();

                it.putExtra(PREF_KEY_SHOW_BAR_ICON_WIFI, enabled);

                startService(new Intent(this, StatusUpdaterService.class)
                        .putExtra(StatusUpdaterService.EXTRA_TARGET, StatusUpdaterService.TARGET_WIFI)
                        .putExtra(StatusUpdaterService.EXTRA_STATE, prefWifi.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_RATES, prefWifiRates.isChecked())
                        .putExtra(StatusUpdaterService.EXTRA_ICON, enabled));

                return true;
            } else if (PREF_KEY_SHOW_BATTERY_INFO.equals(prefKey)) {
                it.putExtra(PREF_KEY_SHOW_BATTERY_INFO,
                        ((CheckBoxPreference) findPreference(PREF_KEY_SHOW_BATTERY_INFO)).isChecked());

                return true;
            } else if (PREF_KEY_INVERSE_NOTIFY_TITLE_COLOR.equals(prefKey)) {
                it.putExtra(PREF_KEY_INVERSE_NOTIFY_TITLE_COLOR,
                        ((CheckBoxPreference) findPreference(PREF_KEY_INVERSE_NOTIFY_TITLE_COLOR)).isChecked());

                return true;
            } else if (PREF_KEY_HIGH_PRIORITY.equals(prefKey)) {
                it.putExtra(PREF_KEY_HIGH_PRIORITY,
                        ((CheckBoxPreference) findPreference(PREF_KEY_HIGH_PRIORITY)).isChecked());

                return true;
            } else if (PREF_KEY_REFRESH_INTERVAL_CPU.equals(prefKey)) {
                editInterval(it, PREF_KEY_REFRESH_INTERVAL_CPU, PREF_KEY_SHOW_INFO_ICON,
                        PREF_KEY_SHOW_CPU_MONITOR);

                return true;
            } else if (PREF_KEY_REFRESH_INTERVAL_MEM.equals(prefKey)) {
                editInterval(it, PREF_KEY_REFRESH_INTERVAL_MEM, PREF_KEY_SHOW_TASK_ICON,
                        PREF_KEY_SHOW_MEM_MONITOR);

                return true;
            } else if (PREF_KEY_REFRESH_INTERVAL_NET.equals(prefKey)) {
                editInterval(it, PREF_KEY_REFRESH_INTERVAL_NET, PREF_KEY_SHOW_WIFI_ACTIVITY);

                return true;
            } else if (PREF_KEY_DISABLE_ALL_ICON.equals(prefKey)) {
                boolean disableAllIcons =
                        ((CheckBoxPreference) findPreference(PREF_KEY_DISABLE_ALL_ICON)).isChecked();
                it.putExtra(PREF_KEY_DISABLE_ALL_ICON, disableAllIcons);

                boolean persist = ((CheckBoxPreference) findPreference(PREF_KEY_PERSISTENT_ICON)).isChecked();
                findPreference(PREF_KEY_AUTO_START_ICON).setEnabled(persist && !disableAllIcons);

                findPreference(PREF_KEY_PERSISTENT_ICON).setEnabled(!disableAllIcons);
                findPreference(PREF_KEY_INVERSE_NOTIFY_TITLE_COLOR).setEnabled(!disableAllIcons);
                findPreference(PREF_KEY_HIGH_PRIORITY).setEnabled(!disableAllIcons);

                findPreference(KEY_INFO_PREF_SCREEN).setEnabled(!disableAllIcons);
                findPreference(KEY_TASK_PREF_SCREEN).setEnabled(!disableAllIcons);
                findPreference(KEY_WIFI_PREF_SCREEN).setEnabled(!disableAllIcons);
                findPreference(KEY_BATT_PREF_SCREEN).setEnabled(!disableAllIcons);
            } else if (PREF_KEY_USE_LEGACY_ICON.equals(prefKey)) {
                it.putExtra(PREF_KEY_USE_LEGACY_ICON,
                        ((CheckBoxPreference) findPreference(PREF_KEY_USE_LEGACY_ICON)).isChecked());
                return true;
            } else if (PREF_KEY_PERSISTENT_ICON.equals(prefKey)) {
                boolean persist =
                        ((CheckBoxPreference) findPreference(PREF_KEY_PERSISTENT_ICON)).isChecked();

                it.putExtra(PREF_KEY_PERSISTENT_ICON, persist);

                ((CheckBoxPreference) findPreference(PREF_KEY_AUTO_START_ICON)).setEnabled(persist);
                if (!persist) {
                    ((CheckBoxPreference) findPreference(PREF_KEY_AUTO_START_ICON)).setChecked(false);
                    it.putExtra(PREF_KEY_AUTO_START_ICON, false);
                }

                return true;
            } else if (PREF_KEY_AUTO_START_ICON.equals(prefKey)) {
                it.putExtra(PREF_KEY_AUTO_START_ICON,
                        ((CheckBoxPreference) findPreference(PREF_KEY_AUTO_START_ICON)).isChecked());

                return true;
            } else if (PREF_KEY_DEFAULT_EMAIL.equals(prefKey)) {
                final EditText txt = new EditText(this);
                txt.setText(it.getStringExtra(PREF_KEY_DEFAULT_EMAIL));

                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        String email = txt.getText().toString();

                        if (email != null) {
                            email = email.trim();

                            if (email.length() == 0) {
                                email = null;
                            }
                        }

                        it.putExtra(PREF_KEY_DEFAULT_EMAIL, email);

                        dialog.dismiss();

                        refreshEmail();
                    }
                };

                new AlertDialog.Builder(this).setTitle(ResUtil.getString(this, R.string.default_email))
                        .setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, null).setView(txt).create().show();

                return true;
            } else if (PREF_KEY_USER_LOCALE.equals(prefKey)) {
                final ArrayList<String> availableLangs = new ArrayList<String>(builtinNLS);

                if (extNLS != null) {
                    for (Iterator<Entry<String, String>> itr = extNLS.entrySet().iterator(); itr.hasNext(); ) {
                        Entry<String, String> ent = itr.next();

                        if (availableLangs.contains(ent.getKey())) {
                            itr.remove();
                        } else {
                            availableLangs.add(ent.getKey());
                        }
                    }
                }

                Collections.sort(availableLangs);

                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            it.removeExtra(PREF_KEY_USER_LOCALE);
                        } else {
                            it.putExtra(PREF_KEY_USER_LOCALE, availableLangs.get(which - 1));
                        }

                        dialog.dismiss();

                        refreshLanguage();

                        String pkgName = null;

                        if (which > 0) {
                            String lcName = availableLangs.get(which - 1);

                            if (extNLS != null && extNLS.containsKey(lcName)) {
                                // this is ext nls, we need to record the res
                                // package name
                                pkgName = extNLS.get(lcName);
                            }
                        }

                        try {
                            ResUtil.setupResources(InfoSettings.this, pkgName, null);
                        } catch (NameNotFoundException e) {
                            Util.shortToast(InfoSettings.this, e.getLocalizedMessage());

                            Log.e(InfoSettings.class.getName(), e.getLocalizedMessage(), e);
                        }
                    }
                };

                int selection = 0;

                String userLocale = it.getStringExtra(PREF_KEY_USER_LOCALE);
                if (userLocale != null) {
                    int idx = availableLangs.indexOf(userLocale);

                    if (idx != -1) {
                        selection = idx + 1;
                    }
                }

                List<String> labels = new ArrayList<String>();
                labels.add(toTitleCase(ResUtil.getString(this, R.string.default_)));

                for (int i = 0, size = availableLangs.size(); i < size; i++) {
                    Locale lc = Util.string2Locale(availableLangs.get(i));

                    if (lc != null) {
                        labels.add(toTitleCase(lc.getDisplayName(lc)));
                    } else {
                        labels.add(ResUtil.getString(this, R.string.unknown));
                    }
                }

                OnClickListener extraLangListener = new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showExtNLSHint();
                    }
                };

                Util.AlertDialogBuilder builder = Util.newAlertDialogBuilder(this).setTitle(ResUtil.getString(this, R.string.language))
                        .setSingleChoiceItems(labels.toArray(new String[labels.size()]), selection, listener);

                boolean isWearable = Util.isWearable(this);
                if (!isWearable) {
                    builder.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                            .setPositiveButton(ResUtil.getString(this, R.string.more), extraLangListener);
                }

                builder.create().show();

                if (!isWearable && userLocale == null && !missingExtNLS() && ResUtil.getInstalledNLPackageCount(this) == 0) {
                    showExtNLSHint();
                }

                return true;
            } else if (PREF_KEY_DEFAULT_TAB.equals(prefKey)) {
                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        it.putExtra(PREF_KEY_DEFAULT_TAB, which);

                        dialog.dismiss();

                        refreshTab();
                    }
                };

                new AlertDialog.Builder(this)
                        .setTitle(ResUtil.getString(this, R.string.default_tab))
                        //.setNeutralButton(ResUtil.getString(this, R.string.close), null)
                        .setSingleChoiceItems(
                                new CharSequence[]{ResUtil.getString(this, R.string.last_active),
                                        ResUtil.getString(this, R.string.tab_info),
                                        ResUtil.getString(this, R.string.tab_apps),
                                        ResUtil.getString(this, R.string.tab_procs),
                                        ResUtil.getString(this, R.string.tab_netstat)},
                                it.getIntExtra(PREF_KEY_DEFAULT_TAB, 0), listener).create().show();

                return true;
            } else if (PREF_KEY_INSTALL_WIDGET_PACK.equals(prefKey)) {
                Intent wpIntent = new Intent(Intent.ACTION_VIEW);
                wpIntent.setData(Uri
                        .parse("market://search?q=pname:org.uguess.android.sysinfo.pro.widgets")); //$NON-NLS-1$

                Util.safeStartActivity(this, Intent.createChooser(wpIntent, null), true);

                return true;
            } else if (PREF_KEY_WIDGET_DISABLED.equals(prefKey)) {
                final boolean[] states = new boolean[6];
                Arrays.fill(states, true);

                String disabled = it.getStringExtra(PREF_KEY_WIDGET_DISABLED);

                if (disabled != null) {
                    int[] ids = getWidgetIds(disabled);

                    if (ids != null) {
                        for (int i : ids) {
                            states[i] = false;
                        }
                    }
                }

                OnClickListener listener = new OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        PackageManager pm = getPackageManager();

                        StringBuilder disabled = new StringBuilder();

                        int idx = 0;
                        for (boolean b : states) {
                            // record disabled
                            if (!b) {
                                String name = getWidgetName(idx);

                                if (name != null) {
                                    if (disabled.length() > 0) {
                                        disabled.append(',');
                                    }

                                    disabled.append(name);
                                }
                            }

                            // refresh widget enablement
                            Class<?> clz = getWidgetClass(idx);

                            if (clz != null) {
                                ComponentName comp = new ComponentName(InfoSettings.this, clz);

                                int setting = pm.getComponentEnabledSetting(comp);

                                if (b && setting != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                                    pm.setComponentEnabledSetting(comp,
                                            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
                                } else if (!b && setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                                    pm.setComponentEnabledSetting(comp,
                                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                                }
                            }

                            idx++;
                        }

                        String names = disabled.length() > 0 ? disabled.toString() : null;

                        it.putExtra(PREF_KEY_WIDGET_DISABLED, names);

                        dialog.dismiss();

                        Util.longToast(InfoSettings.this, R.string.reboot_warning);
                    }
                };

                OnMultiChoiceClickListener multiListener = new OnMultiChoiceClickListener() {

                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        states[which] = isChecked;
                    }
                };

                new AlertDialog.Builder(this)
                        .setTitle(ResUtil.getString(this, R.string.widgets))
                        .setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setMultiChoiceItems(
                                new CharSequence[]{ResUtil.getString(this, R.string.widget_bar_name),
                                        ResUtil.getString(this, R.string.info_widget_name),
                                        ResUtil.getString(this, R.string.task_widget_name),
                                        ResUtil.getString(this, R.string.cache_widget_name),
                                        ResUtil.getString(this, R.string.history_widget_name),
                                        ResUtil.getString(this, R.string.widget_bar_2x_name),}, states, multiListener)
                        .create().show();

                return true;
            } else if (PREF_KEY_EXPORT_PREFERENCES.equals(prefKey)) {
                Util.checkStoragePermission(this);
                doImportExport(false);

                return true;
            } else if (PREF_KEY_IMPORT_PREFERENCES.equals(prefKey)) {
                Util.checkStoragePermission(this);
                doImportExport(true);

                return true;
            }
            return false;
        }
    }
}
