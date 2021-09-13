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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import org.uguess.android.resource.IResourcesService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * ResUtil
 */
public final class ResUtil {

    private static final String RESOURCE_ACTION = "org.uguess.android.action.RESOURCE"; //$NON-NLS-1$
    private static final String RESOURCE_CLZ_NAME = "org.uguess.android.resource.ResourcesService"; //$NON-NLS-1$
    private static final String NL_PACKAGE_PREFIX = "org.uguess.android.sysinfo.nl."; //$NON-NLS-1$

    private static final String CACHE_FNAME = ".stringIdMapping"; //$NON-NLS-1$

    private static final SparseArray<String> stringIds = loadIds(R.string.class);

    private volatile static Resources externalRes;
    private volatile static SparseIntArray idMapping;

    private ResUtil() {
    }

    private static SparseArray<String> loadIds(Class<?> clz) {
        SparseArray<String> mapping = new SparseArray<String>();

        Field[] fds = clz.getFields();

        if (fds != null) {
            try {
                for (Field fd : fds) {
                    if (fd.getType() == int.class) {
                        int value = (Integer) fd.get(null);

                        mapping.put(value, fd.getName());
                    }
                }
            } catch (Exception e) {
                Log.e(ResUtil.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        return mapping;
    }

    static void initResources(Context ctx) {
        String resPkgName =
                Util.getStringOption(ctx, Constants.SYSINFO_MANAGER_STORE_NAME,
                        Constants.PREF_KEY_RES_PKG_NAME, null);

        if (resPkgName != null) {
            try {
                PackageManager pm = ctx.getPackageManager();

                PackageInfo pi = pm.getPackageInfo(resPkgName, 0);

                if (pi != null && isValidResPackage(pm, ctx, resPkgName)) {
                    Bundle cachedMap = loadCachedResource(ctx);

                    loadResource(ctx, resPkgName, cachedMap);

                    return;
                } else {
                    Log.e(ResUtil.class.getName(), "The resource package is invalid."); //$NON-NLS-1$
                }
            } catch (Exception e) {
                Log.e(ResUtil.class.getName(), "Cannot initialize resource package: " + resPkgName); //$NON-NLS-1$
            }
        }

        loadResource(ctx, null, null);
    }

    private static void setupLocale(Context ctx, Configuration baseConfig) {
        String userLocale =
                Util.getStringOption(ctx, Constants.SYSINFO_MANAGER_STORE_NAME,
                        Constants.PREF_KEY_USER_LOCALE, null);

        if (userLocale == null) {
            if (!Locale.getDefault().equals(baseConfig.locale)) {
                Configuration cfg = new Configuration(baseConfig);
                cfg.locale = Locale.getDefault();

                ctx.getResources().updateConfiguration(cfg, null);
            }

            if (externalRes != null && externalRes != ctx.getResources()
                    && !Locale.getDefault().equals(externalRes.getConfiguration().locale)) {
                Configuration cfg = new Configuration(baseConfig);
                cfg.locale = Locale.getDefault();

                externalRes.updateConfiguration(cfg, null);
            }
        } else {
            Locale lc = Util.string2Locale(userLocale);

            if (lc != null && !lc.equals(baseConfig.locale)) {
                Configuration cfg = new Configuration(baseConfig);
                cfg.locale = lc;

                ctx.getResources().updateConfiguration(cfg, null);
            }

            if (externalRes != null && externalRes != ctx.getResources() && lc != null
                    && !lc.equals(externalRes.getConfiguration().locale)) {
                Configuration cfg = new Configuration(baseConfig);
                cfg.locale = lc;

                externalRes.updateConfiguration(cfg, null);
            }
        }
    }

    static void setupResources(final Context ctx, final String pkgName,
                               final ResourceListener listener) throws NameNotFoundException {
        Util.setStringOption(ctx, Constants.SYSINFO_MANAGER_STORE_NAME,
                Constants.PREF_KEY_RES_PKG_NAME, pkgName);

        if (pkgName == null) {
            writeResourceCache(ctx, null);
            loadResource(ctx, null, null);

            if (listener != null) {
                listener.resourceChanged();
            }

            return;
        }

        ServiceConnection sc = new ServiceConnection() {

            public void onServiceDisconnected(ComponentName name) {
            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                IResourcesService lsc = IResourcesService.Stub.asInterface(service);

                try {
                    Bundle mapping = lsc.getStrings();

                    writeResourceCache(ctx, mapping);
                    loadResource(ctx, pkgName, mapping);

                    if (listener != null) {
                        listener.resourceChanged();
                    }
                } catch (RemoteException e) {
                    Log.e(ResUtil.class.getName(), e.getLocalizedMessage(), e);
                } finally {
                    ctx.unbindService(this);
                }
            }
        };

        Intent it = new Intent(RESOURCE_ACTION);
        it.setClassName(pkgName, RESOURCE_CLZ_NAME);

        boolean suc = ctx.bindService(it, sc, Context.BIND_AUTO_CREATE);

        if (!suc) {
            Log.e(ResUtil.class.getName(), "Bind resource service failed: " + pkgName); //$NON-NLS-1$
        }
    }

    static void configureResources(Context ctx, Configuration baseConfig) {
        setupLocale(ctx, baseConfig);
    }

    static int getInstalledNLPackageCount(Context ctx) {
        PackageManager pm = ctx.getPackageManager();

        List<PackageInfo> pkgs = pm.getInstalledPackages(0);

        int count = 0;

        for (PackageInfo pi : pkgs) {
            String pkgName = pi.packageName;

            if (pkgName.startsWith(NL_PACKAGE_PREFIX) && isValidResPackage(pm, ctx, pkgName)) {
                count++;
            }
        }

        return count;
    }

    static void getNLPackages(final Context ctx, final Map<String, String> nls, final Object mutex)
            throws InterruptedException {
        PackageManager pm = ctx.getPackageManager();

        List<PackageInfo> pkgs = pm.getInstalledPackages(0);

        final int[] counter = new int[]{0};

        for (PackageInfo pi : pkgs) {
            final String pkgName = pi.packageName;

            if (pkgName.startsWith(NL_PACKAGE_PREFIX) && isValidResPackage(pm, ctx, pkgName)) {
                ServiceConnection sc = new ServiceConnection() {

                    public void onServiceDisconnected(ComponentName name) {
                    }

                    public void onServiceConnected(ComponentName name, IBinder service) {
                        IResourcesService lsc = IResourcesService.Stub.asInterface(service);

                        try {
                            String[] lcs = lsc.getSupportedLocales();

                            if (lcs != null) {
                                for (String lc : lcs) {
                                    nls.put(lc, pkgName);
                                }
                            }
                        } catch (RemoteException e) {
                            Log.e(ResUtil.class.getName(), e.getLocalizedMessage(), e);
                        } finally {
                            ctx.unbindService(this);
                        }

                        counter[0]--;

                        if (counter[0] <= 0) {
                            synchronized (mutex) {
                                mutex.notifyAll();
                            }
                        }
                    }
                };

                Intent it = new Intent(RESOURCE_ACTION);
                it.setClassName(pkgName, RESOURCE_CLZ_NAME);

                counter[0]++;

                boolean suc = ctx.bindService(it, sc, Context.BIND_AUTO_CREATE);

                if (!suc) {
                    Log.e(ResUtil.class.getName(), "Bind resource service failed: " + pkgName); //$NON-NLS-1$
                }
            }
        }

        if (counter[0] > 0) {
            synchronized (mutex) {
                mutex.wait();
            }
        }
    }

    static void checkResources(final Context ctx, final ResourceListener listener) {
        final String resPkgName =
                Util.getStringOption(ctx, Constants.SYSINFO_MANAGER_STORE_NAME,
                        Constants.PREF_KEY_RES_PKG_NAME, null);

        if (resPkgName != null) {
            try {
                PackageManager pm = ctx.getPackageManager();

                PackageInfo pi = pm.getPackageInfo(resPkgName, 0);

                if (pi != null && isValidResPackage(pm, ctx, resPkgName)) {
                    final Bundle cachedMap = loadCachedResource(ctx);

                    ServiceConnection sc = new ServiceConnection() {

                        public void onServiceDisconnected(ComponentName name) {
                        }

                        public void onServiceConnected(ComponentName name, IBinder service) {
                            IResourcesService lsc = IResourcesService.Stub.asInterface(service);

                            try {
                                Bundle mapping = lsc.getStrings();

                                if (cachedMap != null && mapping != null) {
                                    // compare difference
                                    boolean diff = false;

                                    if (cachedMap.size() != mapping.size()) {
                                        diff = true;
                                    } else {
                                        for (String key : mapping.keySet()) {
                                            Object val = cachedMap.get(key);
                                            Object eval = mapping.get(key);

                                            if (val != null && eval != null) {
                                                if (!val.equals(eval)) {
                                                    diff = true;
                                                    break;
                                                }
                                            } else if (val != null || eval != null) {
                                                diff = true;
                                                break;
                                            }
                                        }
                                    }

                                    if (diff) {
                                        writeResourceCache(ctx, mapping);
                                        loadResource(ctx, resPkgName, mapping);

                                        if (listener != null) {
                                            listener.resourceChanged();
                                        }
                                    }
                                } else if (cachedMap != null || mapping != null) {
                                    writeResourceCache(ctx, mapping);
                                    loadResource(ctx, resPkgName, mapping);

                                    if (listener != null) {
                                        listener.resourceChanged();
                                    }
                                }
                            } catch (RemoteException e) {
                                Log.e(ResUtil.class.getName(), e.getLocalizedMessage(), e);
                            } finally {
                                ctx.unbindService(this);
                            }
                        }
                    };

                    Intent it = new Intent(RESOURCE_ACTION);
                    it.setClassName(resPkgName, RESOURCE_CLZ_NAME);

                    boolean suc = ctx.bindService(it, sc, Context.BIND_AUTO_CREATE);

                    if (!suc) {
                        Log.e(ResUtil.class.getName(), "Bind resource service failed: " + resPkgName); //$NON-NLS-1$
                    }
                } else {
                    Log.e(ResUtil.class.getName(), "The resource package is invalid."); //$NON-NLS-1$
                }
            } catch (NameNotFoundException e) {
                // resource package may be removed, we need to clean up the
                // cache and reload here

                Bundle cache = loadCachedResource(ctx);

                if (cache != null) {
                    writeResourceCache(ctx, null);
                    loadResource(ctx, null, null);

                    if (listener != null) {
                        listener.resourceChanged();
                    }
                }
            } catch (Exception e) {
                Log.e(ResUtil.class.getName(), "Cannot locate resource package: " + resPkgName); //$NON-NLS-1$
            }
        }
    }

    private static boolean isValidResPackage(PackageManager pm, Context ctx, String resPkgName) {
        return Util.isDebugPackage(ctx) || pm.checkSignatures(ctx.getPackageName(), resPkgName) == PackageManager.SIGNATURE_MATCH;
    }

    static void loadResource(Context ctx, String pkgName, Bundle mapping) {
        if (pkgName == null || mapping == null) {
            externalRes = null;

            if (idMapping != null) {
                idMapping.clear();
                idMapping = null;
            }
        } else {
            try {
                externalRes = ctx.getPackageManager().getResourcesForApplication(pkgName);

                idMapping = new SparseIntArray();

                for (int i = 0, size = stringIds.size(); i < size; i++) {
                    int key = stringIds.keyAt(i);

                    String value = stringIds.get(key);

                    Object id = mapping.get(value);

                    if (id instanceof Integer) {
                        idMapping.put(key, (Integer) id);
                    }
                }

            } catch (NameNotFoundException e) {
                idMapping = null;
                externalRes = null;

                Log.e(ResUtil.class.getName(), "Cannot locate resource package: " + pkgName); //$NON-NLS-1$
            }
        }

        setupLocale(ctx, ctx.getResources().getConfiguration());
    }

    static void writeResourceCache(Context ctx, Bundle mapping) {
        File cacheDir = ctx.getFilesDir();

        File stringCache = new File(cacheDir, CACHE_FNAME);

        if (mapping == null) {
            if (stringCache.exists() && stringCache.isFile()) {
                stringCache.delete();
            }

            return;
        }

        BufferedOutputStream fos = null;

        try {
            Properties props = new Properties();

            for (String key : mapping.keySet()) {
                props.setProperty(key, String.valueOf(mapping.getInt(key)));
            }

            fos = new BufferedOutputStream(new FileOutputStream(stringCache), 4096);

            props.store(fos, null);
        } catch (Exception e) {
            Log.e(ResUtil.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private static Bundle loadCachedResource(Context ctx) {
        File cacheDir = ctx.getFilesDir();

        File stringCache = new File(cacheDir, CACHE_FNAME);

        if (stringCache.exists() && stringCache.canRead()) {
            BufferedInputStream fis = null;

            try {
                fis = new BufferedInputStream(new FileInputStream(stringCache), 4096);

                Properties props = new Properties();
                props.load(fis);

                Bundle mapping = new Bundle();

                try {
                    for (Object key : props.keySet()) {
                        mapping.putInt((String) key, Integer.parseInt(props.getProperty((String) key)));
                    }
                } catch (Exception e) {
                    Log.e(ResUtil.class.getName(), e.getLocalizedMessage(), e);
                }

                return mapping.size() == 0 ? null : mapping;
            } catch (Exception e) {
                Log.e(ResUtil.class.getName(), e.getLocalizedMessage(), e);
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }

        return null;
    }

    public static String getString(Context ctx, int resId) {
        if (externalRes != null && idMapping != null) {
            int eid = idMapping.get(resId);

            if (eid != 0) {
                try {
                    return externalRes.getString(eid);
                } catch (NotFoundException e) {
                    Log.w(ResUtil.class.getName(), "ext string not found: " + e.getLocalizedMessage()); //$NON-NLS-1$
                }
            }
        }

        return ctx.getString(resId);
    }

    public static String getString(Context ctx, int resId, Object... formatArgs) {
        if (externalRes != null && idMapping != null) {
            int eid = idMapping.get(resId);

            if (eid != 0) {
                try {
                    return externalRes.getString(eid, formatArgs);
                } catch (NotFoundException e) {
                    Log.w(ResUtil.class.getName(), "ext string not found: " + e.getLocalizedMessage()); //$NON-NLS-1$
                }
            }
        }

        return ctx.getString(resId, formatArgs);
    }

    /**
     * ResourceListener
     */
    interface ResourceListener {

        void resourceChanged();
    }
}
