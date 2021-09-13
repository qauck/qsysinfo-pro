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

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * InputInfoActivity
 */
public final class InputInfoActivity extends PopActivity {

    static Method mtdGetDeviceIds = null;
    static Method mtdGetDevice = null;

    static {
        try {
            Class<?> clz = Class.forName("android.view.InputDevice"); //$NON-NLS-1$

            mtdGetDeviceIds = clz.getDeclaredMethod("getDeviceIds"); //$NON-NLS-1$
            mtdGetDevice = clz.getDeclaredMethod("getDevice", int.class); //$NON-NLS-1$
        } catch (Exception e) {
            Log.d(InputInfoActivity.class.getName(),
                    "Input Device is not supported by current SDK version.");
        }
    }

    static boolean isAvailable() {
        return mtdGetDeviceIds != null && mtdGetDevice != null;
    }

    static int getInputDeviceCount() {
        if (mtdGetDeviceIds != null) {
            try {
                int[] ids = (int[]) mtdGetDeviceIds.invoke(null);

                if (ids != null) {
                    return ids.length;
                }
            } catch (Exception e) {
                Log.e(InputInfoActivity.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        return 0;
    }

    private Runnable task = new Runnable() {

        public void run() {
            List<String[]> ss = new ArrayList<String[]>();

            try {
                int[] ids = (int[]) mtdGetDeviceIds.invoke(null);

                if (ids != null) {
                    for (int id : ids) {
                        Object obj = mtdGetDevice.invoke(null, id);

                        if (obj != null) {
                            ss.add(formatDetails(obj.toString()));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(InputInfoActivity.class.getName(), e.getLocalizedMessage(), e);
            }

            ListView contentView = (ListView) findViewById(R.id.content_list);

            ArrayAdapter<String[]> adapter = (ArrayAdapter<String[]>) contentView.getAdapter();

            adapter.setNotifyOnChange(false);

            adapter.clear();

            for (String[] s : ss) {
                adapter.add(s);
            }

            adapter.notifyDataSetChanged();
        }
    };

    static String[] formatDetails(String details) {
        if (details == null) {
            return null;
        }

        String[] seg = details.split("\n"); //$NON-NLS-1$

        String title = null;
        StringBuilder sb = null;

        if (seg.length > 0) {
            title = seg[0].trim();
        }

        if (seg.length > 1) {
            sb = new StringBuilder();

            for (int i = 1; i < seg.length; i++) {
                if (i > 1) {
                    sb.append('\n');
                }
                sb.append(seg[i].trim());
            }
        }

        return new String[]{title, sb == null ? null : sb.toString()};
    }

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

                if (convertView == null) {
                    v = getLayoutInflater().inflate(R.layout.sensor_item, contentView, false);
                } else {
                    v = convertView;
                }

                String[] item = getItem(position);

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
    }

    @Override
    protected void onResume() {
        super.onResume();

        ListView contentView = (ListView) findViewById(R.id.content_list);

        contentView.post(task);
    }

    @Override
    protected void onPause() {
        ListView contentView = (ListView) findViewById(R.id.content_list);

        contentView.removeCallbacks(task);

        super.onPause();
    }
}
