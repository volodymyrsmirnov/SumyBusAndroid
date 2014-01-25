package com.mindcollapse.sumybus;

import android.content.Context;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;

public class RoutesAdapter extends ArrayAdapter<Route> {
    private final Context context;
    private final ArrayList<Route> routesList;
    private SparseIntArray routesCars;

    public RoutesAdapter(Context context, ArrayList<Route> routesList) {
        super(context, R.layout.route_item, routesList);

        this.context = context;
        this.routesList = routesList;
    }

    public void setRoutesCars(SparseIntArray routesCars) {
        this.routesCars = routesCars;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.route_item, parent, false);

        Route route = routesList.get(position);

        TextView routeName = (TextView) rowView.findViewById(R.id.route_name);
        TextView routeDescription = (TextView) rowView.findViewById(R.id.route_description);

        ProgressBar routeCarsProgressBar = (ProgressBar) rowView.findViewById(R.id.route_progress);
        TextView routeCarsNumber = (TextView) rowView.findViewById(R.id.route_cars_number);

        if (routesCars == null || routesCars.size() == 0 ) {
            routeCarsNumber.setVisibility(View.INVISIBLE);
            routeCarsProgressBar.setVisibility(View.VISIBLE);
        } else {
            routeCarsNumber.setVisibility(View.VISIBLE);
            routeCarsProgressBar.setVisibility(View.INVISIBLE);

            if (routesCars.indexOfKey(route.getId()) >= 0) {
                routeCarsNumber.setText(String.valueOf(routesCars.get(route.getId())));
            } else {
                routeCarsNumber.setText("0");
            }
        }

        routeName.setText(route.getName());
        routeDescription.setText(route.getDescription());

        rowView.setTag(routesList.get(position));

        return rowView;
    }

    @Override
    public boolean isEnabled(int arg0) {
        return true;
    }
}

