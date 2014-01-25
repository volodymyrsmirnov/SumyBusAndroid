package com.mindcollapse.sumybus;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.PropertyListParser;
import com.github.jeremiemartinez.refreshlistview.RefreshListView;
import com.sbstrm.appirater.Appirater;
import com.yandex.metrica.Counter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

public class RoutesActivity extends Activity {
    private ProgressDialog progress;
    private RoutesAdapter adapter;
    private RefreshListView listView;
    private static String routesCarsURL = "http://apps.mindcollapse.com/sumy-bus/routes.json";
    private SparseIntArray routesCars;

    @Override
    protected void onResume() {
        super.onResume();

        Counter.sharedInstance().onResumeActivity(this);

        if (progress != null && progress.isShowing()) {
            progress.hide();
        }


        loadRoutesCars();
    }

    @Override
    protected void onPause() {
        super.onPause();

        progress.dismiss();

        Counter.sharedInstance().onPauseActivity(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Counter.initialize(getApplicationContext());

        setContentView(R.layout.activity_routes);

        Appirater.appLaunched(this);

        adapter = new RoutesAdapter(this, getRoutes());

        listView = (RefreshListView) findViewById(R.id.routes_list);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(routeClickListener);

        progress = new ProgressDialog(this);
        progress.setMessage(this.getString(R.string.map_loading_window));
        progress.setCancelable(false);

        routesCars = new SparseIntArray();

        listView.setRefreshListener(new RefreshListView.OnRefreshListener() {

            @Override
            public void onRefresh(RefreshListView listView) {
                loadRoutesCars();
            }
        });

        listView.setEnabledDate(true, new Date());

        loadRoutesCars();
}

    private void loadRoutesCars(){
        routesCars.clear();

        adapter.setRoutesCars(routesCars);
        adapter.notifyDataSetChanged();

        Ion.getDefault(this).cancelAll();

        Ion.with(this, routesCarsURL)
                .noCache()
                .addQuery("nc", Long.toHexString(Double.doubleToLongBits(Math.random())))
                .asJsonArray()
                .setCallback(new FutureCallback<JsonArray>() {
                    @Override
                    public void onCompleted(Exception responseError, JsonArray result) {
                        if (responseError != null) {
                            responseError.printStackTrace();
                        } else {
                            for (JsonElement resultRow : result) {
                                JsonObject routeInfo = resultRow.getAsJsonObject();
                                routesCars.put(routeInfo.get("id").getAsInt(), routeInfo.get("cars").getAsInt());
                            }

                            adapter.setRoutesCars(routesCars);
                            adapter.notifyDataSetChanged();

                            listView.finishRefreshing();
                        }
                    }
                });
    }

    private AdapterView.OnItemClickListener routeClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            Route routeClicked = (Route) view.getTag();

            Intent showRouteMap = new Intent(getApplicationContext(), MapActivity.class);
            showRouteMap.putExtra("route", routeClicked);

            progress.show();

            startActivity(showRouteMap);
        }
    };

    private ArrayList<Route> getRoutes() {
        ArrayList<Route> routes = new ArrayList<Route>();

        try {
            NSDictionary routesPlist = (NSDictionary) PropertyListParser.parse(getResources().openRawResource(R.raw.routes));

            String[] routeNames = routesPlist.allKeys();
            Arrays.sort(routeNames);

            for (String routeName : routeNames) {
                NSDictionary route = (NSDictionary) routesPlist.get(routeName);

                Route newRoute = new Route(((NSNumber) route.get("id")).intValue(), routeName, route.get("name").toString());

                routes.add(newRoute);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return routes;
    }
}
