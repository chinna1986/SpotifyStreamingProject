package de.pmdroid.spotifystreaming;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.protobuf.InvalidProtocolBufferException;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.ion.Ion;
import com.spotify.metadata.proto.Metadata;
import com.spotify.playlist4.proto.Playlist4Changes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

import de.pascal.SpotifyAPI.Logger;
import de.pascal.SpotifyAPI.Spotify;
import de.pascal.SpotifyAPI.SpotifyUtil;


public class MainActivity extends Activity {

    static Spotify spotify2;
    public static ProgressDialog progressDialog;
    ImageView imageView;
    MediaPlayer mediaPlayer;
    TextView title;
    TextView artist;
    ListView mainListView;
    ArrayAdapter listAdapter;
    private String titlestr;
    private String artiststr;
    private String mp3_uri;
    private String track;
    private String lid;
    private ArrayList<String> tracks_list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressDialog = new ProgressDialog(MainActivity.this);


        // Find the ListView resource.
        mainListView = (ListView) findViewById( R.id.listview2);

        ArrayList<String> trackList = new ArrayList<String>();

        listAdapter = new ArrayAdapter<String>(this, R.layout.simepl, trackList);


        tracks_list = new ArrayList<String>();

        Button send = (Button)this.findViewById(R.id.playlist);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final SpotifyUtil spotifyUtil = new SpotifyUtil();
                Log.d("SpotifyAPI", "getMP3: " + spotifyUtil.uri2id("spotify:track:58J1l73w3TcdpGqN0n1TiZ"));
                //spotify2.new_playlist("Test", "pascalmatthiesen");
                //mediaPlayer.pause();
                //spotify2.end();
                final Logger log = new Logger();
                log.setDEBUG(true);

