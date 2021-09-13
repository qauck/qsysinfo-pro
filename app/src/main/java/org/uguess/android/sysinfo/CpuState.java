/********************************************************************************
 * (C) Copyright 2000-2021, by Shawn Q.
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 ********************************************************************************/

package org.uguess.android.sysinfo;

import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * CpuState
 */
final class CpuState {

    static final String F_SCALE_FREQ = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";
    static final String F_CPU_INFO = "/proc/cpuinfo";

    private static final String[] EMPTY_ARRAY = new String[0];

    private static volatile String cpuModelName;

    static volatile String gpuVendor;
    static volatile String gpuRenderer;

    private List<Map<Long, Long>> mipTable;
    private String[] bogoArray;

    private boolean scaleAvailable = true;
    private boolean avgAvailable = true;

    String[] getMips() {
        String[] mips = null;

        if (scaleAvailable) {
            mips = getScaleMIPS();

            if (mips == null) {
                Log.d(CpuState.class.getName(), "Scaling Mips not available");

                scaleAvailable = false;
            }
        }

        if (mips == null) {
            if (avgAvailable) {
                if (mipTable == null) {
                    mipTable = new ArrayList<Map<Long, Long>>();
                }

                mips = getAverageMIPS(mipTable);

                if (mips == null) {
                    Log.d(CpuState.class.getName(), "Avg Mips not available");

                    avgAvailable = false;
                    mipTable = null;
                }
            }

            if (mips == null) {
                String bogo = getBogoMIPS();

                if (bogo != null) {
                    if (bogoArray == null) {
                        bogoArray = new String[1];
                    }

                    bogoArray[0] = bogo;

                    mips = bogoArray;
                }
            }
        }

        return mips;
    }

    static synchronized boolean needGpuInfo() {
        if (gpuRenderer != null || gpuVendor != null) {
            return false;
        }

        String abi = PropertiesViewer.CPU_ABI;

        return abi != null && (abi.contains("armeabi") || abi.contains("arm64"));
    }

    static String getGpuVendor() {
        return gpuVendor;
    }

    static String getGpuRenderer() {
        return gpuRenderer;
    }

    static synchronized void setGpuInfo(String vendor, String renderer) {
        gpuVendor = vendor;
        gpuRenderer = renderer;

        cpuModelName = getModel().getModelName();
    }

    static synchronized String getProcessor() {
        if (cpuModelName == null) {
            cpuModelName = getModel().getModelName();
        }

        return cpuModelName;
    }

    boolean needSecondRun() {
        return mipTable != null;
    }

    void reset() {
        if (mipTable != null) {
            for (Map<Long, Long> mp : mipTable) {
                if (mp != null) {
                    mp.clear();
                }
            }
        }
    }

    /**
     * @return [mips...]
     */
    // private static String[] getFrequency( )
    // {
    // String[] mips = getScaleMIPS( );
    //
    // if ( mips == null )
    // {
    // Log.d( SysInfoManager.class.getName( ),
    //					"No scaling found, using BogoMips instead" );
    //
    // String bogo = getBogoMIPS( );
    //
    // if ( bogo != null )
    // {
    // mips = new String[]{
    // bogo
    // };
    // }
    // }
    //
    // return mips;
    // }
    static int getTotalCores() {
        try {
            File[] fs = new File("/sys/devices/system/cpu/").listFiles(new FileFilter() {

                public boolean accept(File paramFile) {
                    return Pattern.matches("cpu[0-9]", paramFile.getName());
                }
            });

            if (fs != null) {
                return fs.length;
            }
        } catch (Exception e) {
            Log.e(CpuState.class.getName(), e.getLocalizedMessage(), e);
        }

        return Runtime.getRuntime().availableProcessors();
    }

