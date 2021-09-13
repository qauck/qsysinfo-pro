/********************************************************************************
 * (C) Copyright 2000-2021, by Shawn Q.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 ********************************************************************************/

package org.uguess.android.sysinfo;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/**
 * SensorInfoActivity
 */
public final class SensorInfoActivity extends PopActivity {

    private Runnable task = new Runnable() {

        public void run() {
            List<Sensor> ss = null;

            SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

            if (sm != null) {
                ss = sm.getSensorList(Sensor.TYPE_ALL);
            }

            ListView contentView = (ListView) findViewById(R.id.content_list);

            ArrayAdapter<Sensor> adapter = (ArrayAdapter<Sensor>) contentView.getAdapter();

            adapter.setNotifyOnChange(false);

            adapter.clear();

            if (ss != null) {
                for (Sensor s : ss) {
                    adapter.add(s);
                }
            }

            adapter.notifyDataSetChanged();

            // contentView.postDelayed( this, 2000 );
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ListView contentView = (ListView) findViewById(R.id.content_list);

        if (Util.SDK_VER < 11) {
            registerForContextMenu(contentView);
        }

        ArrayAdapter<Sensor> adapter = new ArrayAdapter<Sensor>(this, R.layout.sensor_item) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v;

                if (convertView == null) {
                    v = getLayoutInflater().inflate(R.layout.sensor_item, contentView, false);
                } else {
                    v = convertView;
                }

                Sensor item = getItem(position);

                TextView t1 = (TextView) v.findViewById(R.id.txt_head);
                TextView t2 = (TextView) v.findViewById(R.id.txt_msg);

                t1.setText(TextUtils.isEmpty(item.getName()) ? ResUtil.getString(SensorInfoActivity.this,
                        R.string.unknown) : item.getName());
                t2.setText(ResUtil.getString(
                        SensorInfoActivity.this,
                        R.string.sensor_msg,
                        TextUtils.isEmpty(item.getVendor()) ? ResUtil.getString(SensorInfoActivity.this,
                                R.string.unknown) : item.getVendor(), item.getVersion(), item.getPower(), item
                                .getResolution(), item.getMaximumRange()));

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
