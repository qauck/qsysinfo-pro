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

import android.app.Activity;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.TextView;

/**
 * PopActivity
 */
abstract class PopActivity extends Activity {

    private GestureDetector gestureDetector;

    protected boolean eventConsumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.pop_view);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (eventConsumed) {
                    eventConsumed = false;
                } else {
                    finish();
                }
                return true;
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // fix: https://code.google.com/p/android/issues/detail?id=19917
        outState.putString("WORKAROUND_FOR_BUG_19917_KEY", //$NON-NLS-1$
                "WORKAROUND_FOR_BUG_19917_VALUE"); //$NON-NLS-1$

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        menu.setHeaderTitle(ResUtil.getString(this, R.string.actions));
        menu.add(ResUtil.getString(this, R.string.copy_text));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        View view = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).targetView;

        if (view != null) {
            TextView txtHead = (TextView) view.findViewById(R.id.txt_head);
            TextView txtMsg = (TextView) view.findViewById(R.id.txt_msg);

            String s = null;

            if (txtHead != null) {
                s = txtHead.getText().toString();
            }

            if (txtMsg != null) {
                if (s != null) {
                    s += '\n' + txtMsg.getText().toString();
                } else {
                    s = txtMsg.getText().toString();
                }
            }

            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

            if (cm != null && !TextUtils.isEmpty(s)) {
                cm.setText(s);

                Util.shortToast(this, R.string.copied_hint);
            }
        }

        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector.onTouchEvent(ev)) {
            return true;
        }

        return super.dispatchTouchEvent(ev);
    }
}
