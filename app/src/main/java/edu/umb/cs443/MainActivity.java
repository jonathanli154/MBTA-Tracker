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


    private final String ICON_SITE = "http://openweathermap.org/img/w/";

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

    private ArrayList<JSONObject> predictions;

    private GoogleMap mMap;
    private Marker mSearched;

    private long a = 0, b = 0, c = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        routes = new ArrayList<JSONArray>();
        stations = new HashMap<String, JSONObject>();
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
                a = currentTimeMillis();
                return getJSONArray(queries[0]);
            }

            catch (IOException e) {
                return null;
            }
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(JSONArray result) {
            TextView text = (TextView) findViewById(R.id.textView);
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
                Log.i(DEBUG_TAG, "returned String is null");
                text.setText("NA");
            }
        }
    }

    private class DownloadStationsTask extends AsyncTask<String, Void, JSONArray> {
        @Override
        protected JSONArray doInBackground(String... queries) {
            try {
                a = currentTimeMillis();
                return getJSONArray(queries[0]);
            }

            catch (IOException e) {
                return null;
            }
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(JSONArray result) {
            TextView text = (TextView) findViewById(R.id.textView);
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
                Log.i(DEBUG_TAG, "returned String is null");
                text.setText("NA");
            }
        }
    }

    private class DownloadPredictionsTask extends AsyncTask<String, Void, JSONArray> {
        @Override
        protected JSONArray doInBackground(String... queries) {
            try {
                a = currentTimeMillis();
                return getJSONArray(queries[0]);
            }

            catch (IOException e) {
                return null;
            }
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(JSONArray result) {
            TextView text = (TextView) findViewById(R.id.textView);
            if (result != null) {
                try {
                    //JSONObject jobj = new JSONObject(result);
                    /*for (int i = 0; i < result.length(); i++) {
                        JSONObject p = (JSONObject) result.get(i);
                        Log.wtf(DEBUG_TAG, p.toString());
                    }*/
                    JSONObject p = (JSONObject) result.get(0);

                    String eta = getEta(p);
                    text.setText(eta); //if until < 30sec, use arriving; if < 0, use boarding

                    String[] data = {eta};
                    setRecyclerViewLayout(data);
                }
                catch (Exception e) {
                    Log.e(DEBUG_TAG, "JSON Exception", e);
                }
            }
            else{
                Log.i(DEBUG_TAG, "returned String is null");
                text.setText("NA");
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
                    //get prediction data
                    getPredictions(id);

                    //get station info
                    JSONObject jobj = stations.get(id);
                    if (jobj != null) {
                        JSONObject a = jobj.getJSONObject("attributes");
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
                    } else {
                        Log.wtf(DEBUG_TAG, "jobj null");
                    }

                } catch (JSONException e) {
                    Log.e(DEBUG_TAG, "JSONException: ok");
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
        new DownloadPredictionsTask().execute("predictions?filter[route_type]=1&filter[stop]=" + id);
    }

    public String getEta(JSONObject prediction) throws JSONException{
        JSONObject a = prediction.getJSONObject("attributes");
        String predictionStr = a.getString("departure_time");
        if (predictionStr.equals("null")) {
            predictionStr = a.getString("arrival_time");
        }
        ZonedDateTime predictionTime = getDateTime(predictionStr);

        Log.wtf(DEBUG_TAG, predictionTime.toString());
        Log.wtf(DEBUG_TAG, ZonedDateTime.now().toString());

        long secondsPrediction = ZonedDateTime.now().until(predictionTime, SECONDS);
        return Double.toString(Math.round(secondsPrediction / 6.0) / 10.0);
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
        RecyclerView recyclerView = findViewById(R.id.rvNumbers);
        int numberOfColumns = 3;
        recyclerView.setLayoutManager(new GridLayoutManager(this, numberOfColumns));
        adapter = new MyRecyclerViewAdapter(this, data);
        recyclerView.setAdapter(adapter);
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
        String url = MBTA_URL_START + query + "&api_key=" + MBTA_API_KEY;
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
                JSONObject jobj = new JSONObject(sb.toString());
                streamReader.close();
                return jobj.getJSONArray("data");


                /*
                Reader reader = new InputStreamReader(in, "UTF-8");
                char[] buffer = new char[4096];
                reader.read(buffer);
                data = new String(buffer);*/
            }
            catch (MalformedURLException e) {
                Log.e(DEBUG_TAG, "Malformed URL", e);
            }

            catch (JSONException e) {
                Log.e(DEBUG_TAG, "JSON Exception", e);
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
