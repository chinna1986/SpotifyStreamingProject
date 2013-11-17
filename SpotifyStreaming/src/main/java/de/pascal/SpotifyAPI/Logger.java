package de.pascal.SpotifyAPI;

import android.util.Log;

public class Logger {

    private final String TAG = "SpotifyAPI";
    private Boolean DEBUG = false;

    public void setDEBUG(Boolean DEBUG) {
        this.DEBUG = DEBUG;
    }

    public void debug(String log){
        if(DEBUG)
            Log.d(TAG, log);
    }

    public void error(String log){
        Log.e(TAG, log);
    }

    public void info(String log){
        if(DEBUG)
            Log.i(TAG, log);
    }
}
