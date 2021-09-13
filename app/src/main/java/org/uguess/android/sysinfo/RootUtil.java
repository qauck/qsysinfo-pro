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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RootUtil <br>
 * <br>
 * <b>Methods in this class should be called in worker thread!</b>
 */
public final class RootUtil {

    private static final String[] rootTestCommands = new String[]{"echo -BOC-", //$NON-NLS-1$
            "id" //$NON-NLS-1$
    };

    public static boolean rootAvailable() {
        List<String> ret = run("su", rootTestCommands, null, false); //$NON-NLS-1$
        return parseAvailableResult(ret, true);
    }

    public static List<String> runRoot(String... commands) {
        return run("su", commands, null, false); //$NON-NLS-1$
    }

    private static boolean parseAvailableResult(List<String> ret, boolean checkForRoot) {
        if (ret == null)
            return false;

        // this is only one of many ways this can be done
        boolean echo_seen = false;

        for (String line : ret) {
            if (line.contains("uid=")) //$NON-NLS-1$
            {
                // id command is working, let's see if we are actually root
                return !checkForRoot || line.contains("uid=0"); //$NON-NLS-1$
            } else if (line.contains("-BOC-")) //$NON-NLS-1$
            {
                // if we end up here, at least the su command starts some kind
                // of shell,
                // let's hope it has root privileges - no way to know without
                // additional
                // native binaries
                echo_seen = true;
            }
        }

        return echo_seen;
    }

    private static List<String> run(String shell, String[] commands, String[] environment,
                                    boolean wantSTDERR) {
        List<String> res = Collections.synchronizedList(new ArrayList<String>());

        try {
            if (environment != null) {
                Map<String, String> newEnvironment = new HashMap<String, String>();
                newEnvironment.putAll(System.getenv());
                int split;
                for (String entry : environment) {
                    if ((split = entry.indexOf("=")) >= 0) //$NON-NLS-1$
                    {
                        newEnvironment.put(entry.substring(0, split), entry.substring(split + 1));
                    }
                }
                int i = 0;
                environment = new String[newEnvironment.size()];
                for (Map.Entry<String, String> entry : newEnvironment.entrySet()) {
                    environment[i] = entry.getKey() + "=" + entry.getValue(); //$NON-NLS-1$
                    i++;
                }
            }

            Process process = Runtime.getRuntime().exec(shell, environment);
            DataOutputStream STDIN = new DataOutputStream(process.getOutputStream());
            StreamHandler STDOUT = new StreamHandler(process.getInputStream(), res);
            StreamHandler STDERR = new StreamHandler(process.getErrorStream(), wantSTDERR ? res : null);

            STDOUT.start();
            STDERR.start();
            for (String write : commands) {
                STDIN.write((write + "\n").getBytes("UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
                STDIN.flush();
            }
            STDIN.write("exit\n".getBytes("UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
            STDIN.flush();

            process.waitFor();

            try {
                STDIN.close();
            } catch (IOException e) {
            }
            STDOUT.join();
            STDERR.join();
            process.destroy();

            // in case of su, 255 usually indicates access denied
            if (shell.equals("su") && (process.exitValue() == 255)) //$NON-NLS-1$
            {
                res = null;
            }
        } catch (IOException e) {
            res = null;
        } catch (InterruptedException e) {
            res = null;
        }

        return res;
    }

    /**
     * StreamHandler
     */
    static final class StreamHandler extends Thread {

        private BufferedReader reader = null;
        private List<String> writer = null;

        StreamHandler(InputStream inputStream, List<String> outputList) {
            reader = new BufferedReader(new InputStreamReader(inputStream));
            writer = outputList;
        }

        @Override
        public void run() {
            try {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    if (writer != null) {
                        writer.add(line);
                    }
                }
            } catch (IOException e) {
                // ignore
            }

            try {
                reader.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
