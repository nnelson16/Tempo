package com.example.nikhil.tempo.ApplicationController;

import android.app.Application;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

/**
 * Created by Nikhil on 3/29/17.
 */

public class ApplicationController  extends Application {
    public static RequestQueue requestQueue;


    @Override
    public  void onCreate(){
        super.onCreate();

        if(requestQueue == null){
            requestQueue = Volley.newRequestQueue(this);
        }
    }
}
