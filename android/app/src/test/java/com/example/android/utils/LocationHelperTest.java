package com.example.android.utils;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for LocationHelper's pure computation methods.
 * calculateDistance uses Haversine formula — no Android dependencies.
 * getGoogleMapsUrl is simple string formatting — no Android dependencies.
 */
public class LocationHelperTest {

    private static final double DELTA = 1.0; // 1 meter tolerance for distance calculations

    // --- Haversine Distance ---

    @Test
    public void calculateDistance_samePoint_returnsZero() {
        float distance = LocationHelper.calculateDistance(12.9716, 77.5946, 12.9716, 77.5946);
        assertEquals("Distance from a point to itself should be 0", 0.0f, distance, 0.01f);
    }

    @Test
    public void calculateDistance_knownCities_correctDistance() {
        // Bangalore to Delhi: ~1740 km
        float distance = LocationHelper.calculateDistance(
                12.9716, 77.5946,  // Bangalore
                28.7041, 77.1025   // Delhi
        );
        // Should be between 1700km and 1800km
        assertTrue("Bangalore-Delhi distance should be ~1740km, got: " + distance,
                distance > 1700000 && distance < 1800000);
    }

    @Test
    public void calculateDistance_newYorkToLondon_correctDistance() {
        // New York to London: ~5570 km
        float distance = LocationHelper.calculateDistance(
                40.7128, -74.0060,  // New York
                51.5074, -0.1278    // London
        );
        assertTrue("NY-London distance should be ~5570km, got: " + distance,
                distance > 5400000 && distance < 5700000);
    }

    @Test
    public void calculateDistance_equatorialPoints_correctDistance() {
        // Two points on the equator, 1 degree apart = ~111km
        float distance = LocationHelper.calculateDistance(0, 0, 0, 1);
        assertTrue("1 degree at equator should be ~111km, got: " + distance,
                distance > 110000 && distance < 112000);
    }

    @Test
    public void calculateDistance_poleToEquator_correctDistance() {
        // North Pole to Equator: ~10,000 km
        float distance = LocationHelper.calculateDistance(90, 0, 0, 0);
        assertTrue("North Pole to Equator should be ~10,000km, got: " + distance,
                distance > 9900000 && distance < 10100000);
    }

    @Test
    public void calculateDistance_symmetric() {
        float d1 = LocationHelper.calculateDistance(10, 20, 30, 40);
        float d2 = LocationHelper.calculateDistance(30, 40, 10, 20);
        assertEquals("Distance should be symmetric", d1, d2, 0.01f);
    }

    @Test
    public void calculateDistance_antipodalPoints() {
        // North Pole to South Pole: ~20,015 km (half Earth circumference)
        float distance = LocationHelper.calculateDistance(90, 0, -90, 0);
        assertTrue("Pole to pole should be ~20,015km, got: " + distance,
                distance > 19900000 && distance < 20200000);
    }

    @Test
    public void calculateDistance_smallDistance_precise() {
        // Two points ~1 meter apart at equator (0.000009 degrees)
        float distance = LocationHelper.calculateDistance(0, 0, 0, 0.000009);
        assertTrue("1 meter tolerance, got: " + distance,
                distance > 0.5 && distance < 2.0);
    }

    // --- getGoogleMapsUrl ---

    @Test
    public void getGoogleMapsUrl_positiveCoordinates() {
        String url = LocationHelper.getGoogleMapsUrl(12.9716, 77.5946);
        assertEquals("https://maps.google.com/?q=12.9716,77.5946", url);
    }

    @Test
    public void getGoogleMapsUrl_negativeCoordinates() {
        String url = LocationHelper.getGoogleMapsUrl(-33.8688, 151.2093);
        assertEquals("https://maps.google.com/?q=-33.8688,151.2093", url);
    }

    @Test
    public void getGoogleMapsUrl_zeroCoordinates() {
        String url = LocationHelper.getGoogleMapsUrl(0.0, 0.0);
        assertEquals("https://maps.google.com/?q=0.0,0.0", url);
    }

    // --- Known landmark distances for verification ---

    @Test
    public void calculateDistance_tajMahalToGatewayOfIndia() {
        // Taj Mahal (27.1751, 78.0421) to Gateway of India (18.9220, 72.8347)
        // Haversine calculation: ~1061 km
        float distance = LocationHelper.calculateDistance(27.1751, 78.0421, 18.9220, 72.8347);
        assertTrue("Should be ~1061km, got: " + distance,
                distance > 1000000 && distance < 1150000);
    }

    @Test
    public void calculateDistance_eiffelTowerToColosseum() {
        // Eiffel Tower (48.8584, 2.2945) to Colosseum (41.8902, 12.4922)
        float distance = LocationHelper.calculateDistance(48.8584, 2.2945, 41.8902, 12.4922);
        assertTrue("Should be ~1100km, got: " + distance,
                distance > 1050000 && distance < 1150000);
    }
}
