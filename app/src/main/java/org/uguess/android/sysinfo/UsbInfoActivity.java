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
import android.hardware.usb.UsbConstants;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 * UsbInfoActivity
 */
public final class UsbInfoActivity extends PopActivity {

    private static Method mtdGetAccessoryList = null;
    private static Method mtdGetDeviceList = null;

    private static Method mtdUsbDeviceGetDeviceClass = null;
    private static Method mtdUsbDeviceGetDeviceName = null;
    private static Method mtdUsbDeviceGetDeviceProtocol = null;
    private static Method mtdUsbDeviceGetDeviceSubclass = null;
    private static Method mtdUsbDeviceGetProductId = null;
    private static Method mtdUsbDeviceGetVendorId = null;
    private static Method mtdUsbDeviceGetInterfaceCount = null;
    private static Method mtdUsbDeviceGetInterface = null;

    private static Method mtdUsbAccessoryGetModel = null;
    private static Method mtdUsbAccessoryGetDescription = null;
    private static Method mtdUsbAccessoryGetManufacturer = null;
    private static Method mtdUsbAccessoryGetSerial = null;
    private static Method mtdUsbAccessoryGetVersion = null;
    private static Method mtdUsbAccessoryGetUri = null;

    private static Method mtdUsbInterfaceGetId = null;
    private static Method mtdUsbInterfaceGetInterfaceClass = null;
    private static Method mtdUsbInterfaceGetInterfaceSubclass = null;
    private static Method mtdUsbInterfaceGetInterfaceProtocol = null;
    private static Method mtdUsbInterfaceGetEndpointCount = null;
    private static Method mtdUsbInterfaceGetEndpoint = null;

    private static Method mtdUsbEndpointGetAddress = null;
    private static Method mtdUsbEndpointGetAttributes = null;
    private static Method mtdUsbEndpointGetInterval = null;
    private static Method mtdUsbEndpointGetMaxPacketSize = null;

