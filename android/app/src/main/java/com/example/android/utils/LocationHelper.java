package com.example.android.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.List;
import java.util.Locale;

/**
 * Helper for location services
 */
public class LocationHelper {

    private static final String TAG = "LocationHelper";

    /**
     * Get last known location
     */
    public static void getLastLocation(Context context, LocationCallback callback) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            callback.onLocationReceived(null);
            return;
        }

        FusedLocationProviderClient fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(context);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> callback.onLocationReceived(location))
                .addOnFailureListener(e -> callback.onLocationReceived(null));
    }

    /**
     * Get address from coordinates. Runs Geocoder on the current thread.
     * Use getAddressAsync to avoid blocking the UI thread.
     */
    public static String getAddressFromLocation(Context context, double latitude, double longitude) {
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            // Note: getFromLocation is blocking and should be called from a background thread.
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder sb = new StringBuilder();

                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    sb.append(address.getAddressLine(i));
                    if (i < address.getMaxAddressLineIndex()) {
                        sb.append(", ");
                    }
                }

                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Geocoder failed for " + latitude + "," + longitude, e);
        }

        return latitude + ", " + longitude;
    }

    /**
     * Get address from coordinates asynchronously using the modern Geocoder API if available.
     */
    public static void getAddressAsync(Context context, double latitude, double longitude, AddressCallback callback) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latitude, longitude, 1, new Geocoder.GeocodeListener() {
                @Override
                public void onGeocode(@NonNull List<Address> addresses) {
                    if (!addresses.isEmpty()) {
                        callback.onAddressReceived(formatAddress(addresses.get(0)));
                    } else {
                        callback.onAddressReceived(latitude + ", " + longitude);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    callback.onAddressReceived(latitude + ", " + longitude);
                }
            });
        } else {
            // Fallback for older versions - should be called from a background thread
            new Thread(() -> {
                String address = getAddressFromLocation(context, latitude, longitude);
                callback.onAddressReceived(address);
            }).start();
        }
    }

    private static String formatAddress(Address address) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
            sb.append(address.getAddressLine(i));
            if (i < address.getMaxAddressLineIndex()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    /**
     * Get Google Maps URL
     */
    public static String getGoogleMapsUrl(double latitude, double longitude) {
        return "https://maps.google.com/?q=" + latitude + "," + longitude;
    }

    /**
     * Calculate distance between two locations (in meters)
     * Modified to use Haversine formula so it works in Unit Tests without mocking android.location.Location
     */
    public static float calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000; // Earth's radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (float) (R * c);
    }

    public interface LocationCallback {
        void onLocationReceived(Location location);
    }

    public interface AddressCallback {
        void onAddressReceived(String address);
    }
}
