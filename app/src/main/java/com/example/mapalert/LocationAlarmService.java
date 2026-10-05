package com.example.mapalert;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class LocationAlarmService extends Service {

    private static final String CHANNEL_ID = "MapAlertServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    // Alarm will trigger when the user is within 100 meters
    // of the selected destination.
    private static final double ALARM_THRESHOLD_METERS = 100;

    FusedLocationProviderClient fusedLocationClient;
    LocationCallback locationCallback;
    MediaPlayer mediaPlayer;
    boolean alarmRinging = false;

    double targetLatitude;
    double targetLongitude;
    String targetAddress;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Get destination information from the Intent.
        targetLatitude = intent.getDoubleExtra("lat", 0);
        targetLongitude = intent.getDoubleExtra("lon", 0);
        targetAddress = intent.getStringExtra("address");

        // Check whether valid coordinates were received.
        if (Double.isNaN(targetLatitude) || Double.isNaN(targetLongitude)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (targetAddress == null || targetAddress.isEmpty()) {
            targetAddress = "Selected location";
        }

        createNotificationChannel();

        Notification notification = buildNotification("Tracking location for: " + targetAddress);
        startForeground(NOTIFICATION_ID, notification);

        startLocationUpdates();

        return START_STICKY;
    }


    /**
     * Creates the notification channel used by the foreground service.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "MapAlert Tracking", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
             manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Builds the persistent notification displayed while
     * the location tracking service is running.
     */
    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MapAlert")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
    }

    /**
     * Starts receiving location updates from the device.
     */
    private void startLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                android.location.Location location = locationResult.getLastLocation();
                if (location != null) {
                    handleNewLocation(location);
                }
            }
        };

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }


    /**
     * Handles every new location received from the device.
     */
    private void handleNewLocation(android.location.Location location) {
        double distance = calculateDistance(location.getLatitude(), location.getLongitude(),
                targetLatitude, targetLongitude);

        updateNotification("Distance: " + (int) distance + "m");

        if (distance <= ALARM_THRESHOLD_METERS && !alarmRinging) {
            playAlarm();
        }
    }

    /**
     * Updates the foreground service notification.
     */
    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    /**
     * Calculates the distance between two geographic coordinates
     * using the Haversine formula.
     *
     * @return distance in meters
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
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Starts the alarm ringtone when the user reaches
     * the selected destination.
     */
    private void playAlarm() {
        alarmRinging = true;
        Uri alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        mediaPlayer = MediaPlayer.create(this, alarmUri);
        mediaPlayer.setLooping(true);
        mediaPlayer.start();
        updateNotification("You have arrived!");
    }

    /**
     * Stops the service and releases all resources.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}