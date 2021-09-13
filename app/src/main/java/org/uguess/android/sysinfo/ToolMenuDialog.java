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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * ToolMenuDialog
 */
public class ToolMenuDialog extends Dialog {

    static boolean support(Activity ac) {
        return ac instanceof ISortMenuProvider || ac instanceof IActionMenuProvider
                || ac instanceof IToggleMenuProvider;
    }

    ToolMenuDialog(Context ctx) {
        super(ctx, R.style.ToolMenu);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.tool_menu_view);

        Window window = getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = WindowManager.LayoutParams.FILL_PARENT;
        lp.height = WindowManager.LayoutParams.FILL_PARENT;
        lp.windowAnimations = android.R.style.Animation_Translucent;

        window.setAttributes(lp);

        ListView list = (ListView) findViewById(android.R.id.list);
        list.setBackgroundResource(android.R.drawable.dialog_holo_dark_frame);

        ArrayAdapter<Object> adapter =
                new ArrayAdapter<Object>(getContext(), android.R.layout.simple_list_item_1) {

                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view = null;

                        Object item = getItem(position);

                        if (item instanceof ActionHint) {
                            view =
                                    getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);

                            ActionHint mi = (ActionHint) item;

                            TextView text = (TextView) view.findViewById(android.R.id.text1);
                            text.setText(mi.label);
                        } else if (item instanceof String) {
                            view = getLayoutInflater().inflate(R.layout.tool_menu_header, parent, false);

                            TextView text = (TextView) view;
                            text.setText((String) item);
                        } else if (item instanceof SortHint) {
                            view = getLayoutInflater().inflate(R.layout.tool_menu_sort_item, parent, false);

                            SortHint sh = (SortHint) item;

                            TextView txtBy = (TextView) view.findViewById(android.R.id.text1);
                            txtBy.setText(sh.getLabel());

                            TextView txtDir = (TextView) view.findViewById(R.id.sortDir);
                            txtDir.setText(sh.ascending ? "↑" : "↓"); //$NON-NLS-1$ //$NON-NLS-2$
                            txtDir.setTag(sh);

                            txtDir.setOnClickListener(new View.OnClickListener() {

                                @Override
                                public void onClick(View v) {
                                    SortHint sh = (SortHint) v.getTag();

                                    sh.ascending = !sh.ascending;

                                    ((TextView) v).setText(sh.ascending ? "↑" : "↓"); //$NON-NLS-1$ //$NON-NLS-2$

                                    ((ISortMenuProvider) getOwnerActivity()).updateSort(sh);
                                }
                            });
                        } else if (item instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<ToggleHint> toggles = (List<ToggleHint>) item;

                            view = getLayoutInflater().inflate(R.layout.tool_menu_toggle_pane, parent, false);

                            ViewGroup pane = (ViewGroup) view.findViewById(R.id.pane_1);

                            for (int i = 0, size = toggles.size(); i < size; i++) {
                                ToggleHint hint = toggles.get(i);

                                if (i == 5) {
                                    pane = (ViewGroup) view.findViewById(R.id.pane_2);
                                    pane.setVisibility(View.VISIBLE);
                                }

                                TextView tv =
                                        (TextView) getLayoutInflater().inflate(R.layout.tool_menu_toggle_item, pane,
                                                false);
                                tv.setText(hint.label);
                                tv.setSelected(hint.value);
                                tv.setTag(hint);

                                tv.setOnClickListener(new View.OnClickListener() {

                                    @Override
                                    public void onClick(View v) {
                                        ToggleHint hint = (ToggleHint) v.getTag();

                                        hint.value = !hint.value;
                                        v.setSelected(hint.value);

                                        ((IToggleMenuProvider) getOwnerActivity()).updateToggle(hint);
                                    }
                                });

                                if (hint.hint != null) {
                                    tv.setOnLongClickListener(new OnLongClickListener() {

                                        @Override
                                        public boolean onLongClick(View v) {
                                            ToggleHint hint = (ToggleHint) v.getTag();

                                            Util.shortToast(getContext(), hint.hint);

                                            return true;
                                        }
                                    });
                                }

                                pane.addView(tv);
                            }
                        }

                        return view;
                    }
                };

        list.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);

                if (item instanceof ActionHint) {
                    ((IActionMenuProvider) getOwnerActivity()).onActionSelected(((ActionHint) item).action);

                    dismiss();
                } else if (item instanceof SortHint) {
                    final SortHint sh = (SortHint) item;

                    OnClickListener listener = new OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            TextView txtBy = (TextView) view.findViewById(android.R.id.text1);

                            sh.sortBy = which;

                            txtBy.setText(sh.getLabel());

                            ((ISortMenuProvider) getOwnerActivity()).updateSort(sh);
                        }
                    };
                    new AlertDialog.Builder(getContext())
                            .setTitle(ResUtil.getString(getContext(), R.string.sort_type))
                            .setItems(sh.sortByLabels, listener).create().show();
                }
            }
        });

        list.setAdapter(adapter);

        List<Object> entries = getContents();

        adapter.setNotifyOnChange(false);
        adapter.clear();

        for (int i = 0, size = entries.size(); i < size; i++) {
            adapter.add(entries.get(i));
        }

        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            dismiss();
            return true;
        }
        return false;
    }

    protected List<Object> getContents() {

        final Activity ac = getOwnerActivity();

        List<Object> entries = new ArrayList<Object>();

        if (ac instanceof IToggleMenuProvider) {
            List<ToggleHint> toggles = ((IToggleMenuProvider) ac).getToggles();

            if (toggles != null && toggles.size() > 0) {
                entries.add(ResUtil.getString(getContext(), R.string.toggles));
                entries.add(toggles);
            }
        }

        if (ac instanceof ISortMenuProvider) {
            SortHint sh = ((ISortMenuProvider) ac).getSort(true);
            if (sh != null) {
                entries.add(ResUtil.getString(getContext(), R.string.prime_sort));
                entries.add(sh);
            }

            sh = ((ISortMenuProvider) ac).getSort(false);
            if (sh != null) {
                entries.add(ResUtil.getString(getContext(), R.string.second_sort));
                entries.add(sh);
            }
        }

        if (ac instanceof IActionMenuProvider) {
            List<ActionHint> actions = ((IActionMenuProvider) ac).getActions();

            if (actions != null && actions.size() > 0) {
                entries.add(ResUtil.getString(getContext(), R.string.actions));
                entries.addAll(actions);
            }
        }

        return entries;
    }

    /**
     * IActionMenuProvider
     */
    interface IActionMenuProvider {

        List<ActionHint> getActions();

        boolean onActionSelected(int action);
    }

    /**
     * ISortMenuProvider
     */
    interface ISortMenuProvider {

        SortHint getSort(boolean primary);

        void updateSort(SortHint hint);
    }

    /**
     * IToggleMenuProvider
     */
    interface IToggleMenuProvider {

        List<ToggleHint> getToggles();

        void updateToggle(ToggleHint hint);
    }

    /**
     * ActionHint
     */
    static final class ActionHint {

        String label;
        int action;

        ActionHint(String label, int action) {
            this.label = label;
            this.action = action;
        }
    }

    /**
     * SortHint
     */
    static final class SortHint {

        boolean primary;

        int sortBy;

        boolean ascending;

        String[] sortByLabels;

        String getLabel() {
            if (sortBy >= 0 && sortBy < sortByLabels.length) {
                return sortByLabels[sortBy];
            }

            return null;
        }
    }

    /**
     * ToggleHint
     */
    static final class ToggleHint {

        String label;

        String hint;

        String key;

        boolean value;
    }
}
