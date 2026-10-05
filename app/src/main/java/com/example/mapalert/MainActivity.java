package com.example.mapalert;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import android.location.Address;
import android.location.Geocoder;
import java.io.IOException;
import java.util.List;
import android.os.Looper;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;
import android.location.Location;
import android.view.View;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;

import android.text.TextWatcher;
import android.text.Editable;
import android.os.Build;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;

public class MainActivity extends AppCompatActivity {

    FusedLocationProviderClient fusedLocationClient;
    TextView locationText;
    TextView targetInfoText;
    private static final int LOCATION_PERMISSION_CODE = 100;
    private static final int NOTIFICATION_PERMISSION_CODE = 101;
    EditText targetAddressInput;
    Button setAlarmButton;
    Button stopAlarmButton;
    Button openSettingsButton;
    double targetLatitude;
    double targetLongitude;
    boolean alarmSet = false;
    LocationCallback locationCallback;
    LocationRequest locationRequest;


    /**
     * Initializes the MapAlert activity and its user interface.
     *
     * Sets the activity layout, initializes UI components, checks location
     * permission, and configures click and text-change listeners. Restores
     * any previously saved alarm, initializes the fused location provider,
     * and handles destination address input by converting the address into
     * geographical coordinates using a background geocoding operation.
     *
     * Also checks for the required notification permission on Android 13
     * (TIRAMISU) and starts location permission and location-service checks
     * when the activity is created.
     *
     * @param savedInstanceState contains the previously saved state of the
     *                           activity, if available
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        locationText = findViewById(R.id.locationText);
        targetInfoText = findViewById(R.id.targetInfoText);
        targetAddressInput = findViewById(R.id.targetAddress);
        openSettingsButton = findViewById(R.id.openSettingsButton);
        setAlarmButton = findViewById(R.id.setAlarmButton);
        stopAlarmButton = findViewById(R.id.stopAlarmButton);
        if (!hasLocationPermission()) {
            targetAddressInput.setVisibility(View.GONE);
            setAlarmButton.setVisibility(View.GONE);
        }
        openSettingsButton.setOnClickListener(v -> openAppSettings());
        targetAddressInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().trim().isEmpty()) {
                    targetInfoText.setText("");
                    alarmSet = false;
                }
            }
        });
        loadAlarmFromPrefs();
        stopAlarmButton.setOnClickListener(v -> stopAlarm());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setAlarmButton.setOnClickListener(v -> {
            String address = targetAddressInput.getText().toString().trim();

            if (address.isEmpty()) {
                locationText.setText("Please enter a destination address.");
                return;
            }

            // Geocoding must run off the main thread — it's a blocking network/database call
            new Thread(() -> {
                try {
                    Geocoder geocoder = new Geocoder(this);
                    List<Address> results = geocoder.getFromLocationName(address, 1);

                    runOnUiThread(() -> {
                        if (results != null && !results.isEmpty()) {
                            Address found = results.get(0);
                            targetLatitude = found.getLatitude();
                            targetLongitude = found.getLongitude();
                            alarmSet = true;
                            saveAlarmToPrefs(address, targetLatitude, targetLongitude);
                            startAlarmService(address, targetLatitude, targetLongitude);
                            showAlarmActiveState(address);
                        } else {
                            locationText.setText("Couldn't find that address. Try again.");
                        }
                    });
                } catch (IOException e) {
                    runOnUiThread(() -> locationText.setText("Geocoding failed: " + e.getMessage()));
                }
            }).start();
        });
        checkPermissionAndGetLocation();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
        }
    }


    /**
     * Updates the UI to show that an alarm is currently active.
     *
     * Hides the target address input and Set Alarm button, displays the
     * selected target address, and makes the Stop Alarm button visible.
     *
     * @param address the address for which the alarm has been set
     */
    private void showAlarmActiveState(String address) {
        targetAddressInput.setVisibility(View.GONE);
        setAlarmButton.setVisibility(View.GONE);
        targetInfoText.setText("Alarm set for: " + address);
        stopAlarmButton.setVisibility(View.VISIBLE);
    }

    /**
     * Updates the UI to show the alarm setup state.
     *
     * Makes the target address input and Set Alarm button visible,
     * clears any previously entered address and target information,
     * and hides the Stop Alarm button.
     */
    private void showSetAlarmState() {
        targetAddressInput.setVisibility(View.VISIBLE);
        targetAddressInput.setText("");
        setAlarmButton.setVisibility(View.VISIBLE);
        targetInfoText.setText("");
        stopAlarmButton.setVisibility(View.GONE);
    }

