package com.example.nikhil.tempo.Models;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Nikhil on 3/26/17.
 */

public class Song implements Parcelable
{
    private String songTitle;
    private int songDuration;
    private String songDurationMinutesAndSeconds;
    private String songArtist;
    private String songPath;

    public Song(){}

    private Song(Parcel in)
    {
        songTitle = in.readString();
        songDuration = in.readInt();
        songDurationMinutesAndSeconds = in.readString();
        songArtist = in.readString();
        songPath = in.readString();
    }

    public String getSongTitle() {
        return songTitle;
    }

    public void setSongTitle(String songTitle) {
        this.songTitle = songTitle;
    }

    public int getSongDuration() {
        return songDuration;
    }

    public void setSongDuration(int songDuration) {
        this.songDuration = songDuration;
    }

    public String getSongDurationMinutesAndSeconds() {
        return songDurationMinutesAndSeconds;
    }

    public void setSongDurationMinutesAndSeconds(String songDurationMinutesAndSeconds) {
        this.songDurationMinutesAndSeconds = songDurationMinutesAndSeconds;
    }

    public String getSongArtist() {
        return songArtist;
    }

    public void setSongArtist(String songArtist) {
        this.songArtist = songArtist;
    }

    public String getSongPath() {
        return songPath;
    }

    public void setSongPath(String songPath) {
        this.songPath = songPath;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(songTitle);
        out.writeInt(songDuration);
        out.writeString(songDurationMinutesAndSeconds);
        out.writeString(songArtist);
        out.writeString(songPath);
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    public static final Parcelable.Creator<Song> CREATOR
            = new Parcelable.Creator<Song>() {

        @Override
        public Song createFromParcel(Parcel in) {
            return new Song(in);
        }


        @Override
        public Song[] newArray(int size) {
            return new Song[size];
        }
    };
}
