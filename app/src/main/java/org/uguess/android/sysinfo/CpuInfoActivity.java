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

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.uguess.android.sysinfo.PropertiesViewer.GLPropertiesViewerStub;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * CpuInfoActivity
 */
public final class CpuInfoActivity extends PopActivity {

    boolean paused;

    private CpuState cpuObj = new CpuState();

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

        refresh();

        if (CpuState.getGpuVendor() == null && CpuState.getGpuRenderer() == null) {
            Intent it = new Intent(this, GLPropertiesViewerStub.class);
            it.putExtra(PropertiesViewer.EXTRA_NO_DETAILS, true);
            startActivityForResult(it, Constants.REQUEST_GPU_INFO);

            PropertiesViewer.overridePendingTransition(this, 0, 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.REQUEST_GPU_INFO && data != null) {
            Util.finishStub(this, data.getIntExtra(Constants.EXTRA_PID, 0));

            CpuState.setGpuInfo(data.getStringExtra(PropertiesViewer.EXTRA_GLVENDOR),
                    data.getStringExtra(PropertiesViewer.EXTRA_GLRENDERER));

            refresh();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        paused = false;

        if (cpuObj.needSecondRun()) {
            ListView contentView = (ListView) findViewById(R.id.content_list);

            contentView.postDelayed(new Runnable() {

                public void run() {
                    if (paused) {
                        return;
                    }

                    refresh();
                }
            }, 1000);
        }
    }

    @Override
    protected void onPause() {
        paused = true;

        super.onPause();
    }

    void refresh() {
        ArrayList<String[]> data = new ArrayList<String[]>();

        String model = CpuState.getProcessor();

        if (model != null) {
            data.add(new String[]{ResUtil.getString(this, R.string.model), model});
        }

        if (CpuState.getGpuVendor() != null || CpuState.getGpuRenderer() != null) {
            data.add(new String[]{ResUtil.getString(this, R.string.gpu),
                    ResUtil.getString(this, R.string.vendor) + ": " //$NON-NLS-1$
                            + CpuState.getGpuVendor() + '\n' + ResUtil.getString(this, R.string.renderer) + ": " //$NON-NLS-1$
                            + CpuState.getGpuRenderer()});
        }

        String[] stat = cpuObj.getMips();

        if (stat != null && stat.length > 0) {
            if (stat.length == 1) {
                if (stat[0] != null) {
                    data.add(new String[]{ResUtil.getString(this, R.string.cur_freq), stat[0]});
                }
            } else {
                StringBuilder freq = new StringBuilder();

                for (int i = 0; i < stat.length; i++) {
                    if (freq.length() > 0) {
                        freq.append('\n');
                    }

                    freq.append(i).append(": ") //$NON-NLS-1$
                            .append(stat[i] != null ? stat[i] : "zZ"); //$NON-NLS-1$
                }

                if (freq.length() > 0) {
                    data.add(new String[]{ResUtil.getString(this, R.string.cur_freq), freq.toString()});
                }
            }
        }

        int pc = Runtime.getRuntime().availableProcessors();

        String cpuMin = readFreq("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq"); //$NON-NLS-1$
        String cpuMax = readFreq("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"); //$NON-NLS-1$

        if (cpuMin != null && cpuMax != null) {
            if (pc == 1) {
                data.add(new String[]{ResUtil.getString(this, R.string.cpu_freq_range),
                        cpuMin + " - " + cpuMax //$NON-NLS-1$
                });
            } else {
                StringBuilder freq = new StringBuilder();

                freq.append("0: ") //$NON-NLS-1$
                        .append(cpuMin).append(" - ") //$NON-NLS-1$
                        .append(cpuMax);

                for (int i = 1; i < pc; i++) {
                    cpuMin = readFreq("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_min_freq"); //$NON-NLS-1$ //$NON-NLS-2$
                    cpuMax = readFreq("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq"); //$NON-NLS-1$ //$NON-NLS-2$

                    if (cpuMin != null && cpuMax != null) {
                        freq.append('\n').append(i).append(": ") //$NON-NLS-1$
                                .append(cpuMin).append(" - ") //$NON-NLS-1$
                                .append(cpuMax);
                    } else {
                        freq.append('\n').append(i).append(": ") //$NON-NLS-1$
                                .append("zZ"); //$NON-NLS-1$
                    }
                }

                data.add(new String[]{ResUtil.getString(this, R.string.cpu_freq_range), freq.toString()});
            }
        }

        String scaleMin = readFreq("/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq"); //$NON-NLS-1$
        String scaleMax = readFreq("/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq"); //$NON-NLS-1$

        if (scaleMin != null && scaleMax != null) {
            if (pc == 1) {
                data.add(new String[]{ResUtil.getString(this, R.string.scaling_range),
                        scaleMin + " - " + scaleMax //$NON-NLS-1$
                });
            } else {
                StringBuilder freq = new StringBuilder();

                freq.append("0: ") //$NON-NLS-1$
                        .append(scaleMin).append(" - ") //$NON-NLS-1$
                        .append(scaleMax);

                for (int i = 1; i < pc; i++) {
                    scaleMin = readFreq("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_min_freq"); //$NON-NLS-1$ //$NON-NLS-2$
                    scaleMax = readFreq("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_max_freq"); //$NON-NLS-1$ //$NON-NLS-2$

                    if (scaleMin != null && scaleMax != null) {
                        freq.append('\n').append(i).append(": ") //$NON-NLS-1$
                                .append(scaleMin).append(" - ") //$NON-NLS-1$
                                .append(scaleMax);
                    } else {
                        freq.append('\n').append(i).append(": ") //$NON-NLS-1$
                                .append("zZ"); //$NON-NLS-1$
                    }
                }

                data.add(new String[]{ResUtil.getString(this, R.string.scaling_range), freq.toString()});
            }
        }

        String governor =
                Util.readFileFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", //$NON-NLS-1$
                        32);

        if (governor != null) {
            if (pc == 1) {
                data.add(new String[]{ResUtil.getString(this, R.string.scaling_governor), governor});
            } else {
                data.add(new String[]{ResUtil.getString(this, R.string.scaling_governor),
                        readMultiCpuStat(governor, pc, "scaling_governor", 32) //$NON-NLS-1$
                });
            }
        }

        String scalingDriver =
                Util.readFileFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/scaling_driver", //$NON-NLS-1$
                        16);

