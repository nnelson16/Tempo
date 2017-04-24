package com.example.nikhil.tempo;


import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.Integer;
import java.net.URL;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.example.nikhil.tempo.ApplicationController.ApplicationController;
import com.google.android.gms.common.api.ResultCallback;
import com.example.nikhil.tempo.Models.Song;
import com.example.nikhil.tempo.MusicController.MusicController;
import com.example.nikhil.tempo.Services.ActivityRecognitionService;
import com.example.nikhil.tempo.Services.MusicService;
import com.example.nikhil.tempo.Services.MusicService.MusicBinder;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBuffer;
import com.google.android.gms.location.places.Places;

import android.widget.MediaController.MediaPlayerControl;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;


import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.R.attr.button;
import static android.provider.ContactsContract.CommonDataKinds.Website.URL;


public class MainActivity extends AppCompatActivity implements MediaPlayerControl, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    private boolean permissionsGranted = false;
    private static final int PERMISSIONS_REQUEST_CODE = 100;
    public static ArrayList<JSONObject> songs = new ArrayList<JSONObject>();

    public TextView weatherText;
    public TextView activityText;
    public TextView locationText;

    TextView songName;
    TextView artistName;
    MediaMetadataRetriever metaData;

    Switch mood;

    //service
    private MusicService musicSrv;
    private Intent playIntent;
    //binding
    private boolean musicBound=false;

    //controller
    private MusicController controller;

    //activity and playback pause flags
    private boolean paused=false, playbackPaused=false;

    private GoogleApiClient googleApiClient;
    private boolean placeClicked = false;
    private double currentLatitude;
    private double currentLongitude;
    private JSONObject weatherResponse = null;
    public static String activityLabel = "";
    private String weatherLabel = "";
    private List<Integer> placeTypes = null;
    private List<String> localSongsList = null;
    private String activityInput = "";
    private String weatherInput = "";
    private String moodInput = "";
    private SwipeRefreshLayout swipeRefreshLayout;
    private AsyncTask<Void, Integer, Void> songUploadTask = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        checkAndRequestPermissions();
        localSongsList = scanDeviceForMp3Files();

        Button uploadButton = (Button) findViewById(R.id.Upload);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                songUploadTask = new SongUploadTask();
                songUploadTask.execute();
            }
        });
        
        Button button = (Button) findViewById(R.id.trigger_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                musicSrv.playSong(false);
                controller.show();
            }
        });

        mood = (Switch) findViewById(R.id.moodSwitch);
        getMoodInput();
        mood.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getMoodInput();
                gatherData();
            }
        });

        setController();

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(ActivityRecognition.API)
                .addApi( Places.GEO_DATA_API )
                .addApi( Places.PLACE_DETECTION_API )
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        googleApiClient.connect();

        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                controller.hide();
                gatherData();
                musicSrv.playSong(true);
            }
        });
    }

    //connect to the service
    private ServiceConnection musicConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if(!songs.isEmpty())
            {
                Log.v("Tempo", "song list is not empty");
            }
            MusicBinder binder = (MusicBinder)service;
            //get service
            musicSrv = binder.getService();
            //pass list
            musicSrv.setList(songs);
            musicBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };

    private void getMoodInput()
    {
        if(mood.isChecked()) {
            moodInput = mood.getTextOn().toString();
            System.out.println("MOODON: " + moodInput);
        } else {
            moodInput = mood.getTextOff().toString();
            System.out.println("MOODOFF: " + moodInput);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Intent intent = new Intent( this, ActivityRecognitionService.class );
        PendingIntent pendingIntent = PendingIntent.getService( this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(googleApiClient, 5000, pendingIntent);
        gatherData();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onResume()
    {
        super.onResume();
        if(paused)
        {
            setController();
            paused=false;
        }
    }

    public void gatherData()
    {
        Intent intent = new Intent(getApplicationContext(), ActivityRecognitionService.class );
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(googleApiClient, 5000, pendingIntent);
        guessCurrentPlaceAndWeather();
    }


    public String getWeatherInput()
    {
        if(weatherLabel.contains("Flurries") || weatherLabel.contains("Snow") || weatherLabel.contains("Freezing") || weatherLabel.contains("Sleet"))
        {
            return "snowy";
        }
        else if(weatherLabel.contains("Rain") || weatherLabel.contains("Thunderstorm"))
        {
            return "rainy";
        }
        else if(weatherLabel.contains("Cloud") || weatherLabel.contains("Overcast") || weatherLabel.contains("Fog") || weatherLabel.contains("Haze"))
        {
            return "cold";
        }
        else
        {
            return "sunny";
        }
    }

    public String getActivityInput()
    {
        if (placeTypes.contains(Place.TYPE_NIGHT_CLUB))
        {
            return "party";
        }
        else if(placeTypes.contains(Place.TYPE_GYM) || activityLabel.equalsIgnoreCase("RUNNING") || activityLabel.equalsIgnoreCase("ON_BICYCLE"))
        {
            return "exercise";
        }
        else if(activityLabel.equalsIgnoreCase("STILL") && (placeTypes.contains(Place.TYPE_UNIVERSITY) || placeTypes.contains(Place.TYPE_LIBRARY)))
        {
            return "focused";
        }
        else
        {
            return "daily";
        }
    }


    public void checkAndRequestPermissions() {
        int readExtResult = ContextCompat.checkSelfPermission(getApplicationContext(), READ_EXTERNAL_STORAGE);
        int writeExtResult = ContextCompat.checkSelfPermission(getApplicationContext(), WRITE_EXTERNAL_STORAGE);
        int locationFineResult = ContextCompat.checkSelfPermission(getApplicationContext(), ACCESS_FINE_LOCATION);
        int locationCoarseResult = ContextCompat.checkSelfPermission(getApplicationContext(), ACCESS_COARSE_LOCATION);

        if ((readExtResult != PackageManager.PERMISSION_GRANTED) || (writeExtResult != PackageManager.PERMISSION_GRANTED) || (locationFineResult != PackageManager.PERMISSION_GRANTED) || (locationCoarseResult != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_CODE);
        } else {
            permissionsGranted = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length == 4 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED && grantResults[2] == PackageManager.PERMISSION_GRANTED && grantResults[3] == PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(getApplicationContext(), "Tempo Permissions are granted!", Toast.LENGTH_SHORT).show();
                permissionsGranted = true;
            }
            else
            {
                Toast.makeText(getApplicationContext(), "Need to enable Permissions!", Toast.LENGTH_LONG).show();
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    private void makeWeatherRequest()
    {
        String URL = "http://api.wunderground.com/api/663639f0328f1895/conditions/q/"+currentLatitude+","+currentLongitude+".json";
        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.GET, URL, null, new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        try
                        {
                            weatherResponse = response;
                            weatherLabel = weatherResponse.getJSONObject("current_observation").getString("weather");

                            activityInput = getActivityInput();
                            weatherInput = getWeatherInput();
                            weatherText = (TextView) findViewById(R.id.weatherText);
                            weatherText.setText(weatherInput);
                            activityText = (TextView) findViewById(R.id.activityText);
                            activityText.setText(activityInput);

                            makeSongRequest();
                            Log.v("Tempo", response.toString(4));
                        }
                        catch(Exception e)
                        {
                            Log.e("Tempo", e.toString());
                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // TODO Auto-generated method stub

                    }
                });
        ApplicationController.requestQueue.add(jsObjRequest);
    }

    private void guessCurrentPlaceAndWeather()
    {

        try
        {
            PendingResult<PlaceLikelihoodBuffer> result = Places.PlaceDetectionApi.getCurrentPlace(googleApiClient, null);
            result.setResultCallback( new ResultCallback<PlaceLikelihoodBuffer>() {
                @Override
                public void onResult( PlaceLikelihoodBuffer likelyPlaces ) {
                    int count = 0;

                    for(PlaceLikelihood p : likelyPlaces)
                    {
                        if(count == 0)
                        {
                            currentLatitude = p.getPlace().getLatLng().latitude;
                            currentLongitude = p.getPlace().getLatLng().longitude;
                            placeTypes = p.getPlace().getPlaceTypes();
                            makeWeatherRequest();
                            count = count + 1;
                            locationText = (TextView) findViewById(R.id.locationText);
                            locationText.setText(p.getPlace().getName().toString());
                            Log.v("Tempo", p.getPlace().getName().toString() + " " + ( p.getLikelihood() * 100));
                        }

                    }
                    likelyPlaces.release();
                }
            });
        }
        catch(SecurityException se)
        {
            Log.e("Tempo", se.getLocalizedMessage());
        }
    }

    private void makeSongRequest()
    {
        //String URL = "http://ec2-54-242-27-140.compute-1.amazonaws.com/users/1/recommendations?weather="+weatherInput+"&activity="+activityInput+"&mood="+moodInput;
        String URL = "http://ec2-54-242-27-140.compute-1.amazonaws.com/users/1/recommendations?weather=sunny"+"&activity=daily"+"&mood="+moodInput;
        Log.v("Tempo song request", URL);
        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.GET, URL, null, new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        try
                        {
                            songs.clear();
                            JSONArray songsArray = response.getJSONArray("songs");
                            for(int i=0; i<songsArray.length(); i++)
                            {
                                songs.add(songsArray.getJSONObject(i));
                            }
                            musicSrv.setList(songs);
                            swipeRefreshLayout.setRefreshing(false);
                            Toast.makeText(getApplicationContext(), "Your Songs Have Arrived!", Toast.LENGTH_SHORT).show();
                            Log.v("Tempo", response.toString(4));
                        }
                        catch(Exception e)
                        {
                            Log.e("Tempo", e.toString());
                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // TODO Auto-generated method stub

                    }
                });
        ApplicationController.requestQueue.add(jsObjRequest);
    }

    //set the controller up
    private void setController(){
        controller = new MusicController(this);
        //set previous and next button listeners
        controller.setPrevNextListeners(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNext();
            }
        }, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPrev();
            }
        });
        //set and show
        controller.setMediaPlayer(this);
        controller.setAnchorView(findViewById(R.id.anchor));
        controller.setEnabled(true);
    }

    private void playNext(){
        musicSrv.playNext();
        if(playbackPaused){
            setController();
            playbackPaused=false;
        }
        controller.show(0);
    }

    private void playPrev(){
        musicSrv.playPrev();
        if(playbackPaused){
            setController();
            playbackPaused=false;
        }
        controller.show(0);
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public int getCurrentPosition() {
        if(musicSrv!=null && musicBound && musicSrv.isPng())
            return musicSrv.getPosn();
        else return 0;
    }

    @Override
    public int getDuration() {
        if(musicSrv!=null && musicBound && musicSrv.isPng())
            return musicSrv.getDur();
        else return 0;
    }

    @Override
    public boolean isPlaying() {
        if(musicSrv!=null && musicBound)
            return musicSrv.isPng();
        return false;
    }

    @Override
    public void pause() {
        playbackPaused=true;
        musicSrv.pausePlayer();
    }

    @Override
    public void seekTo(int pos) {
        musicSrv.seek(pos);
    }

    @Override
    public void start() {
        musicSrv.go();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(playIntent==null){
            Log.v("Tempo", "starting service");
            playIntent = new Intent(this, MusicService.class);
            getApplicationContext().bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        controller.hide();
        paused=true;
    }

    @Override
    protected void onStop() {
        controller.hide();
        googleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        musicBound = false;
        stopService(playIntent);
        getApplicationContext().unbindService(musicConnection);
        musicSrv=null;
        songUploadTask.cancel(true);
        Log.v("Tempo", "in onDestroy");
        super.onDestroy();
    }

    private List<String> scanDeviceForMp3Files(){
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String[] projection = {
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION
        };
        final String sortOrder = MediaStore.Audio.AudioColumns.TITLE + " COLLATE LOCALIZED ASC";
        List<String> mp3Files = new ArrayList<>();

        Cursor cursor = null;
        try
        {
            Uri uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            cursor = getContentResolver().query(uri, projection, selection, null, sortOrder);
            if( cursor != null){
                cursor.moveToFirst();

                while( !cursor.isAfterLast() ){
                    String path = cursor.getString(2);
                    cursor.moveToNext();
                    if(path != null && path.endsWith(".mp3")) {
                        mp3Files.add(path);
                    }
                }

            }

        }
        catch (Exception e)
        {
            Log.e("TAG", e.toString());
        }
        finally
        {
            if( cursor != null){
                cursor.close();
            }
        }
        return mp3Files;
    }

    private class SongUploadTask extends AsyncTask<Void, Integer, Void>
    {
        @Override
        protected Void doInBackground(Void... params)
        {
            int songCount = 0;
            for (String fileName : localSongsList)
            {
                doFileUpload(fileName);
                songCount = songCount + 1;
            }
            publishProgress(songCount);
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values)
        {
            Toast.makeText(getApplicationContext(), values[0] + "/" + localSongsList.size() + " songs have been uploaded", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected void onPostExecute(Void aVoid)
        {

            Toast.makeText(getApplicationContext(), "All songs have been uploaded!", Toast.LENGTH_SHORT).show();

        }
    }

    private void doFileUpload(String fileName) {

        HttpURLConnection conn = null;
        DataOutputStream dos = null;
        DataInputStream inStream = null;
        String existingFileName = fileName;
        Log.v("Tempo", "Song Path: "+existingFileName);
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1 * 1024 * 1024;
        String urlString = "http://ec2-54-242-27-140.compute-1.amazonaws.com/users/1/upload";

        try {

            //------------------ CLIENT REQUEST
            FileInputStream fileInputStream = new FileInputStream(new File(existingFileName));
            // open a URL connection to the Servlet
            URL url = new URL(urlString);
            // Open a HTTP connection to the URL
            conn = (HttpURLConnection) url.openConnection();
            // Allow Inputs
            conn.setDoInput(true);
            // Allow Outputs
            conn.setDoOutput(true);
            // Don't use a cached copy.
            conn.setUseCaches(false);
            // Use a post method.
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            dos = new DataOutputStream(conn.getOutputStream());
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"song[]\";filename=\"" + existingFileName.substring(existingFileName.lastIndexOf("/")+1) + "\"" + lineEnd);
            dos.writeBytes(lineEnd);
            // create a buffer of maximum size
            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];
            // read file and write it into form...
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);

            while (bytesRead > 0) {

                dos.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);

            }

            // send multipart form data necesssary after file data...
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            // close streams
            Log.e("Debug", "File is written");
            fileInputStream.close();
            dos.flush();
            dos.close();

        } catch (MalformedURLException ex) {
            Log.e("Debug", "error: " + ex.getMessage(), ex);
        } catch (IOException ioe) {
            Log.e("Debug", "error: " + ioe.getMessage(), ioe);
        }


        

        /*

        //------------------ read the SERVER RESPONSE
        try {

            inStream = new DataInputStream(conn.getInputStream());
            String str;

            while ((str = inStream.readLine()) != null) {

                Log.e("Debug", "Server Response " + str);

            }

            inStream.close();

        } catch (IOException ioex) {
            Log.v("Tempo", "Exception Happened Here");
            Log.e("Debug", "error: " + ioex.getMessage(), ioex);
        }
        */
    }

}