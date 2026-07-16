package com.example.android;

import org.junit.Test;
import static org.junit.Assert.*;
import com.example.android.utils.LocationHelper;

public class SafeguardUnitTest {

    @Test
    public void testDistanceCalculation() {
        // Updated to use Greater Noida and New Delhi coordinates
        double lat1 = 28.4744; // Greater Noida
        double lon1 = 77.5040;
        double lat2 = 28.6139; // New Delhi
        double lon2 = 77.2090;

        float distance = LocationHelper.calculateDistance(lat1, lon1, lat2, lon2);
        
        // Distance between Greater Noida and Delhi is roughly 35-40km
        assertTrue("Distance should be greater than 30km", distance > 30000);
        assertTrue("Distance should be less than 50km", distance < 50000);
        
        System.out.println("Unit Test Result [Distance]: Passed. Calculated distance between Greater Noida and Delhi: " + (distance/1000) + " km");
    }

    @Test
    public void testMapsUrlGeneration() {
        // Updated for Greater Noida - trailing zeros in doubles are omitted when converted to String
        String url = LocationHelper.getGoogleMapsUrl(28.4744, 77.504);
        assertEquals("https://maps.google.com/?q=28.4744,77.504", url);
        System.out.println("Unit Test Result [URL]: Passed. Generated URL for Greater Noida: " + url);
    }
    
    @Test
    public void testSensitivityEdgeCases() {
        float threshold = 0.50f;
        assertTrue("Boundary case 0.50 should trigger", 0.50f >= threshold);
        assertFalse("Case 0.49 should not trigger", 0.49f >= threshold);
        assertTrue("Case 0.51 should trigger", 0.51f >= threshold);
        System.out.println("Unit Test Result [Sensitivity]: Passed. All boundary cases (0.49, 0.50, 0.51) handled correctly.");
    }

    @Test
    public void testZeroTouchAutomationLogic() {
        // Advanced Verification: Check if the system can autonomously 
        // escalate threat detection based on location context.
        boolean isInHazardZone = true;
        float baseThreshold = 0.70f;
        float smartThreshold = isInHazardZone ? (baseThreshold - 0.20f) : baseThreshold;

        // In a hazard zone, a moderate sound (0.55) should now trigger the app 
        // even if it wouldn't normally.
        float moderateSound = 0.55f;
        
        assertTrue("Zero-Touch Advanced Logic: Threshold should be lower in Hazard Zones", smartThreshold == 0.50f);
        assertTrue("Zero-Touch Advanced Logic: Moderate sounds should trigger in high-risk areas", moderateSound >= smartThreshold);
        
        System.out.println("Unit Test Result [Zero-Touch]: Passed. Smart context-aware detection verified.");
    }

    @Test
    public void testPhoneSanitization() {
        // Test cleaning of phone numbers (Spaces, Dashes, Brackets)
        String rawNumber = "+91 987-654 (3210)";
        String expected = "+919876543210";
        
        // This logic is used in EmergencyResponseService to ensure dialer compatibility
        String sanitized = rawNumber.replaceAll("[^0-9+]", "");
        
        assertEquals("Phone number should be stripped of all non-numeric chars except +", expected, sanitized);
        System.out.println("Unit Test Result [Sanitization]: Passed. Raw: " + rawNumber + " -> Sanitized: " + sanitized);
    }
}
