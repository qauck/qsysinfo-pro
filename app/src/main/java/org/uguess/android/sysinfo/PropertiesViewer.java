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
import android.app.ActivityManager;
import android.app.ExpandableListActivity;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.CellLocation;
import android.telephony.TelephonyManager;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.uguess.android.sysinfo.ToolMenuDialog.IActionMenuProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.Collator;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;
import javax.microedition.khronos.opengles.GL11ExtensionPack;

/**
 * PropertiesViewer
 */
public class PropertiesViewer extends ExpandableListActivity implements Constants, IActionMenuProvider {

    private static final String ACTION_GETPROPS = "org.uguess.android.sysinfo.pro.action.GETPROPS"; //$NON-NLS-1$

    private static Method mtdGetSystemAvailableFeatures = null;
    private static Method mtdOverridePendingTransition = null;
    private static Field fdDensityDpi = null;
    private static String MANUFACTURER = null;
    private static String BOOTLOADER = null;
    private static String RADIO = null;
    private static String SERIAL = null;
    private static String SECURITY_PATCH = null;

    static String EXTRA_GLRENDERER = "glRenderer"; //$NON-NLS-1$
    static String EXTRA_GLVENDOR = "glVendor"; //$NON-NLS-1$
    static String EXTRA_NO_DETAILS = "noDetails"; //$NON-NLS-1$
    static String EXTRA_GLPROPS = "glProps"; //$NON-NLS-1$

    static String CPU_ABI = null;
    static String CPU_ABI2 = null;
    static String HARDWARE = null;

    static String[] SUPPORTED_ABIS = null;
    static String[] SUPPORTED_32BIT_ABIS = null;
    static String[] SUPPORTED_64BIT_ABIS = null;

    private static final Method mtdGetRealMetrics = Util.getMethod(Display.class, "getRealMetrics", //$NON-NLS-1$
            DisplayMetrics.class);
    private static final Method mtdGetName = Util.getMethod(Display.class, "getName"); //$NON-NLS-1$
    private static final Method mtdGetType = Util.getMethod(Display.class, "getType"); //$NON-NLS-1$
    private static final Method mtdGetAddress = Util.getMethod(Display.class, "getAddress"); //$NON-NLS-1$
    private static final Method mtdGetOwnerUid = Util.getMethod(Display.class, "getOwnerUid"); //$NON-NLS-1$
    private static final Method mtdGetOwnerPackageName = Util.getMethod(Display.class,
            "getOwnerPackageName"); //$NON-NLS-1$
    private static final Method mtdGetSupportedRefreshRates = Util.getMethod(Display.class,
            "getSupportedRefreshRates"); //$NON-NLS-1$
    private static final Method mtdGetLayerStack = Util.getMethod(Display.class, "getLayerStack"); //$NON-NLS-1$
    private static final Method mtdGetFlags = Util.getMethod(Display.class, "getFlags"); //$NON-NLS-1$

    static final Method mtdSetEGLContextClientVersion = Util.getMethod(GLSurfaceView.class,
            "setEGLContextClientVersion", //$NON-NLS-1$
            int.class);
    static final Field fdReqGlEsVersion = Util.getField(ConfigurationInfo.class,
            "reqGlEsVersion"); //$NON-NLS-1$

    static {
        try {
            mtdGetSystemAvailableFeatures = PackageManager.class.getMethod("getSystemAvailableFeatures"); //$NON-NLS-1$
        } catch (Exception e) {
            Log.d(PropertiesViewer.class.getName(),
                    "PackageManager.getSystemAvailableFeatures() not available, ignore and continue."); //$NON-NLS-1$
        }

        try {
            mtdOverridePendingTransition = Activity.class.getMethod("overridePendingTransition", //$NON-NLS-1$
                    int.class, int.class);
        } catch (Exception e) {
            Log.d(PropertiesViewer.class.getName(),
                    "Activity.overridePendingTransition() not available, ignore and continue."); //$NON-NLS-1$
        }

        try {
            Field fd = Build.class.getField("CPU_ABI"); //$NON-NLS-1$
            CPU_ABI = (String) fd.get(null);

            fd = Build.class.getField("MANUFACTURER"); //$NON-NLS-1$
            MANUFACTURER = (String) fd.get(null);
        } catch (Exception e) {
            Log.d(PropertiesViewer.class.getName(),
                    "Build.CPU_ABI/MANUFACTURER not available, ignore and continue."); //$NON-NLS-1$
        }

        try {
            fdDensityDpi = DisplayMetrics.class.getField("densityDpi"); //$NON-NLS-1$
        } catch (Exception e) {
            Log.d(PropertiesViewer.class.getName(),
                    "DisplayMetrics.densityDpi not available, ignore and continue."); //$NON-NLS-1$
        }

        try {
            Field fd = Build.class.getField("BOOTLOADER"); //$NON-NLS-1$
            BOOTLOADER = (String) fd.get(null);

            Method mtdGetRadioVersion = Util.getMethod(Build.class, "getRadioVersion"); //$NON-NLS-1$

            if (mtdGetRadioVersion != null) {
                try {
                    RADIO = (String) mtdGetRadioVersion.invoke(null);
                } catch (Exception e) {
                    // ignore
                }
            }

            if (RADIO == null) {
                fd = Build.class.getField("RADIO"); //$NON-NLS-1$
                RADIO = (String) fd.get(null);
            }

            fd = Build.class.getField("HARDWARE"); //$NON-NLS-1$
            HARDWARE = (String) fd.get(null);

            fd = Build.class.getField("CPU_ABI2"); //$NON-NLS-1$
            CPU_ABI2 = (String) fd.get(null);
        } catch (Exception e) {
            Log.d(PropertiesViewer.class.getName(),
                    "Build.BOOTLOADER/RADIO/HARDWARE/CPU_ABI2 not available, ignore and continue."); //$NON-NLS-1$
        }

        try {
            Field fd = Build.class.getField("SERIAL"); //$NON-NLS-1$
            SERIAL = (String) fd.get(null);
        } catch (Exception e) {
            Log.d(PropertiesViewer.class.getName(), "Build.SERIAL not available, ignore and continue."); //$NON-NLS-1$
        }

        try {
            Field fd = Build.VERSION.class.getField("SECURITY_PATCH"); //$NON-NLS-1$
            SECURITY_PATCH = (String) fd.get(null);
            if (SECURITY_PATCH != null) {
                try {
                    SimpleDateFormat template = new SimpleDateFormat("yyyy-MM-dd");
                    Date patchDate = template.parse(SECURITY_PATCH);
                    SECURITY_PATCH = DateFormat.getDateInstance().format(patchDate);
                } catch (Exception e) {
                    // in case broken parse, use the raw string
                }
            }
        } catch (Exception e) {
            Log.d(PropertiesViewer.class.getName(), "Build.VERSION.SECURITY_PATCH not available, ignore and continue."); //$NON-NLS-1$
        }

        try {
            SUPPORTED_ABIS = (String[]) Util.safeGetStatic(21, false, Build.class, "SUPPORTED_ABIS");
            SUPPORTED_32BIT_ABIS = (String[]) Util.safeGetStatic(21, false, Build.class, "SUPPORTED_32_BIT_ABIS");
            SUPPORTED_64BIT_ABIS = (String[]) Util.safeGetStatic(21, false, Build.class, "SUPPORTED_64_BIT_ABIS");
        } catch (Exception e) {
            Log.d(PropertiesViewer.class.getName(), "Fail to get Supported ABIs."); //$NON-NLS-1$
        }
    }

    static void overridePendingTransition(Activity ac, int enterAnim, int exitAnim) {
        if (mtdOverridePendingTransition != null) {
            try {
                mtdOverridePendingTransition.invoke(ac, enterAnim, exitAnim);
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }
        }
    }

    static String getFeatures(Context ctx) {
        if (mtdGetSystemAvailableFeatures != null) {
            PackageManager pm = ctx.getPackageManager();

            try {
                Object obj = mtdGetSystemAvailableFeatures.invoke(pm);

                if (obj != null) {
                    int len = Array.getLength(obj);

                    if (len > 0) {
                        StringBuilder sb = new StringBuilder();
                        String s;
                        int idx;

                        for (int i = 0; i < len; i++) {
                            Object fi = Array.get(obj, i);

                            if (fi != null) {
                                if (sb.length() > 0) {
                                    sb.append('\n');
                                }

                                s = String.valueOf(fi);
                                idx = s.indexOf(' ');

                                if (idx == -1 || idx + 1 >= s.length()) {
                                    sb.append(s);
                                } else {
                                    sb.append(s.substring(idx + 1));
                                }
                            }
                        }

                        return sb.toString();
                    }
                }
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }
        }
        return ResUtil.getString(ctx, R.string.info_not_available);
    }

    ProgressDialog progress;

    volatile boolean aborted;

    Handler handler = new PropertiesHandler(this);

    private static final class PropertiesHandler extends Handler {

        private WeakReference<PropertiesViewer> acRef;

        PropertiesHandler(PropertiesViewer ac) {
            acRef = new WeakReference<PropertiesViewer>(ac);
        }

        public void handleMessage(android.os.Message msg) {
            PropertiesViewer host = acRef.get();
            if (host == null) {
                return;
            }

            switch (msg.what) {
                case MSG_INIT_OK:

                    @SuppressWarnings("unchecked")
                    ArrayList<String[]> data = (ArrayList<String[]>) msg.obj;

                    PropertiesAdapter adapter = (PropertiesAdapter) host.getExpandableListAdapter();

                    adapter.update(data);

                    adapter.notifyDataSetChanged();

                    sendEmptyMessage(MSG_DISMISS_PROGRESS);

                    break;
                case MSG_CONTENT_READY:

                    sendEmptyMessage(MSG_DISMISS_PROGRESS);

                    Util.handleMsgSendContentReady((String) msg.obj, "Android Device Properties - ", //$NON-NLS-1$
                            host, msg.arg2 == 1);

                    break;
                case MSG_CHECK_FORCE_COMPRESSION:

                    sendEmptyMessage(MSG_DISMISS_PROGRESS);

                    Util.checkForceCompression(this, host, (String) msg.obj, msg.arg1, "android_properties"); //$NON-NLS-1$

                    break;
                case MSG_DISMISS_PROGRESS:

                    if (host.progress != null) {
                        Util.safeDismissDialog(host.progress);
                        host.progress = null;
                    }
                    break;
                case MSG_TOAST:

                    Util.shortToast(host, (String) msg.obj);
                    break;
            }
        }
    }

