package com.mindcollapse.sumybus;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Window;
import android.widget.TextView;
import android.os.Handler;

import org.json.*;
import java.util.HashMap;

import com.turbomanage.httpclient.android.AndroidHttpClient;
import com.turbomanage.httpclient.AsyncCallback;
import com.turbomanage.httpclient.HttpResponse;
import com.turbomanage.httpclient.ParameterMap;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.yandex.metrica.Counter;

public class MapActivity extends FragmentActivity {
    Boolean routeLoaded = false;
    private Route route;
    private GoogleMap map;
    private ProgressDialog progress;
    private AndroidHttpClient httpClient;
    private int internalRouteId = 0;
    private Handler handler;
    private Runnable runnable;
    private HashMap<String, Marker> cars;

    @Override
    protected void onResume() {
        super.onResume();

        if (routeLoaded) {
            getRouteCars();
        }

        Counter.sharedInstance().onResumeActivity(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        progress.dismiss();

        if (runnable != null && handler != null) {
            handler.removeCallbacks(runnable);
        }

        Counter.sharedInstance().onPauseActivity(this);
    }

    // hotfix google maps error on cheap china phones
    // http://stackoverflow.com/a/20905954/2075875
    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        try {
            super.startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_map);

        route = (Route) getIntent().getSerializableExtra("route");

        cars = new HashMap<String, Marker>();
        handler = new Handler();

        Activity topActivity = this;
        final Activity selfActivity = this;

        while(topActivity.getParent() != null) {
            topActivity = topActivity.getParent();
        }

        progress = new ProgressDialog(topActivity);
        progress.setCancelable(false);
        progress.setButton(DialogInterface.BUTTON_NEGATIVE, this.getString(R.string.close), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();

                selfActivity.finish();
            }
        });


        httpClient = new AndroidHttpClient("http://sumy.gps-tracker.com.ua/");

        runnable = new Runnable() {
            @Override
            public void run() {
                getRouteCars();
            }
        };

        TextView routeName = (TextView) findViewById(R.id.map_route_name);
        routeName.setText(route.getDescription());

        map = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();

        if (map != null) {

            map.getUiSettings().setRotateGesturesEnabled(false);
            map.getUiSettings().setTiltGesturesEnabled(false);

            map.setBuildingsEnabled(false);
            map.setMyLocationEnabled(true);
            map.setIndoorEnabled(false);
            map.setTrafficEnabled(false);

            try {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(50.91, 34.8), 12));
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }

            if (!checkInternetConnection()) {
                showResponseError(1);
            } else {
                getRouteInformation();
            }
        }
    }

    private double coordStringToDouble(String coord) {
        if (coord.length() == 0 || coord.equals("null")) {
            return 0;
        } else {
            return Double.parseDouble(coord);
        }
    }

    private void getRouteCarsDelayed() {
        handler.postDelayed(runnable, 20000);
    }

    private void getRouteCars() {
        if (progress.isShowing()) {
            progress.setMessage(this.getString(R.string.map_loading_route_cars));
        }

        ParameterMap params = httpClient.newParams()
                .add("act", "cars")
                .add("id", Integer.toString(route.getId()));

        httpClient.get("mash.php", params, new AsyncCallback() {
            @Override
            public void onComplete(HttpResponse httpResponse) {
                try {
                    JSONArray responseArray;

                    try {
                        responseArray = new JSONObject(httpResponse.getBodyAsString().replace("\uFEFF", "")).getJSONArray("rows");
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                        showResponseError(3);
                        return;
                    }

                    routeLoaded = true;

                    for (int i = 0; i < responseArray.length(); i++) {
                        JSONObject car = responseArray.getJSONObject(i);

                        String carId = car.getString("CarId");

                        double carLat = coordStringToDouble(car.getString("X"));
                        double carLng = coordStringToDouble(car.getString("Y"));
                        double carPLat = coordStringToDouble(car.getString("pX"));
                        double carPLng = coordStringToDouble(car.getString("pY"));

                        double carAngle = 90 - (Math.atan2(carLat - carPLat, carLng - carPLng) / Math.PI) * 180;

                        if (!cars.containsKey(carId)) {
                            cars.put(carId, map.addMarker(new MarkerOptions()
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_bus))
                                    .position(new LatLng(carLat, carLng))
                                    .rotation(Double.valueOf(carAngle).floatValue())
                                    .flat(true)));
                        } else {
                            cars.get(carId).setPosition(new LatLng(carLat, carLng));
                            cars.get(carId).setRotation(Double.valueOf(carAngle).floatValue());
                        }


                        if ( carLat == 0 || carLng == 0 || carPLat == 0 || carPLng == 0 ||
                             car.getString("inzone").equals("f") ||
                             car.getString("color").equals("#555555") ||
                             carLat == 10000 || carLng == 10000) {
                            cars.get(carId).setVisible(false);
                        } else {
                            cars.get(carId).setVisible(true);
                        }
                    }

                    if (progress.isShowing()) {
                        progress.hide();
                    }

                } catch (JSONException e){
                    e.printStackTrace();
                } finally {
                    getRouteCarsDelayed();
                }
            }

            @Override
            public void onError(Exception e) {
                e.printStackTrace();

                getRouteCarsDelayed();
            }
        });
    }

    private void getRouteStops() {
        progress.setMessage(this.getString(R.string.map_loading_route_stops));

        ParameterMap params = httpClient.newParams()
                .add("act", "stops")
                .add("id", Integer.toString(route.getId()))
                .add("mar", Integer.toString(internalRouteId));

        httpClient.get("mash.php", params, new AsyncCallback() {
            @Override
            public void onComplete(HttpResponse httpResponse) {
                try {
                    JSONArray responseArray;

                    try {
                        responseArray = new JSONArray(httpResponse.getBodyAsString().replace("\uFEFF", ""));
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                        showResponseError(3);
                        return;
                    }

                    for (int i=0; i<responseArray.length(); i++) {
                        JSONObject stop = responseArray.getJSONObject(i);

                        LatLng stopPoint = new LatLng(stop.getDouble("lng"), stop.getDouble("lat"));

                        map.addMarker(new MarkerOptions()
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_stop))
                                .position(stopPoint)
                                .title(stop.getString("name"))
                                .flat(true));

                    }

                    getRouteCars();

                } catch (JSONException e){
                    e.printStackTrace();

                    showResponseError(3);
                }
            }

            @Override
            public void onError(Exception e) {
                e.printStackTrace();

                showResponseError(3);
            }
        });
    }


    private void getRoutePath() {
        progress.setMessage(this.getString(R.string.map_loading_route_path));

        ParameterMap params = httpClient.newParams()
                .add("act", "path")
                .add("id", Integer.toString(route.getId()))
                .add("mar", Integer.toString(internalRouteId));

        httpClient.get("mash.php", params, new AsyncCallback() {
            @Override
            public void onComplete(HttpResponse httpResponse) {
                try {
                    JSONArray responseArray;

                    try {
                        responseArray = new JSONArray(httpResponse.getBodyAsString().replace("\uFEFF", ""));
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                        showResponseError(3);
                        return;
                    }

                    PolylineOptions routeTo = new PolylineOptions().color(getResources().getColor(R.color.route_color_to)).width(5);
                    PolylineOptions routeFrom = new PolylineOptions().color(getResources().getColor(R.color.route_color_to)).width(5);

                    LatLngBounds.Builder routeBounds = new LatLngBounds.Builder();

                    for (int i=0; i<responseArray.length(); i++) {
                        JSONObject coordinate = responseArray.getJSONObject(i);

                        LatLng coordinatePoint = new LatLng(coordinate.getDouble("lng"), coordinate.getDouble("lat"));

                        if (coordinate.getString("direction").equals("t")) {
                            routeTo.add(coordinatePoint);
                        } else {
                            routeFrom.add(coordinatePoint);
                        }

                        routeBounds.include(coordinatePoint);
                    }

                    map.addPolyline(routeTo);
                    map.addPolyline(routeFrom);

                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(routeBounds.build(), 5));

                    getRouteStops();

                } catch (JSONException e){
                    e.printStackTrace();

                    showResponseError(3);
                }
            }

            @Override
            public void onError(Exception e) {
                e.printStackTrace();

                showResponseError(3);
            }
        });
    }

    private void getRouteInformation() {
        progress.setMessage(this.getString(R.string.map_loading_route_info));
        progress.show();

        ParameterMap params = httpClient.newParams()
                .add("act", "marw")
                .add("id", Integer.toString(route.getId()));

        httpClient.get("mash.php", params, new AsyncCallback() {
            @Override
            public void onComplete(HttpResponse httpResponse) {
                try {
                    JSONArray responseArray;

                    try {
                        responseArray = new JSONArray(httpResponse.getBodyAsString().replace("\uFEFF", ""));
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                        showResponseError(3);
                        return;
                    }

                    if (responseArray.length() > 0) {
                        JSONObject routeInformation = responseArray.getJSONObject(0);

                        if (routeInformation.has("id")) {
                            internalRouteId = routeInformation.getInt("id");
                        }
                    }

                    if (internalRouteId == 0) {
                        showResponseError(2);
                    } else {
                        getRoutePath();
                    }
                } catch (JSONException e){
                    e.printStackTrace();

                    showResponseError(2);
                }
            }

            @Override
            public void onError(Exception e) {
                e.printStackTrace();

                showResponseError(3);
            }
        });
    }

    private Boolean checkInternetConnection() {
        ConnectivityManager connectionManager = (ConnectivityManager) getSystemService (Context.CONNECTIVITY_SERVICE);

        return (connectionManager.getActiveNetworkInfo() != null
                && connectionManager.getActiveNetworkInfo().isAvailable()
                && connectionManager.getActiveNetworkInfo().isConnected());
    }

    private void showResponseError(int reason) {
        if (this.isFinishing()) {
            return;
        }

        String reasonText = "";

        if (reason == 1) {
            reasonText = this.getString(R.string.map_loading_error_no_internet);
        }
        else if (reason == 2) {
            reasonText = this.getString(R.string.map_loading_error_empty);
        }
        else if (reason == 3) {
            reasonText = this.getString(R.string.map_loading_error_content);
        }

        progress.hide();

        Activity topActivity = this;
        final MapActivity selfActivity = this;

        while(topActivity.getParent() != null) {
            topActivity = topActivity.getParent();
        }

        new AlertDialog.Builder(topActivity).setMessage(reasonText).setCancelable(false).setPositiveButton(R.string.close,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        selfActivity.finish();
                    }
                }
        ).show();
    }
}
