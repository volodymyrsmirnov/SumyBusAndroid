package com.mindcollapse.sumybus;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class RoutesAdapter extends ArrayAdapter<Route> {
    private final Context context;
    private final ArrayList<Route> routesList;

    public RoutesAdapter(Context context, ArrayList<Route> routesList) {
        super(context, R.layout.route_item, routesList);

        this.context = context;
        this.routesList = routesList;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.route_item, parent, false);

        TextView routeName = (TextView) rowView.findViewById(R.id.route_name);
        TextView routeDescription = (TextView) rowView.findViewById(R.id.route_description);

        routeName.setText(routesList.get(position).getName());
        routeDescription.setText(routesList.get(position).getDescription());

        rowView.setTag(routesList.get(position));

        return rowView;
    }

    @Override
    public boolean isEnabled(int arg0) {
        return true;
    }
}