    static LinkedHashMap<String, String> getHeaderProps(Context ctx) {
        LinkedHashMap<String, String> props = new LinkedHashMap<String, String>();

        props.put(ResUtil.getString(ctx, R.string.device), Build.DEVICE);
        props.put(ResUtil.getString(ctx, R.string.model), Build.MODEL);
        props.put(ResUtil.getString(ctx, R.string.board), Build.BOARD);
        props.put(ResUtil.getString(ctx, R.string.product), Build.PRODUCT);
        props.put(ResUtil.getString(ctx, R.string.brand), Build.BRAND);
        props.put(ResUtil.getString(ctx, R.string.manufacturer),
                MANUFACTURER == null ? ResUtil.getString(ctx, R.string.unknown) : MANUFACTURER);
        props.put("CPU+ABI", //$NON-NLS-1$
                CPU_ABI == null ? ResUtil.getString(ctx, R.string.unknown) : CPU_ABI);
        props.put("CPU+ABI2", //$NON-NLS-1$
                CPU_ABI2 == null ? ResUtil.getString(ctx, R.string.unknown) : CPU_ABI2);

        if (SUPPORTED_ABIS != null) {
            props.put("Supported ABIs", Util.joinString(SUPPORTED_ABIS, ", "));
        }

        if (SUPPORTED_32BIT_ABIS != null) {
            props.put("Supported 32-Bit ABIs", Util.joinString(SUPPORTED_32BIT_ABIS, ", "));
        }

        if (SUPPORTED_64BIT_ABIS != null) {
            props.put("Supported 64-Bit ABIs", Util.joinString(SUPPORTED_64BIT_ABIS, ", "));
        }

        props.put(ResUtil.getString(ctx, R.string.bootloader), BOOTLOADER == null ? ResUtil.getString(ctx, R.string.unknown) : BOOTLOADER);
        props.put(ResUtil.getString(ctx, R.string.radio), RADIO == null ? ResUtil.getString(ctx, R.string.unknown) : RADIO);
        props.put(ResUtil.getString(ctx, R.string.hardware), HARDWARE == null ? ResUtil.getString(ctx, R.string.unknown) : HARDWARE);
        props.put(ResUtil.getString(ctx, R.string.serial), SERIAL == null ? ResUtil.getString(ctx, R.string.unknown) : SERIAL);
        props.put(ResUtil.getString(ctx, R.string.release), Build.VERSION.RELEASE);
        props.put("SDK", getSdkVersionName()); //$NON-NLS-1$
        props.put(ResUtil.getString(ctx, R.string.security_patch), SECURITY_PATCH == null ? ResUtil.getString(ctx, R.string.unknown) : SECURITY_PATCH);
        props.put(ResUtil.getString(ctx, R.string.type), Build.TYPE);
        props.put(ResUtil.getString(ctx, R.string.build), Build.DISPLAY);
        props.put(ResUtil.getString(ctx, R.string.id), Build.ID);
        props.put(ResUtil.getString(ctx, R.string.tags), Build.TAGS);
        props.put(ResUtil.getString(ctx, R.string.fingerprint), Build.FINGERPRINT);
        props.put(ResUtil.getString(ctx, R.string.locale), Locale.getDefault().toString());

        return props;
    }

    private static String getSdkVersionName() {
        String codeName = null;

        int sdk = Util.SDK_VER;
        if (sdk == 3) {
            codeName = "Cupcake"; //$NON-NLS-1$
        } else if (sdk == 4) {
            codeName = "Donut"; //$NON-NLS-1$
        } else if (sdk >= 5 && sdk <= 7) {
            codeName = "Eclair"; //$NON-NLS-1$
        } else if (sdk == 8) {
            codeName = "Froyo"; //$NON-NLS-1$
        } else if (sdk >= 9 && sdk <= 10) {
            codeName = "Gingerbread"; //$NON-NLS-1$
        } else if (sdk >= 11 && sdk <= 13) {
            codeName = "Honeycomb"; //$NON-NLS-1$
        } else if (sdk >= 14 && sdk <= 15) {
            codeName = "Ice Cream Sandwich"; //$NON-NLS-1$
        } else if (sdk >= 16 && sdk <= 18) {
            codeName = "Jelly Bean"; //$NON-NLS-1$
        } else if (sdk >= 19 && sdk <= 20) {
            codeName = "KitKat"; //$NON-NLS-1$
        } else if (sdk >= 21 && sdk <= 22) {
            codeName = "Lollipop"; //$NON-NLS-1$
        } else if (sdk == 23) {
            codeName = "Marshmallow"; //$NON-NLS-1$
        } else if (sdk >= 24 && sdk <= 25) {
            codeName = "Nougat"; //$NON-NLS-1$
        } else if (sdk >= 26 && sdk <= 26) {
            codeName = "Oreo"; //$NON-NLS-1$
        }

        return codeName == null ? String.valueOf(sdk) : (String.valueOf(sdk) + " (" + codeName + ')'); //$NON-NLS-1$
    }

    static void parseProp(String line, Bundle props) {
        if (line == null) {
            return;
        }

        int idx = line.indexOf(':');

        if (idx != -1) {
            String key = line.substring(0, idx).trim();
            String val = line.substring(idx + 1).trim();

            if (key.length() > 2 && val.length() > 1) {
                if (key.charAt(0) == '[' && key.charAt(key.length() - 1) == ']' && val.charAt(0) == '['
                        && val.charAt(val.length() - 1) == ']') {
                    key = key.substring(1, key.length() - 1);

                    if (val.length() > 2) {
                        val = val.substring(1, val.length() - 1);
                    } else {
                        val = null;
                    }

                    props.putString(key, val);
                }
            }
        }
    }

