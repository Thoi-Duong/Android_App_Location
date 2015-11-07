package com.dnhthoi.network_accses_location;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final long ONE_MIN = 1000 * 60;
    private static final long TWO_MIN = ONE_MIN * 2;
    private static final long FIVE_MIN = ONE_MIN * 5;
    private static final long MEASURE_TIME = 1000 * 30;
    private static final long POLLING_FREQ = 1000 * 10;
    private static final float MIN_ACCURACY = 25.0f;
    private static final float MIN_LAST_READ_ACCURACY = 500.0f;
    private static final float MIN_DISTANCE = 10.0f;

    // Views for display location information
    private TextView mAccuracyView;
    private TextView mTimeView;
    private TextView mLatView;
    private TextView mLngView;

    private int mTextViewColor = Color.GRAY;

    // Current best location estimate
    private Location mBestReading;

    // Reference to the LocationManager and LocationListener
    private LocationManager mLocationManager;
    private LocationListener mLocationListener;

    private final String TAG = "GetLocationActivity";

    private boolean mFirstUpdate = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAccuracyView = (TextView) findViewById(R.id.acura);
        mTimeView = (TextView) findViewById(R.id.time);
        mLatView = (TextView) findViewById(R.id.lat);
        mLngView = (TextView) findViewById(R.id.lon);

        Button btnMap = (Button)findViewById(R.id.start_map);

        btnMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri geoLocation = Uri.parse("geo:" + mBestReading.getLatitude() +
                        "," + mBestReading.getLongitude());

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(geoLocation);

                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Log.d("SHow Map", "Couldn't call " + geoLocation.toString() + ", no receiving apps installed!");
                }
            }
        });
        // Acquire reference to the LocationManager
        if (null == (mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE)))
            finish();

        // Get best last location measurement
        mBestReading = bestLastKnownLocation(MIN_LAST_READ_ACCURACY, FIVE_MIN);

        // Display last reading information
        if (null != mBestReading) {

            updateDisplay(mBestReading);

        } else {

            mAccuracyView.setText("No Initial Reading Available");

        }
        mLocationListener = new LocationListener() {

            // Called back when location changes

            public void onLocationChanged(Location location) {

                ensureColor();

                // Determine whether new location is better than current best
                // estimate

                if (null == mBestReading
                        || location.getAccuracy() < mBestReading.getAccuracy()) {

                    // Update best estimate
                    mBestReading = location;

                    // Update display
                    updateDisplay(location);
                    try {

                        if (mBestReading.getAccuracy() < MIN_ACCURACY)
                            mLocationManager.removeUpdates(mLocationListener);
                    }
                    catch (SecurityException ex){
                        Log.e("Scure::" , ex.toString()) ;
                    }

                }
            }

            public void onStatusChanged(String provider, int status,
                                        Bundle extras) {
                // NA
            }

            public void onProviderEnabled(String provider) {
                // NA
            }

            public void onProviderDisabled(String provider) {
                // NA
            }
        };
    }

    @Override
    public void onResume(){
        super.onResume();
        // Determine whether initial reading is
        // "good enough". If not, register for
        // further location updates
        try {


            if (null == mBestReading
                    || mBestReading.getAccuracy() > MIN_LAST_READ_ACCURACY
                    || mBestReading.getTime() < System.currentTimeMillis()
                    - TWO_MIN) {

                // Register for network location updates
                if (null != mLocationManager
                        .getProvider(LocationManager.NETWORK_PROVIDER)) {
                    mLocationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER, POLLING_FREQ,
                            MIN_DISTANCE, mLocationListener);
                }

                // Register for GPS location updates
                if (null != mLocationManager
                        .getProvider(LocationManager.GPS_PROVIDER)) {
                    mLocationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, POLLING_FREQ,
                            MIN_DISTANCE, mLocationListener);
                }

                // Schedule a runnable to unregister location listeners
                Executors.newScheduledThreadPool(1).schedule(new Runnable() {

                    @Override
                    public void run() {

                        Log.i(TAG, "location updates cancelled");
                        try {

                            mLocationManager.removeUpdates(mLocationListener);
                        }catch (SecurityException ex){
                            Log.e("Scure::" , ex.toString()) ;
                        }

                    }
                }, MEASURE_TIME, TimeUnit.MILLISECONDS);
            }
        }
        catch (SecurityException ex){
            Log.e("Scure::" , ex.toString()) ;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    try {

        mLocationManager.removeUpdates(mLocationListener);

    }
    catch (SecurityException ex){
        Log.e("Scure::" , ex.toString()) ;
    }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }



    // Get the last known location from all providers
    // return best reading that is as accurate as minAccuracy and
    // was taken no longer then minAge milliseconds ago. If none,
    // return null.

    private Location bestLastKnownLocation(float minAccuracy, long maxAge) {

        Location bestResult = null;
        float bestAccuracy = Float.MAX_VALUE;
        long bestAge = Long.MIN_VALUE;

        List<String> matchingProviders = mLocationManager.getAllProviders();

        for (String provider : matchingProviders) {
            try {

                Location location = mLocationManager.getLastKnownLocation(provider);


            if (location != null) {

                float accuracy = location.getAccuracy();
                long time = location.getTime();

                if (accuracy < bestAccuracy) {

                    bestResult = location;
                    bestAccuracy = accuracy;
                    bestAge = time;

                }
            }
            }
            catch (SecurityException ex){
                Log.e("Scure::" , ex.toString()) ;
            }
        }

        // Return best reading or null
        if (bestAccuracy > minAccuracy
                || (System.currentTimeMillis() - bestAge) > maxAge) {
            return null;
        } else {
            return bestResult;
        }
    }

    // Update display
    private void updateDisplay(Location location) {

        mAccuracyView.setText("Accuracy:" + location.getAccuracy());

        mTimeView.setText("Time:"
                + new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale
                .getDefault()).format(new Date(location.getTime())));

        mLatView.setText("Longitude:" + location.getLongitude());

        mLngView.setText("Latitude:" + location.getLatitude());

    }

    private void ensureColor() {
        if (mFirstUpdate) {
            setTextViewColor(mTextViewColor);
            mFirstUpdate = false;
        }
    }

    private void setTextViewColor(int color) {

        mAccuracyView.setTextColor(color);
        mTimeView.setTextColor(color);
        mLatView.setTextColor(color);
        mLngView.setTextColor(color);

    }
}
