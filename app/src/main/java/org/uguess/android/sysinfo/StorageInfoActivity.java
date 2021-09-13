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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;

/**
 * StorageInfoActivity
 */
public final class StorageInfoActivity extends PopActivity {

    static final String EXTRA_STATE = "extra_state"; //$NON-NLS-1$
    static final String EXTRA_SD_PATH = "sd_path"; //$NON-NLS-1$
    static final String EXTRA_PRIMARY_SD = "primary_sd"; //$NON-NLS-1$
    static final String EXTRA_ACTION_LABEL = "action_label"; //$NON-NLS-1$
    static final String EXTRA_ACTION_CLASS = "action_class"; //$NON-NLS-1$

    private static final Method mtdIsExternalStorageEmulated = Util.getMethod(Environment.class,
            "isExternalStorageEmulated"); //$NON-NLS-1$
    private static final Method mtdIsExternalStorageRemovable = Util.getMethod(Environment.class,
            "isExternalStorageRemovable"); //$NON-NLS-1$

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String actionLabel = getIntent().getStringExtra(EXTRA_ACTION_LABEL);
        final String actionClass = getIntent().getStringExtra(EXTRA_ACTION_CLASS);

        final ListView contentView = (ListView) findViewById(R.id.content_list);

        if (Util.SDK_VER < 11) {
            registerForContextMenu(contentView);
        }

        ArrayAdapter<String[]> adapter = new ArrayAdapter<String[]>(this, R.layout.sensor_item) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v;

                final String[] item = getItem(position);

                if (actionClass != null && actionClass.equals(item[0])) {
                    v = getLayoutInflater().inflate(R.layout.pub_info, contentView, false);

                    Button btn = (Button) v.findViewById(R.id.btn_action);

                    if (actionLabel != null) {
                        btn.setText(actionLabel);
                    } else {
                        btn.setText(ResUtil.getString(StorageInfoActivity.this, R.string.more_info));
                    }

                    btn.setOnClickListener(new OnClickListener() {

                        public void onClick(View v) {
                            eventConsumed = true;

                            Intent it = Util.getSettingsIntent(getPackageManager(), item[0]);

                            if (it != null) {
                                Util.safeStartActivity(StorageInfoActivity.this, it, false);
                            }
                        }
                    });
                } else {
                    v = getLayoutInflater().inflate(R.layout.sensor_item, contentView, false);

                    TextView t1 = (TextView) v.findViewById(R.id.txt_head);
                    TextView t2 = (TextView) v.findViewById(R.id.txt_msg);

                    t1.setText(item[0]);
                    t2.setText(item[1]);

                    if (Util.SDK_VER >= 11) {
                        Util.setTextIsSelectable(t1, true);
                        Util.setTextIsSelectable(t2, true);
                    }
                }

