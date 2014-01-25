package com.mindcollapse.sumybus;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import com.yandex.metrica.Counter;

import java.util.HashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

public class MapActivity extends FragmentActivity {
    Boolean routeLoaded = false;
    private Route route;
    private GoogleMap map;
    private ProgressDialog progress;
    private AlertDialog alert;
    private int internalRouteId = 0;
    private Handler handler;
    private Runnable runnable;
    private HashMap<String, Marker> cars;
    private static String apiURI = "http://sumy.gps-tracker.com.ua/mash.php";

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

        if (alert != null) {
            alert.dismiss();
        }

        if (handler != null) {
            handler.removeMessages(0);
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

        final Activity selfActivity = this;

        progress = new ProgressDialog(this);
        progress.setCancelable(false);
        progress.setButton(DialogInterface.BUTTON_NEGATIVE, this.getString(R.string.close), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                selfActivity.finish();
            }
        });

        runnable = new Runnable() {
            @Override
            public void run() {
                getRouteCars();
            }
        };

        TextView routeName = (TextView) findViewById(R.id.map_route_name);
        routeName.setText(route.getDescription());

        View contentView = this.findViewById(android.R.id.content);
        ViewTreeObserver viewTree = null;

        if (contentView != null) {
            viewTree = contentView.getViewTreeObserver();
        }

        Ion.getDefault(this).configure()
                .setLogging("MyLogs", Log.VERBOSE)
                .userAgent("SumyBus (android, +http://j.mp/sumybus_android)");

        if (viewTree != null) {
            viewTree.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (map == null) {
                        map = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();

                        if (map != null) {
                            map.getUiSettings().setRotateGesturesEnabled(false);
                            map.getUiSettings().setTiltGesturesEnabled(false);

                            map.setBuildingsEnabled(false);
                            map.setMyLocationEnabled(true);
                            map.setIndoorEnabled(false);
                            map.setTrafficEnabled(false);

                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(50.91, 34.8), 12));

                            getRouteInformation();
                        }
                    }
                }
            });
        }
    }

    private void getRouteInformation() {
        progress.setMessage(this.getString(R.string.map_loading_route_info));
        progress.show();

        Ion.with(this, apiURI)
                .addQuery("act", "marw")
                .addQuery("id", Integer.toString(route.getId()))
                .asJsonArray()
                .setCallback(new FutureCallback<JsonArray>() {
                    @Override
                    public void onCompleted(Exception responseError, JsonArray result) {
                        if (responseError != null && responseError instanceof com.google.gson.JsonParseException) {
                            showResponseError(2);
                        }
                        else if (responseError != null || result == null || result.size() == 0 || !result.get(0).getAsJsonObject().has("id")) {
                            showResponseError(1);
                        } else {
                            internalRouteId = result.get(0).getAsJsonObject().get("id").getAsInt();
                            getRoutePath();
                        }
                    }
                });
    }


    private void getRoutePath() {
        progress.setMessage(this.getString(R.string.map_loading_route_path));

        Ion.with(this, apiURI)
                .addQuery("act", "path")
                .addQuery("id", Integer.toString(route.getId()))
                .addQuery("mar", Integer.toString(internalRouteId))
                .asJsonArray()
                .setCallback(new FutureCallback<JsonArray>() {
                    @Override
                    public void onCompleted(Exception responseError, JsonArray result) {
                        if (responseError != null || result == null || result.size() == 0) {
                            showResponseError(1);
                        } else {
                            PolylineOptions routeTo = new PolylineOptions().color(getResources().getColor(R.color.route_color_to)).width(5);
                            PolylineOptions routeFrom = new PolylineOptions().color(getResources().getColor(R.color.route_color_to)).width(5);

                            LatLngBounds.Builder routeBounds = new LatLngBounds.Builder();

                            for (JsonElement routePoint : result) {
                                JsonObject routePointObject = routePoint.getAsJsonObject();

                                if (!routePointObject.has("lng") || !routePointObject.has("lat") || !routePointObject.has("direction")) {
                                    showResponseError(1);
                                    return;
                                } else {
                                    LatLng coordinatePoint = new LatLng(routePointObject.get("lng").getAsDouble(), routePointObject.get("lat").getAsDouble());

                                    routeBounds.include(coordinatePoint);

                                    if (routePointObject.get("direction").getAsString().equals("t")) {
                                        routeTo.add(coordinatePoint);
                                    } else {
                                        routeFrom.add(coordinatePoint);
                                    }
                                }

                            }

                            map.addPolyline(routeTo);
                            map.addPolyline(routeFrom);

                            map.moveCamera(CameraUpdateFactory.newLatLngBounds(routeBounds.build(), 5));

                            getRouteStops();
                        }
                    }
                });
    }

    private void getRouteStops() {
        progress.setMessage(this.getString(R.string.map_loading_route_stops));

        Ion.with(this, apiURI)
                .addQuery("act", "stops")
                .addQuery("id", Integer.toString(route.getId()))
                .addQuery("mar", Integer.toString(internalRouteId))
                .asJsonArray()
                .setCallback(new FutureCallback<JsonArray>() {
                    @Override
                    public void onCompleted(Exception responseError, JsonArray result) {
                        if (responseError != null || result == null || result.size() == 0) {
                            showResponseError(1);
                        } else {
                            for (JsonElement routeStop : result) {
                                JsonObject routeStopObject = routeStop.getAsJsonObject();

                                if (!routeStopObject.has("lng") || !routeStopObject.has("lat") || !routeStopObject.has("name")) {
                                    showResponseError(1);
                                } else {
                                    LatLng routeStopCoordinate = new LatLng(routeStopObject.get("lng").getAsDouble(), routeStopObject.get("lat").getAsDouble());

                                    map.addMarker(new MarkerOptions()
                                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_stop))
                                            .position(routeStopCoordinate)
                                            .title(routeStopObject.get("name").getAsString())
                                            .flat(true));
                                }
                            }

                            getRouteCars();
                        }
                    }
                });
    }

    private double coordinateStringToDouble(JsonObject routeCarObject, String keyName) {
        JsonElement key = routeCarObject.get(keyName);

        if (key.isJsonNull()) {
            return 0;
        } else {
            try {
                return key.getAsDouble();
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    private void getRouteCars() {
        if (progress.isShowing()) {
            progress.setMessage(this.getString(R.string.map_loading_route_cars));
        }

        Ion.with(this, apiURI)
                .noCache()
                .addQuery("act", "cars")
                .addQuery("id", Integer.toString(route.getId()))
                .addQuery("nc", Long.toHexString(Double.doubleToLongBits(Math.random())))
                .asJsonObject()
                .setCallback(new FutureCallback<JsonObject>() {
                    @Override
                    public void onCompleted(Exception responseError, JsonObject result) {
                        if (responseError != null || result == null || !result.has("rows")) {
                            if (!routeLoaded) {
                                showResponseError(1);
                            }
                        } else {
                            JsonArray routeCarsRows = result.getAsJsonArray("rows");

                            routeLoaded = true;

                            for (JsonElement routeCar : routeCarsRows) {
                                JsonObject routeCarObject = routeCar.getAsJsonObject();

                                if (!routeCarObject.has("X") || !routeCarObject.has("Y") || !routeCarObject.has("pX") ||
                                        !routeCarObject.has("pY") || !routeCarObject.has("inzone") || !routeCarObject.has("color")) {
                                    if (!routeLoaded) {
                                        showResponseError(1);
                                    }
                                } else {
                                    String routeCarID = routeCarObject.get("CarId").getAsString();

                                    double routeCarLat = coordinateStringToDouble(routeCarObject, "X");
                                    double routeCarLng = coordinateStringToDouble(routeCarObject, "Y");
                                    double routeCarPLat = coordinateStringToDouble(routeCarObject, "pX");
                                    double routeCarPLng = coordinateStringToDouble(routeCarObject, "pY");

                                    double routeCarAngle = 90 - (Math.atan2(routeCarLat - routeCarPLat, routeCarLng - routeCarPLng) / Math.PI) * 180;

                                    if (!cars.containsKey(routeCarID)) {
                                        cars.put(routeCarID, map.addMarker(new MarkerOptions()
                                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_bus))
                                                .position(new LatLng(routeCarLat, routeCarLng))
                                                .rotation(Double.valueOf(routeCarAngle).floatValue())
                                                .flat(true)));
                                    } else {
                                        cars.get(routeCarID).setPosition(new LatLng(routeCarLat, routeCarLng));
                                        cars.get(routeCarID).setRotation(Double.valueOf(routeCarAngle).floatValue());
                                    }

                                    if (routeCarLat == 0 || routeCarLng == 0 || routeCarPLat == 0 || routeCarPLng == 0 ||
                                            routeCarObject.get("inzone").getAsString().equals("f") || routeCarObject.get("color").getAsString().equals("#555555") ||
                                            routeCarLat == 10000 || routeCarLng == 10000) {
                                        cars.get(routeCarID).setVisible(false);
                                    } else {
                                        cars.get(routeCarID).setVisible(true);
                                    }
                                }

                                if (progress.isShowing()) {
                                    progress.hide();
                                }
                            }

                            handler.postDelayed(runnable, 10000);
                        }
                    }
                });
    }

    private void showResponseError(int reason) {
        if (this.isFinishing() || (alert != null && alert.isShowing())) {
            return;
        }

        String reasonText = "";

        if (reason == 1) {
            reasonText = this.getString(R.string.error_connection_problem);
        } else if (reason == 2) {
            reasonText = this.getString(R.string.error_route_no_gps);
        }

        progress.hide();

        final Activity selfActivity = this;

        alert = new AlertDialog.Builder(this).setMessage(reasonText).setCancelable(false).setPositiveButton(R.string.close,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        alert.dismiss();
                        selfActivity.finish();
                    }
                }
        ).create();

        try {
            alert.show();
        } catch (WindowManager.BadTokenException e) {
            this.finish();
        }
    }
}
