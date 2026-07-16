package com.example.android.services;

import android.content.Context;

/**
 * @deprecated Replaced by {@link com.example.android.utils.AudioMonitoringService}.
 * This wrapper exists only for backward compatibility with existing references
 * and will be removed in a future release.
 */
@Deprecated
public class AudioMonitoringService extends com.example.android.utils.AudioMonitoringService {

    public AudioMonitoringService(Context context) {
        super(context);
    }
}