                spotify2.playlist_request("spotify:user:pascalmatthiesen:playlist:4r8tLVOfcbyNYmvmLbT5w1", 0, 100, new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        log.debug("Playlist: " + s);
                        JSONObject jsonObject = null;
                        Playlist4Changes.ListDump playlist = null;
                        try {
                            jsonObject = new JSONObject(s);
                            playlist = spotify2.parse_playlist(jsonObject.getJSONArray("result"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        } catch (InvalidProtocolBufferException e) {
                            e.printStackTrace();
                        }

                        Integer playlist_count = playlist.getContents().getItemsCount();
                        if(playlist.getLength() > 100){
                            playlist_count = 100;
                        }

                        log.debug("playlist count: " + playlist_count);

                        final String[] array = new String[playlist_count];

                        for(Integer i = 0; i < playlist_count; i++){
                            array[i] = playlist.getContents().getItems(i).getUri();
                            log.debug(playlist.getContents().getItems(i).getUri());
                        }

                        log.debug(array.length + "");

                        spotify2.metadata_request(array, new WebSocket.StringCallback() {
                            @Override
                            public void onStringAvailable(String s) {
                                log.debug(s);
                                JSONObject jsonObject1 = spotify2.StringToJSONObject(s);
                                try {
                                    ArrayList<Metadata.Track> result = spotify2.parse_metadata(jsonObject1.getJSONArray("result"));
                                    for(Integer i = 0; i < result.size(); i++){
                                        Metadata.Track track1 = result.get(i);
                                        log.debug(track1.getArtist(0).getName() + " - " + track1.getName());
                                        listAdapter.add(track1.getArtist(0).getName() + " - " + track1.getName());
                                        log.debug("tracks");
                                        tracks_list.add(spotifyUtil.gid2uri("track", track1.getGid().toByteArray()));

                                    }
                                } catch (InvalidProtocolBufferException e) {
                                    e.printStackTrace();
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                MainActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Set the ArrayAdapter as the ListView's adapter.
                                        mainListView.setAdapter( listAdapter );
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });

        mainListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                track = tracks_list.get(position);
                if(mediaPlayer.isPlaying()){
                    mediaPlayer.reset();
                }
                Toast.makeText(MainActivity.this, "Track selected " + track, Toast.LENGTH_LONG).show();
            }
        });

        imageView = (ImageView)this.findViewById(R.id.coverimage);
        //title = (TextView)this.findViewById(R.id.title_text);
        //artist = (TextView)this.findViewById(R.id.artists_text);
        mediaPlayer = new MediaPlayer();



        Button xbmc = (Button)this.findViewById(R.id.stop);
        xbmc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //spotify2.new_playlist("Test");
                //spotify2.remove_playlist("spotify:user:pascalmatthiesen:playlist:65ixCKexl0RTREoRHERwtN");
                /*spotify2.rename_playlist("spotify:user:pascalmatthiesen:playlist:2Sv0Lvj1K9scb8p66H8zQo", "BBBBBBBB", new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        Log.d("SpotifyAPI", s);
                    }
                });*/
                //spotify2.playlist_remove_track("spotify:user:pascalmatthiesen:playlist:08cxiCweEf5LwczvkDuZAk", "spotify:track:1xswn2S8ZdQZQRh41P1UJp");
                //spotify2.set_starred("spotify:track:6oDPg7fXW3Ug3KmbafrXzA", true);
                //spotify2.set_starred("spotify:track:3wepnWWqG3Kn8yt3tj1wDy", false);
                spotify2.toplist_request("track", "user", "pascalmatthiesen", "global", new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        Log.d("SpotifyAPI", s);
                        JSONObject result = spotify2.StringToJSONObject(s);
                        Metadata.Toplist toplist = Metadata.Toplist.getDefaultInstance();
                        try {
                            toplist = toplist.parseFrom(Base64.decode(result.getJSONArray("result").getString(1), Base64.DEFAULT));
                        } catch (InvalidProtocolBufferException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        Log.d("SpotifyAPI", toplist.getItemsCount() + "items count");

                        final String[] array = new String[toplist.getItemsCount()];

                        final SpotifyUtil spotifyUtil = new SpotifyUtil();
                        final Logger log = new Logger();
                        log.setDEBUG(true);

                        for(Integer i = 0; i < toplist.getItemsCount(); i++){
                            array[i] = spotifyUtil.id2uri("track", toplist.getItems(i));
                            log.debug(spotifyUtil.id2uri("track", toplist.getItems(i)));
                        }

                        log.debug(array.length + "");

                        spotify2.metadata_request(array, new WebSocket.StringCallback() {
                            @Override
                            public void onStringAvailable(String s) {
                                log.debug(s);
                                JSONObject jsonObject1 = spotify2.StringToJSONObject(s);
                                try {
                                    ArrayList<Metadata.Track> result = spotify2.parse_metadata(jsonObject1.getJSONArray("result"));
                                    for(Integer i = 0; i < result.size(); i++){
                                        Metadata.Track track1 = result.get(i);
                                        log.debug(track1.getArtist(0).getName() + " - " + track1.getName());
                                        listAdapter.add(track1.getArtist(0).getName() + " - " + track1.getName());
                                        log.debug("tracks");
                                        tracks_list.add(spotifyUtil.gid2uri("track", track1.getGid().toByteArray()));

                                    }
                                } catch (InvalidProtocolBufferException e) {
                                    e.printStackTrace();
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                MainActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Set the ArrayAdapter as the ListView's adapter.
                                        mainListView.setAdapter( listAdapter );
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });

        Button stop = (Button)this.findViewById(R.id.play);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] tracks = new String[1];
                tracks[0] = track;
                Log.d("SpotifyAPI", track);
                spotify2.metadata_request(tracks, new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        final Metadata.Track track = spotify2.stringToTrack(s);
                        final Logger log = new Logger();

                        log.setDEBUG(true);
                        log.debug(track.getArtist(0).getName());
                        log.debug(track.getName());

                        titlestr = track.getName();
                        artiststr = track.getArtist(0).getName();


                        final SpotifyUtil spotifyUtil = new SpotifyUtil();


                        spotify2.track_uri(track, new WebSocket.StringCallback() {
                            @Override
                            public void onStringAvailable(String s) {

                                    log.debug(s);

                                    JSONObject result = spotify2.StringToJSONObject(s);
                                try {
                                    Log.d("SpotifyAPI", result.getJSONObject("result").getString("uri"));
                                    lid = result.getJSONObject("result").getString("lid");
                                    String url = result.getJSONObject("result").getString("uri"); // your URL here
                                    mp3_uri = result.getJSONObject("result").getString("uri");
                                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                                    mediaPlayer.setDataSource(url);
                                    mediaPlayer.prepare(); // might take long! (for buffering, etc)
                                    mediaPlayer.start();

                                    /*MainActivity.this.runOnUiThread(new Runnable() {

                                        public void run() {
                                            //title.setText(titlestr);
                                            //artist.setText(artiststr);
                                        }
                                    });*/



                                } catch (JSONException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });

                    }
                });

            }
        });



        Button button = (Button)this.findViewById(R.id.login);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Login login = new Login();
                login.execute();

            }
        });
    }

    private void settext(Metadata.Track track){
        title.setText(track.getName());
        artist.setText(track.getArtist(0).getName());
    }

    static public interface Callback {
        public void onResult(Boolean result, String message);
    }

    private class Login extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            progressDialog.setTitle("Login");
            progressDialog.setMessage("Login.....");
            progressDialog.show();
            super.onPreExecute();

        }
        @Override
        protected String doInBackground(String... urls) {
            spotify2 = new Spotify(MainActivity.this);
            spotify2.setUsername("uername");
            spotify2.setPassword("password");
            try {
                spotify2.auth(new Callback() {
                    @Override
                    public void onResult(Boolean result, String message) {
                        if(result)
                            progressDialog.dismiss();
                        Log.d("SpotifyAPI ", String.valueOf(result));
                        Log.d("SpotifyAPI ", message);
                    }
                });
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(String result) {

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
}
