package edu.umb.cs443;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static java.lang.System.currentTimeMillis;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;


public class MainActivity extends FragmentActivity implements OnMapReadyCallback{

    public final static String DEBUG_TAG="edu.umb.cs443.MYMSG";

    private final String MBTA_URL_START = "https://api-v3.mbta.com/";
    private final String MBTA_API_KEY = "6c46d81ddbeb4280a1b08c417a24d287";

    /*
    routes.get(0) : trolley routes
    routes.get(1) : subway routes
    routes.get(2) : commuter rail routes
    routes.get(3) : bus routes
    routes.get(4) : ferry routes
     */
    private ArrayList<JSONArray> routes;
    private final int NUM_ROUTE_TYPES = 5;

    private HashMap<String, JSONObject> stations;
    private HashMap<String, String> stationIDs;

    MyRecyclerViewAdapter adapter;

    private String[] predictions;

    private final int MAX_ROWS = 100;

    private GoogleMap mMap;
    private Marker mSearched;

    private long a = 0, b = 0, c = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(DEBUG_TAG, "starting");
        a = currentTimeMillis();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MapFragment mFragment=((MapFragment) getFragmentManager().findFragmentById(R.id.map));
        mFragment.getMapAsync(this);

        EditText edittext=(EditText)findViewById(R.id.editText);
        edittext.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    // Perform action on key press
                    ok();
                    return true;
                }
                return false;
            }
        });

        Log.i(DEBUG_TAG, "" + (currentTimeMillis() - a));
        routes = new ArrayList<JSONArray>();
        stations = new HashMap<String, JSONObject>();
        predictions = new String[MAX_ROWS * 3];
        predictions[0] = "LINE";
        predictions[1] = "DESTINATION";
        predictions[2] = "ETA";
        Log.i(DEBUG_TAG, "" + (currentTimeMillis() - a));
        setRecyclerViewLayout(predictions);
        Log.i(DEBUG_TAG, "" + (currentTimeMillis() - a));
        initializeStationNameMap();
        if (checkConnection()) {
            /*
            String query = "routes?filter[type]=1";
            new DownloadRoutesTask().execute(query);*/
            String[] queries = new String[NUM_ROUTE_TYPES];
            for (int i = 0; i < NUM_ROUTE_TYPES; i++) {
                //queries[i] = "routes?filter[type]=" + i;
                new DownloadRoutesTask().execute("routes?filter[type]=" + i);
            }
            //new DownloadRoutesTask().execute(queries);

        }
        else {
            Log.e(DEBUG_TAG, "No network connection");
        }
    }

    private class DownloadRoutesTask extends AsyncTask<String, Void, JSONArray> {
        @Override
        protected JSONArray doInBackground(String... queries) {
            try {
                return getJSONArray(queries[0]);
            }

            catch (IOException e) {
                return null;
            }
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(JSONArray result) {
            if (result != null) {
                try {
                    //JSONObject jobj = new JSONObject(result);
                    routes.add(result);
                    if (routes.size() == NUM_ROUTE_TYPES) {
                        b = currentTimeMillis();
                        Log.wtf(DEBUG_TAG, "" + (b - a));
                        for (int i = 0; i < 3; i++) {
                            JSONArray jarr = routes.get(i);
                            for (int j = 0; j < jarr.length(); j++) {
                                JSONObject r = (JSONObject) jarr.get(j);
                                String id = r.getString("id");
                                new DownloadStationsTask().execute("stops?filter[route]=" + id);
                            }
                        }
                        c = currentTimeMillis();
                        Log.wtf(DEBUG_TAG, "" + (c - a));
                    }

                    /*
                    double[] coord = getCoord(jobj);
                    CameraUpdate center=CameraUpdateFactory.newLatLng(new LatLng(coord[0], coord[1]));
                    CameraUpdate zoom=CameraUpdateFactory.zoomTo(12);
                    mMap.moveCamera(center);
                    mMap.animateCamera(zoom);*/
                }
                catch (Exception e) {
                    Log.e(DEBUG_TAG, "JSON Exception", e);
                }
            }
            else{
                Log.e(DEBUG_TAG, "returned String is null");
            }
        }
    }

    private class DownloadStationsTask extends AsyncTask<String, Void, JSONArray> {
        @Override
        protected JSONArray doInBackground(String... queries) {
            try {
                return getJSONArray(queries[0]);
            }

            catch (IOException e) {
                return null;
            }
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(JSONArray result) {
            if (result != null) {
                try {
                    //JSONObject jobj = new JSONObject(result);
                    for (int i = 0; i < result.length(); i++) {
                        JSONObject station = (JSONObject) result.get(i);
                        JSONObject a = station.getJSONObject("attributes");
                        String name = a.getString("name").toLowerCase();
                        String id = station.getString("id");
                        stations.put(id, station);
                    }
                    Log.wtf(DEBUG_TAG, "ready " + (currentTimeMillis() - a));
                }
                catch (Exception e) {
                    Log.e(DEBUG_TAG, "JSON Exception", e);
                }
            }
            else{
                Log.e(DEBUG_TAG, "returned String is null");
            }
        }
    }

    private class DownloadPredictionsTask extends AsyncTask<String, Void, JSONArray> {
        @Override
        protected JSONArray doInBackground(String... queries) {
            try {
                return getJSONArray(queries[0]);
            }

            catch (IOException e) {
                return null;
            }
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(JSONArray result) {
            if (result != null) {
                try {
                    int row = 1;
                    for (int i = 0; i < result.length() && i < MAX_ROWS; i++) {
                        JSONObject p = (JSONObject) result.get(i);

                        String route = getRoute(p);
                        String trip = getTrip(p);
                        String eta = getEta(p);

                        if (eta != null) { //not the last station for this prediction
                            predictions[row * 3] = route;
                            new DownloadTripTask().execute((row * 3 + 1) + ",trips/" + trip);
                            Log.e(DEBUG_TAG, trip);
                            predictions[row * 3 + 2] = eta;
                            adapter.notifyItemRangeChanged(row * 3, 3);
                            row++;
                        }
                    }
                }
                catch (JSONException e) {
                    Log.e(DEBUG_TAG, "JSON Exception", e);
                }

                catch (Exception e) {
                    Log.e(DEBUG_TAG,"Exception", e);
                }
            }
            else{
                Log.e(DEBUG_TAG, "returned String is null");
            }
        }
    }

    private class DownloadTripTask extends AsyncTask<String, Void, JSONObject> {
        int loc;
        @Override
        protected JSONObject doInBackground(String... queries) {
            try {
                String[] split = queries[0].split(",");
                loc = Integer.parseInt(split[0]);
                return getJSONObject(split[1]);
            }

            catch (IOException e) {
                return null;
            }
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(JSONObject result) {
            if (result != null) {
                try {
                    JSONObject t = result.getJSONObject("data");
                    String destination = getDestination(t);

                    predictions[loc] = destination;
                    adapter.notifyItemRangeChanged(loc,1);
                }
                catch (Exception e) {
                    Log.e(DEBUG_TAG, "JSON Exception", e);
                }
            }
            else{
                Log.e(DEBUG_TAG, "returned String is null");
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void ok(View v) {
        ok();
    }

    public void ok() {
        hideKeyboard();
        EditText msgTextField = (EditText) findViewById(R.id.editText);
        String search = msgTextField.getText().toString().toLowerCase();
        String id = stationIDs.get(search);
        Log.wtf(DEBUG_TAG, id);
        if (id != null) {
            if (checkConnection()) {
                try {
                    clearRecyclerView();
                    //get prediction data
                    getPredictions(id);

                    //add marker
                    JSONObject jobj = stations.get(id);
                    if (jobj != null) {
                        addMarker(jobj);
                    } else {
                        Log.wtf(DEBUG_TAG, "jobj null");
                    }

                } catch (JSONException e) {
                    Log.e(DEBUG_TAG, "JSONException: ok");
                } catch (Exception e) {
                    Log.e(DEBUG_TAG, "error", e);
                }
            } else {
                Log.e(DEBUG_TAG, "No network connection");
            }
        }
        else {
            Log.wtf(DEBUG_TAG, "search term not found");
        }
    }

    public void getPredictions(String id) {
        new DownloadPredictionsTask().execute("predictions?sort=departure_time&filter[route_type]=0,1,2&filter[stop]=" + id);
    }

    public void addMarker(JSONObject station) throws JSONException {
        JSONObject a = station.getJSONObject("attributes");
        double lat = a.getDouble("latitude");
        double lng = a.getDouble("longitude");
        String name = a.getString("name");
        LatLng loc = new LatLng(lat, lng);

        //add marker
        if (mSearched != null) {
            mSearched.remove();
        }
        mSearched = mMap.addMarker(new MarkerOptions()
                .position(loc)
                .title(name)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.mbta32))
        );

        mMap.moveCamera(CameraUpdateFactory.newLatLng(loc));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(14));
    }

    //methods to retrieve data from JSONObjects
    public String getRoute(JSONObject prediction) throws JSONException {
        JSONObject r = prediction.getJSONObject("relationships");
        JSONObject route = r.getJSONObject("route");
        JSONObject data = route.getJSONObject("data");
        String str = data.getString("id");
        return (str.substring(0, 3).equals("CR-") ? str.substring(3, str.length()) : str);
    }

    public String getTrip(JSONObject prediction) throws JSONException {
        JSONObject r = prediction.getJSONObject("relationships");
        JSONObject trip = r.getJSONObject("trip");
        JSONObject data = trip.getJSONObject("data");
        return data.getString("id");
    }

    public String getDestination(JSONObject trip) throws JSONException {
        JSONObject a = trip.getJSONObject("attributes");
        return a.getString("headsign");
    }

    public String getEta(JSONObject prediction) throws JSONException {

        JSONObject a = prediction.getJSONObject("attributes");
        String departureTime = a.getString("departure_time");
        if (departureTime.equals("null")) {
            return null; //last stop, ETA shouldn't be displayed
        }
        String arrivalTime = a.getString("arrival_time");
        boolean firstStop = arrivalTime.equals("null"); //if no arrival time, then this is the first stop
        String predictionStr = (firstStop ? departureTime : arrivalTime);

        ZonedDateTime predictionTime = getDateTime(predictionStr);

        Log.wtf(DEBUG_TAG, predictionTime.toString());
        Log.wtf(DEBUG_TAG, ZonedDateTime.now().toString());

        long secondsPrediction = ZonedDateTime.now().until(predictionTime, SECONDS);
        if (secondsPrediction <= 0) {
            return "Boarding";
        }
        else if (secondsPrediction <= 30) {
            return (firstStop ? "Boarding" : "Arriving"); //if eta < 30sec, use arriving; if < 0, use boarding
        }
        return Long.toString(secondsPrediction / 60) + " min " + Long.toString((secondsPrediction % 60) / 10 * 10) + " sec";
    }

    public ZonedDateTime getDateTime(String str) {
        return ZonedDateTime.parse(str);
        /*
        String[] strs = str.split("[\\-T:]");
        int[] ints = new int[6];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = Integer.parseInt(strs[i]);
        }
        return LocalDateTime.of(ints[0], ints[1], ints[2], ints[3], ints[4], ints[5]);
        */
    }

    // set up the RecyclerView
    public void setRecyclerViewLayout(String[] data) {
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        int numberOfColumns = 3;
        recyclerView.setLayoutManager(new GridLayoutManager(this, numberOfColumns));
        adapter = new MyRecyclerViewAdapter(this, data);
        recyclerView.setAdapter(adapter);
    }

    public void clearRecyclerView() {
        for (int i = 5; i < predictions.length; i += 3) {
            boolean cleared = true;
            for (int j = i - 2; j <= i; j++) {
                if (predictions[j] != null && !predictions[j].equals("")) {
                    cleared = false;
                }
            }
            if (cleared) {
                i = predictions.length;
            }
            else {
                for (int k = i - 2; k <= i; k++) {
                    predictions[k] = "";
                }
            }
        }
    }

    public void initializeStationNameMap() {
        stationIDs = new HashMap<String, String>();
        Resources res = getResources();
        String[] arr = res.getStringArray(R.array.stationIDs);
        for (String s: arr) {
            String[] split = s.split(",", 2);
            stationIDs.put(split[0], split[1]);
        }
    }


    public JSONArray getJSONArray(String query) throws IOException {
        try {
            JSONObject jobj = getJSONObject(query);
            return jobj.getJSONArray("data");
        }

        catch (JSONException e) {
            Log.e(DEBUG_TAG, "JSON Exception", e);
            return null;
        }
    }

    public JSONObject getJSONObject(String query) throws IOException {
        try {
            String sb = getJSON(query);
            return new JSONObject(sb);
        }

        catch (JSONException e) {
            Log.e(DEBUG_TAG, "JSON Exception", e);
            return null;
        }
    }

    public String getJSON(String query) throws IOException {
        String url = MBTA_URL_START + query;
        url += (query.contains("?") ? "&" : "?");
        url += "api_key=" + MBTA_API_KEY;
        //Log.wtf(DEBUG_TAG, url);
        if (checkConnection()) {
            try {
                URL u = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setReadTimeout(10000 /* milliseconds */);
                conn.setConnectTimeout(15000 /* milliseconds */);
                conn.setChunkedStreamingMode(0);
                conn.connect();
                InputStream in = new BufferedInputStream(conn.getInputStream());
                BufferedReader streamReader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String inputStr;
                while ((inputStr = streamReader.readLine()) != null) {
                    sb.append(inputStr);
                }
                streamReader.close();
                return sb.toString();

            }
            catch (MalformedURLException e) {
                Log.e(DEBUG_TAG, "Malformed URL", e);
            }
        }
        return null;
    }

    public boolean checkConnection() {
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(this);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.mMap = map;
        LatLng dtx = new LatLng(42.3555, -71.0605);
        //mMap.addMarker(new MarkerOptions().position(dtx).title("Marker in Downtown Crossing"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(dtx));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(10));
    }

}