                return v;
            }
        };

        contentView.setAdapter(adapter);

        refresh();
    }

    private void refresh() {
        long total = 0;
        long free = 0;
        long used = 0;

        long[] state = getIntent().getLongArrayExtra(EXTRA_STATE);
        if (state != null && state.length > 1) {
            total = state[0];
            free = state[1];
            used = total - free;
        }

        ArrayList<String[]> data = new ArrayList<String[]>();

        data.add(new String[]{ResUtil.getString(this, R.string.capacity),
                formatSize(this, total, null)});

        String percent = total == 0 ? "0" : String.format(Locale.ENGLISH, "%.2f", used //$NON-NLS-1$ //$NON-NLS-2$
                * 100f / total);
        data.add(new String[]{ResUtil.getString(this, R.string.used), formatSize(this, used, percent)});

        percent = total == 0 ? "0" //$NON-NLS-1$
                : String.format(Locale.ENGLISH, "%.2f", free * 100f / total); //$NON-NLS-1$
        data.add(new String[]{ResUtil.getString(this, R.string.free), formatSize(this, free, percent)});

        String sdPath = getIntent().getStringExtra(EXTRA_SD_PATH);

        if (sdPath != null) {
            String sdInfo = probeSDInfo(sdPath);

            if (sdInfo != null) {
                data.add(new String[]{ResUtil.getString(this, R.string.sd_card_info), sdInfo});
            }
        }

        if (getIntent().getBooleanExtra(EXTRA_PRIMARY_SD, false)) {
            if (mtdIsExternalStorageEmulated != null) {
                try {
                    Object obj = mtdIsExternalStorageEmulated.invoke(null);
                    if (obj instanceof Boolean) {
                        data.add(new String[]{ResUtil.getString(this, R.string.emulated),
                                ResUtil.getString(this, ((Boolean) obj) ? R.string.yes : R.string.no)});
                    }
                } catch (Exception e) {
                    Log.e(StorageInfoActivity.class.getName(), e.getLocalizedMessage(), e);
                }
            }

            if (mtdIsExternalStorageRemovable != null) {
                try {
                    Object obj = mtdIsExternalStorageRemovable.invoke(null);
                    if (obj instanceof Boolean) {
                        data.add(new String[]{ResUtil.getString(this, R.string.removable),
                                ResUtil.getString(this, ((Boolean) obj) ? R.string.yes : R.string.no)});
                    }
                } catch (Exception e) {
                    Log.e(StorageInfoActivity.class.getName(), e.getLocalizedMessage(), e);
                }
            }
        }

        String actionClass = getIntent().getStringExtra(EXTRA_ACTION_CLASS);

        if (actionClass != null && Util.getSettingsIntent(getPackageManager(), actionClass) != null) {
            data.add(new String[]{actionClass, null});
        }

        ListView contentView = (ListView) findViewById(R.id.content_list);

        @SuppressWarnings("unchecked")
        ArrayAdapter<String[]> adapter = (ArrayAdapter<String[]>) contentView.getAdapter();

        adapter.setNotifyOnChange(false);

        adapter.clear();

        for (String[] d : data) {
            adapter.add(d);
        }

        adapter.notifyDataSetChanged();
    }

    private String probeSDInfo(String sdPath) {
        File sdFile = new File(sdPath);

        if (sdFile.exists()) {
            try {
                sdPath = sdFile.getCanonicalPath();
            } catch (IOException e) {
                Log.e(StorageInfoActivity.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        BufferedReader reader = null;
        String mountSource = null;

        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/mounts")), //$NON-NLS-1$
                    1024);

            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                int idx = line.indexOf(' ');

                if (idx != -1) {
                    String sourcePath = line.substring(0, idx).trim();

                    String subLine = line.substring(idx).trim();

                    idx = subLine.indexOf(' ');

                    if (idx != -1) {
                        String mountPath = subLine.substring(0, idx).trim();

                        if (sdPath.equals(mountPath)) {
                            mountSource = sourcePath;

                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(StorageInfoActivity.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                    reader = null;
                } catch (IOException ie) {
                    Log.e(StorageInfoActivity.class.getName(), ie.getLocalizedMessage(), ie);
                }
            }
        }

        if (mountSource != null) {
            String mountDev = null;

            int idx = mountSource.lastIndexOf('/');

            if (idx != -1 && idx < mountSource.length() - 1) {
                String dev = mountSource.substring(idx + 1).trim();

                mountSource = mountSource.substring(0, idx).trim();

                // currently we only support vold mounted sd card
                if (mountSource.endsWith("/vold")) //$NON-NLS-1$
                {
                    mountDev = dev;
                }
            }

            if (mountDev != null) {
                File devFile = new File("/sys/dev/block/" + mountDev); //$NON-NLS-1$

                if (devFile.exists() && devFile.isDirectory()) {
                    String sdInfo = querySDInfo(devFile);

                    if (sdInfo == null) {
                        // possibly multiple partitions on sd, check the parent
                        // folder

                        try {
                            devFile = devFile.getCanonicalFile().getParentFile();

                            if (devFile != null && devFile.exists() && devFile.isDirectory()) {
                                sdInfo = querySDInfo(devFile);
                            }
                        } catch (IOException e) {
                            Log.e(StorageInfoActivity.class.getName(), e.getLocalizedMessage(), e);
                        }
                    }

                    return sdInfo;
                }
            }
        }

        return null;
    }

    private String querySDInfo(File f) {
        File dev = new File(f, "device"); //$NON-NLS-1$

        if (dev.exists() && dev.isDirectory() && dev.canRead()) {
            String type = Util.readFileFirstLine(new File(dev, "type"), 8); //$NON-NLS-1$

            if ("sd".equalsIgnoreCase(type)) //$NON-NLS-1$
            {
                StringBuilder sb = new StringBuilder();

                sb.append(ResUtil.getString(this, R.string.name)).append(": ") //$NON-NLS-1$
                        .append(Util.readFileFirstLine(new File(dev, "name"), //$NON-NLS-1$
                                16));
                sb.append("\n") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.serial)).append(": ") //$NON-NLS-1$
                        .append(Util.readFileFirstLine(new File(dev, "serial"), 16)); //$NON-NLS-1$
                sb.append("\n") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.date)).append(": ") //$NON-NLS-1$
                        .append(Util.readFileFirstLine(new File(dev, "date"), //$NON-NLS-1$
                                16));
                sb.append("\n") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.oem_id)).append(": ") //$NON-NLS-1$
                        .append(Util.readFileFirstLine(new File(dev, "oemid"), //$NON-NLS-1$
                                16));
                sb.append("\n") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.manf_id)).append(": ") //$NON-NLS-1$
                        .append(Util.readFileFirstLine(new File(dev, "manfid"), 16)); //$NON-NLS-1$
                sb.append("\n") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.hw_rev)).append(": ") //$NON-NLS-1$
                        .append(Util.readFileFirstLine(new File(dev, "hwrev"), //$NON-NLS-1$
                                16));
                sb.append("\n") //$NON-NLS-1$
                        .append(ResUtil.getString(this, R.string.fw_rev)).append(": ") //$NON-NLS-1$
                        .append(Util.readFileFirstLine(new File(dev, "fwrev"), //$NON-NLS-1$
                                16));
                sb.append("\nCID: ") //$NON-NLS-1$
                        .append(Util.readFileFirstLine(new File(dev, "cid"), //$NON-NLS-1$
                                48));
                sb.append("\nCSD: ") //$NON-NLS-1$
                        .append(Util.readFileFirstLine(new File(dev, "csd"), //$NON-NLS-1$
                                48));
                sb.append("\nSCR: ") //$NON-NLS-1$
                        .append(Util.readFileFirstLine(new File(dev, "scr"), //$NON-NLS-1$
                                32));

                return sb.toString();
            }
        }

        return null;
    }

    private static String formatSize(Context ctx, long sz, String percent) {
        StringBuilder sb = new StringBuilder();

        if (sz >= 1024) {
            sb.append(String.format("%,d", sz)) //$NON-NLS-1$
                    .append(ResUtil.getString(ctx, R.string.bytes)).append("  "); //$NON-NLS-1$
        }

        sb.append(Util.safeFormatFileSize(ctx, sz));

        if (percent != null) {
            sb.append("  ").append(percent).append('%'); //$NON-NLS-1$
        }

        return sb.toString();
    }
}
