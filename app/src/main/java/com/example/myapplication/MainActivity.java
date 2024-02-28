package com.example.myapplication;

import static com.mapbox.mapboxsdk.style.layers.Property.NONE;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconSize;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.visibility;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.Layer;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapboxMap mapboxMap) {
                mapboxMap.setStyle(Style.OUTDOORS, new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(Style style) {
                        // Hiding unnecessary layers reduces system usage when zooming the map.

                        hideLayerById(style, "transit-station-label");
                        for (Layer layer : style.getLayers()) {
                            if(layer.getId().contains("building")||layer.getId().contains("transit-label")
                                    ||layer.getId().contains("shadow")||layer.getId().contains("contour-line" )
                                    ||layer.getId().contains("cliff")||layer.getId().contains("hillshade")){
                                hideLayerById(style,layer.getId());
                                //System.out.println(layer.getId()+" hidden");
                            }
                            //System.out.println("Layer ID: " + layer.getId() + ", Type: " + layer.getClass().getSimpleName());
                        }

                        CameraPosition position = new CameraPosition.Builder()
                                .target(new LatLng(-41.2864603, 174.776236))
                                .zoom(13)
                                .build();
                        mapboxMap.setCameraPosition(position);
                        fetchVehiclesData(mapboxMap,style); // Calling the method that gets the data

                    }
                });

            }
        });
    }

    //layer hidden method
    private void hideLayerById(@NonNull Style style, String layerId) {
        Layer layer = style.getLayer(layerId);
        if (layer != null) {
            layer.setProperties(visibility(NONE));
        }
    }
    // fetch vehicles data
    private void fetchVehiclesData(MapboxMap mapboxMap, Style style) {
        executorService.execute(() -> {
            try {
                URL url = new URL("https://api.mevo.co.nz/public/vehicles/wellington");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    System.out.println(response.toString());
                    //System.out.println(1);

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONObject data = jsonResponse.getJSONObject("data");
                    JSONArray features = data.getJSONArray("features");
                    for (int i = 0; i < features.length(); i++) {
                        JSONObject feature = features.getJSONObject(i);
                        JSONObject geometry = feature.getJSONObject("geometry");
                        JSONArray coordinates = geometry.getJSONArray("coordinates");
                        String longitudeStr = coordinates.getString(0);
                        String latitudeStr = coordinates.getString(1);
                        String id = latitudeStr+latitudeStr;
                        double longitude = Double.parseDouble(longitudeStr);
                        double latitude = Double.parseDouble(latitudeStr);
                        String urlStr = feature.getJSONObject("properties").getString("iconUrl");
                        URL iconUrl = new URL(urlStr);
                        LatLng carLocation = new LatLng(latitude, longitude);

                        //System.out.println(urlStr);
                        //System.out.println("Longitude: " + longitude + ", Latitude: " + latitude);

                        addIconToMap(style,iconUrl,longitude,latitude,id);

                    }
                } else {
                    System.err.println("Error: HTTP response code " + connection.getResponseCode());
                }
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    //Define a cache of bitmap at the class level fro reusing
    private Map<String, Bitmap> bitmapCache = new ConcurrentHashMap<>();
    private void addIconToMap(@NonNull Style style, URL iconUrl, Double longitude, Double latitude, String id) {
        executorService.execute(() -> {
            try {
                Bitmap iconBitmap;
                String iconUrlString = iconUrl.toString();

                // Check if the Bitmap already exists in the cache
                if (bitmapCache.containsKey(iconUrlString)) {
                    iconBitmap = bitmapCache.get(iconUrlString);
                } else {
                    // Downloading Bitmap from the Web
                    iconBitmap = BitmapFactory.decodeStream(iconUrl.openConnection().getInputStream());
                    // Add the downloaded Bitmap to the cache
                    bitmapCache.put(iconUrlString, iconBitmap);
                }

                // UI-related code must be run in the main thread
                final Bitmap finalIconBitmap = iconBitmap; // Because to use a lambda expression in a
                runOnUiThread(() -> {
                    // Adding icons to the map
                    style.addImage(id, finalIconBitmap);
                    // Create GeoJsonSource and add to map
                    addGeoJsonSourceToMap(style, longitude, latitude, id);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    private void addGeoJsonSourceToMap(@NonNull Style style, Double longitude, Double latitude, String id) {
        // Creating GeoJSON strings with variables
        String geoJsonString = String.format(
                "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%f, %f]}}]}",
                longitude, latitude);

        // Creating a GeoJsonSource with a constructed GeoJSON string
        GeoJsonSource source = new GeoJsonSource(id, geoJsonString);
        style.addSource(source);

        SymbolLayer symbolLayer = new SymbolLayer(id, id);
        symbolLayer.withProperties(iconImage(id), iconSize(1.0f));
        style.addLayer(symbolLayer);
    }


    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}