    static {
        try {
            Class<?> clz = Class.forName("android.hardware.usb.UsbManager"); //$NON-NLS-1$

            mtdGetAccessoryList = clz.getDeclaredMethod("getAccessoryList"); //$NON-NLS-1$
            mtdGetDeviceList = clz.getDeclaredMethod("getDeviceList"); //$NON-NLS-1$

            clz = Class.forName("android.hardware.usb.UsbDevice"); //$NON-NLS-1$
            mtdUsbDeviceGetDeviceClass = clz.getDeclaredMethod("getDeviceClass"); //$NON-NLS-1$
            mtdUsbDeviceGetDeviceName = clz.getDeclaredMethod("getDeviceName"); //$NON-NLS-1$
            mtdUsbDeviceGetDeviceProtocol = clz.getDeclaredMethod("getDeviceProtocol"); //$NON-NLS-1$
            mtdUsbDeviceGetDeviceSubclass = clz.getDeclaredMethod("getDeviceSubclass"); //$NON-NLS-1$
            mtdUsbDeviceGetProductId = clz.getDeclaredMethod("getProductId"); //$NON-NLS-1$
            mtdUsbDeviceGetVendorId = clz.getDeclaredMethod("getVendorId"); //$NON-NLS-1$
            mtdUsbDeviceGetInterfaceCount = clz.getDeclaredMethod("getInterfaceCount"); //$NON-NLS-1$
            mtdUsbDeviceGetInterface = clz.getDeclaredMethod("getInterface", int.class); //$NON-NLS-1$

            clz = Class.forName("android.hardware.usb.UsbAccessory"); //$NON-NLS-1$
            mtdUsbAccessoryGetModel = clz.getDeclaredMethod("getModel"); //$NON-NLS-1$
            mtdUsbAccessoryGetDescription = clz.getDeclaredMethod("getDescription"); //$NON-NLS-1$
            mtdUsbAccessoryGetManufacturer = clz.getDeclaredMethod("getManufacturer"); //$NON-NLS-1$
            mtdUsbAccessoryGetSerial = clz.getDeclaredMethod("getSerial"); //$NON-NLS-1$
            mtdUsbAccessoryGetVersion = clz.getDeclaredMethod("getVersion"); //$NON-NLS-1$
            mtdUsbAccessoryGetUri = clz.getDeclaredMethod("getUri"); //$NON-NLS-1$

            clz = Class.forName("android.hardware.usb.UsbInterface"); //$NON-NLS-1$
            mtdUsbInterfaceGetId = clz.getDeclaredMethod("getId"); //$NON-NLS-1$;
            mtdUsbInterfaceGetInterfaceClass = clz.getDeclaredMethod("getInterfaceClass"); //$NON-NLS-1$;
            mtdUsbInterfaceGetInterfaceSubclass = clz.getDeclaredMethod("getInterfaceSubclass"); //$NON-NLS-1$;;
            mtdUsbInterfaceGetInterfaceProtocol = clz.getDeclaredMethod("getInterfaceProtocol"); //$NON-NLS-1$;;
            mtdUsbInterfaceGetEndpointCount = clz.getDeclaredMethod("getEndpointCount"); //$NON-NLS-1$;;
            mtdUsbInterfaceGetEndpoint = clz.getDeclaredMethod("getEndpoint", int.class); //$NON-NLS-1$;;

            clz = Class.forName("android.hardware.usb.UsbEndpoint"); //$NON-NLS-1$
            mtdUsbEndpointGetAddress = clz.getDeclaredMethod("getAddress"); //$NON-NLS-1$;;
            mtdUsbEndpointGetAttributes = clz.getDeclaredMethod("getAttributes"); //$NON-NLS-1$;;
            mtdUsbEndpointGetInterval = clz.getDeclaredMethod("getInterval"); //$NON-NLS-1$;;
            mtdUsbEndpointGetMaxPacketSize = clz.getDeclaredMethod("getMaxPacketSize"); //$NON-NLS-1$;;
        } catch (Exception e) {
            Log.d(UsbInfoActivity.class.getName(), "USB Device is not supported by current SDK version."); //$NON-NLS-1$
        }
    }

    static boolean isAvailable(Context ctx) {
        Object sv = ctx.getSystemService(Context.USB_SERVICE);
        return sv != null && mtdGetAccessoryList != null && mtdGetDeviceList != null;
    }

    static int getAccessoryCount(Context ctx) {
        Object[] accs = getAccessories(ctx);

        if (accs != null) {
            return accs.length;
        }

        return 0;
    }

