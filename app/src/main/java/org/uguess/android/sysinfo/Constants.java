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

/**
 * Constants
 */
public interface Constants {

    // preference store names
    String SYSINFO_MANAGER_STORE_NAME = "SysInfoManager";
    String APPLICATION_MANAGER_STORE_NAME = "ApplicationManager";
    String PROCESS_MANAGER_STORE_NAME = "ProcessManager";
    String NETSTATE_MANAGER_STORE_NAME = "NetStateManager";
    String RESTORE_MANAGER_STORE_NAME = "RestoreAppActivity";
    String PROPERTIES_VIEWER_STORE_NAME = "PropertiesViewer";
    String LOG_VIEWER_STORE_NAME = "LogViewer";

    // shared preference keys
    String PREF_KEY_REFRESH_INTERVAL = "refresh_interval"; //$NON-NLS-1$

    int REFRESH_HIGH = 0;
    int REFRESH_NORMAL = 1;
    int REFRESH_LOW = 2;
    int REFRESH_PAUSED = 3;
    int REFRESH_HIGHER = 4;

    String PREF_KEY_SORT_ORDER_TYPE = "sort_order_type"; //$NON-NLS-1$
    String PREF_KEY_SORT_DIRECTION = "sort_direction"; //$NON-NLS-1$
    String PREF_KEY_SECONDARY_SORT_ORDER_TYPE = "secondary_sort_order_type"; //$NON-NLS-1$
    String PREF_KEY_SECONDARY_SORT_DIRECTION = "secondary_sort_direction"; //$NON-NLS-1$

    int ORDER_ASC = 1;
    int ORDER_DESC = -1;

    String PREF_CATEGORY_DEFAULT = "PREFERENCES";
    String PREF_CATEGORY_NOTIFICATIONS = "NOTIFICATIONS";
    String PREF_CATEGORY_BACKUP = "BACKUP";

    String PREF_KEY_RES_PKG_NAME = "res_pkg_name"; //$NON-NLS-1$
    String PREF_KEY_SHOW_SIZE = "show_size"; //$NON-NLS-1$
    String PREF_KEY_SHOW_DATE = "show_date"; //$NON-NLS-1$
    String PREF_KEY_SHOW_ICON = "show_icon"; //$NON-NLS-1$
    String PREF_KEY_DEFAULT_TAP_ACTION = "default_tap_action"; //$NON-NLS-1$
    String PREF_KEY_USE_TAG_VIEW = "use_tag_view"; //$NON-NLS-1$
    String PREF_KEY_SHOW_COUNT = "show_count"; //$NON-NLS-1$
    String PREF_KEY_REMEMBER_LAST_SHARE_SETTING = "remember_last_share_setting"; //$NON-NLS-1$
    String PREF_KEY_LAST_SHARE_SETTING = "last_share_setting"; //$NON-NLS-1$

    String PREF_KEY_CUSTOM_TAGS = "custom_tags"; //$NON-NLS-1$
    String PREF_KEY_TAG_VISIBILITY = "tag_visibility"; //$NON-NLS-1$
    String PREF_KEY_TAG_LINKS_PREFIX = "tag_links_"; //$NON-NLS-1$
    String PREF_KEY_RECENT_SCOPE = "recent_scope"; //$NON-NLS-1$

    // global settings
    String PREF_KEY_USER_LOCALE = "user_locale"; //$NON-NLS-1$
    String PREF_KEY_DEFAULT_EMAIL = "default_email"; //$NON-NLS-1$
    String PREF_KEY_DEFAULT_TAB = "default_tab"; //$NON-NLS-1$

    // notification icon settings
    String PREF_KEY_AUTO_START_ICON = "auto_start_icon"; //$NON-NLS-1$
    String PREF_KEY_PERSISTENT_ICON = "persistent_icon"; //$NON-NLS-1$
    String PREF_KEY_DISABLE_ALL_ICON = "disable_all_icon"; //$NON-NLS-1$
    String PREF_KEY_USE_LEGACY_ICON = "use_legacy_icon"; //$NON-NLS-1$
    String PREF_KEY_SHOW_INFO_ICON = "show_info_icon"; //$NON-NLS-1$
    String PREF_KEY_SHOW_TASK_ICON = "show_task_icon"; //$NON-NLS-1$
    String PREF_KEY_SHOW_MEM_MONITOR = "show_mem_monitor"; //$NON-NLS-1$
    String PREF_KEY_SHOW_CPU_MONITOR = "show_cpu_monitor"; //$NON-NLS-1$
    String PREF_KEY_SHOW_MEM_HISTORY = "show_mem_history"; //$NON-NLS-1$
    String PREF_KEY_SHOW_CPU_HISTORY = "show_cpu_history"; //$NON-NLS-1$
    String PREF_KEY_INVERSE_NOTIFY_TITLE_COLOR = "inverse_notify_title_color"; //$NON-NLS-1$
    String PREF_KEY_HIGH_PRIORITY = "high_priority"; //$NON-NLS-1$
    String PREF_KEY_SHOW_WIFI_ACTIVITY = "show_wifi_activity"; //$NON-NLS-1$
    String PREF_KEY_SHOW_WIFI_RATES = "show_wifi_rates"; //$NON-NLS-1$
    String PREF_KEY_SHOW_WIFI_SSID = "show_wifi_ssid"; //$NON-NLS-1$
    String PREF_KEY_SHOW_BAR_ICON_INFO = "show_bar_icon_info"; //$NON-NLS-1$
    String PREF_KEY_SHOW_BAR_ICON_TASK = "show_bar_icon_task"; //$NON-NLS-1$
    String PREF_KEY_SHOW_BAR_ICON_WIFI = "show_bar_icon_wifi"; //$NON-NLS-1$
    String PREF_KEY_SHOW_BATTERY_INFO = "show_battery_info"; //$NON-NLS-1$
    String PREF_KEY_REFRESH_INTERVAL_CPU = "refresh_interval_cpu"; //$NON-NLS-1$
    String PREF_KEY_REFRESH_INTERVAL_MEM = "refresh_interval_mem"; //$NON-NLS-1$
    String PREF_KEY_REFRESH_INTERVAL_NET = "refresh_interval_net"; //$NON-NLS-1$

