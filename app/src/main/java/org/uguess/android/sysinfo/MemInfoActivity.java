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
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * MemInfoActivity
 */
public final class MemInfoActivity extends PopActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ListView contentView = (ListView) findViewById(R.id.content_list);

        if (Util.SDK_VER < 11) {
            registerForContextMenu(contentView);
        }

        ArrayAdapter<String[]> adapter = new ArrayAdapter<String[]>(this, R.layout.sensor_item) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v;

                String[] item = getItem(position);

//                if (item.length == 3) {
                v = getLayoutInflater().inflate(R.layout.sensor_item, contentView, false);
//                } else {
//                v = getLayoutInflater().inflate(R.layout.battery_item, contentView, false);
//                }

                TextView t1 = (TextView) v.findViewById(R.id.txt_head);
                TextView t2 = (TextView) v.findViewById(R.id.txt_msg);

                t1.setText(item[0]);
                t2.setText(item[1]);

                if (Util.SDK_VER >= 11) {
                    Util.setTextIsSelectable(t1, true);
                    Util.setTextIsSelectable(t2, true);
                }

                return v;
            }
        };

        contentView.setAdapter(adapter);

        refresh();
    }

    private void refresh() {
        ArrayList<String[]> data = collectMemInfo(this);

        ListView contentView = (ListView) findViewById(R.id.content_list);

        ArrayAdapter<String[]> adapter = (ArrayAdapter<String[]>) contentView.getAdapter();

        adapter.setNotifyOnChange(false);

        adapter.clear();

        for (String[] d : data) {
            adapter.add(d);
        }

        adapter.notifyDataSetChanged();
    }

    private static ArrayList<String[]> collectMemInfo(Context ctx) {
        ArrayList<String[]> data = new ArrayList<String[]>();

        BufferedReader reader = null;

        try {
            reader =
                    new BufferedReader(new InputStreamReader(new FileInputStream(new File("/proc/meminfo"))), //$NON-NLS-1$
                            1024);

            String line;
            String totalMsg = null;
            String freeMsg = null;
            String buffersMsg = null;
            String cachedMsg = null;
            String swapTotalMsg = null;
            String swapFreeMsg = null;

            while ((line = reader.readLine()) != null) {
                if (totalMsg == null && line.startsWith("MemTotal")) //$NON-NLS-1$
                {
                    totalMsg = line;
                } else if (freeMsg == null && line.startsWith("MemFree")) //$NON-NLS-1$
                {
                    freeMsg = line;
                } else if (buffersMsg == null && line.startsWith("Buffers")) //$NON-NLS-1$
                {
                    buffersMsg = line;
                } else if (cachedMsg == null && line.startsWith("Cached")) //$NON-NLS-1$
                {
                    cachedMsg = line;
                } else if (swapTotalMsg == null && line.startsWith("SwapTotal")) //$NON-NLS-1$
                {
                    swapTotalMsg = line;
                } else if (swapFreeMsg == null && line.startsWith("SwapFree")) //$NON-NLS-1$
                {
                    swapFreeMsg = line;
                }

                if (totalMsg != null && freeMsg != null && buffersMsg != null && cachedMsg != null
                        && swapTotalMsg != null && swapFreeMsg != null) {
                    break;
                }
            }

            long total = Util.extractMemCount(totalMsg);
            long free = Util.extractMemCount(freeMsg);
            long buffers = Util.extractMemCount(buffersMsg);
            long cached = Util.extractMemCount(cachedMsg);
            long swapTotal = Util.extractMemCount(swapTotalMsg);
            long swapFree = Util.extractMemCount(swapFreeMsg);

            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            MemoryInfo mi = new MemoryInfo();
            am.getMemoryInfo(mi);

            data.add(new String[]{ResUtil.getString(ctx, R.string.total), formatSize(total, ctx)});
            data.add(new String[]{ResUtil.getString(ctx, R.string.free) + ":", //$NON-NLS-1$
                    formatSize(mi.availMem, ctx)});
            data.add(new String[]{ResUtil.getString(ctx, R.string.idle), formatSize(free, ctx)});
            data.add(new String[]{ResUtil.getString(ctx, R.string.threshold),
                    formatSize(mi.threshold, ctx)});
            data.add(new String[]{ResUtil.getString(ctx, R.string.buffers), formatSize(buffers, ctx)});
            data.add(new String[]{ResUtil.getString(ctx, R.string.cached), formatSize(cached, ctx)});
            data.add(new String[]{ResUtil.getString(ctx, R.string.swap_total),
                    formatSize(swapTotal, ctx)});
            data.add(new String[]{ResUtil.getString(ctx, R.string.swap_free), formatSize(swapFree, ctx)});

            // detect low memory killer settings
            File lmk = new File("/sys/module/lowmemorykiller/parameters/minfree"); //$NON-NLS-1$

            if (lmk.exists() && lmk.canRead()) {
                String params =
                        Util.readFileFirstLine("/sys/module/lowmemorykiller/parameters/minfree", 256); //$NON-NLS-1$

                if (params != null) {
                    StringTokenizer st = new StringTokenizer(params, ","); //$NON-NLS-1$

                    final int paramCount = 6;

                    int count = 0;
                    int[] szs = new int[paramCount];

                    while (st.hasMoreTokens()) {
                        String t = st.nextToken();

                        try {
                            szs[count] = Integer.parseInt(t);
                        } catch (Exception e) {
                            Log.e(MemInfoActivity.class.getName(), "Parsing low memory killer paramters failed", //$NON-NLS-1$
                                    e);

                            break;
                        }

                        count++;

                        if (count >= paramCount) {
                            break;
                        }
                    }

                    if (count >= paramCount) {
                        StringBuilder sb = new StringBuilder();

                        for (int i = 0; i < paramCount; i++) {
                            if (i > 0) {
                                sb.append('\n');
                            }

                            switch (i) {
                                case 0:
                                    sb.append(ResUtil.getString(ctx, R.string.forground_app)).append(": "); //$NON-NLS-1$
                                    break;
                                case 1:
                                    sb.append(ResUtil.getString(ctx, R.string.visible_app)).append(": "); //$NON-NLS-1$
                                    break;
                                case 2:
                                    sb.append(ResUtil.getString(ctx, R.string.secondary_server)).append(": "); //$NON-NLS-1$
                                    break;
                                case 3:
                                    sb.append(ResUtil.getString(ctx, R.string.hidden_app)).append(": "); //$NON-NLS-1$
                                    break;
                                case 4:
                                    sb.append(ResUtil.getString(ctx, R.string.content_provider)).append(": "); //$NON-NLS-1$
                                    break;
                                case 5:
                                    sb.append(ResUtil.getString(ctx, R.string.empty_app)).append(": "); //$NON-NLS-1$
                                    break;
                            }

                            sb.append(Util.safeFormatFileSize(ctx, szs[i] * 4 * 1024));
                        }

                        data.add(new String[]{ResUtil.getString(ctx, R.string.lmk), sb.toString(), "lmk" //$NON-NLS-1$
                        });
                    }
                }
            }

            File rawMem = new File("/proc/meminfo"); //$NON-NLS-1$

            if (rawMem.exists()) {
                StringBuffer sb = new StringBuffer();

                Util.readRawText(sb, new FileInputStream(rawMem), 512);

                data.add(new String[]{ResUtil.getString(ctx, R.string.more_info), sb.toString(), "raw" //$NON-NLS-1$
                });
            }
        } catch (Exception e) {
            Log.e(MemInfoActivity.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ie) {
                    Log.e(MemInfoActivity.class.getName(), ie.getLocalizedMessage(), ie);
                }
            }
        }

        return data;
    }

    private static String formatSize(long size, Context ctx) {
        if (size == -1) {
            return ResUtil.getString(ctx, R.string.info_not_available);
        } else {
            return Util.safeFormatFileSize(ctx, size);
        }
    }
}