    static Object[] getAccessories(Context ctx) {
        Object sv = ctx.getSystemService(Context.USB_SERVICE);

        if (sv != null && mtdGetAccessoryList != null) {
            try {
                Object result = mtdGetAccessoryList.invoke(sv);

                if (result instanceof Object[]) {
                    return (Object[]) result;
                }
            } catch (InvocationTargetException npe) {
                // ignore exception caused by NPE by null remote service
            } catch (Exception e) {
                Log.e(UsbInfoActivity.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        return null;
    }

    static int getDeviceCount(Context ctx) {
        HashMap devs = getDevices(ctx);

        if (devs != null) {
            return devs.size();
        }

        return 0;
    }

    static HashMap getDevices(Context ctx) {
        Object sv = ctx.getSystemService(Context.USB_SERVICE);

        if (sv != null && mtdGetDeviceList != null) {
            try {
                Object result = mtdGetDeviceList.invoke(sv);

                if (result instanceof HashMap) {
                    return (HashMap) result;
                }
            } catch (InvocationTargetException npe) {
                // ignore exception caused by NPE by null remote service
            } catch (Exception e) {
                Log.e(UsbInfoActivity.class.getName(), e.getLocalizedMessage(), e);
            }
        }

        return null;
    }

    private Runnable task = new Runnable() {

        public void run() {
            List<String[]> ss = new ArrayList<String[]>();

            try {
                HashMap<?, ?> devs = getDevices(UsbInfoActivity.this);

                if (devs != null) {
                    for (Entry ent : devs.entrySet()) {
                        ss.add(formatDeviceInfo(ent.getValue()));
                    }
                }
            } catch (Exception e) {
                Log.e(UsbInfoActivity.class.getName(), e.getLocalizedMessage(), e);
            }

            try {
                Object[] accs = getAccessories(UsbInfoActivity.this);

                if (accs != null) {
                    for (int i = 0; i < accs.length; i++) {
                        ss.add(new String[]{"Accessory " + i, formatAccessoryInfo(accs[i])});
                    }
                }
            } catch (Exception e) {
                Log.e(UsbInfoActivity.class.getName(), e.getLocalizedMessage(), e);
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

    static String[] formatDeviceInfo(Object dv) throws Exception {
        StringBuilder sb = new StringBuilder();

        String name = (String) mtdUsbDeviceGetDeviceName.invoke(dv);
        sb.append("Name: ").append(name).append('\n');

        Integer clz = (Integer) mtdUsbDeviceGetDeviceClass.invoke(dv);
        sb.append("Class: ").append(clz);

        String clzName = getUsbClassName(clz);
        if (clzName != null) {
            sb.append(" (").append(clzName).append(')'); //$NON-NLS-1$
        }
        sb.append('\n');

        Integer subClz = (Integer) mtdUsbDeviceGetDeviceSubclass.invoke(dv);
        sb.append("Subclass: ").append(subClz).append('\n');

        Integer protocol = (Integer) mtdUsbDeviceGetDeviceProtocol.invoke(dv);
        sb.append("Protocol: ").append(protocol).append('\n');

        Integer pid = (Integer) mtdUsbDeviceGetProductId.invoke(dv);
        sb.append("Product Id: ").append(pid).append('\n');

        Integer vid = (Integer) mtdUsbDeviceGetVendorId.invoke(dv);
        sb.append("Vendor Id: ").append(vid).append('\n');

        Integer count = (Integer) mtdUsbDeviceGetInterfaceCount.invoke(dv);

        if (count != null) {
            for (int i = 0; i < count; i++) {
                Object intf = mtdUsbDeviceGetInterface.invoke(dv, i);

                if (intf == null) {
                    continue;
                }

                sb.append("\n- Interface ").append(i).append(":\n");

                Integer iid = (Integer) mtdUsbInterfaceGetId.invoke(intf);
                sb.append("- Id: ").append(iid).append('\n');

                Integer iclz = (Integer) mtdUsbInterfaceGetInterfaceClass.invoke(intf);
                sb.append("- Class: ").append(iclz);

                String iclzName = getUsbClassName(iclz);

                if (iclzName != null) {
                    sb.append(" (").append(iclzName).append(')'); //$NON-NLS-1$
                }

                sb.append('\n');

                Integer isubclz = (Integer) mtdUsbInterfaceGetInterfaceSubclass.invoke(intf);
                sb.append("- Subclass: ").append(iclz).append('\n');

                Integer ipt = (Integer) mtdUsbInterfaceGetInterfaceProtocol.invoke(intf);
                sb.append("- Protocol: ").append(ipt).append('\n');

                Integer icount = (Integer) mtdUsbInterfaceGetEndpointCount.invoke(intf);

                if (icount != null) {
                    for (int j = 0; j < icount; j++) {
                        Object ep = mtdUsbInterfaceGetEndpoint.invoke(intf, j);

                        if (ep == null) {
                            continue;
                        }

                        sb.append("\n  - Endpoint ").append(j).append(":\n");

                        Integer addr = (Integer) mtdUsbEndpointGetAddress.invoke(ep);
                        sb.append("  - Address: ").append(addr).append('\n');

                        if (addr != null) {
                            sb.append("  - Endpoint Number: ")
                                    .append(addr & UsbConstants.USB_ENDPOINT_NUMBER_MASK).append('\n');

                            int dir = addr & UsbConstants.USB_ENDPOINT_DIR_MASK;

                            sb.append("  - Direction: ").append(dir == UsbConstants.USB_DIR_IN ? "IN" : "OUT")
                                    .append('\n');
                        }

                        Integer attr = (Integer) mtdUsbEndpointGetAttributes.invoke(ep);
                        sb.append("  - Attributes: ").append(attr).append('\n');

                        if (attr != null) {
                            String type = null;
                            switch (attr & UsbConstants.USB_ENDPOINT_XFERTYPE_MASK) {
                                case UsbConstants.USB_ENDPOINT_XFER_CONTROL:
                                    type = "Control";
                                    break;
                                case UsbConstants.USB_ENDPOINT_XFER_ISOC:
                                    type = "Isochronous";
                                    break;
                                case UsbConstants.USB_ENDPOINT_XFER_BULK:
                                    type = "Bulk";
                                    break;
                                case UsbConstants.USB_ENDPOINT_XFER_INT:
                                    type = "Interrupt";
                                    break;
                            }

                            if (type != null) {
                                sb.append("  - Type: ").append(type).append('\n');
                            }
                        }

                        Integer intv = (Integer) mtdUsbEndpointGetInterval.invoke(ep);
                        sb.append("  - Interval: ").append(intv).append('\n');

                        Integer mps = (Integer) mtdUsbEndpointGetMaxPacketSize.invoke(ep);
                        sb.append("  - MaxPacketSize: ").append(mps).append('\n');
                    }
                }

            }
        }

        sb.deleteCharAt(sb.length() - 1);

        return new String[]{name, sb.toString()};
    }

    static String getUsbClassName(Integer clz) {
        if (clz == null) {
            return null;
        }

        switch (clz) {
            case UsbConstants.USB_CLASS_APP_SPEC:
                return "Application Specific";
            case UsbConstants.USB_CLASS_AUDIO:
                return "Audio";
            case UsbConstants.USB_CLASS_CDC_DATA:
                return "CDC";
            case UsbConstants.USB_CLASS_COMM:
                return "COMM";
            case UsbConstants.USB_CLASS_CONTENT_SEC:
                return "Content Security";
            case UsbConstants.USB_CLASS_CSCID:
                return "Content Smart Card";
            case UsbConstants.USB_CLASS_HID:
                return "HID";
            case UsbConstants.USB_CLASS_HUB:
                return "Hub";
            case UsbConstants.USB_CLASS_MASS_STORAGE:
                return "Mass Storage";
            case UsbConstants.USB_CLASS_MISC:
                return "Wireless Misc";
            case UsbConstants.USB_CLASS_PER_INTERFACE:
                return "Per Interface";
            case UsbConstants.USB_CLASS_PHYSICA:
                return "Physical";
            case UsbConstants.USB_CLASS_PRINTER:
                return "Printer";
            case UsbConstants.USB_CLASS_STILL_IMAGE:
                return "Still Image";
            case UsbConstants.USB_CLASS_VENDOR_SPEC:
                return "Vendor Specific";
            case UsbConstants.USB_CLASS_VIDEO:
                return "Video";
            case UsbConstants.USB_CLASS_WIRELESS_CONTROLLER:
                return "Wireless Controller";
        }

        return null;
    }

    static String formatAccessoryInfo(Object acc) throws Exception {
        StringBuilder sb = new StringBuilder();

        sb.append("Model: ").append(mtdUsbAccessoryGetModel.invoke(acc)).append('\n');

        sb.append("Manufacturer: ").append(mtdUsbAccessoryGetManufacturer.invoke(acc)).append('\n');

        sb.append("Version: ").append(mtdUsbAccessoryGetVersion.invoke(acc)).append('\n');

        sb.append("Description: ").append(mtdUsbAccessoryGetDescription.invoke(acc)).append('\n');

        sb.append("Serial: ").append(mtdUsbAccessoryGetSerial.invoke(acc)).append('\n');

        sb.append("Uri: ").append(mtdUsbAccessoryGetUri.invoke(acc)).append('\n');

        return sb.toString();
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
