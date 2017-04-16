package com.example.nikhil.tempo.Services;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.example.nikhil.tempo.MainActivity;
import com.example.nikhil.tempo.R;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import android.os.Handler;

import java.util.List;

/**
 * Created by Nikhil on 3/28/17.
 */

public class ActivityRecognitionService extends IntentService
{
    public ActivityRecognitionService()
    {
        super("ActivityRecognitionService");
    }

    public ActivityRecognitionService(String name)
    {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent)
    {
        if(ActivityRecognitionResult.hasResult(intent)) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            handleDetectedActivities( result.getProbableActivities() );
        }
    }

    private void handleDetectedActivities(List<DetectedActivity> probableActivities) {
        DetectedActivity maxConfidenceActivity = null;
        int maxConfidenceValue = 0;
        for( DetectedActivity activity : probableActivities )
        {
            if(activity.getConfidence() > maxConfidenceValue)
            {
                maxConfidenceValue = activity.getConfidence();
                maxConfidenceActivity = activity;
            }
        }
        resolveActivity(maxConfidenceActivity);
    }

    private void resolveActivity(DetectedActivity activity) {
        switch (activity.getType()) {
            case DetectedActivity.IN_VEHICLE: {
                Log.v("ActivityRecogition", "In Vehicle: " + activity.getConfidence());
                MainActivity.activityLabel = "IN_VEHICLE";
                break;
            }
            case DetectedActivity.ON_BICYCLE: {
                Log.v("ActivityRecogition", "On Bicycle: " + activity.getConfidence());
                MainActivity.activityLabel = "ON_BICYCLE";
                break;
            }
            case DetectedActivity.ON_FOOT: {
                Log.v("ActivityRecogition", "On Foot: " + activity.getConfidence());
                MainActivity.activityLabel = "ON_FOOT";
                break;
            }
            case DetectedActivity.RUNNING: {
                Log.v("ActivityRecogition", "Running: " + activity.getConfidence());
                MainActivity.activityLabel = "RUNNING";
                break;
            }
            case DetectedActivity.STILL: {
                Log.v("ActivityRecogition", "Still: " + activity.getConfidence());
                MainActivity.activityLabel = "STILL";
                break;
            }
            case DetectedActivity.TILTING: {
                Log.v("ActivityRecogition", "Tilting: " + activity.getConfidence());
                MainActivity.activityLabel = "TILTING";
                break;
            }
            case DetectedActivity.WALKING: {
                Log.v("ActivityRecogition", "Walking: " + activity.getConfidence());
                MainActivity.activityLabel = "WALKING";
                break;
            }
            case DetectedActivity.UNKNOWN: {
                Log.v("ActivityRecogition", "Unknown: " + activity.getConfidence());
                MainActivity.activityLabel = "UNKNOWN";
                break;
            }
        }
    }
}
