package com.example.nikhil.tempo;


import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import java.lang.Integer;
import com.example.nikhil.tempo.Models.Song;

import java.util.ArrayList;


import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity {

    private boolean permissionsGranted = false;
    private static final int PERMISSIONS_REQUEST_CODE = 100;
    public static ArrayList<Song> songs = new ArrayList<Song>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkAndRequestPermissions();
        initMp3FilesList();

        for (Song s: songs)
        {
            Log.v("Tempo", s.getSongArtist() + " " + s.getSongTitle() + " " + s.getSongDuration() + " " + s.getSongDurationMinutesAndSeconds());
            Log.v("Tempo", s.getSongPath());
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if(permissionsGranted)
        {
            scanDeviceForMp3Files();
        }
    }

    public void initMp3FilesList()
    {
        if(permissionsGranted)
        {
            scanDeviceForMp3Files();
        }
    }

    public void checkAndRequestPermissions() {
        int readExtResult = ContextCompat.checkSelfPermission(getApplicationContext(), READ_EXTERNAL_STORAGE);
        int writeExtResult = ContextCompat.checkSelfPermission(getApplicationContext(), WRITE_EXTERNAL_STORAGE);

        if ((readExtResult != PackageManager.PERMISSION_GRANTED) || (writeExtResult != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
        } else {
            permissionsGranted = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED)
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

    private void scanDeviceForMp3Files()
    {
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String[] projection = {
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION
        };
        final String sortOrder = MediaStore.Audio.AudioColumns.TITLE + " ASC";


        Cursor cursor = null;
        try {
            Uri uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            cursor = getContentResolver().query(uri, projection, selection, null, sortOrder);
            if( cursor != null){
                cursor.moveToFirst();

                while( !cursor.isAfterLast() ){
                    String title = cursor.getString(0);
                    String path = cursor.getString(1);
                    String songDuration = cursor.getString(2);
                    cursor.moveToNext();
                    if(path != null && path.endsWith(".mp3")) {
                        Song song = parseInfo(title, songDuration);
                        song.setSongPath(path);
                        songs.add(song);
                    }
                }

            }

        } catch (Exception e) {
            Log.e("TAG", e.toString());
        }finally{
            if( cursor != null){
                cursor.close();
            }
        }
    }

    private Song parseInfo(String fileName, String duration)
    {
        String[] splitInfo = fileName.split("-");
        Song song = new Song();
        song.setSongArtist(splitInfo[0]);
        song.setSongTitle(splitInfo[1]);

        int convertedValue = Integer.parseInt(duration);
        song.setSongDuration(convertedValue);
        song.setSongDurationMinutesAndSeconds(parseDuration(convertedValue));
        return song;
    }

    private String parseDuration(int duration)
    {
        int minutesValue = (duration / 1000) / 60;
        int secondsValue = (duration - (minutesValue * 60 * 1000)) / 1000;
        return minutesValue+":"+secondsValue;
    }
}