    // pro settings
    String PREF_KEY_USER_NAME = "verify_user_name"; //$NON-NLS-1$
    String PREF_KEY_USER_KEY = "verify_user_key"; //$NON-NLS-1$

    // shared keys
    String KEY_REMOVED_TAGS = "removed_tags"; //$NON-NLS-1$

    // context menu
    int MI_DELETE = 1;
    int MI_LAUNCH = 2;
    int MI_SEARCH = 3;
    int MI_DISPLAY = 4;
    int MI_ENDTASK = 5;
    int MI_IGNORE = 6;
    int MI_DETAILS = 7;
    int MI_END_OTHERS = 8;
    int MI_ARCHIVE = 9;
    int MI_MANAGE = 10;
    int MI_GUARD = 11;
    int MI_FORCE_STOP = 12;
    int MI_VIEW_LOG = 13;
    int MI_TOGGLE_SELECTION = 14;

    // option menu
    int MI_REVERT = 101;
    int MI_SHARE = 102;
    int MI_REFRESH = 103;
    int MI_CLEAR_CACHE = 104;
    int MI_SYNC_APP = 105;
    int MI_USAGE_STATS = 106;
    int MI_ABOUT = 107;
    int MI_HELP = 108;
    int MI_UNLOCK = 109;
    int MI_PREFERENCE = 110;
    int MI_EXIT = 111;
    int MI_TAGS = 112;
    int MI_LIVE_MONITOR = 113;
    int MI_TO_USER = 114;
    int MI_TO_SYSTEM = 115;
    int MI_CLEAR_HISTORY = 116;
    int MI_PRIVACY = 117;

    // message
    int MSG_INIT_OK = 1;
    int MSG_DISMISS_PROGRESS = 2;
    int MSG_CONTENT_READY = 3;
    int MSG_CHECK_FORCE_COMPRESSION = 4;
    int MSG_TOAST = 5;

    int MSG_PRIVATE = 200;

    // request
    int REQUEST_PREFERENCES = 1;
    int REQUEST_GPU_INFO = 2;
    int REQUEST_SCAN_RESULT = 3;
    int REQUEST_INVOKE = 4;

    int REQUEST_PRIVATE = 200;

    // notification
    int NOTIFY_ERROR_REPORT = 1;
    int NOTIFY_EXPORT_FINISHED = 2;
    int NOTIFY_INFO_UPDATE = 3;
    int NOTIFY_TASK_UPDATE = 4;
    int NOTIFY_NET_UPDATE = 5;
    int NOTIFY_BATTERY_UPDATE = 6;
    int NOTIFY_BOOT = 99;

    // extras keys
    String EXTRA_PID = "pid"; //$NON-NLS-1$
    String EXTRA_SVC_CLZ_NAME = "svc_clz_name"; //$NON-NLS-1$

    // format
    int PLAINTEXT = 0;
    int HTML = 1;
    int CSV = 2;

    // layer
    int LAYER_TAG = 1;
    int LAYER_ITEM = 4;

    // other constants
    char[] CSV_SEARCH_CHARS = new char[]{',', '"', '\r', '\n'};
    char[] HTML_SEARCH_CHARS = new char[]{'<', '>', '&', '\'', '"', '\n'};

    String F_MEM_INFO = "/proc/meminfo"; //$NON-NLS-1$
    String F_VERSION = "/proc/version"; //$NON-NLS-1$
    String F_MOUNT_INFO = "/proc/mounts"; //$NON-NLS-1$

    String HEADER_SPLIT = "========================================================================================\n"; //$NON-NLS-1$

    String openFullRow = "<tr align=\"left\" valign=\"top\"><td colspan=5><small>"; //$NON-NLS-1$
    String openHeaderRow = "<tr align=\"left\" bgcolor=\"#E0E0FF\"><td><b>"; //$NON-NLS-1$
    String closeHeaderRow = "</b></td><td colspan=4/></tr>\n"; //$NON-NLS-1$
    String openRow = "<tr align=\"left\" valign=\"top\"><td nowrap><small>"; //$NON-NLS-1$
    String openTitleRow = "<tr bgcolor=\"#E0E0E0\" align=\"left\" valign=\"top\"><td><small>"; //$NON-NLS-1$
    String closeRow = "</small></td></tr>\n"; //$NON-NLS-1$
    String nextColumn = "</small></td><td><small>"; //$NON-NLS-1$
    String nextColumn4 = "</small></td><td colspan=4><small>"; //$NON-NLS-1$
    String emptyRow = "<tr><td>&nbsp;</td></tr>\n"; //$NON-NLS-1$
}