    private static String[] getAverageMIPS(List<Map<Long, Long>> mipTable) {
        String[] result = null;

        try {
            int pc = Runtime.getRuntime().availableProcessors();

            for (int i = 0; i < pc; i++) {
                File f = new File("/sys/devices/system/cpu/cpu"
                        + i + "/cpufreq/stats/time_in_state");
                ;

                if (!f.exists() || !f.isFile() || !f.canRead()) {
                    continue;
                }

                if (result == null) {
                    result = new String[pc];
                }

                Map<Long, Long> freqMap = null;

                if (mipTable.size() <= i) {
                    freqMap = new HashMap<Long, Long>();
                    mipTable.add(freqMap);
                } else {
                    freqMap = mipTable.get(i);
                }

                BufferedReader reader = null;

                try {
                    reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)), 512);

                    String line;

                    long accumLoad = 0;
                    long accumTime = 0;

                    while ((line = reader.readLine()) != null) {
                        line = line.trim();

                        int idx = line.indexOf(' ');

                        if (idx != -1) {
                            try {
                                long freq = Long.parseLong(line.substring(0, idx).trim());
                                long time = Long.parseLong(line.substring(idx).trim());

                                Long lastTime = freqMap.get(freq);

                                freqMap.put(freq, time);

                                if (lastTime != null) {
                                    long deltaTime = time - lastTime.longValue();

                                    accumTime += deltaTime;
                                    accumLoad += freq * deltaTime;
                                }
                            } catch (Exception e) {
                                Log.e(CpuState.class.getName(), e.getLocalizedMessage(), e);
                            }
                        }
                    }

                    if (accumTime != 0) {
                        result[i] = getMipsLabel(accumLoad / accumTime / 1000);
                    }
                } catch (Exception e) {
                    Log.e(CpuState.class.getName(), e.getLocalizedMessage(), e);
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException ie) {
                            Log.e(CpuState.class.getName(), ie.getLocalizedMessage(), ie);
                        }
                    }
                }
            }

            if (result != null) {
                // if all mips are null, reset the array to null
                boolean emptyExtra = true;

                for (String s : result) {
                    if (s != null) {
                        emptyExtra = false;
                        break;
                    }
                }

                if (emptyExtra) {
                    result = EMPTY_ARRAY;
                }
            }
        } catch (Exception e) {
            Log.e(CpuState.class.getName(), e.getLocalizedMessage(), e);
        }

        return result;
    }

    private static String[] getScaleMIPS() {
        try {
            int pc = Runtime.getRuntime().availableProcessors();

            String[] allMips = new String[pc];

            for (int i = 0; i < pc; i++) {
                try {
                    long freq = Util.readFileLong("/sys/devices/system/cpu/cpu"
                                    + i + "/cpufreq/scaling_cur_freq",
                            true);

                    if (freq != -1) {
                        allMips[i] = getMipsLabel(freq / 1000);
                    }
                } catch (Exception e) {
                    Log.e(CpuState.class.getName(), e.getLocalizedMessage(), e);
                }
            }

            // if all mips are null, reset the array to null
            boolean emptyExtra = true;

            for (String s : allMips) {
                if (s != null) {
                    emptyExtra = false;
                    break;
                }
            }

            if (emptyExtra) {
                allMips = null;
            }

            return allMips;
        } catch (Exception e) {
            Log.e(CpuState.class.getName(), e.getLocalizedMessage(), e);
        }

        return null;
    }

    private static String getBogoMIPS() {
        String mips = null;
        BufferedReader reader = null;

        try {
            reader =
                    new BufferedReader(new InputStreamReader(new FileInputStream(new File(F_CPU_INFO))), 1024);

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("BogoMIPS")) {
                    mips = line;

                    int idx = mips.indexOf(':');

                    if (idx != -1) {
                        mips = mips.substring(idx + 1).trim();
                    }

                    mips += "MHz";
                }

                if (mips != null) {
                    break;
                }
            }

            if (mips == null) {
                Log.w(CpuState.class.getName(), "Cannot find BogoMIPS");
            }
        } catch (Exception e) {
            Log.e(CpuState.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ie) {
                    Log.e(CpuState.class.getName(), ie.getLocalizedMessage(), ie);
                }
            }
        }

        return mips;
    }

    private static String getMipsLabel(long mhz) {
        if (mhz < 1000) {
            return String.valueOf(mhz) + "MHz";
        } else {
            long ghz = mhz / 1000;
            long rmd = Math.round((mhz % 1000) * 1d / 100);

            // e.g. 1950mhz => 2ghz, 1910mhz => 1.9ghz
            if (rmd > 9) {
                ghz++;
                rmd = 0;
            }

            if (rmd > 0) {
                return String.valueOf(ghz) + '.' + String.valueOf(rmd) + "GHz";
            } else {
                return String.valueOf(ghz) + "GHz";
            }
        }
    }

    static String getNameWithCores(String name, int cores) {
        if (cores > 1) {
            if (cores == 2) {
                name += " Dual-Core";
            } else if (cores == 4) {
                name += " Quad-Core";
            } else if (cores == 8) {
                name += " Octa-Core";
            } else {
                name += " " + cores + "-Core";
            }
        }

        return name;
    }

    private static CpuModel getModel() {
        CpuModel model = new CpuModel();
        model.cores = getTotalCores();

        String line;
        BufferedReader reader = null;

        try {
            reader =
                    new BufferedReader(new InputStreamReader(new FileInputStream(new File(F_CPU_INFO))), 1024);

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Processor")) {
                    int idx = line.indexOf(':');

                    if (idx != -1) {
                        String name = line.substring(idx + 1).trim();

                        model.baseModel = getNameWithCores(name, model.cores);
                    } else {
                        Log.e(CpuState.class.getName(), "Unexpected processor format: " + line);
                    }
                } else if (line.startsWith("model name")
                        || line.startsWith("cpu model")) {
                    int idx = line.indexOf(':');
                    if (idx != -1) {
                        model.baseModel = line.substring(idx + 1).trim();
                    } else {
                        Log.e(CpuState.class.getName(), "Unexpected processor format: " + line);
                    }
                } else if (line.startsWith("Hardware")) {
                    int idx = line.indexOf(':');
                    if (idx != -1) {
                        model.hardware = line.substring(idx + 1).trim().toLowerCase(Locale.ENGLISH);
                    } else {
                        Log.e(CpuState.class.getName(), "Unexpected hardware format: " + line);
                    }
                } else if (line.startsWith("CPU implementer")) {
                    int idx = line.indexOf(':');
                    if (idx != -1) {
                        try {
                            model.implementer = Integer.decode(line.substring(idx + 1).trim());
                        } catch (Exception e) {
                            Log.e(CpuState.class.getName(), "Wrong CPU implementer format: " + line);
                        }
                    } else {
                        Log.e(CpuState.class.getName(), "Unexpected CPU implementer format: " + line);
                    }
                } else if (line.startsWith("CPU variant")) {
                    int idx = line.indexOf(':');
                    if (idx != -1) {
                        try {
                            model.variant = Integer.decode(line.substring(idx + 1).trim());
                        } catch (Exception e) {
                            Log.e(CpuState.class.getName(), "Wrong CPU variant format: " + line);
                        }
                    } else {
                        Log.e(CpuState.class.getName(), "Unexpected CPU variant format: " + line);
                    }
                } else if (line.startsWith("CPU part")) {
                    int idx = line.indexOf(':');
                    if (idx != -1) {
                        try {
                            model.part = Integer.decode(line.substring(idx + 1).trim());
                        } catch (Exception e) {
                            Log.e(CpuState.class.getName(), "Wrong CPU part format: " + line);
                        }
                    } else {
                        Log.e(CpuState.class.getName(), "Unexpected CPU part format: " + line);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(CpuState.class.getName(), e.getLocalizedMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ie) {
                    Log.e(CpuState.class.getName(), ie.getLocalizedMessage(), ie);
                }
            }
        }

        return model;
    }

    /**
     * CpuModel
     */
    private static final class CpuModel {

        String baseModel;
        String hardware;

        String devHardware;
        String board;
        String model;

        int cores;
        int implementer;
        int variant;
        int part;

        CpuModel() {
            devHardware = PropertiesViewer.HARDWARE;
            board = Build.BOARD.toLowerCase(Locale.ENGLISH);
            model = Build.MODEL.toLowerCase(Locale.ENGLISH);
        }

        String getModelName() {
            String abi = PropertiesViewer.CPU_ABI;

            if (abi != null) {
                if (abi.contains("armeabi") || abi.contains("arm64")) {
                    return getArmModel();
                } else if (abi.contains("x86")) {
                    return getX86Model();
                } else if (abi.contains("mips")) {
                    return getMipsModel();
                }
            }

            if (baseModel != null && baseModel.startsWith("ARM")) {
                return getArmModel();
            }

            return baseModel;
        }

        private String getArmModel() {
            String name = null;

            switch (implementer) {
                case 0x41: //65
                    name = "ARM 0x" + Integer.toHexString(part);

                    switch (part) {
                        case 0x920: //2336
                            name = refineArmModel("ARM", "ARM920T");
                            break;
                        case 0x926: //2342
                            name = refineArmModel("ARM", "ARM926EJ");
                            break;
                        case 0x946: //2374
                            name = refineArmModel("ARM", "ARM946");
                            break;
                        case 0x966: //2406
                            name = refineArmModel("ARM", "ARM966");
                            break;
                        case 0xb02: //2818
                            name = refineArmModel("ARM", "ARM11 MPCore");
                            break;
                        case 0xb36: //2870
                            name = refineArmModel("ARM", "ARM1136");
                            break;
                        case 0xb56: //2902
                            name = refineArmModel("ARM", "ARM1156");
                            break;
                        case 0xb76: //2934
                            name = refineArmModel("ARM", "ARM1176");
                            break;
                        case 0xc05: //3077
                            name = refineArmModel("ARM", "Cortex-A5");
                            break;
                        case 0xc07: //3079
                            name = refineArmModel("ARM", "Cortex-A7");
                            break;
                        case 0xc08: //3080
                            name = refineArmModel("ARM", "Cortex-A8");
                            break;
                        case 0xc09: //3081
                            name = refineArmModel("ARM", "Cortex-A9");
                            break;
                        case 0xc0c: //3084
                            name = refineArmModel("ARM", "Cortex-A12");
                            break;
                        case 0xc0d: //3085
                        case 0xc0e: //3086
                            name = refineArmModel("ARM", "Cortex-A17");
                            break;
                        case 0xc0f: //3087
                            name = refineArmModel("ARM", "Cortex-A15");
                            break;
                        case 0xc14: //3092
                            name = refineArmModel("ARM", "Cortex-R4");
                            break;
                        case 0xc15: //3093
                            name = refineArmModel("ARM", "Cortex-R5");
                            break;
                        case 0xc20: //3104
                            name = refineArmModel("ARM", "Cortex-M0");
                            break;
                        case 0xc21: //3105
                            name = refineArmModel("ARM", "Cortex-M1");
                            break;
                        case 0xc23: //3107
                            name = refineArmModel("ARM", "Cortex-M3");
                            break;
                        case 0xc24: //3108
                            name = refineArmModel("ARM", "Cortex-M4");
                            break;
                        case 0xd03: //3331
                            name = refineArmModel("ARM", "Cortex-A53");
                            break;
                        case 0xd07: //3335
                            name = refineArmModel("ARM", "Cortex-A57");
                            break;
                        case 0xd08: //3336
                            name = refineArmModel("ARM", "Cortex-A72");
                            break;
                        case 0xd09: //3337
                            name = refineArmModel("ARM", "Cortex-A73");
                            break;
                    }

                    break;
                case 0x44: //68
                    name = "DEC 0x" + Integer.toHexString(part);
                    break;
                case 0x4d: //77
                    name = "Motorola 0x" + Integer.toHexString(part);
                    break;
                case 0x51: //81
                    switch (part) {
                        case 0xf: //15
                            name = "Qualcomm Snapdragon S1 (Scorpion)";
                            // fall through
                        case 0x2d: //45
                            if (name == null) {
                                name = "Qualcomm Snapdragon (Scorpion)";
                            }

                            if (gpuRenderer != null) {
                                if (gpuRenderer.contains("205")) {
                                    name = "Qualcomm Snapdragon S2 (Scorpion)";
                                } else if (gpuRenderer.contains("220")) {
                                    name = "Qualcomm Snapdragon S3 (Scorpion)";
                                } else if (gpuRenderer.contains("225")) {
                                    name = "Qualcomm Snapdragon S4 (Krait)";
                                }
                            }
                            break;
                        case 0x4d: //77
                            name = "Qualcomm Snapdragon S4 (Krait)";
                            break;
                        case 0x6f: //111
                            name = "Qualcomm Snapdragon (Krait)";

                            if (gpuRenderer != null) {
                                if (gpuRenderer.contains("430")) {
                                    name = "Qualcomm Snapdragon 810";
                                } else if (gpuRenderer.contains("420")) {
                                    name = "Qualcomm Snapdragon 805 (Krait 450)";
                                } else if (gpuRenderer.contains("418")) {
                                    name = "Qualcomm Snapdragon 808";
                                } else if (gpuRenderer.contains("405")) {
                                    if (cores == 8) {
                                        name = "Qualcomm Snapdragon 615";
                                    } else {
                                        name = "Qualcomm Snapdragon 610";
                                    }
                                } else if (gpuRenderer.contains("330")) {
                                    name = "Qualcomm Snapdragon 800 (Krait 400)";
                                } else if (gpuRenderer.contains("320")) {
                                    long maxFrequency =
                                            Util.readFileLong("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq",
                                                    false);

                                    if (maxFrequency > 1650000) {
                                        name = "Qualcomm Snapdragon 600 (Krait 300)";
                                    } else {
                                        name = "Qualcomm Snapdragon S4 Pro";
                                    }
                                } else if (gpuRenderer.contains("306")) {
                                    name = "Qualcomm Snapdragon 410";
                                } else if (gpuRenderer.contains("305")) {
                                    long maxFrequency =
                                            Util.readFileLong("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq",
                                                    false);

                                    if (maxFrequency > 1650000) {
                                        name = "Qualcomm Snapdragon 400 (Krait 300)";
                                    } else {
                                        name = "Qualcomm Snapdragon 400 (Krait 200)";
                                    }
                                } else if (gpuRenderer.contains("203")
                                        || gpuRenderer.contains("302")) {
                                    name = "Qualcomm Snapdragon 200";
                                }
                            }
                            break;
                        case 0x201: //513
                            name = "Qualcomm Snapdragon (Kryo)";
                            break;
                        case 0x205: //517
                            name = "Qualcomm Snapdragon 820";
                            break;
                        case 0x211: //529
                            name = "Qualcomm Snapdragon 821";
                            break;
                        case 0x800: //2048
                            name = "Qualcomm Snapdragon 835";
                            break;
                    }

                    if (board.contains("mako")) {
                        name = "Qualcomm Snapdragon S4 Pro (Krait)";
                    } else if (board.contains("mahimahi")) {
                        name = "QSD 8250";
                    }

                    //					if ( hardware.contains( "qualcomm" )
                    //							|| hardware.contains( "qct" )
                    //							|| hardware.contains( "msm" ) )
                    // {
                    // if ( hardware.contains( "apq8064" ) )
                    // {
                    // name = "Qualcomm Snapdragon S4 Pro (Krait) <APQ8064>";
                    // }
                    // }

                    break;
                case 0x53: //83
                    if (part == 1) {
                        name = "Samsung Exynos M1";
                    } else {
                        name = "Samsung 0x" + Integer.toHexString(part);
                    }
                    break;
                case 0x56: //86
                    name = "Marvell 0x" + Integer.toHexString(part);
                    break;
                case 0x69: //105
                    name = "Intel 0x" + Integer.toHexString(part);
                    break;
            }

            if (name != null) {
                return getNameWithCores(name, cores);
            }

            return baseModel;
        }

        private boolean devHardwareContains(String name) {
            return devHardware != null && devHardware.contains(name);
        }

        private boolean hardwareContains(String name) {
            return hardware != null && hardware.contains(name);
        }

        private boolean anyHardwareContains(String name) {
            return devHardwareContains(name) || hardwareContains(name);
        }

        private String refineArmModel(String originName, String arch) {
            String name = null;

            // samsung
            if (board.contains("herring") || board.contains("aries")) {
                name = "Samsung Exynos 3110";
            } else if (anyHardwareContains("universal3475")) {
                name = "Samsung Exynos 3475";
            } else if (devHardwareContains("smdkc210")
                    || devHardwareContains("smdk4210")) {
                name = "Samsung Exynos 4210";
            } else if (devHardwareContains("smdk4x12")) {
                if (cores == 2) {
                    name = "Samsung Exynos 4212";
                } else if (cores == 4) {
                    name = "Samsung Exynos 4412";
                }
            } else if (board.contains("manta")) {
                name = "Samsung Exynos 5250";
            } else if (devHardwareContains("universal5260")) {
                name = "Samsung Exynos Hexa 5260";
            } else if (anyHardwareContains("universal5410")) {
                name = "Samsung Exynos Octa 5410";
                arch = "ARM Cortex-A15 + ARM Cortex-A7";
            } else if (anyHardwareContains("universal5420")) {
                name = "Samsung Exynos Octa 5420";
                arch = "ARM Cortex-A15 + ARM Cortex-A7";
            } else if (devHardwareContains("universal5422")) {
                name = "Samsung Exynos Octa 5422";
            } else if (anyHardwareContains("universal5430")) {
                name = "Samsung Exynos Octa 5430";
            } else if (devHardwareContains("universal5433")) {
                name = "Samsung Exynos Octa 5433";
            } else if (devHardwareContains("samsungexynos7420")) {
                name = "Samsung Exynos Octa 7420";
            } else if (devHardwareContains("samsungexynos7580")) {
                name = "Samsung Exynos Octa 7580";
            } else if (devHardwareContains("samsungexynos7870")) {
                name = "Samsung Exynos Octa 7870";
            } else if (devHardwareContains("samsungexynos8890")) {
                name = "Samsung Exynos Octa 8890";

                // allwinner
            } else if (devHardwareContains("sun4i")) {
                name = "Allwinner A10";
            } else if (devHardwareContains("sun5i")) {
                name = "Allwinner A13";
            } else if (devHardwareContains("sun6i")) {
                name = "Allwinner A31/A31s";
            } else if (devHardwareContains("sun7i")) {
                name = "Allwinner A20";
            } else if (devHardwareContains("sun8i")) {
                name = "Allwinner A23";
            } else if (devHardwareContains("sun9i")) {
                name = "Allwinner A80";

                // mediatek
            } else if (devHardwareContains("mt6517")) {
                name = "MediaTek MT6517";
            } else if (devHardwareContains("mt6572")) {
                name = "MediaTek MT6572";
            } else if (devHardwareContains("mt6575")) {
                name = "MediaTek MT6575";
            } else if (devHardwareContains("mt6577")) {
                name = "MediaTek MT6577";
            } else if (devHardwareContains("mt6580")) {
                name = "MediaTek MT6580";
            } else if (devHardwareContains("mt6582")) {
                name = "MediaTek MT6582";
            } else if (devHardwareContains("mt6588")) {
                name = "MediaTek MT6588";
            } else if (devHardwareContains("mt6589")) {
                name = "MediaTek MT6589";
            } else if (devHardwareContains("mt6591")) {
                name = "MediaTek MT6591";
            } else if (devHardwareContains("mt6592")) {
                name = "MediaTek MT6592";
            } else if (devHardwareContains("mt6595")) {
                name = "MediaTek MT6595";
            } else if (devHardwareContains("mt6732")) {
                name = "MediaTek MT6732";
            } else if (devHardwareContains("mt6735")) {
                name = "MediaTek MT6735";
            } else if (devHardwareContains("mt6750")) {
                name = "MediaTek MT6750";
            } else if (devHardwareContains("mt6752")) {
                name = "MediaTek MT6752";
            } else if (devHardwareContains("mt6753")) {
                name = "MediaTek MT6753";
            } else if (devHardwareContains("mt6755")) {
                name = "MediaTek MT6755";
            } else if (devHardwareContains("mt6757")) {
                name = "MediaTek MT6757";
            } else if (devHardwareContains("mt6795")) {
                name = "MediaTek MT6795";
            } else if (devHardwareContains("mt6797d")) {
                name = "MediaTek Helio X23";
            } else if (devHardwareContains("mt6797t")) {
                name = "MediaTek Helio X25";
            } else if (devHardwareContains("mt6797x")) {
                name = "MediaTek Helio X27";
            } else if (devHardwareContains("mt6797")) {
                name = "MediaTek Helio X20";
            } else if (devHardwareContains("mt6799")) {
                name = "MediaTek MT6799";
            } else if (devHardwareContains("mt8377")) {
                name = "MediaTek MT8377";
            } else if (devHardwareContains("mt8382")) {
                name = "MediaTek MT8382";
            } else if (devHardwareContains("mt8127")) {
                name = "MediaTek MT8127";

                // broadcom
            } else if (devHardwareContains("bcm21553")) {
                name = "Broadcom BCM21553";
            } else if (board.contains("rhea")) {
                name = "Broadcom BCM21654/G";
            } else if (board.contains("hawaii")) {
                name = "Broadcom BCM21664";
            } else if (board.contains("capri")) {
                name = "Broadcom BCM28155";

                // marvell
            } else if (devHardwareContains("pxa1088")) {
                name = "Marvell PXA1088";

                // hisilicon
            } else if (hardwareContains("hi6210")) {
                name = "HiSilicon Kirin 620";
            } else if (hardwareContains("hi6250")) {
                name = "HiSilicon Kirin 650";
            } else if (hardwareContains("hi6620")) {
                name = "HiSilicon Kirin 910";
            } else if (hardwareContains("kirin92")
                    || hardwareContains("kirin920")
                    || devHardwareContains("hi3630")
                    || board.contains("hi3630")) {
                name = "HiSilicon Kirin 920";
            } else if (devHardwareContains("hi3635")) {
                if (hardwareContains("kirin 935")) {
                    name = "HiSilicon Kirin 935";
                } else if (hardwareContains("kirin 930")) {
                    name = "HiSilicon Kirin 930";
                }
            } else if (hardwareContains("kirin 940")) {
                name = "HiSilicon Kirin 940";
            } else if (hardwareContains("kirin 950")) {
                name = "HiSilicon Kirin 950";
            } else if (hardwareContains("kirin 955")
                    || devHardwareContains("hi3650")) {
                name = "HiSilicon Kirin 955";
            } else if (devHardwareContains("hi3660")) {
                name = "HiSilicon Kirin 960";

                // ti
            } else if (model.contains("gt-i9100g")
                    || model.contains("xt910")
                    || board.contains("piranha")) {
                name = "TI OMAP 4430";
            } else if (board.contains("tuna")) {
                name = "TI OMAP 4460";

                // ste
            } else if (model.contains("gt-i8190l")) {
                name = "STE NovaThor U8420";
            } else if (model.contains("gt-i8190")
                    || model.contains("gt-i9070p")
                    || model.contains("st25i")
                    || board.contains("montblanc")) {
                name = "STE NovaThor U8500";

                // rockchip
            } else if (board.contains("rk32sdk")) {
                name = "Rockchip RK3288";
            } else if (board.contains("rk31sdk")) {
                name = "Rockchip RK31";
                if (cores == 4) {
                    name = "Rockchip RK3188";
                } else if (cores == 2) {
                    name = "Rockchip RK3168";
                }
            } else if (board.contains("rk30sdk")) {
                name = "Rockchip RK3066";
            } else if (board.contains("rk29sdk")) {
                name = "Rockchip RK29";
                if (part == 0xc08) {
                    name = "Rockchip RK2928";
                } else if (part == 0xc09) {
                    name = "Rockchip RK2918";
                }
            } else if (board.contains("rk2928sdk")) {
                name = "Rockchip RK2928";
            } else if (board.contains("rk28sdk")) {
                name = "Rockchip RK28";
            }

            // check by gpu
            if (name == null && gpuVendor != null && gpuRenderer != null) {
                if (gpuVendor.contains("NVIDIA Corporation")) {
                    name = gpuRenderer;

                    if (part == 0xc09) {
                        if (cores == 4) {
                            if (variant == 4) {
                                name = "NVIDIA Tegra 4i";
                            } else {
                                name = "NVIDIA Tegra 3";
                            }
                        } else {
                            name = "NVIDIA Tegra 2";
                        }
                    } else if (part == 0xc0f) {
                        if (variant == 3) {
                            name = "NVIDIA Tegra K1";
                        } else {
                            name = "NVIDIA Tegra 4";
                        }
                    }
                } else if (gpuVendor.contains("Qualcomm")) {
                    if (gpuRenderer.contains("540")) {
                        name = "Qualcomm Snapdragon 835";
                    } else if (gpuRenderer.contains("530")) {
                        name = "Qualcomm Snapdragon 820";
                    } else if (gpuRenderer.contains("510")) {
                        if (cores == 8) {
                            name = "Qualcomm Snapdragon 652";
                        } else if (cores == 6) {
                            name = "Qualcomm Snapdragon 650";
                        }
                    } else if (gpuRenderer.contains("506")) {
                        name = "Qualcomm Snapdragon 625";
                    } else if (gpuRenderer.contains("505")) {
                        if (board.contains("msm8937")) {
                            name = "Qualcomm Snapdragon 435";
                        } else {
                            name = "Qualcomm Snapdragon 430";
                        }
                    } else if (gpuRenderer.contains("450")) {
                        name = "Qualcomm Snapdragon 815";
                    } else if (gpuRenderer.contains("430")) {
                        name = "Qualcomm Snapdragon 810";
                    } else if (gpuRenderer.contains("420")) {
                        name = "Qualcomm Snapdragon 805";
                    } else if (gpuRenderer.contains("418")) {
                        name = "Qualcomm Snapdragon 808";
                    } else if (gpuRenderer.contains("405")) {
                        if (cores != 8) {
                            name = "Qualcomm Snapdragon 610";
                        }
                        if (board.contains("msm8952")) {
                            name = "Qualcomm Snapdragon 617";
                        } else {
                            name = "Qualcomm Snapdragon 615";
                        }
                    } else if (gpuRenderer.contains("330")) {
                        name = "Qualcomm Snapdragon 800/801";
                    } else if (gpuRenderer.contains("306")) {
                        name = "Qualcomm Snapdragon 400/410";
                    } else if (gpuRenderer.contains("305")) {
                        name = "Qualcomm Snapdragon 400";
                    } else if (gpuRenderer.contains("203")) {
                        name = "Qualcomm Snapdragon 200";
                    } else if (gpuRenderer.contains("200")) {
                        name = "Qualcomm MSM7x27";
                    }
                } else if (gpuVendor.contains("Hisilicon")) {
                    if (part == 0xc09) {
                        name = "HiSilicon K3V2";
                    } else if (part == 0xc0f) {
                        name = "HiSilicon K3V3";
                    }
                }
            }

            if (name == null) {
                name = originName;
            }
            return name + " (" + arch + ')';
        }

        private String getX86Model() {
            return baseModel;
        }

        private String getMipsModel() {
            return baseModel;
        }

    }
}