        if (scalingDriver != null) {
            if (pc == 1) {
                data.add(new String[]{ResUtil.getString(this, R.string.scaling_driver), scalingDriver});
            } else {
                data.add(new String[]{ResUtil.getString(this, R.string.scaling_driver),
                        readMultiCpuStat(scalingDriver, pc, "scaling_driver", //$NON-NLS-1$
                                16)});
            }
        }

        String availableGovernors =
                Util.readFileFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors", //$NON-NLS-1$
                        64);

        if (availableGovernors != null) {
            data.add(new String[]{ResUtil.getString(this, R.string.scaling_aval_govs),
                    availableGovernors});
        }

        String availableFreqs =
                Util.readFileFirstLine(
                        "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies", //$NON-NLS-1$
                        128);

        if (availableFreqs != null) {
            String freqs = parseFrequencies(availableFreqs);

            if (freqs != null) {
                data.add(new String[]{ResUtil.getString(this, R.string.scaling_aval_freqs), freqs});
            }
        }

        File rawCpu = new File("/proc/cpuinfo"); //$NON-NLS-1$

        if (rawCpu.exists()) {
            try {
                StringBuffer sb = new StringBuffer();

                Util.readRawText(sb, new FileInputStream(rawCpu), 512);

                data.add(new String[]{ResUtil.getString(this, R.string.more_info), sb.toString()});
            } catch (Exception e) {
                Log.e(CpuInfoActivity.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        ListView contentView = (ListView) findViewById(R.id.content_list);

        ArrayAdapter<String[]> adapter = (ArrayAdapter<String[]>) contentView.getAdapter();

        adapter.setNotifyOnChange(false);

        adapter.clear();

        for (String[] d : data) {
            adapter.add(d);
        }

        adapter.notifyDataSetChanged();
    }

    private static String readFreq(String fname) {
        long freq = Util.readFileLong(fname, true);

        if (freq != -1) {
            return String.valueOf(freq / 1000) + "MHz"; //$NON-NLS-1$;
        }

        return null;
    }

    private static String parseFrequencies(String line) {
        try {
            StringBuilder sb = new StringBuilder();

            StringTokenizer st = new StringTokenizer(line);

            while (st.hasMoreTokens()) {
                String tk = st.nextToken();

                if (!TextUtils.isEmpty(tk)) {
                    long freq = Long.parseLong(tk.trim());

                    if (sb.length() > 0) {
                        sb.append("  "); //$NON-NLS-1$
                    }

                    sb.append(String.valueOf(freq / 1000)).append("MHz"); //$NON-NLS-1$
                }
            }

            if (sb.length() > 0) {
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(CpuInfoActivity.class.getName(), "Error parsing frequencies: " //$NON-NLS-1$
                    + line, e);
        }

        return null;
    }

    private static String readMultiCpuStat(String firstData, int pc, String statFile, int bufSize) {
        StringBuilder sb = new StringBuilder();

        sb.append("0: ") //$NON-NLS-1$
                .append(firstData);

        for (int i = 1; i < pc; i++) {
            firstData = Util.readFileFirstLine("/sys/devices/system/cpu/cpu" //$NON-NLS-1$
                    + i + "/cpufreq/" //$NON-NLS-1$
                    + statFile, bufSize);

            sb.append('\n').append(i).append(": ") //$NON-NLS-1$
                    .append(firstData != null ? firstData : "zZ"); //$NON-NLS-1$
        }

        return sb.toString();
    }
}
