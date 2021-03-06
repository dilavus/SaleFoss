/**
 * Odoo, Open Source Management Solution
 * Copyright (C) 2012-today Odoo SA (<http:www.odoo.com>)
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details
 * <p/>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http:www.gnu.org/licenses/>
 * <p/>
 * Created on 13/1/15 11:06 AM
 */
package com.odoo.addons.sale;

import android.app.ProgressDialog;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import com.odoo.R;
import com.odoo.addons.sale.models.AccountPaymentTerm;
import com.odoo.addons.sale.models.ProductProduct;
import com.odoo.addons.sale.models.ProductTemplate;
import com.odoo.addons.sale.models.SaleOrder;
import com.odoo.addons.sale.models.SalesOrderLine;
import com.odoo.core.orm.ODataRow;
import com.odoo.core.rpc.helper.ODomain;
import com.odoo.core.support.addons.fragment.BaseFragment;
import com.odoo.core.support.addons.fragment.IOnSearchViewChangeListener;
import com.odoo.core.support.addons.fragment.ISyncStatusObserverListener;
import com.odoo.core.support.drawer.ODrawerItem;
import com.odoo.core.support.list.IOnItemClickListener;
import com.odoo.core.support.list.OCursorListAdapter;
import com.odoo.core.utils.IntentUtils;
import com.odoo.core.utils.OAlert;
import com.odoo.core.utils.OControls;
import com.odoo.core.utils.OCursorUtils;
import com.odoo.core.utils.ODateUtils;
import com.odoo.core.utils.OResource;
import com.odoo.core.utils.controls.OBottomSheet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Sales extends BaseFragment implements
        OCursorListAdapter.OnViewBindListener, LoaderManager.LoaderCallbacks<Cursor>,
        SwipeRefreshLayout.OnRefreshListener, IOnSearchViewChangeListener,
        ISyncStatusObserverListener, IOnItemClickListener, View.OnClickListener,
        OBottomSheet.OSheetActionClickListener, OBottomSheet.OSheetItemClickListener {
    public static final String TAG = Sales.class.getSimpleName();
    public static final String KEY_MENU = "key_sales_menu";
    public Bundle dataGlob;
    private View mView;
    private ListView mList;
    private OCursorListAdapter mAdapter;
    private String mFilter = null;
    private Type mType = Type.Quotation;
    private Boolean mSyncRequested = false;

    public enum Type {
        Quotation,
        SaleOrder
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        mType = Type.valueOf(getArguments().getString(KEY_MENU));
        return inflater.inflate(R.layout.common_listview, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mView = view;
            // saleOrder.syncDataFromOdoo();
            //((SaleOrder) db()).syncDataFromOdoo();

/*
        Log.i(TAG, "Sync Starting: !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        if (inNetwork()) {
            ((SaleOrder) db()).syncDataFromOdoo();
        } else {
            Toast.makeText(getActivity(), R.string.toast_network_required, Toast.LENGTH_LONG).show();
        }

*/
        initAdapter();
    }

    private void initAdapter() {
        mList = (ListView) mView.findViewById(R.id.listview);
        mAdapter = new OCursorListAdapter(getActivity(), null, R.layout.sale_order_item);
        mAdapter.setOnViewBindListener(this);
        mList.setAdapter(mAdapter);
        mAdapter.handleItemClickListener(mList, this);
        setHasFloatingButton(mView, R.id.fabButton, mList, this);
        if (mType == Type.SaleOrder)
            mView.findViewById(R.id.fabButton).setVisibility(View.GONE);
        setHasSyncStatusObserver(TAG, this, db());
        setHasSwipeRefreshView(mView, R.id.swipe_container, this);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onViewBind(View view, Cursor cursor, ODataRow row) {
        OControls.setText(view, R.id.name, row.getString("name"));
        String format = (db().getUser().getOdooVersion().getVersionNumber() <= 7)
                ? ODateUtils.DEFAULT_DATE_FORMAT : ODateUtils.DEFAULT_FORMAT;
        String date = ODateUtils.convertToDefault(row.getString("date_order"),
                format, "MMMM, dd");
        OControls.setText(view, R.id.date_order, date);
        OControls.setText(view, R.id.state, row.getString("state_title"));

        if (row.getString("partner_name").equals("false")) {
            OControls.setGone(view, (R.id.partner_name));
        } else {
            OControls.setVisible(view, R.id.partner_name);
            OControls.setText(view, R.id.partner_name, row.getString("partner_name"));
        }
        OControls.setText(view, R.id.amount_total, row.getString("amount_total"));
        if (row.getString("currency_symbol").equals("false")) {
            OControls.setGone(view, (R.id.currency_symbol));
        } else {
            OControls.setVisible(view, R.id.currency_symbol);
            OControls.setText(view, R.id.currency_symbol, row.getString("currency_symbol"));
        }
        OControls.setText(view, R.id.order_lines, row.getString("order_line_count"));
    }

    @Override
    public List<ODrawerItem> drawerMenus(Context context) {
        List<ODrawerItem> menu = new ArrayList<>();
        menu.add(new ODrawerItem(TAG).setTitle(OResource.string(context, R.string.label_quotation))
                .setIcon(R.drawable.ic_action_quotation)
                .setInstance(new Sales())
                .setExtra(data(Type.Quotation)));
        menu.add(new ODrawerItem(TAG).setTitle(OResource.string(context, R.string.label_sale_orders))
                .setIcon(R.drawable.ic_action_sale_order)
                .setInstance(new Sales())
                .setExtra(data(Type.SaleOrder)));

        return menu;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle data) {
        String where = null;
        String[] whereArgs = null;
        List<String> args = new ArrayList<>();
        switch (mType) {
            case Quotation:
                where = " (state = ? or state = ? or state = ?)";
                args.addAll(Arrays.asList(new String[]{"draft", "sent", "cancel"}));
                break;
            case SaleOrder:
                where = "(state = ? or state = ? or state = ?)";
                args.addAll(Arrays.asList(new String[]{"manual", "sale",
                        "done"}));
                break;
        }
        if (mFilter != null) {
            where += " and (name like ? or partner_name like ? or state_title like ?)";
            args.add("%" + mFilter + "%");
            args.add("%" + mFilter + "%");
            args.add("%" + mFilter + "%");
        }
        whereArgs = args.toArray(new String[args.size()]);
        return new CursorLoader(getActivity(), db().uri(), null, where, whereArgs, "date_order DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.changeCursor(data);
        if (data.getCount() > 0) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    OControls.setGone(mView, R.id.loadingProgress);
                    OControls.setVisible(mView, R.id.swipe_container);
                    OControls.setGone(mView, R.id.data_list_no_item);
                    setHasSwipeRefreshView(mView, R.id.swipe_container, Sales.this);
                }
            }, 500);

        } else {
            if (db().isEmptyTable() && !mSyncRequested) {
                mSyncRequested = true;
                onRefresh();
            }
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    OControls.setGone(mView, R.id.loadingProgress);
                    OControls.setGone(mView, R.id.swipe_container);
                    OControls.setVisible(mView, R.id.data_list_no_item);
                    setHasSwipeRefreshView(mView, R.id.data_list_no_item, Sales.this);
                    OControls.setImage(mView, R.id.icon,
                            (mType == Type.Quotation) ? R.drawable.ic_action_quotation : R.drawable.ic_action_sale_order);
                    OControls.setText(mView, R.id.title, "No " + mType + " Found");
                    OControls.setText(mView, R.id.subTitle, "");
                }
            }, 500);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.changeCursor(null);
    }

    private Bundle data(Type type) {
        Bundle extra = new Bundle();
        extra.putString(KEY_MENU, type.toString());
        return extra;
    }

    @Override
    public Class<SaleOrder> database() {
        return SaleOrder.class;
    }


    @Override
    public void onRefresh() {
        if (inNetwork()) {
            parent().sync().requestSync(SaleOrder.AUTHORITY);
            setSwipeRefreshing(true);
        } else {
            hideRefreshingProgress();
            Toast.makeText(getActivity(), _s(R.string.toast_network_required), Toast.LENGTH_LONG)
                    .show();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
        inflater.inflate(R.menu.menu_sales_order, menu);
        setHasSearchView(this, menu, R.id.menu_sales_search);

    }

    @Override
    public boolean onSearchViewTextChange(String newFilter) {
        mFilter = newFilter;
        getLoaderManager().restartLoader(0, null, this);
        return true;
    }

    @Override
    public void onSearchViewClose() {
        //Nothing to do
    }

    @Override
    public void onStatusChange(Boolean refreshing) {
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void onItemDoubleClick(View view, int position) {
        onDoubleClick(position);
    }

    private void onDoubleClick(int position) {
        ODataRow row = OCursorUtils.toDatarow((Cursor) mAdapter.getItem(position));
        Bundle data = row.getPrimaryBundleData();
        data.putString("type", mType.toString());
        IntentUtils.startActivity(getActivity(), SalesDetail.class, data);
    }

    @Override
    public void onItemClick(View view, int position) {
        if (mType == Type.Quotation)
            showSheet((Cursor) mAdapter.getItem(position));
        else
            onDoubleClick(position);
    }

    private void showSheet(Cursor data) {
        OBottomSheet bottomSheet = new OBottomSheet(getActivity());
        bottomSheet.setData(data);
        bottomSheet.setSheetTitle(data.getString(data.getColumnIndex("name")));
        bottomSheet.setActionIcon(R.drawable.ic_action_edit, this);
        bottomSheet.setSheetItemClickListener(this);

        if (data.getString(data.getColumnIndex("state")).equals("cancel"))
            bottomSheet.setSheetActionsMenu(R.menu.menu_quotation_cancel_sheet);
        else
            bottomSheet.setSheetActionsMenu(R.menu.menu_quotation_sheet);
        bottomSheet.show();
    }

    @Override
    public void onSheetItemClick(OBottomSheet sheet, MenuItem item, Object data) {
        sheet.dismiss();
        ODataRow row = OCursorUtils.toDatarow((Cursor) data);
        switch (item.getItemId()) {
            case R.id.menu_quotation_cancel:
                ((SaleOrder) db()).cancelOrder(mType, row, cancelOrder);
                break;
            case R.id.menu_quotation_new:
                if (inNetwork()) {
                    ((SaleOrder) db()).newCopyQuotation(row, newCopyQuotation);
                } else {
                    Toast.makeText(getActivity(), R.string.toast_network_required, Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.menu_so_confirm_sale:
                if (row.getFloat("amount_total") > 0) {
                    if (inNetwork()) {
                        ((SaleOrder) db()).confirmSale(row, confirmSale);
                    } else {
                        Toast.makeText(getActivity(), R.string.toast_network_required, Toast.LENGTH_LONG).show();
                    }
                } else {
                    OAlert.showWarning(getActivity(), "You cannot a sales order which has no line");
                }
                break;
        }
    }

    @Override
    public void onSheetActionClick(OBottomSheet sheet, Object data) {
        sheet.dismiss();
        ODataRow row = OCursorUtils.toDatarow((Cursor) data);
        Bundle extras = row.getPrimaryBundleData();
        extras.putString("type", mType.toString());
        IntentUtils.startActivity(getActivity(), SalesDetail.class, extras);
    }

    SaleOrder.OnOperationSuccessListener cancelOrder = new SaleOrder.OnOperationSuccessListener() {
        @Override
        public void OnSuccess() {
            Toast.makeText(getActivity(), mType + " cancelled", Toast.LENGTH_LONG).show();
        }

        @Override
        public void OnCancelled() {
        }
    };
    SaleOrder.OnOperationSuccessListener confirmSale = new SaleOrder.OnOperationSuccessListener() {
        @Override
        public void OnSuccess() {
            Toast.makeText(getActivity(), "Quotation confirmed !", Toast.LENGTH_LONG).show();
        }

        @Override
        public void OnCancelled() {
        }
    };
    SaleOrder.OnOperationSuccessListener newCopyQuotation = new SaleOrder.OnOperationSuccessListener() {
        @Override
        public void OnSuccess() {
            Toast.makeText(getActivity(), R.string.label_copy_quotation, Toast.LENGTH_LONG).show();
        }

        @Override
        public void OnCancelled() {
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fabButton:
                Bundle bundle = new Bundle();
                bundle.putString("type", Type.Quotation.toString());
                IntentUtils.startActivity(getActivity(), SalesDetail.class, bundle);
                break;
        }
    }


}