    /**
     * Checks whether GPS/location services and location permission are enabled.
     *
     * If GPS is disabled, displays a message asking the user to enable
     * location services. If location permission has not been granted, requests
     * the ACCESS_FINE_LOCATION permission. Otherwise, makes the location input
     * and alarm button visible and starts receiving location updates.
     */
    private void checkPermissionAndGetLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationText.setText("Please enable GPS/Location services to use MapAlert.");
            return;
        }

        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE);
        } else {
            targetAddressInput.setVisibility(View.VISIBLE);
            setAlarmButton.setVisibility(View.VISIBLE);
            startLocationUpdates();
        }
    }

    /**
     * Handles the result of the location permission request.
     *
     * If permission is granted, makes the location input and alarm button
     * visible and starts receiving location updates. If permission is denied,
     * checks whether the permission can be requested again or whether the user
     * needs to enable location permission manually through the device Settings.
     *
     * @param requestCode the code identifying the permission request
     * @param permissions the requested permissions
     * @param grantResults the results of the permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                targetAddressInput.setVisibility(View.VISIBLE);
                setAlarmButton.setVisibility(View.VISIBLE);
                startLocationUpdates();
            } else {
                boolean canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.ACCESS_FINE_LOCATION);

                if (canAskAgain) {
                    locationText.setText("Permission denied. Location access is needed for MapAlert to work.");
                } else {
                    locationText.setText("Permission permanently denied. Please enable it in Settings.");
                    openSettingsButton.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    /**
     * Handles a newly received location update.
     *
     * Retrieves the user's current latitude and longitude, displays them
     * in the UI, and, if an alarm is set, calculates and displays the
     * distance between the current location and the target location.
     *
     * @param location the user's latest location received from the
     *                 location provider
     */
    private void handleNewLocation(Location location) {
        double currentLat = location.getLatitude();
        double currentLong = location.getLongitude();

        String text = "Lat: " + currentLat + "\nLong: " + currentLong;

        if (alarmSet) {
            double distance = calculateDistance(currentLat, currentLong, targetLatitude, targetLongitude);
            text += "\nDistance to target: " + distance + " meters";
        }

        locationText.setText(text);
    }

    /**
     * Calculates the distance between two geographical coordinates
     * using the Haversine formula.
     *
     * Converts latitude and longitude values from degrees to radians,
     * calculates the angular distance between the two points, and
     * converts it into meters using the Earth's average radius.
     *
     * @param lat1 latitude of the first location
     * @param lon1 longitude of the first location
     * @param lat2 latitude of the second location
     * @param lon2 longitude of the second location
     * @return distance between the two locations in meters
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS_METERS = 6371000;

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c; // distance in meters
    }

    /**
     * Starts receiving periodic updates of the user's current location.
     *
     * Creates a high-accuracy location request with updates every 5 seconds,
     * processes each received location through the location callback, and
     * passes the latest location to handleNewLocation(). The method also
     * checks whether fine location permission has been granted before
     * requesting location updates.
     */
    private void startLocationUpdates() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    handleNewLocation(location);
                }
            }
        };

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    /**
     * Stops the active alarm and resets the alarm state.
     *
     * Stops the alarm service, clears the saved alarm data,
     * updates the alarm status, and displays a message indicating
     * that the alarm has been stopped.
     */
    private void stopAlarm() {
        stopAlarmService();
        alarmSet = false;
        clearAlarmFromPrefs();
        showSetAlarmState();
        locationText.setText("Alarm stopped.");
    }

    /**
     * Clears all saved alarm data from SharedPreferences.
     *
     * Removes the stored address, latitude, longitude, and alarm status
     * so that the previously saved alarm information is no longer available.
     */
    private void clearAlarmFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("MapAlertPrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    /**
     * Saves the alarm details to SharedPreferences for persistent storage.
     *
     * Stores the selected location's address, latitude, longitude, and
     * alarm status so that the alarm can be restored when the application
     * is opened again.
     *
     * @param address the address of the selected location
     * @param lat the latitude of the selected location
     * @param lon the longitude of the selected location
     */
    private void saveAlarmToPrefs(String address, double lat, double lon) {
        SharedPreferences prefs = getSharedPreferences("MapAlertPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("address", address);
        editor.putFloat("lat", (float) lat);
        editor.putFloat("lon", (float) lon);
        editor.putBoolean("alarmSet", true);
        editor.apply();
    }

    /**
     * Loads the previously saved alarm details from SharedPreferences.
     *
     * If an alarm was previously set, this method restores the saved
     * latitude, longitude, and address, updates the alarm state in the UI,
     * and restarts the LocationAlarmService using the saved location.
     */
    private void loadAlarmFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("MapAlertPrefs", MODE_PRIVATE);
        boolean savedAlarmSet = prefs.getBoolean("alarmSet", false);

        if (savedAlarmSet) {
            targetLatitude = prefs.getFloat("lat", 0f);
            targetLongitude = prefs.getFloat("lon", 0f);
            String address = prefs.getString("address", "");
            alarmSet = true;
            showAlarmActiveState(address);
            startAlarmService(address, targetLatitude, targetLongitude);  // ADD THIS
        }
    }

    /**
     * Starts the LocationAlarmService as a foreground service.
     *
     * Passes the selected location's address, latitude, and longitude
     * to the service through Intent extras so the service can use
     * this information for location and alarm-related operations.
     *
     * @param address the address of the selected location
     * @param lat the latitude of the selected location
     * @param lon the longitude of the selected location
     */
    private void startAlarmService(String address, double lat, double lon) {
        Intent serviceIntent = new Intent(this, LocationAlarmService.class);
        serviceIntent.putExtra("address", address);
        serviceIntent.putExtra("lat", lat);
        serviceIntent.putExtra("lon", lon);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    /**
     * Stops the LocationAlarmService by creating an Intent targeting the service
     * and requesting the Android system to stop it.
     *
     * This method is used to stop the location and alarm-related background
     * operations handled by LocationAlarmService.
     */
    private void stopAlarmService() {
        Intent serviceIntent = new Intent(this, LocationAlarmService.class);
        stopService(serviceIntent);
    }


    /**
     * Opens the application's settings page in the device Settings.
     *
     * This method creates an Intent using ACTION_APPLICATION_DETAILS_SETTINGS
     * and sets the URI to the current application's package name. This allows
     * the user to manually manage application permissions and other settings.
     */
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }


    /**
     * Checks whether the application has been granted permission
     * to access the user's precise location.
     *
     * This method uses ContextCompat.checkSelfPermission() to check
     * the ACCESS_FINE_LOCATION permission. It returns true if the
     * permission has been granted, otherwise it returns false.
     *
     * @return true if fine location permission is granted,
     * ```
    false otherwise.
     */
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}