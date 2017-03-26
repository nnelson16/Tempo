package com.example.nikhil.tempo;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.lang.Integer;
import com.example.nikhil.tempo.Models.Song;
import com.example.nikhil.tempo.MusicController.MusicController;
import com.example.nikhil.tempo.Services.MusicService;
import com.example.nikhil.tempo.Services.MusicService.MusicBinder;
import android.widget.MediaController.MediaPlayerControl;

import java.util.ArrayList;


import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.R.id.list;

public class MainActivity extends AppCompatActivity implements MediaPlayerControl{

    private boolean permissionsGranted = false;
    private static final int PERMISSIONS_REQUEST_CODE = 100;
    public static ArrayList<Song> songs = new ArrayList<Song>();

    //service
    private MusicService musicSrv;
    private Intent playIntent;
    //binding
    private boolean musicBound=false;

    //controller
    private MusicController controller;

    //activity and playback pause flags
    private boolean paused=false, playbackPaused=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkAndRequestPermissions();
        Button button = (Button) findViewById(R.id.trigger_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                musicSrv.playSong();
                controller.show(5000);
            }
        });
        initMp3FilesList();
        setController();
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

    @Override
    public void onResume()
    {
        super.onResume();
        if(permissionsGranted)
        {
            scanDeviceForMp3Files();
        }

        if(paused)
        {
            setController();
            paused=false;
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
                MediaStore.Audio.Media._ID,
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
                    int songid = cursor.getInt(0);
                    String title = cursor.getString(1);
                    String path = cursor.getString(2);
                    String songDuration = cursor.getString(3);
                    cursor.moveToNext();
                    if(path != null && path.endsWith(".mp3")) {
                        Song song = parseInfo(title, songDuration);
                        song.setSongID(songid);
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
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        paused=true;
    }

    @Override
    protected void onStop() {
        controller.hide();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopService(playIntent);
        musicSrv=null;
        Log.v("Tempo", "in onDestroy");
        super.onDestroy();
    }

}
