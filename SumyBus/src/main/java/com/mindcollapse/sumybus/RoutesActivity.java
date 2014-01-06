package com.mindcollapse.sumybus;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.dd.plist.*;

import java.util.Arrays;
import java.util.ArrayList;

public class RoutesActivity extends Activity {
    private ProgressDialog progress;

    @Override
    protected void onResume() {
        super.onResume();

        if (progress != null && progress.isShowing()) {
            progress.hide();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_routes);

        RoutesAdapter adapter = new RoutesAdapter(this, getRoutes());

        ListView listView = (ListView) findViewById(R.id.routes_list);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(routeClickListener);

        progress = new ProgressDialog(this);
        progress.setMessage(this.getString(R.string.map_loading_window));
        progress.setCancelable(false);
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
                NSDictionary route = (NSDictionary)routesPlist.get(routeName);

                Route newRoute = new Route(((NSNumber) route.get("id")).intValue(), routeName, ((NSString) route.get("name")).toString());

                routes.add(newRoute);
            }

        } catch(Exception ex) {
            ex.printStackTrace();
        }

        return routes;
    }
}