    static Map<String, String> parseGetprops(final Context ctx) {
        final Map<String, String> result = new HashMap<String, String>();

        final boolean[] unregFlag = new boolean[]{false};

        BroadcastReceiver receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_GETPROPS.equals(intent.getAction())) {
                    Bundle bd = intent.getBundleExtra("result"); //$NON-NLS-1$

                    if (bd != null) {
                        for (String key : bd.keySet()) {
                            result.put(key, bd.getString(key));
                        }
                    }

                    ctx.unregisterReceiver(this);
                    unregFlag[0] = true;

                    Util.finishStub(ctx, intent.getIntExtra(EXTRA_PID, 0));

                    synchronized (result) {
                        result.notify();
                    }
                }
            }
        };

        ctx.registerReceiver(receiver, new IntentFilter(ACTION_GETPROPS));

        ctx.startService(new Intent(ctx, GetPropsService.class));

        try {
            synchronized (result) {
                result.wait(5000);
            }
        } catch (InterruptedException e) {
            Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
        }

        if (!unregFlag[0]) {
            try {
                // in case the receiver is not unregistered
                ctx.unregisterReceiver(receiver);
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        ctx.stopService(new Intent(ctx, GetPropsService.class));

        return result;
    }

    static void formatElapsedTime(Context ctx, StringBuilder sb, long elapsedSeconds) {
        long days = 0;
        long hours = 0;
        long minutes = 0;
        long seconds = 0;

        if (elapsedSeconds >= 86400) {
            days = elapsedSeconds / 86400;
            elapsedSeconds -= days * 86400;
        }

        if (elapsedSeconds >= 3600) {
            hours = elapsedSeconds / 3600;
            elapsedSeconds -= hours * 3600;
        }

        if (elapsedSeconds >= 60) {
            minutes = elapsedSeconds / 60;
            elapsedSeconds -= minutes * 60;
        }

        seconds = elapsedSeconds;

        if (days != 0) {
            sb.append(days).append(' ')
                    .append(ResUtil.getString(ctx, days == 1 ? R.string.day : R.string.days)).append(' ');
        }

        if (days != 0 || hours != 0) {
            sb.append(hours).append(' ')
                    .append(ResUtil.getString(ctx, hours == 1 ? R.string.hour : R.string.hours)).append(' ');
        }

        if (days != 0 || hours != 0 || minutes != 0) {
            sb.append(minutes).append(' ')
                    .append(ResUtil.getString(ctx, minutes == 1 ? R.string.minute : R.string.minutes))
                    .append(' ');
        }

        sb.append(seconds).append(' ')
                .append(ResUtil.getString(ctx, seconds == 1 ? R.string.second : R.string.seconds));
    }

    private Runnable task = new Runnable() {

        private Comparator<Entry<?, ?>> comparator = new Comparator<Entry<?, ?>>() {

            private Collator clt = Collator.getInstance();

            public int compare(Entry<?, ?> obj1, Entry<?, ?> obj2) {
                return clt.compare(String.valueOf(obj1.getKey()), String.valueOf(obj2.getKey()));
            }
        };

        public void run() {
            ArrayList<String[]> data = new ArrayList<String[]>();

            StringBuilder sb = new StringBuilder();

            LinkedHashMap<String, String> headerProps = getHeaderProps(PropertiesViewer.this);

            boolean first = true;
            for (Entry<String, String> entry : headerProps.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    sb.append('\n');
                }

                sb.append(entry.getKey()).append(": ") //$NON-NLS-1$
                        .append(entry.getValue());
            }

            data.add(new String[]{ResUtil.getString(PropertiesViewer.this, R.string.device),
                    sb.toString()});

            sb.delete(0, sb.length());

            String kver = Util.readFileFirstLine("/proc/version", 1024); //$NON-NLS-1$

            sb.append(ResUtil.getString(PropertiesViewer.this, R.string.kernel)).append(": ") //$NON-NLS-1$
                    .append(kver == null ? ResUtil.getString(PropertiesViewer.this, R.string.unknown) : kver)
                    .append('\n');

            String ua = getIntent().getStringExtra("userAgent"); //$NON-NLS-1$

            sb.append(ResUtil.getString(PropertiesViewer.this, R.string.user_agent)).append(": ") //$NON-NLS-1$
                    .append(ua == null ? ResUtil.getString(PropertiesViewer.this, R.string.unknown) : ua);

            sb.append('\n').append(ResUtil.getString(PropertiesViewer.this, R.string.up_time))
                    .append(": "); //$NON-NLS-1$

            formatElapsedTime(PropertiesViewer.this, sb, SystemClock.elapsedRealtime() / 1000);

            sb.append('\n').append(ResUtil.getString(PropertiesViewer.this, R.string.up_time_non_sleep))
                    .append(": "); //$NON-NLS-1$

            formatElapsedTime(PropertiesViewer.this, sb, SystemClock.uptimeMillis() / 1000);

            data.add(new String[]{ResUtil.getString(PropertiesViewer.this, R.string.sys_apps),
                    sb.toString()});

            sb.delete(0, sb.length());

            TelephonyManager otm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

            if (otm == null) {
                sb.append(ResUtil.getString(PropertiesViewer.this, R.string.info_not_available));
            } else {
                Configuration conf = getResources().getConfiguration();

                SafeTelephonyManager tm = new SafeTelephonyManager(otm);

                int pt = tm.getPhoneType();

                sb.append(ResUtil.getString(PropertiesViewer.this, R.string.phone_type))
                        .append(": ") //$NON-NLS-1$
                        .append(resolvePhoneType(pt))
                        .append('\n')
                        .append(pt == TelephonyManager.PHONE_TYPE_GSM ? "IMEI: " //$NON-NLS-1$
                                : (pt == TelephonyManager.PHONE_TYPE_CDMA ? "MEID: " //$NON-NLS-1$
                                : "ID: ")) //$NON-NLS-1$
                        .append(resolveNull(tm.getDeviceId())).append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.soft_version)).append(": ") //$NON-NLS-1$
                        .append(resolveNull(tm.getDeviceSoftwareVersion())).append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.phone_num)).append(": ") //$NON-NLS-1$
                        .append(resolveNull(tm.getLine1Number()));

                Boolean hasIccCard = tm.hasIccCard();
                if (hasIccCard != null) {
                    sb.append('\n')
                            .append(ResUtil.getString(PropertiesViewer.this, R.string.icc_card))
                            .append(": ") //$NON-NLS-1$
                            .append(
                                    ResUtil.getString(PropertiesViewer.this, hasIccCard ? R.string.present
                                            : R.string.none));
                }

                Boolean voiceCapable = tm.isVoiceCapable();
                if (voiceCapable != null) {
                    sb.append('\n')
                            .append(ResUtil.getString(PropertiesViewer.this, R.string.voice_capable))
                            .append(": ") //$NON-NLS-1$
                            .append(
                                    ResUtil.getString(PropertiesViewer.this, voiceCapable ? R.string.yes
                                            : R.string.no));
                }

                Boolean smsCapable = tm.isSmsCapable();
                if (smsCapable != null) {
                    sb.append('\n')
                            .append(ResUtil.getString(PropertiesViewer.this, R.string.sms_capable))
                            .append(": ") //$NON-NLS-1$
                            .append(
                                    ResUtil.getString(PropertiesViewer.this, smsCapable ? R.string.yes : R.string.no));
                }

                String multiSimConfig = tm.getMultiSimConfiguration();
                if (multiSimConfig != null) {
                    sb.append('\n')
                            .append(ResUtil.getString(PropertiesViewer.this, R.string.multi_sim_support))
                            .append(": ") //$NON-NLS-1$
                            .append(resolveMultiSimConfig(multiSimConfig));
                }

                sb.append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.network_country_code))
                        .append(": ") //$NON-NLS-1$
                        .append(resolveNull(tm.getNetworkCountryIso()))
                        .append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.network_op_name))
                        .append(": ") //$NON-NLS-1$
                        .append(resolveNull(tm.getNetworkOperatorName()))
                        .append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.network_op_code))
                        .append(": ") //$NON-NLS-1$
                        .append(resolveNull(tm.getNetworkOperator()))
                        .append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.network_type))
                        .append(": ") //$NON-NLS-1$
                        .append(resolveNetworkType(tm.getNetworkType()))
                        .append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.voice_network_type))
                        .append(": ") //$NON-NLS-1$
                        .append(resolveNetworkType(tm.getVoiceNetworkType()))
                        .append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.roaming_state))
                        .append(": ") //$NON-NLS-1$
                        .append(
                                ResUtil.getString(PropertiesViewer.this, tm.isNetworkRoaming() ? R.string.roaming
                                        : R.string.not_roaming))
                        .append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.sim_country_code))
                        .append(": ") //$NON-NLS-1$
                        .append(resolveNull(tm.getSimCountryIso()))
                        .append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.sim_op_name))
                        .append(": ") //$NON-NLS-1$
                        .append(resolveNull(tm.getSimOperatorName()))
                        .append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.sim_op_code))
                        .append(": ") //$NON-NLS-1$
                        .append(resolveNull(tm.getSimOperator()))
                        .append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.sim_serial_num))
                        .append(": ") //$NON-NLS-1$
                        .append(resolveNull(tm.getSimSerialNumber()))
                        .append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.sim_state))
                        .append(": ") //$NON-NLS-1$
                        .append(resolveSimState(tm.getSimState()))
                        .append('\n')
                        .append(pt == TelephonyManager.PHONE_TYPE_GSM ? "IMSI" //$NON-NLS-1$
                                : ResUtil.getString(PropertiesViewer.this, R.string.subscriber_id))
                        .append(": ") //$NON-NLS-1$
                        .append(resolveNull(tm.getSubscriberId()))
                        .append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.mobile_country_code))
                        .append(": ") //$NON-NLS-1$
                        .append(conf.mcc)
                        .append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.mobile_network_code))
                        .append(": ") //$NON-NLS-1$
                        .append(conf.mnc).append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.voice_mail_num)).append(": ") //$NON-NLS-1$
                        .append(resolveNull(tm.getVoiceMailNumber())).append('\n')
                        .append(ResUtil.getString(PropertiesViewer.this, R.string.voice_mail_tag)).append(": ") //$NON-NLS-1$
                        .append(resolveNull(tm.getVoiceMailAlphaTag()));

                String val = tm.getGroupIdLevel1();
                if (val != null) {
                    sb.append('\n').append(ResUtil.getString(PropertiesViewer.this, R.string.gid_level1))
                            .append(": ") //$NON-NLS-1$
                            .append(val);
                }

                val = tm.getMmsUserAgent();
                if (val != null) {
                    sb.append('\n').append(ResUtil.getString(PropertiesViewer.this, R.string.mms_ua))
                            .append(": ") //$NON-NLS-1$
                            .append(val);
                }

                val = tm.getMmsUAProfUrl();
                if (val != null) {
                    sb.append('\n')
                            .append(ResUtil.getString(PropertiesViewer.this, R.string.mms_ua_profile_url))
                            .append(": ") //$NON-NLS-1$
                            .append(val);
                }
            }

            data.add(new String[]{ResUtil.getString(PropertiesViewer.this, R.string.phone),
                    sb.toString()});

            sb.delete(0, sb.length());

            Object dispMg = getSystemService(Context.DISPLAY_SERVICE);

            if (dispMg == null) {
                Display disp = getWindowManager().getDefaultDisplay();
                getDisplayDetails(disp, sb);
            } else {
                try {
                    Method mtd = Class.forName("android.hardware.display.DisplayManager") //$NON-NLS-1$
                            .getDeclaredMethod("getDisplays"); //$NON-NLS-1$

                    Object result = mtd.invoke(dispMg);

                    if (result instanceof Display[]) {
                        Display[] disps = (Display[]) result;

                        if (disps.length == 1) {
                            getDisplayDetails(disps[0], sb);
                        } else {
                            for (int i = 0; i < disps.length; i++) {
                                if (i > 0) {
                                    sb.append("\n\n"); //$NON-NLS-1$
                                }

                                getDisplayDetails(disps[i], sb);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
                }

                if (sb.length() == 0) {
                    // we failed somehow for using the new API, just fall back
                    // to the old way.
                    Display disp = getWindowManager().getDefaultDisplay();
                    getDisplayDetails(disp, sb);
                }
            }

            data.add(new String[]{ResUtil.getString(PropertiesViewer.this, R.string.screen),
                    sb.toString()});

            sb.delete(0, sb.length());

            String glProps = getIntent().getStringExtra(EXTRA_GLPROPS);
            data.add(new String[]{
                    "OpenGL", //$NON-NLS-1$
                    glProps == null ? ResUtil.getString(PropertiesViewer.this, R.string.info_not_available)
                            : glProps});

            data.add(new String[]{ResUtil.getString(PropertiesViewer.this, R.string.features),
                    getFeatures(PropertiesViewer.this)});

            HashSet<String> settings = new HashSet<String>();

            settings.add(Settings.System.ACCELEROMETER_ROTATION);
            settings.add(Settings.System.AIRPLANE_MODE_ON);
            settings.add(Settings.System.AIRPLANE_MODE_RADIOS);
            settings.add(Settings.System.ALWAYS_FINISH_ACTIVITIES);
            settings.add(Settings.System.APPEND_FOR_LAST_AUDIBLE);
            settings.add(Settings.System.AUTO_TIME);
            settings.add(Settings.System.BLUETOOTH_DISCOVERABILITY);
            settings.add(Settings.System.BLUETOOTH_DISCOVERABILITY_TIMEOUT);
            settings.add(Settings.System.DATE_FORMAT);
            settings.add(Settings.System.DEBUG_APP);
            settings.add(Settings.System.DIM_SCREEN);
            settings.add(Settings.System.DTMF_TONE_WHEN_DIALING);
            settings.add(Settings.System.END_BUTTON_BEHAVIOR);
            settings.add(Settings.System.FONT_SCALE);
            settings.add(Settings.System.HAPTIC_FEEDBACK_ENABLED);
            settings.add(Settings.System.LOCK_PATTERN_ENABLED);
            settings.add(Settings.System.LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED);
            settings.add(Settings.System.LOCK_PATTERN_VISIBLE);
            settings.add(Settings.System.MODE_RINGER);
            settings.add(Settings.System.MODE_RINGER_STREAMS_AFFECTED);
            settings.add(Settings.System.MUTE_STREAMS_AFFECTED);
            settings.add(Settings.System.NEXT_ALARM_FORMATTED);
            settings.add(Settings.System.NOTIFICATION_SOUND);
            settings.add(Settings.System.RINGTONE);
            settings.add(Settings.System.SCREEN_BRIGHTNESS);
            settings.add(Settings.System.SCREEN_OFF_TIMEOUT);
            settings.add(Settings.System.SETUP_WIZARD_HAS_RUN);
            settings.add(Settings.System.SHOW_GTALK_SERVICE_STATUS);
            settings.add(Settings.System.SHOW_PROCESSES);
            settings.add(Settings.System.SHOW_WEB_SUGGESTIONS);
            settings.add(Settings.System.SOUND_EFFECTS_ENABLED);
            settings.add(Settings.System.STAY_ON_WHILE_PLUGGED_IN);
            settings.add(Settings.System.TEXT_AUTO_CAPS);
            settings.add(Settings.System.TEXT_AUTO_PUNCTUATE);
            settings.add(Settings.System.TEXT_AUTO_REPLACE);
            settings.add(Settings.System.TEXT_SHOW_PASSWORD);
            settings.add(Settings.System.TIME_12_24);
            settings.add(Settings.System.TRANSITION_ANIMATION_SCALE);
            settings.add(Settings.System.VIBRATE_ON);
            settings.add(Settings.System.VOLUME_ALARM);
            settings.add(Settings.System.VOLUME_MUSIC);
            settings.add(Settings.System.VOLUME_NOTIFICATION);
            settings.add(Settings.System.VOLUME_RING);
            settings.add(Settings.System.VOLUME_SYSTEM);
            settings.add(Settings.System.VOLUME_VOICE);
            settings.add(Settings.System.WAIT_FOR_DEBUGGER);
            settings.add(Settings.System.WALLPAPER_ACTIVITY);
            settings.add(Settings.System.WIFI_MAX_DHCP_RETRY_COUNT);
            settings.add(Settings.System.WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS);
            settings.add(Settings.System.WIFI_SLEEP_POLICY);
            settings.add(Settings.System.WIFI_STATIC_DNS1);
            settings.add(Settings.System.WIFI_STATIC_DNS2);
            settings.add(Settings.System.WIFI_STATIC_GATEWAY);
            settings.add(Settings.System.WIFI_STATIC_IP);
            settings.add(Settings.System.WIFI_STATIC_NETMASK);
            settings.add(Settings.System.WIFI_USE_STATIC_IP);
            settings.add(Settings.System.WINDOW_ANIMATION_SCALE);

            ContentResolver cr = getContentResolver();

            Cursor cursor = cr.query(Settings.System.CONTENT_URI, null, null, null, null);

            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name"); //$NON-NLS-1$

                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        try {
                            String nameVal = cursor.getString(nameIndex);

                            if (!TextUtils.isEmpty(nameVal)) {
                                settings.add(nameVal);
                            }
                        } catch (Exception e) {
                            Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
                        }
                    }
                }

                cursor.close();
            }

            try {
                cursor = cr.query(Uri.parse("content://settings/global"), //$NON-NLS-1$
                        null, null, null, null);

                if (cursor != null) {
                    int nameIndex = cursor.getColumnIndex("name"); //$NON-NLS-1$

                    if (nameIndex != -1) {
                        while (cursor.moveToNext()) {
                            try {
                                String nameVal = cursor.getString(nameIndex);

                                if (!TextUtils.isEmpty(nameVal)) {
                                    settings.add(nameVal);
                                }
                            } catch (Exception e) {
                                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
                            }
                        }
                    }

                    cursor.close();
                }
            } catch (Exception e) {
                Log.w(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }

            ArrayList<String> sortedSettings = new ArrayList<String>(settings);

            Collections.sort(sortedSettings);

            for (int i = 0, size = sortedSettings.size(); i < size; i++) {
                String name = sortedSettings.get(i);

                try {
                    String v = Settings.System.getString(cr, name);

                    if (i > 0) {
                        sb.append('\n');
                    }

                    sb.append(name).append('=');

                    if (v != null) {
                        sb.append(v);
                    }
                } catch (Exception e) {
                    Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
                }
            }

            data.add(new String[]{ResUtil.getString(PropertiesViewer.this, R.string.system_settings),
                    sb.toString()});

            sb.delete(0, sb.length());

            HashSet<String> secure = new HashSet<String>();

            secure.add(Settings.Secure.ACCESSIBILITY_ENABLED);
            secure.add(Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD);
            secure.add(Settings.Secure.ADB_ENABLED);
            secure.add(Settings.Secure.ALLOW_MOCK_LOCATION);
            secure.add(Settings.Secure.ALLOWED_GEOLOCATION_ORIGINS);
            secure.add(Settings.Secure.ANDROID_ID);
            secure.add(Settings.Secure.BACKGROUND_DATA);
            secure.add(Settings.Secure.BLUETOOTH_ON);
            secure.add(Settings.Secure.DATA_ROAMING);
            secure.add(Settings.Secure.DEFAULT_INPUT_METHOD);
            secure.add(Settings.Secure.DEVELOPMENT_SETTINGS_ENABLED);
            secure.add(Settings.Secure.DEVICE_PROVISIONED);
            secure.add(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            secure.add(Settings.Secure.ENABLED_INPUT_METHODS);
            secure.add(Settings.Secure.HTTP_PROXY);
            secure.add(Settings.Secure.INPUT_METHOD_SELECTOR_VISIBILITY);
            secure.add(Settings.Secure.INSTALL_NON_MARKET_APPS);
            secure.add(Settings.Secure.LOCATION_MODE);
            secure.add(Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            secure.add(Settings.Secure.LOCK_PATTERN_ENABLED);
            secure.add(Settings.Secure.LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED);
            secure.add(Settings.Secure.LOCK_PATTERN_VISIBLE);
            secure.add(Settings.Secure.LOGGING_ID);
            secure.add(Settings.Secure.NETWORK_PREFERENCE);
            secure.add(Settings.Secure.PARENTAL_CONTROL_ENABLED);
            secure.add(Settings.Secure.PARENTAL_CONTROL_LAST_UPDATE);
            secure.add(Settings.Secure.PARENTAL_CONTROL_REDIRECT_URL);
            secure.add(Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE);
            secure.add(Settings.Secure.SETTINGS_CLASSNAME);
            secure.add(Settings.Secure.SYS_PROP_SETTING_VERSION);
            secure.add(Settings.Secure.TTS_DEFAULT_COUNTRY);
            secure.add(Settings.Secure.TTS_DEFAULT_LANG);
            secure.add(Settings.Secure.TTS_DEFAULT_PITCH);
            secure.add(Settings.Secure.TTS_DEFAULT_RATE);
            secure.add(Settings.Secure.TTS_DEFAULT_SYNTH);
            secure.add(Settings.Secure.TTS_DEFAULT_VARIANT);
            secure.add(Settings.Secure.TTS_ENABLED_PLUGINS);
            secure.add(Settings.Secure.TTS_USE_DEFAULTS);
            secure.add(Settings.Secure.USB_MASS_STORAGE_ENABLED);
            secure.add(Settings.Secure.USE_GOOGLE_MAIL);
            secure.add(Settings.Secure.WIFI_MAX_DHCP_RETRY_COUNT);
            secure.add(Settings.Secure.WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS);
            secure.add(Settings.Secure.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON);
            secure.add(Settings.Secure.WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY);
            secure.add(Settings.Secure.WIFI_NUM_OPEN_NETWORKS_KEPT);
            secure.add(Settings.Secure.WIFI_ON);
            secure.add(Settings.Secure.WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE);
            secure.add(Settings.Secure.WIFI_WATCHDOG_AP_COUNT);
            secure.add(Settings.Secure.WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS);
            secure.add(Settings.Secure.WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED);
            secure.add(Settings.Secure.WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS);
            secure.add(Settings.Secure.WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT);
            secure.add(Settings.Secure.WIFI_WATCHDOG_MAX_AP_CHECKS);
            secure.add(Settings.Secure.WIFI_WATCHDOG_ON);
            secure.add(Settings.Secure.WIFI_WATCHDOG_PING_COUNT);
            secure.add(Settings.Secure.WIFI_WATCHDOG_PING_DELAY_MS);
            secure.add(Settings.Secure.WIFI_WATCHDOG_PING_TIMEOUT_MS);
            secure.add(Settings.Secure.WIFI_WATCHDOG_WATCH_LIST);

            cursor = cr.query(Settings.Secure.CONTENT_URI, null, null, null, null);

            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name"); //$NON-NLS-1$

                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        try {
                            String nameVal = cursor.getString(nameIndex);

                            if (!TextUtils.isEmpty(nameVal)) {
                                secure.add(nameVal);
                            }
                        } catch (Exception e) {
                            Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
                        }
                    }
                }

                cursor.close();
            }

            ArrayList<String> sortedSecure = new ArrayList<String>(secure);

            Collections.sort(sortedSecure);

            for (int i = 0, size = sortedSecure.size(); i < size; i++) {
                String name = sortedSecure.get(i);

                try {
                    String v = Settings.Secure.getString(cr, name);

                    if (i > 0) {
                        sb.append('\n');
                    }

                    sb.append(name).append('=');

                    if (v != null) {
                        sb.append(v);
                    }
                } catch (Exception e) {
                    Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
                }
            }

            data.add(new String[]{ResUtil.getString(PropertiesViewer.this, R.string.secure_settings),
                    sb.toString()});

            sb.delete(0, sb.length());

            HashMap<String, String> gsf = new HashMap<String, String>();

            try {
                queryGSF(cr, "%", gsf); //$NON-NLS-1$

                if (gsf.isEmpty()) {
                    queryGSF(cr, "", gsf); //$NON-NLS-1$
                }
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }

            ArrayList<Entry<?, ?>> lgsf = new ArrayList<Entry<?, ?>>();

            lgsf.addAll(gsf.entrySet());

            Collections.sort(lgsf, comparator);

            for (int i = 0, size = lgsf.size(); i < size; i++) {
                Entry<?, ?> ent = lgsf.get(i);

                try {
                    if (i > 0) {
                        sb.append('\n');
                    }

                    sb.append(ent.getKey()).append('=');

                    Object v = ent.getValue();

                    if (v != null) {
                        sb.append(v);
                    }
                } catch (Exception e) {
                    Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
                }
            }

            data.add(new String[]{
                    ResUtil.getString(PropertiesViewer.this, R.string.gsf_settings),
                    sb.length() == 0 ? ResUtil.getString(PropertiesViewer.this, R.string.info_not_available)
                            : sb.toString()});

            sb.delete(0, sb.length());

            Properties props = System.getProperties();

            if (props != null) {
                addProps(sb, props.entrySet());
            }

            data.add(new String[]{ResUtil.getString(PropertiesViewer.this, R.string.vm_settings),
                    sb.toString()});

            sb.delete(0, sb.length());

            Map<String, String> env = System.getenv();

            if (env != null) {
                addProps(sb, env.entrySet());
            }

            data.add(new String[]{ResUtil.getString(PropertiesViewer.this, R.string.env_vars),
                    sb.toString()});

            sb.delete(0, sb.length());

            File buildFile = new File(Environment.getRootDirectory(), "build.prop"); //$NON-NLS-1$

            if (buildFile.exists() && buildFile.isFile() && buildFile.canRead()) {
                props = new Properties();

                InputStream is = null;

                try {
                    is = new FileInputStream(buildFile);
                    props.load(is);

                    addProps(sb, props.entrySet());
                } catch (Exception e) {
                    Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                            Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
                        }
                    }
                }
            }

            data.add(new String[]{
                    ResUtil.getString(PropertiesViewer.this, R.string.build_prop),
                    sb.length() > 0 ? sb.toString() : ResUtil.getString(PropertiesViewer.this,
                            R.string.info_not_available)});

            sb.delete(0, sb.length());

            Map<String, String> getprops = parseGetprops(PropertiesViewer.this);

            if (getprops.size() > 0) {
                addProps(sb, getprops.entrySet());
            }

            data.add(new String[]{
                    ResUtil.getString(PropertiesViewer.this, R.string.runtime_props),
                    sb.length() > 0 ? sb.toString() : ResUtil.getString(PropertiesViewer.this,
                            R.string.info_not_available)});

            handler.sendMessage(handler.obtainMessage(MSG_INIT_OK, data));
        }

        private void queryGSF(ContentResolver cr, String selectionArg, Map<String, String> gsf) {
            Cursor cursor = cr.query(Uri.parse("content://com.google.android.gsf.gservices/prefix"), //$NON-NLS-1$
                    null, null, new String[]{selectionArg}, null);

            if (cursor != null && cursor.getColumnCount() > 1) {
                while (cursor.moveToNext()) {
                    try {
                        String nameVal = cursor.getString(0);

                        if (!TextUtils.isEmpty(nameVal)) {
                            String propVal = cursor.getString(1);

                            if ("android_id".equals(nameVal)) //$NON-NLS-1$
                            {
                                try {
                                    long lv = Long.parseLong(propVal);

                                    // convert it to hex view
                                    propVal = Long.toHexString(lv);
                                } catch (Exception e) {
                                    // ignore
                                }
                            }

                            gsf.put(nameVal, propVal);
                        }
                    } catch (Exception e) {
                        Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
                    }
                }

                cursor.close();
            }
        }

        private void addProps(StringBuilder sb, Set<? extends Entry<?, ?>> props) {
            ArrayList<Entry<?, ?>> lps = new ArrayList<Entry<?, ?>>();

            lps.addAll(props);

            Collections.sort(lps, comparator);

            Object val;
            for (int i = 0, size = lps.size(); i < size; i++) {
                Entry<?, ?> ent = lps.get(i);

                val = ent.getValue();

                if (i > 0) {
                    sb.append('\n');
                }

                sb.append(ent.getKey()).append('=').append(val == null ? "" : val); //$NON-NLS-1$
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.exp_lst_view);
        ExpandableListView contentView = getExpandableListView();
        contentView.setFastScrollEnabled(true);

        if (Util.SDK_VER < 11) {
            registerForContextMenu(contentView);
        }

        setListAdapter(new PropertiesAdapter(this));

        Intent it = new Intent(this, GLPropertiesViewerStub.class);
        startActivityForResult(it, REQUEST_GPU_INFO);

        overridePendingTransition(this, 0, 0);
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

        Util.checkPhonePermission(this);
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
    }

    @Override
    protected void onPause() {
        aborted = true;

        handler.removeMessages(MSG_CHECK_FORCE_COMPRESSION);
        handler.removeMessages(MSG_CONTENT_READY);

        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem mi = menu.add(Menu.NONE, MI_SHARE, Menu.NONE, ResUtil.getString(this, R.string.share));
        mi.setIcon(android.R.drawable.ic_menu_share);
        Util.setShowAsAction(mi, MenuItem.SHOW_AS_ACTION_IF_ROOM);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return onActionSelected(item.getItemId());
    }

    @Override
    public List<ToolMenuDialog.ActionHint> getActions() {
        List<ToolMenuDialog.ActionHint> hints = new ArrayList<ToolMenuDialog.ActionHint>();
        hints.add(new ToolMenuDialog.ActionHint(ResUtil.getString(this, R.string.share), MI_SHARE));

        return hints;
    }

    @Override
    public boolean onActionSelected(int action) {
        if (action == MI_SHARE) {
            final boolean[] items;

            // TODO add ui for setting
            final boolean rememberShareSettigns =
                    Util.getBooleanOption(this, SYSINFO_MANAGER_STORE_NAME,
                            PREF_KEY_REMEMBER_LAST_SHARE_SETTING, true);

            if (rememberShareSettigns) {
                items = Util.getBits(Util.getStringOption(this, PROPERTIES_VIEWER_STORE_NAME,
                        PREF_KEY_LAST_SHARE_SETTING, null), 13, true);
            } else {
                items = new boolean[]{true, true, true, true, true, true, true, true, true, true, true, true,
                        true};
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
                        Util.shortToast(PropertiesViewer.this, R.string.no_cat_selected);

                        return;
                    }

                    if (rememberShareSettigns) {
                        Util.setStringOption(PropertiesViewer.this, PROPERTIES_VIEWER_STORE_NAME,
                                PREF_KEY_LAST_SHARE_SETTING, Util.getBitsString(items));
                    }

                    sendContent(items);
                }
            };

            Util.newAlertDialogBuilder(this)
                    .setTitle(ResUtil.getString(this, R.string.send_props))
                    .setMultiChoiceItems(
                            new CharSequence[]{
                                    ResUtil.getString(this, R.string.device),
                                    ResUtil.getString(this, R.string.sys_apps),
                                    ResUtil.getString(this, R.string.phone),
                                    ResUtil.getString(this, R.string.screen),
                                    "OpenGL", //$NON-NLS-1$
                                    ResUtil.getString(this, R.string.features),
                                    ResUtil.getString(this, R.string.system_settings),
                                    ResUtil.getString(this, R.string.secure_settings),
                                    ResUtil.getString(this, R.string.gsf_settings),
                                    ResUtil.getString(this, R.string.vm_settings),
                                    ResUtil.getString(this, R.string.env_vars),
                                    ResUtil.getString(this, R.string.build_prop),
                                    ResUtil.getString(this, R.string.runtime_props),}, items, selListener)
                    .setPositiveButton(android.R.string.ok, sendListener)
                    .setNegativeButton(android.R.string.cancel, null).create().show();

            return true;
        }

        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        menu.setHeaderTitle(ResUtil.getString(this, R.string.actions));
        menu.add(ResUtil.getString(this, R.string.copy_text));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) item.getMenuInfo();

        int type = ExpandableListView.getPackedPositionType(info.packedPosition);

        if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP
                || type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
            int pos = ExpandableListView.getPackedPositionGroup(info.packedPosition);

            ArrayList<String[]> data = ((PropertiesAdapter) getExpandableListAdapter()).data;

            if (data != null && pos >= 0 && pos < data.size()) {
                String[] ss = data.get(pos);

                String s = ss[0] + '\n' + ss[1];

                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

                if (cm != null && !TextUtils.isEmpty(s)) {
                    cm.setText(s);

                    Util.shortToast(this, R.string.copied_hint);
                }
            }
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_GPU_INFO) {
            if (data != null) {
                if (data.getExtras() != null) {
                    getIntent().putExtras(data.getExtras());
                }

                Util.finishStub(this, data.getIntExtra(EXTRA_PID, 0));
            }

            refresh();
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

        try {
            WebView wv = new WebView(this);
            String ua = wv.getSettings().getUserAgentString();
            getIntent().putExtra("userAgent", ua); //$NON-NLS-1$
            wv.destroy();
        } catch (Exception e) {
            Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
        }

        new Thread(task, "PropertiesLoader").start(); //$NON-NLS-1$
    }

    void sendContent(final boolean[] items) {
        if (progress != null) {
            Util.safeDismissDialog(progress);
        }
        progress = new ProgressDialog(this);
        progress.setMessage(ResUtil.getString(this, R.string.loading));
        progress.setIndeterminate(true);
        progress.show();

        new Thread(new Runnable() {

            public void run() {
                String content = collectTextContent(items);

                if (aborted) {
                    return;
                }

                if (content != null) {
                    handler.sendMessage(handler.obtainMessage(MSG_CHECK_FORCE_COMPRESSION,
                            PLAINTEXT, 0, content));
                } else {
                    handler.sendMessage(handler.obtainMessage(MSG_CONTENT_READY, PLAINTEXT, 0,
                            content));
                }
            }
        }).start();
    }

    String collectTextContent(boolean[] items) {
        ArrayList<String[]> data = ((PropertiesAdapter) getExpandableListAdapter()).data;

        if (data == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        int c = data.size();

        for (int i = 0; i < items.length; i++) {
            if (items[i] && i < c) {
                String[] cat = data.get(i);

                sb.append(cat[0]).append('\n');
                sb.append(HEADER_SPLIT);

                sb.append(cat[1]).append("\n\n"); //$NON-NLS-1$
            }
        }

        if (sb.length() > 0) {
            return sb.toString();
        }

        return null;
    }

    String resolveSimState(int state) {
        switch (state) {
            case TelephonyManager.SIM_STATE_ABSENT:
                return ResUtil.getString(this, R.string.absent);
            case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                return ResUtil.getString(this, R.string.pin_required);
            case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                return ResUtil.getString(this, R.string.puk_required);
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                return ResUtil.getString(this, R.string.network_locked);
            case TelephonyManager.SIM_STATE_READY:
                return ResUtil.getString(this, R.string.ready);
        }
        return ResUtil.getString(this, R.string.unknown);
    }

    String resolveMultiSimConfig(String config) {
        if ("DSDS".equals(config)) { //$NON-NLS-1$
            return ResUtil.getString(this, R.string.dsds);
        } else if ("DSDA".equals(config)) { //$NON-NLS-1$
            return ResUtil.getString(this, R.string.dsda);
        } else if ("TSTS".equals(config)) { //$NON-NLS-1$
            return ResUtil.getString(this, R.string.tsts);
        }
        return ResUtil.getString(this, R.string.unknown);
    }

    String resolveNetworkType(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "GPRS"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "EDGE"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return "UMTS"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_CDMA:
                return "CDMA"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
                return "CDMA - EVDO rev.0"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
                return "CDMA - EVDO rev.A"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                return "CDMA - 1xRTT"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_HSDPA:
                return "HSDPA"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                return "HSUPA"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return "HSPA"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "iDEN"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                return "CDMA - EVDO rev.B"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "LTE"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                return "CDMA - eHRPD"; //$NON-NLS-1$
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "HSPA+"; //$NON-NLS-1$
            case 16: // TelephonyManager.NETWORK_TYPE_GSM:
                return "GSM"; //$NON-NLS-1$
        }
        return ResUtil.getString(this, R.string.unknown);
    }

    String resolvePhoneType(int type) {
        switch (type) {
            case TelephonyManager.PHONE_TYPE_NONE:
                return ResUtil.getString(this, R.string.none);
            case TelephonyManager.PHONE_TYPE_GSM:
                return "GSM"; //$NON-NLS-1$
            case TelephonyManager.PHONE_TYPE_CDMA:
                return "CDMA"; //$NON-NLS-1$
            case TelephonyManager.PHONE_TYPE_SIP:
                return "SIP"; //$NON-NLS-1$
        }
        return ResUtil.getString(this, R.string.unknown);
    }

    String resolveNull(String val) {
        if (val == null) {
            return ResUtil.getString(this, R.string.unknown);
        }
        return val;
    }

    String resolveDensityDpi(DisplayMetrics dm) {
        if (fdDensityDpi != null) {
            try {
                int dpi = (Integer) fdDensityDpi.get(dm);

                switch (dpi) {
                    case DisplayMetrics.DENSITY_LOW:
                        return dpi + " (" + ResUtil.getString(this, R.string.low) + ')'; //$NON-NLS-1$
                    case DisplayMetrics.DENSITY_MEDIUM:
                        return dpi + " (" + ResUtil.getString(this, R.string.medium) + ')'; //$NON-NLS-1$
                    case DisplayMetrics.DENSITY_TV:
                        return dpi + " (TV)"; //$NON-NLS-1$
                    case DisplayMetrics.DENSITY_HIGH:
                        return dpi + " (" + ResUtil.getString(this, R.string.high) + ')'; //$NON-NLS-1$
                    case DisplayMetrics.DENSITY_XHIGH:
                        return dpi + " (xHigh)"; //$NON-NLS-1$
                    case DisplayMetrics.DENSITY_XXHIGH:
                        return dpi + " (xxHigh)"; //$NON-NLS-1$
                    case DisplayMetrics.DENSITY_XXXHIGH:
                        return dpi + " (xxxHigh"; //$NON-NLS-1$
                    default:
                        return "" + dpi; //$NON-NLS-1$
                }
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }
        }
        return ResUtil.getString(this, R.string.unknown);
    }

    String getColorDepth(int pf) {
        PixelFormat info = new PixelFormat();
        PixelFormat.getPixelFormatInfo(pf, info);

        if (info.bitsPerPixel > 0) {
            return "" + info.bitsPerPixel + ' ' + ResUtil.getString(this, R.string.bit); //$NON-NLS-1$
        }
        return ResUtil.getString(this, R.string.unknown);
    }

    void getDisplayDetails(Display disp, StringBuilder sb) {
        DisplayMetrics metrics = new DisplayMetrics();
        disp.getMetrics(metrics);

        DisplayMetrics logicalMetrics = null;

        if (mtdGetRealMetrics != null) {
            try {
                DisplayMetrics dm = new DisplayMetrics();
                mtdGetRealMetrics.invoke(disp, dm);

                logicalMetrics = dm;
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        float xIn =
                logicalMetrics == null ? (metrics.widthPixels / metrics.xdpi)
                        : (logicalMetrics.widthPixels / logicalMetrics.xdpi);
        float yIn =
                logicalMetrics == null ? (metrics.heightPixels / metrics.ydpi)
                        : (logicalMetrics.heightPixels / logicalMetrics.ydpi);
        double dIn = Math.sqrt(xIn * xIn + yIn * yIn);
        float dfIn = ((int) (dIn * 100 + 0.5)) / 100f;
        float dfMm = ((int) (dfIn * 25.4f * 100 + 0.5)) / 100f;

        if (mtdGetName != null) {
            try {
                Object obj = mtdGetName.invoke(disp);

                if (obj != null) {
                    sb.append(obj).append(" (id:") //$NON-NLS-1$
                            .append(disp.getDisplayId()).append(")\n"); //$NON-NLS-1$
                }
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        if (mtdGetType != null) {
            try {
                Object obj = mtdGetType.invoke(disp);

                if (obj instanceof Integer) {
                    String tp = null;

                    switch ((Integer) obj) {
                        case 0:
                            tp = "Unknown"; //$NON-NLS-1$
                            break;
                        case 1:
                            tp = "Built-in"; //$NON-NLS-1$
                            break;
                        case 2:
                            tp = "HDMI"; //$NON-NLS-1$
                            break;
                        case 3:
                            tp = "Wifi"; //$NON-NLS-1$
                            break;
                        case 4:
                            tp = "Overlay"; //$NON-NLS-1$
                            break;
                        case 5:
                            tp = "Virtual"; //$NON-NLS-1$
                            break;
                    }

                    if (tp == null) {
                        tp = ResUtil.getString(this, R.string.unknown) + " (" //$NON-NLS-1$
                                + obj + ')';
                    }

                    sb.append(ResUtil.getString(this, R.string.type)).append(": ") //$NON-NLS-1$
                            .append(tp).append('\n');
                }
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        if (mtdGetAddress != null) {
            try {
                Object obj = mtdGetAddress.invoke(disp);

                if (obj != null) {
                    sb.append(ResUtil.getString(this, R.string.address)).append(": ") //$NON-NLS-1$
                            .append(obj).append('\n');
                }
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        if (mtdGetLayerStack != null) {
            try {
                Object obj = mtdGetLayerStack.invoke(disp);

                if (obj != null) {
                    sb.append(ResUtil.getString(this, R.string.layer_stack)).append(": ") //$NON-NLS-1$
                            .append(obj).append('\n');
                }
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        if (mtdGetOwnerUid != null) {
            try {
                Object obj = mtdGetOwnerUid.invoke(disp);

                if (obj != null) {
                    sb.append(ResUtil.getString(this, R.string.owner_uid)).append(": ") //$NON-NLS-1$
                            .append(obj).append('\n');
                }
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        if (mtdGetOwnerPackageName != null) {
            try {
                Object obj = mtdGetOwnerPackageName.invoke(disp);

                if (obj != null) {
                    sb.append(ResUtil.getString(this, R.string.owner_pkg)).append(": ") //$NON-NLS-1$
                            .append(obj).append('\n');
                }
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        sb.append(ResUtil.getString(this, R.string.resolution)).append(": ") //$NON-NLS-1$
                .append(metrics.widthPixels).append(" x ") //$NON-NLS-1$
                .append(metrics.heightPixels).append('\n');

        if (logicalMetrics != null) {
            sb.append(ResUtil.getString(this, R.string.logical_resolution)).append(": ") //$NON-NLS-1$
                    .append(logicalMetrics.widthPixels).append(" x ") //$NON-NLS-1$
                    .append(logicalMetrics.heightPixels).append('\n');
        }

        sb.append(ResUtil.getString(this, R.string.density_dpi))
                .append(": ") //$NON-NLS-1$
                .append(resolveDensityDpi(metrics)).append('\n')
                .append(ResUtil.getString(this, R.string.density))
                .append(": ") //$NON-NLS-1$
                .append(metrics.density).append('\n')
                .append(ResUtil.getString(this, R.string.scale_density)).append(": ") //$NON-NLS-1$
                .append(metrics.scaledDensity).append('\n')
                .append(ResUtil.getString(this, R.string.refresh_rate)).append(": ") //$NON-NLS-1$
                .append(disp.getRefreshRate()).append('\n');

        if (mtdGetSupportedRefreshRates != null) {
            try {
                Object obj = mtdGetSupportedRefreshRates.invoke(disp);

                if (obj instanceof float[]) {
                    sb.append(ResUtil.getString(this, R.string.supported_refresh_rates)).append(": "); //$NON-NLS-1$
                    float[] rates = (float[]) obj;
                    for (int i = 0; i < rates.length; i++) {
                        if (i > 0) {
                            sb.append(", "); //$NON-NLS-1$
                        }
                        sb.append(rates[i]);
                    }
                    sb.append('\n');
                }
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        sb.append("X DPI: ") //$NON-NLS-1$
                .append(metrics.xdpi)
                .append('\n')
                .append("Y DPI: ") //$NON-NLS-1$
                .append(metrics.ydpi).append('\n')
                .append(ResUtil.getString(this, R.string.pixel_format))
                .append(": ") //$NON-NLS-1$
                .append(getPixelFormat(disp.getPixelFormat())).append('\n')
                .append(ResUtil.getString(this, R.string.color_depth)).append(": ") //$NON-NLS-1$
                .append(getColorDepth(disp.getPixelFormat())).append('\n')
                .append(ResUtil.getString(this, R.string.estimated_screen_size)).append(": ") //$NON-NLS-1$
                .append(dfIn).append(' ').append(ResUtil.getString(this, R.string.in)).append(" / ") //$NON-NLS-1$
                .append(dfMm).append(' ').append(ResUtil.getString(this, R.string.mm));

        if (mtdGetFlags != null) {
            try {
                Object obj = mtdGetFlags.invoke(disp);

                if (obj instanceof Integer) {
                    int flag = (Integer) obj;

                    sb.append('\n').append(ResUtil.getString(this, R.string.flags)).append(": "); //$NON-NLS-1$

                    if ((flag & Display.FLAG_SUPPORTS_PROTECTED_BUFFERS) != 0) {
                        sb.append("SUPPORTS_PROTECTED_BUFFERS "); //$NON-NLS-1$
                    }

                    if ((flag & Display.FLAG_SECURE) != 0) {
                        sb.append("SECURE "); //$NON-NLS-1$
                    }

                    if ((flag & Display.FLAG_PRIVATE) != 0) {
                        sb.append("PRIVATE "); //$NON-NLS-1$
                    }

                    if ((flag & Display.FLAG_PRESENTATION) != 0) {
                        sb.append("PRESENTATION "); //$NON-NLS-1$
                    }
                }
            } catch (Exception e) {
                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
            }
        }
    }

    String getPixelFormat(int pf) {
        switch (pf) {
            case PixelFormat.TRANSLUCENT:
                return "TRANSLUCENT"; //$NON-NLS-1$
            case PixelFormat.TRANSPARENT:
                return "TRANSPARENT"; //$NON-NLS-1$
            case PixelFormat.OPAQUE:
                return "OPAQUE"; //$NON-NLS-1$
            case PixelFormat.RGBA_8888:
                return "RGBA 8888"; //$NON-NLS-1$
            case PixelFormat.RGBX_8888:
                return "RGBX 8888"; //$NON-NLS-1$
            case PixelFormat.RGB_888:
                return "RGB 888"; //$NON-NLS-1$
            case PixelFormat.RGB_565:
                return "RGB 565"; //$NON-NLS-1$
            case PixelFormat.RGBA_5551:
                return "RGBA 5551"; //$NON-NLS-1$
            case PixelFormat.RGBA_4444:
                return "RGBA 4444"; //$NON-NLS-1$
            case PixelFormat.A_8:
                return "A 8"; //$NON-NLS-1$
            case PixelFormat.L_8:
                return "L 8"; //$NON-NLS-1$
            case PixelFormat.LA_88:
                return "LA 88"; //$NON-NLS-1$
            case PixelFormat.RGB_332:
                return "RGB 332"; //$NON-NLS-1$
            case PixelFormat.YCbCr_422_SP:
                return "YCbCr 422 SP/NV16"; //$NON-NLS-1$
            case PixelFormat.YCbCr_420_SP:
                return "YCbCr 420 SP/NV21"; //$NON-NLS-1$
            case PixelFormat.YCbCr_422_I:
                return "YCbCr 422 I/YUYV(YUY2)"; //$NON-NLS-1$
            case PixelFormat.JPEG:
                return "JPEG"; //$NON-NLS-1$
        }

        return ResUtil.getString(this, R.string.unknown) + " (" + pf + ')'; //$NON-NLS-1$
    }

    /**
     * SafeTelephonyManager
     */
    static final class SafeTelephonyManager {

        private static final Method mtdGetGroupIdLevel1 = Util.getMethod(TelephonyManager.class,
                "getGroupIdLevel1"); //$NON-NLS-1$
        private static final Method mtdGetMmsUAProfUrl = Util.getMethod(TelephonyManager.class,
                "getMmsUAProfUrl"); //$NON-NLS-1$
        private static final Method mtdGetMmsUserAgent = Util.getMethod(TelephonyManager.class,
                "getMmsUserAgent"); //$NON-NLS-1$
        private static final Method mtdGetVoiceNetworkType = Util.getMethod(TelephonyManager.class,
                "getVoiceNetworkType"); //$NON-NLS-1$
        private static final Method mtdHasIccCard = Util
                .getMethod(TelephonyManager.class, "hasIccCard"); //$NON-NLS-1$
        private static final Method mtdIsVoiceCapable = Util.getMethod(TelephonyManager.class,
                "isVoiceCapable"); //$NON-NLS-1$
        private static final Method mtdIsSmsCapable = Util.getMethod(TelephonyManager.class,
                "isSmsCapable"); //$NON-NLS-1$
        private static final Method mtdGetMultiSimConfiguration = Util.getMethod(
                TelephonyManager.class, "getMultiSimConfiguration"); //$NON-NLS-1$

        private TelephonyManager tm;

        SafeTelephonyManager(TelephonyManager tm) {
            this.tm = tm;
        }

        int getPhoneType() {
            try {
                return tm.getPhoneType();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return 0;
            } catch (Exception e) {
                // may have no permission
                return 0;
            }
        }

        String getDeviceId() {
            try {
                return tm.getDeviceId();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return null;
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }

        String getDeviceSoftwareVersion() {
            try {
                return tm.getDeviceSoftwareVersion();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return null;
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }

        String getGroupIdLevel1() {
            if (mtdGetGroupIdLevel1 != null) {
                try {
                    return (String) mtdGetGroupIdLevel1.invoke(tm);
                } catch (Exception e) {
                    // ignore
                }
            }
            return null;
        }

        String getLine1Number() {
            try {
                return tm.getLine1Number();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return null;
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }

        String getMmsUserAgent() {
            if (mtdGetMmsUserAgent != null) {
                try {
                    return (String) mtdGetMmsUserAgent.invoke(tm);
                } catch (Exception e) {
                    // ignore
                }
            }
            return null;
        }

        String getMmsUAProfUrl() {
            if (mtdGetMmsUAProfUrl != null) {
                try {
                    return (String) mtdGetMmsUAProfUrl.invoke(tm);
                } catch (Exception e) {
                    // ignore
                }
            }
            return null;
        }

        int getVoiceNetworkType() {
            if (mtdGetVoiceNetworkType != null) {
                try {
                    return (Integer) mtdGetVoiceNetworkType.invoke(tm);
                } catch (Exception e) {
                    // ignore
                }
            }
            return 0;
        }

        Boolean hasIccCard() {
            if (mtdHasIccCard != null) {
                try {
                    return (Boolean) mtdHasIccCard.invoke(tm);
                } catch (Exception e) {
                    // ignore
                }
            }
            return null;
        }

        Boolean isVoiceCapable() {
            if (mtdIsVoiceCapable != null) {
                try {
                    return (Boolean) mtdIsVoiceCapable.invoke(tm);
                } catch (Exception e) {
                    // ignore
                }
            }
            return null;
        }

        Boolean isSmsCapable() {
            if (mtdIsSmsCapable != null) {
                try {
                    return (Boolean) mtdIsSmsCapable.invoke(tm);
                } catch (Exception e) {
                    // ignore
                }
            }
            return null;
        }

        String getMultiSimConfiguration() {
            if (mtdGetMultiSimConfiguration != null) {
                try {
                    return String.valueOf(mtdGetMultiSimConfiguration.invoke(tm));
                } catch (Exception e) {
                    // ignore
                }
            }
            return null;
        }

        String getNetworkCountryIso() {
            try {
                return tm.getNetworkCountryIso();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return null;
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }

        String getNetworkOperatorName() {
            try {
                return tm.getNetworkOperatorName();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return null;
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }

        String getNetworkOperator() {
            try {
                return tm.getNetworkOperator();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return null;
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }

        int getNetworkType() {
            try {
                return tm.getNetworkType();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return 0;
            } catch (Exception e) {
                // may have no permission
                return 0;
            }
        }

        boolean isNetworkRoaming() {
            try {
                return tm.isNetworkRoaming();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return false;
            } catch (Exception e) {
                // may have no permission
                return false;
            }
        }

        String getSimCountryIso() {
            try {
                return tm.getSimCountryIso();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return null;
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }

        String getSimOperatorName() {
            try {
                return tm.getSimOperatorName();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return null;
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }

        String getSimOperator() {
            try {
                return tm.getSimOperator();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return null;
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }

        String getSimSerialNumber() {
            try {
                return tm.getSimSerialNumber();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return null;
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }

        int getSimState() {
            try {
                return tm.getSimState();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return 0;
            } catch (Exception e) {
                // may have no permission
                return 0;
            }
        }

        String getSubscriberId() {
            try {
                return tm.getSubscriberId();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return null;
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }

        String getVoiceMailNumber() {
            try {
                return tm.getVoiceMailNumber();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return null;
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }

        String getVoiceMailAlphaTag() {
            try {
                return tm.getVoiceMailAlphaTag();
            } catch (NullPointerException e) {
                // This is a remedy for sdk 1.6 and earlier
                return null;
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }

        CellLocation getCellLocation() {
            try {
                return tm.getCellLocation();
            } catch (Exception e) {
                // may have no permission
                return null;
            }
        }
    }

    /**
     * PropertiesAdapter
     */
    static final class PropertiesAdapter extends BaseExpandableListAdapter {

        private Context ctx;
        private float scale;

        ArrayList<String[]> data;

        PropertiesAdapter(Context ctx) {
            this.ctx = ctx;
            this.scale = ctx.getResources().getDisplayMetrics().density;
        }

        void update(ArrayList<String[]> data) {
            this.data = data;
        }

        public int getGroupCount() {
            if (data != null) {
                return data.size();
            }
            return 0;
        }

        public int getChildrenCount(int groupPosition) {
            return 1;
        }

        public Object getGroup(int groupPosition) {
            if (data != null) {
                return data.get(groupPosition);
            }
            return null;
        }

        public Object getChild(int groupPosition, int childPosition) {
            if (data != null) {
                return data.get(groupPosition)[1];
            }
            return null;
        }

        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        public boolean hasStableIds() {
            return true;
        }

        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                                 ViewGroup parent) {
            TextView v = getTextView(convertView);

            v.setTextAppearance(ctx, android.R.style.TextAppearance_Medium);

            v.setPadding((int) (40 * scale), (int) (5 * scale), (int) (5 * scale), (int) (5 * scale));

            v.setText(data.get(groupPosition)[0]);

            return v;
        }

        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                                 View convertView, ViewGroup parent) {
            TextView v = getTextView(convertView);

            v.setTextAppearance(ctx, android.R.style.TextAppearance_Small);

            v.setPadding((int) (8 * scale), (int) (5 * scale), (int) (5 * scale), (int) (5 * scale));

            v.setText(data.get(groupPosition)[1]);

            if (Util.SDK_VER >= 11) {
                Util.setTextIsSelectable(v, true);
            }

            return v;
        }

        private TextView getTextView(View convertView) {
            TextView v;

            if (convertView instanceof TextView) {
                v = (TextView) convertView;
            } else {
                v = new TextView(ctx);
                v.setGravity(Gravity.CENTER_VERTICAL);
            }

            return v;
        }

        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

    }

    /**
     * GetPropsService
     */
    public static final class GetPropsService extends Service {

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onStart(Intent intent, final int startId) {
            super.onStart(intent, startId);

            new Thread("GetPropsStub") { //$NON-NLS-1$

                public void run() {
                    Bundle result = new Bundle();

                    BufferedReader reader = null;

                    try {
                        java.lang.Process proc = Runtime.getRuntime().exec("getprop"); //$NON-NLS-1$

                        reader = new BufferedReader(new InputStreamReader(proc.getInputStream()), 2048);

                        String line;
                        while ((line = reader.readLine()) != null) {
                            parseProp(line, result);
                        }
                    } catch (Exception e) {
                        Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                Log.e(PropertiesViewer.class.getName(), e.getLocalizedMessage(), e);
                            }
                        }
                    }

                    Intent it = new Intent(ACTION_GETPROPS);
                    it.putExtra("result", result); //$NON-NLS-1$
                    it.putExtra(EXTRA_PID, Process.myPid());

                    sendBroadcast(it);

                    stopSelfResult(startId);
                }

                ;
            }.start();
        }
    }

    /**
     * GLPropertiesViewerStub
     */
    public static final class GLPropertiesViewerStub extends Activity {

        private GLSurfaceView surface, surface2;
        private int clientVersion;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            requestWindowFeature(Window.FEATURE_NO_TITLE);

            setResult(RESULT_OK, getIntent());

            if (mtdSetEGLContextClientVersion != null && fdReqGlEsVersion != null) {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ConfigurationInfo cfg = am.getDeviceConfigurationInfo();

                try {
                    int esVer = (Integer) fdReqGlEsVersion.get(cfg);

                    if (esVer >= 0x20000) {
                        // mtdSetEGLContextClientVersion.invoke( surface, 2 );
                        clientVersion = 2;
                    }
                } catch (Exception e) {
                    Log.e(GLPropertiesViewerStub.class.getName(), e.getLocalizedMessage(), e);
                }
            }

            boolean needDetails = !getIntent().getBooleanExtra(EXTRA_NO_DETAILS, false);

            if (clientVersion < 2 || !needDetails) {
                surface = new GLSurfaceView(this);

                if (clientVersion >= 2) {
                    try {
                        mtdSetEGLContextClientVersion.invoke(surface, 2);
                    } catch (Exception e) {
                        Log.e(GLPropertiesViewerStub.class.getName(), e.getLocalizedMessage(), e);
                    }
                }

                surface.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
                surface.setRenderer(new GLRenderer(this, clientVersion, needDetails, null));

                setContentView(surface);
            } else {
                RelativeLayout pane = new RelativeLayout(this);

                final CountDownLatch latch = new CountDownLatch(2);

                surface = new GLSurfaceView(this);
                surface.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
                surface.setRenderer(new GLRenderer(this, 0, needDetails, latch));
                pane.addView(surface);

                surface2 = new GLSurfaceView(this);
                try {
                    mtdSetEGLContextClientVersion.invoke(surface2, 2);
                } catch (Exception e) {
                    Log.e(GLPropertiesViewerStub.class.getName(), e.getLocalizedMessage(), e);
                }
                surface2.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
                surface2.setRenderer(new GLRenderer(this, clientVersion, needDetails, latch));
                pane.addView(surface2);

                setContentView(pane);

                new Thread() {

                    public void run() {
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            // ignore
                        } finally {
                            finish();
                        }
                    }

                    ;
                }.start();
            }
        }

        @Override
        protected void onSaveInstanceState(Bundle outState) {
            // fix: https://code.google.com/p/android/issues/detail?id=19917
            outState.putString("WORKAROUND_FOR_BUG_19917_KEY", //$NON-NLS-1$
                    "WORKAROUND_FOR_BUG_19917_VALUE"); //$NON-NLS-1$

            super.onSaveInstanceState(outState);
        }

        @Override
        protected void onResume() {
            super.onResume();

            surface.onResume();

            if (surface2 != null) {
                surface2.onResume();
            }
        }

        @Override
        protected void onPause() {
            super.onPause();

            surface.onPause();

            if (surface2 != null) {
                surface2.onPause();
            }
        }
    }

    /**
     * GLRenderer
     */
    private static final class GLRenderer implements Renderer {

        private Activity ac;
        private CountDownLatch latch;
        private int clientVersion;
        private boolean needDetails;

        GLRenderer(Activity ac, int clientVersion, boolean needDetails, CountDownLatch latch) {
            this.ac = ac;
            this.latch = latch;
            this.clientVersion = clientVersion;
            this.needDetails = needDetails;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Intent it = ac.getIntent();

            if (needDetails) {
                StringBuilder props = new StringBuilder();

                props
                        .append(ResUtil.getString(ac, R.string.vendor))
                        .append(": ") //$NON-NLS-1$
                        .append(gl.glGetString(GL10.GL_VENDOR)).append('\n')
                        .append(ResUtil.getString(ac, R.string.renderer))
                        .append(": ") //$NON-NLS-1$
                        .append(gl.glGetString(GL10.GL_RENDERER)).append('\n')
                        .append(ResUtil.getString(ac, R.string.version)).append(": ") //$NON-NLS-1$
                        .append(gl.glGetString(GL10.GL_VERSION)).append('\n')
                        .append(ResUtil.getString(ac, R.string.extensions)).append(": ") //$NON-NLS-1$
                        .append(gl.glGetString(GL10.GL_EXTENSIONS)).append('\n');

                int[] buf = new int[1];

                if (clientVersion >= 2) {
                    gl.glGetIntegerv(GLES20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_combined_texture_image_units))
                            .append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GLES20.GL_MAX_CUBE_MAP_TEXTURE_SIZE, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_cube_map_texture_size)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GLES20.GL_MAX_FRAGMENT_UNIFORM_VECTORS, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_fragment_uniform_vectors)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GLES20.GL_MAX_RENDERBUFFER_SIZE, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_render_buf_size)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GLES20.GL_MAX_TEXTURE_IMAGE_UNITS, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_texture_image_units)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_tex_size)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GLES20.GL_MAX_VARYING_VECTORS, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_varying_vectors)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GLES20.GL_MAX_VERTEX_ATTRIBS, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_vertex_attributes)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GLES20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_vertex_texture_image_units)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GLES20.GL_MAX_VERTEX_UNIFORM_VECTORS, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_vertex_uniform_vectors)).append(": ") //$NON-NLS-1$
                            .append(buf[0]);
                } else {
                    gl.glGetIntegerv(GL10.GL_MAX_LIGHTS, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_lights)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GL11.GL_MAX_CLIP_PLANES, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_clip_planes)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GL10.GL_MAX_ELEMENTS_INDICES, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_elem_indices)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GL10.GL_MAX_ELEMENTS_VERTICES, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_elem_vertices)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GL11Ext.GL_MAX_PALETTE_MATRICES_OES, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_pal_matrices)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GL11Ext.GL_MAX_VERTEX_UNITS_OES, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_vertex_units)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GL10.GL_MAX_TEXTURE_SIZE, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_tex_size)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GL10.GL_MAX_TEXTURE_UNITS, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_tex_units)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GL10.GL_MAX_TEXTURE_STACK_DEPTH, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_tex_stack_depth)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GL10.GL_MAX_MODELVIEW_STACK_DEPTH, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_mv_stack_depth)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GL10.GL_MAX_PROJECTION_STACK_DEPTH, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_proj_stack_depth)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GL11ExtensionPack.GL_MAX_COLOR_ATTACHMENTS_OES, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_color_atts)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GL11ExtensionPack.GL_MAX_CUBE_MAP_TEXTURE_SIZE, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_cube_map_tex_size)).append(": ") //$NON-NLS-1$
                            .append(buf[0]).append('\n');

                    gl.glGetIntegerv(GL11ExtensionPack.GL_MAX_RENDERBUFFER_SIZE_OES, buf, 0);
                    props.append(ResUtil.getString(ac, R.string.max_render_buf_size)).append(": ") //$NON-NLS-1$
                            .append(buf[0]);
                }

                synchronized (ac) {
                    String details = it.getStringExtra(EXTRA_GLPROPS);
                    if (details == null) {
                        it.putExtra(EXTRA_GLPROPS, props.toString());
                    } else {
                        it.putExtra(EXTRA_GLPROPS, details + "\n\n" + props.toString()); //$NON-NLS-1$
                    }
                }
            }

            synchronized (ac) {
                it.putExtra(EXTRA_PID, Process.myPid());
            }

            if (latch == null || clientVersion >= 2) {
                it.putExtra(EXTRA_GLVENDOR, gl.glGetString(GL10.GL_VENDOR));
                it.putExtra(EXTRA_GLRENDERER, gl.glGetString(GL10.GL_RENDERER));
            }

            if (latch == null) {
                ac.finish();
            } else {
                latch.countDown();
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
        }

        @Override
        public void onDrawFrame(GL10 gl) {
        }

    }
}
