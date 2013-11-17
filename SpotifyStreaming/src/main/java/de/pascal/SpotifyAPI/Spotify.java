package de.pascal.SpotifyAPI;

import android.content.Context;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.ion.Ion;
import com.spotify.metadata.proto.Metadata;
import com.spotify.playlist4.proto.Playlist4Changes;
import com.spotify.playlist4.proto.Playlist4Meta;
import com.spotify.playlist4.proto.Playlist4Ops;
import com.spotify.playlist4.proto.Playlist4Service;
import com.spotify.proto.Mercury;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.JSONValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.pascal.SpotifyAPI.JSON.Credentials;
import de.pascal.SpotifyAPI.JSON.UserInfo;
import de.pmdroid.spotifystreaming.MainActivity;


public class Spotify {

    //fixed String
    private final String useragent =  "node-spotify-web in python (Chrome/13.37 compatible-ish)";
    private final String auth_protokoll = "https://";
    private final String auth_server = "play.spotify.com";
    private final String auth_url = "/xhr/json/auth.php";
    private final String csrf_regex = "\"csrftoken\":\"(.*?)\"";

    private String[] webSocketCredentials = new String[2];
    private String wss = "";
    private String secret = "";
    private Integer seq = 0;
    private String username = "";
    private String password = "";
    private String country = "";
    private String account_type = "";

    public Context context;
    private JsonObject authResponse;
    private Logger log;
    private WebSocket webSocket;



    public Spotify(Context context) {
        this.context = context;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void auth(final MainActivity.Callback callback) throws ExecutionException, InterruptedException {

        //Start new Logger
        log = new Logger();
        //enable DEBUG
        log.setDEBUG(true);

        //get the csrf secret
        String settings = Ion.with(context, auth_protokoll + auth_server)
                .addHeader("User-Agent", useragent)
                .asString()
                .get();

        Pattern p = Pattern.compile(csrf_regex);
        Matcher m = p.matcher(settings);

        //test if secret are there
        if(m.find()){
            secret = m.group(1);
            log.debug("Secret: " + secret);
        }else{
            callback.onResult(false, "There was a problem authenticating, no auth secret found");
            log.error("There was a problem authenticating, no auth secret found");
        }


        authResponse = Ion.with(context, auth_protokoll + auth_server + auth_url)
                .addHeader("User-Agent", useragent)
                .setBodyParameter("type", "sp")
                .setBodyParameter("username", username)
                .setBodyParameter("password", password)
                .setBodyParameter("secret", secret)
                .asJsonObject().get();

        log.debug("Auth response: " + authResponse.toString());

        if(authResponse.get("status").getAsString().equals("OK")){

            log.debug("Login successful");

            wss = Ion.with(context, "http://apresolve.spotify.com/")
                    .addHeader("User-Agent", useragent)
                    .addQuery("client", "24:0:0:" + authResponse
                            .get("config")
                            .getAsJsonObject()
                            .get("version")
                            .getAsString())
                    .asJsonObject()
                    .get()
                    .get("ap_list")
                    .getAsJsonArray()
                    .get(0)
                    .getAsString();

            log.debug("Websocket url: " + wss);

            String[] webSocketLoginData = authResponse.get("config")
                    .getAsJsonObject()
                    .get("credentials")
                    .getAsJsonArray()
                    .get(0)
                    .getAsString()
                    .split(":", 2);

            webSocketCredentials[0] = webSocketLoginData[1].split(":", 2)[1];

            webSocketCredentials[1] = webSocketLoginData[1].split(":", 2)[0];


            startWebsocket(callback);

        }else{
            callback.onResult(false, "Error return from auth was false");
            log.error("Error return from auth was false");
        }
    }

    public void startWebsocket(final MainActivity.Callback callback){
        AsyncHttpClient.getDefaultInstance().websocket("https://" + wss, null, new AsyncHttpClient.WebSocketConnectCallback() {
            @Override
            public void onCompleted(Exception ex, WebSocket websocket) {
                if (ex != null) {
                    ex.printStackTrace();
                    callback.onResult(false, "Websocket Error");
                }
                //make the websocket global available
                webSocket = websocket;

                log.debug("Connected");

                login(callback);
            }
        });

    }

    public void login(final MainActivity.Callback callback){
        Gson gson = new Gson();
        Credentials credentials = gson.fromJson(webSocketCredentials[0], Credentials.class);

        //make login json
        JSONArray credentialArray = new JSONArray();
        credentialArray.put("201");
        credentialArray.put(webSocketCredentials[1]);
        credentialArray.put(credentials.getJsonObject());

        Map credentialObject = new LinkedHashMap();
        credentialObject.put("args", credentialArray);
        credentialObject.put("name", "connect");
        credentialObject.put("id", String.valueOf(seq));

        log.debug("Credentials " + JSONValue.toJSONString(credentialObject).replace("\\\\\\/", "\\\\/"));

        //make socket request for login after login get user info to confirm account type
        sendWebsocketRequest(JSONValue.toJSONString(credentialObject).replace("\\\\\\/", "\\\\/"), new WebSocket.StringCallback() {
            public void onStringAvailable(String s) {
                log.debug("LOGIN: " + s);

                JSONObject result = null;
                try {
                    result = new JSONObject(s);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if (result.has("message")) {
                    try {
                        //when login ist complete call getUserInfos
                        JSONArray array = result.getJSONArray("message");
                        if (array.getString(0).equals("login_complete")) {
                            getUserInfos(callback);
                        }
                    } catch (JSONException e) {
                        callback.onResult(false, "Login Error");
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void getUserInfos(final MainActivity.Callback callback){
        Map user_info = new LinkedHashMap();
        user_info.put("args", "[]");
        user_info.put("name", "sp/user_info");
        user_info.put("id", String.valueOf(seq));

        sendWebsocketRequest(JSONValue.toJSONString(user_info), new WebSocket.StringCallback() {
            public void onStringAvailable(String s) {
                log.debug("User Info: " + s);
                populate_userdata_callback(s, callback);
            }
        });
    }

    public void populate_userdata_callback(String resp, MainActivity.Callback callback){

        Gson gson = new Gson();
        UserInfo userInfo = gson.fromJson(resp, UserInfo.class);

        username = userInfo.getResult().getUser();
        country = userInfo.getResult().getCountry();
        account_type = userInfo.getResult().getCatalogue();

        log.debug("Account Type: " + account_type.equals("premium"));

        if(account_type.equals("premium")){
            Thread heartbeatThread = new Thread(new HeartBeat());
            heartbeatThread.setPriority(Thread.NORM_PRIORITY);
            heartbeatThread.start();
            sendWorkDone(callback);
        }else{
            log.error("Please upgrade to Premium");
            sendWorkDone(callback);
            callback.onResult(false, "Please upgrade to Premium");
        }
    }

    public void sendWorkDone(final MainActivity.Callback callback){
        JSONArray args = new JSONArray();
        args.put("v1");

        Map work_done = new LinkedHashMap();
        work_done.put("args", args);
        work_done.put("name", "sp/work_done");
        work_done.put("id", String.valueOf(seq));

        sendWebsocketRequest(JSONValue.toJSONString(work_done), new WebSocket.StringCallback() {
            @Override
            public void onStringAvailable(String s) {
                try {
                    JSONObject result = new JSONObject(s);
                    if (result.getString("id") == String.valueOf(seq)) {
                        callback.onResult(true, "Finish");
                    } else {
                        seq = result.getInt("id");
                        seq++;
                        callback.onResult(true,"Finish");
                    }
                } catch (Exception e) {
                    callback.onResult(false, "Send Work Done Error");
                    e.printStackTrace();
                }
                log.debug("Work Done: " + s);
            }
        });
    }


    public void sendWebsocketRequest(String data, WebSocket.StringCallback stringCallback){
        webSocket.send(data);
        webSocket.setStringCallback(stringCallback);
        seq++;
    }

    /**
     * works
     * @param op
     * @param path
     * @param optype
     * @param name
     * @param index
     */
    public void playlist_op(String op, String path, String optype, String name, Integer index, final WebSocket.StringCallback stringCallback){
        Mercury.MercuryRequest mercuryRequest = Mercury.MercuryRequest.getDefaultInstance();
        mercuryRequest = mercuryRequest.newBuilderForType().setBody(ByteString.copyFromUtf8(op)).setUri("hm://" + path).build();
        String req = Base64.encodeToString(mercuryRequest.toByteArray(), Base64.DEFAULT);

        Playlist4Ops.Op op_req = Playlist4Ops.Op.getDefaultInstance();
        if(optype.equals("update")){
            Playlist4Ops.UpdateListAttributes updateItemAttributes = Playlist4Ops.UpdateListAttributes.getDefaultInstance();
            Playlist4Ops.ListAttributesPartialState itemAttributesPartialState = Playlist4Ops.ListAttributesPartialState.getDefaultInstance();
            Playlist4Meta.ListAttributes listAttributes = Playlist4Meta.ListAttributes.getDefaultInstance();
            listAttributes = listAttributes.newBuilderForType().setName(name).build();
            itemAttributesPartialState = itemAttributesPartialState.newBuilderForType().setValues(listAttributes).build();
            updateItemAttributes = updateItemAttributes.newBuilderForType().setNewAttributes(itemAttributesPartialState).build();
            op_req = op_req.newBuilderForType().setKind(Playlist4Ops.Op.Kind.UPDATE_LIST_ATTRIBUTES).setUpdateListAttributes(updateItemAttributes).build();
        }else if(optype.equals("remove")){
            op_req = op_req.newBuilderForType().setKind(Playlist4Ops.Op.Kind.REM)
                    .setRem(Playlist4Ops.Rem.newBuilder()
                            .setFromIndex(index)
                            .setLength(1))
                    .build();
        }

        Mercury.MercuryRequest mercury_request_payload = Mercury.MercuryRequest.getDefaultInstance();
        mercury_request_payload = mercury_request_payload.newBuilderForType().setUri(new String(op_req.toByteArray())).build();
        String payload = Base64.encodeToString(mercury_request_payload.toByteArray(), Base64.DEFAULT);

        JSONArray args = new JSONArray();
        args.put(0);
        args.put(req);
        args.put(payload);

        Map request_web = new LinkedHashMap();
        request_web.put("args", args);
        request_web.put("name", "sp/hm_b64");
        request_web.put("id", String.valueOf(seq));

        sendWebsocketRequest(JSONValue.toJSONString(request_web), new WebSocket.StringCallback() {
            @Override
            public void onStringAvailable(String s) {
                log.debug(s);

                JSONObject result = StringToJSONObject(s);

                new_playlist_callback("", result, stringCallback);
            }
        });
    }

    /**
     * works
     * @param name
     */
    public void new_playlist(String name, WebSocket.StringCallback stringCallback){
        playlist_op("PUT", "playlist/user/" + username, "update", name, 0, stringCallback);
    }

    /**
     * works
     * @param playlist_uri
     * @param name
     */
    public  void rename_playlist(String playlist_uri, String name,  WebSocket.StringCallback stringCallback){
        String path = "playlist/user/" + username + "/playlist/" + playlist_uri.split(":")[4] + "?syncpublished=true";
        playlist_op("MODIFY", path, "update", name, 0, stringCallback);
    }

    /**
     *
     * @param playlist_uri
     */
    public void remove_playlist(String playlist_uri){
        playlist_op_track("rootlist", playlist_uri, "REMOVE");
    }

    /**
     * works
     * @param sp
     * @param data
     * @throws JSONException
     * @throws InvalidProtocolBufferException
     */
    public void new_playlist_callback(String sp, JSONObject data, WebSocket.StringCallback stringCallback){
        Playlist4Service.CreateListReply reply = Playlist4Service.CreateListReply.getDefaultInstance();
        try {
            reply = reply.parseFrom(Base64.decode(data.getJSONArray("result").getString(1), Base64.DEFAULT));
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Mercury.MercuryRequest request = Mercury.MercuryRequest.getDefaultInstance();
        request = request.newBuilderForType().setBody(ByteString.copyFromUtf8("ADD")).setUri("hm://playlist/user/" + username + "/rootlist?add_first=1&syncpublished=1").build();

        String req = Base64.encodeToString(request.toByteArray(), Base64.DEFAULT);

        JSONArray args = new JSONArray();
        args.put(0);
        args.put(req);
        args.put(Base64.encodeToString(reply.getUri().toByteArray(), Base64.DEFAULT));

        Map request_web = new LinkedHashMap();
        request_web.put("args", args);
        request_web.put("name", "sp/hm_b64");
        request_web.put("id", String.valueOf(seq));
    }

    /**
     *
     * @param playlist_uri
     * @param track_uri
     * @param op
     */
    public void playlist_op_track(String playlist_uri, String track_uri, String op){
        String[] playlist = playlist_uri.split(":");

        String user = "";
        String playlist_id = "";
        if(playlist_uri.equals("rootlist")){
            user = username;
            playlist_id = "rootlist";
        }else{
            user = playlist[2];
            if(playlist[3].equals("starred")){
                playlist_id = "starred";
            }else{
                playlist_id = "playlist/" + playlist[4];
            }
        }

        Mercury.MercuryRequest mercuryRequest = Mercury.MercuryRequest.getDefaultInstance();
        mercuryRequest = mercuryRequest.newBuilderForType()
                .setBody(ByteString.copyFromUtf8(op))
                .setUri("hm://playlist/user/" + user + "/" + playlist_id + "?syncpublished=1")
                .build();

        String req = Base64.encodeToString(mercuryRequest.toByteArray(), Base64.DEFAULT);

        JSONArray args = new JSONArray();
        args.put(0);
        args.put(req);
        args.put(Base64.encodeToString(track_uri.getBytes(), Base64.DEFAULT));

        Map request_web = new LinkedHashMap();
        request_web.put("args", args);
        request_web.put("name", "sp/hm_b64");
        request_web.put("id", String.valueOf(seq));


        log.debug(JSONValue.toJSONString(request_web));

        sendWebsocketRequest(JSONValue.toJSONString(request_web), new WebSocket.StringCallback() {
            @Override
            public void onStringAvailable(String s) {
                log.debug(s);
            }
        });

    }

    /**
     *
     * @param playlist_uri
     * @param track_uri
     */
    public void playlist_add_track(String playlist_uri, String track_uri){
        playlist_op_track(playlist_uri, track_uri, "ADD");
    }

    /**
     *
     * @param playlist_uri
     * @param track_uri
     */
    public void playlist_remove_track(String playlist_uri, String track_uri){
        playlist_op_track(playlist_uri, track_uri, "REMOVE");
    }

    /**
     *
     * @param track_uri
     * @param starred
     */
    public void set_starred(String track_uri, Boolean starred){
        if(starred){
            playlist_add_track("spotify:user:" + username + ":starred", track_uri);
        }else{
            playlist_remove_track("spotify:user:" + username + ":starred", track_uri);
        }
    }

    /**
     * works
     * @param s
     * @return
     */
    public JSONObject StringToJSONObject(String s){
        try {
            JSONObject jsonObject = new JSONObject(s);
            return jsonObject;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * works
     * @param s
     * @return
     */
    public Metadata.Track stringToTrack(String s){
        try {
            JSONObject json = new JSONObject(s);
            Metadata.Track track = Metadata.Track.getDefaultInstance();
            return track.parseFrom(Base64.decode(json.getJSONArray("result").getString(1), Base64.DEFAULT));
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * works
     * @param toplist_content_type
     * @param toplist_type
     * @param username
     * @param region
     * @param stringCallback
     */
    public void toplist_request(String toplist_content_type, String toplist_type, String username, String region, WebSocket.StringCallback stringCallback){
        if(username.isEmpty()){
            username = this.username;
        }

        String request_str = "";
        if(toplist_type.equals("user")){
            request_str = "hm://toplist/toplist/user/" + username;
        }else if(toplist_type.equals("region")){
            request_str = "hm://toplist/toplist/region";
        }
        if(toplist_type.equals("region") && !region.isEmpty() && !region.equals("global")){
            request_str += "/"+region;
        }

        request_str += "?type=" + toplist_content_type;

        Mercury.MercuryRequest request = Mercury.MercuryRequest.getDefaultInstance();
        request = request.newBuilderForType().setBody(ByteString.copyFromUtf8("GET")).setUri(request_str).build();

        JSONArray args = new JSONArray();
        args.put(0);
        args.put(Base64.encodeToString(request.toByteArray(), Base64.DEFAULT));

        Map metadata_request = new LinkedHashMap();
        metadata_request.put("args", args);
        metadata_request.put("name", "sp/hm_b64");
        metadata_request.put("id", String.valueOf(seq));

        log.debug("Request: " + JSONValue.toJSONString(metadata_request));

        sendWebsocketRequest(JSONValue.toJSONString(metadata_request), stringCallback);
    }

    /**
     * should work
     * @param lid
     * @param track_uri
     * @param ms_played
     * @param stringCallback
     */
    public void send_track_end(String lid, String track_uri, Integer ms_played, WebSocket.StringCallback stringCallback){
        Integer ms_played_union = ms_played;
        Integer n_seeks_forward = 0;
        Integer n_seeks_backward = 0;
        Integer ms_seeks_forward = 0;
        Integer ms_seeks_backward = 0;
        Integer ms_latency = 100;
        String display_track = null;
        String play_context = "unknown";
        String source_start = "unknown";
        String source_end = "unknown";
        String reason_start = "unknown";
        String reason_end = "unknown";
        String referrer = "unknown";
        String referrer_version = "0.1.0";
        String referrer_vendor = "com.spotify";
        Integer max_continuous = ms_played;

        ArrayList args = new ArrayList<String>();
        args.add(lid);
        args.add(ms_played);
        args.add(n_seeks_forward);
        args.add(n_seeks_backward);
        args.add(ms_seeks_forward);
        args.add(ms_seeks_backward);
        args.add(ms_latency);
        args.add(display_track);
        args.add(play_context);
        args.add(source_start);
        args.add(source_end);
        args.add(reason_start);
        args.add(reason_end);
        args.add(referrer);
        args.add(referrer_version);
        args.add(referrer_vendor);
        args.add(max_continuous);

        Map track_end = new LinkedHashMap();
        track_end.put("args", args);
        track_end.put("name", "sp/track_end");
        track_end.put("id", String.valueOf(seq));

        log.debug("Trackend Response: " + JSONValue.toJSONString(track_end));

        sendWebsocketRequest(JSONValue.toJSONString(track_end), stringCallback);
    }

    /**
     * should work
     * @param lid
     * @param event
     * @param ms_where
     * @param stringCallback
     */
    public void send_track_event(String lid, String event, Integer ms_where, WebSocket.StringCallback stringCallback){
        if(event.equals("pause") || event.equals("play")){
            Integer ev_n = 4;

            ArrayList args = new ArrayList<String>();
            args.add(lid);
            args.add(ev_n);
            args.add(ms_where);

            Map request = new LinkedHashMap();
            request.put("args", args);
            request.put("name", "sp/track_event");
            request.put("id", String.valueOf(seq));

            sendWebsocketRequest(JSONValue.toJSONString(request), stringCallback);

        }else if(event.equals("unpause") || event.equals("continue") || event.equals("play")){
            Integer ev_n = 3;

            ArrayList args = new ArrayList<String>();
            args.add(lid);
            args.add(ev_n);
            args.add(ms_where);

            Map request = new LinkedHashMap();
            request.put("args", args);
            request.put("name", "sp/track_event");
            request.put("id", String.valueOf(seq));

            sendWebsocketRequest(JSONValue.toJSONString(request), stringCallback);
        }

    }

    /**
     * should work
     * @param resp
     * @return
     * @throws JSONException
     * @throws InvalidProtocolBufferException
     */
    public Metadata.Toplist parse_toplist(JSONArray resp) throws JSONException, InvalidProtocolBufferException {
        Metadata.Toplist toplist = Metadata.Toplist.getDefaultInstance();
        toplist.parseFrom(Base64.decode(resp.getString(1), Base64.DEFAULT));
        return toplist;
    }

    /**
     * works
     * @param resp
     * @return
     * @throws JSONException
     * @throws InvalidProtocolBufferException
     */
    public Playlist4Changes.ListDump parse_playlist(JSONArray resp) throws JSONException, InvalidProtocolBufferException {
        Playlist4Changes.ListDump listDump = Playlist4Changes.ListDump.getDefaultInstance();
        listDump = listDump.parseFrom(Base64.decode(resp.getString(1), Base64.DEFAULT));
        return listDump;
    }

    /**
     * should word
     * @param lid
     * @param ms_played
     * @param stringCallback
     */
    public void send_track_progress(String lid, Integer ms_played, WebSocket.StringCallback stringCallback){
        String source_start = "unknown";
        String reason_start = "unknown";
        Integer ms_latency = 100;
        String play_context = "unknown";
        String display_track = "";
        String referrer = "unknown";
        String referrer_version = "0.1.0";
        String referrer_vendor = "com.spotify";

        ArrayList args = new ArrayList<String>();
        args.add(lid);
        args.add(source_start);
        args.add(reason_start);
        args.add(ms_played);
        args.add(ms_latency);
        args.add(play_context);
        args.add(display_track);
        args.add(referrer);
        args.add(referrer_version);
        args.add(referrer_vendor);

        Map request = new LinkedHashMap();
        request.put("args", args);
        request.put("name", "sp/track_progress");
        request.put("id", String.valueOf(seq));

        log.debug("Track Progress Response: " + JSONValue.toJSONString(request));

        sendWebsocketRequest(JSONValue.toJSONString(request), stringCallback);
    }

    /**
     * works but i things this needs a callback
     * @return
     */
    public ArrayList getImageURL(Metadata.Track track){
        String sourceUrl = "https://d3rt1990lpmkn.cloudfront.net";
        SpotifyUtil spotifyUtil = new SpotifyUtil();

        ArrayList images = new ArrayList();

        Map image = new LinkedHashMap();
        image.put("tiny", sourceUrl + "/60/" + spotifyUtil.gid2id(track.getAlbum().getCover(1).getFileId().toByteArray()));
        image.put("small", sourceUrl + "/120/" + spotifyUtil.gid2id(track.getAlbum().getCover(1).getFileId().toByteArray()));
        image.put("normal", sourceUrl + "/300/" + spotifyUtil.gid2id(track.getAlbum().getCover(1).getFileId().toByteArray()));
        image.put("large", sourceUrl + "/640/" + spotifyUtil.gid2id(track.getAlbum().getCover(1).getFileId().toByteArray()));
        image.put("avatar", sourceUrl + "/artist_image/" + spotifyUtil.gid2id(track.getAlbum().getCover(1).getFileId().toByteArray()));
        images.add(image);

        return images;
    }

    /**
     * works good
     * @param uri
     * @param fromnum
     * @param num
     * @param stringCallback
     */
    public void playlist_request(String uri, Integer fromnum, Integer num, WebSocket.StringCallback stringCallback){

        String playlist = uri.replace(":", "/").substring(8);
        Mercury.MercuryRequest request = Mercury.MercuryRequest.getDefaultInstance();
        request = request.newBuilderForType()
                .setBody(ByteString.copyFromUtf8("GET"))
                .setUri("hm://playlist/" + playlist + "?from=" + String.valueOf(fromnum) + "&length=" + String.valueOf(num))
                .build();

        log.debug(request.getUri());


        JSONArray args = new JSONArray();
        args.put(0);
        args.put(Base64.encodeToString(request.toByteArray(), Base64.DEFAULT));

        Map metadata_request = new LinkedHashMap();
        metadata_request.put("args", args);
        metadata_request.put("name", "sp/hm_b64");
        metadata_request.put("id", String.valueOf(seq));

        log.debug(JSONValue.toJSONString(metadata_request));

        sendWebsocketRequest(JSONValue.toJSONString(metadata_request), stringCallback);
    }

    /**
     * works
     * @param query
     * @param query_type
     * @param max_results
     * @param offset
     * @param stringCallback
     */
    public void search_request(String query, String query_type, Integer max_results, Integer offset, WebSocket.StringCallback stringCallback){
        if(max_results > 50){
            log.error("Maximum of 50 results per request, capping at 50");
            max_results = 50;
        }

        Map search_types = new LinkedHashMap();
        search_types.put("tracks", 1);
        search_types.put("albums", 2);
        search_types.put("artists", 4);
        search_types.put("playlists", 8);

        ArrayList args = new ArrayList<String>();
        args.add(query);
        args.add(search_types.get(query_type));
        args.add(max_results);
        args.add(offset);

        Map search = new LinkedHashMap();
        search.put("args", args);
        search.put("name", "sp/search");
        search.put("id", String.valueOf(seq));

        log.debug("Search Request: " + JSONValue.toJSONString(search));

        sendWebsocketRequest(JSONValue.toJSONString(search), stringCallback);
    }

    /**
     * works
     * @param track
     * @throws JSONException
     */
    public void track_uri(Metadata.Track track, WebSocket.StringCallback stringCallback){
        track = recurseAlternatives(track);

        SpotifyUtil spotifyUtil = new SpotifyUtil();

        if(track.hasGid()){
            JSONArray args = new JSONArray();
            args.put("mp3160");
            args.put(spotifyUtil.gid2id(track.getGid().toByteArray()));

            Map track_uri = new LinkedHashMap();
            track_uri.put("args", args);
            track_uri.put("name", "sp/track_uri");
            track_uri.put("id", String.valueOf(seq));

            log.debug(JSONValue.toJSONString(track_uri));

            sendWebsocketRequest(JSONValue.toJSONString(track_uri), stringCallback);
        }else{
            log.error("Track has not gid");
        }
    }

    /**
     * should work needed more testing
     *
     * @param resp
     * @return
     * @throws InvalidProtocolBufferException
     * @throws JSONException
     */
    public ArrayList parse_metadata(JSONArray resp) throws InvalidProtocolBufferException, JSONException {
        ArrayList ret = new ArrayList();
        Mercury.MercuryReply mercuryReply = Mercury.MercuryReply.getDefaultInstance();
        mercuryReply = mercuryReply.parseFrom(Base64.decode(resp.getString(0), Base64.DEFAULT));

        if(mercuryReply.getStatusMessage().equals("vnd.spotify/mercury-mget-reply")){
            Mercury.MercuryMultiGetReply reply = Mercury.MercuryMultiGetReply.getDefaultInstance();
            try {
                reply = reply.parseFrom(Base64.decode(resp.getString(1), Base64.DEFAULT));

                ArrayList items = new ArrayList();
                for(Integer i = 0; i < reply.getReplyCount(); i++){
                    if(reply.getReply(i).getStatusCode() != 200)
                        continue;

                    if(new String(reply.getReply(i).getContentType().toByteArray()).equals("vnd.spotify/metadata-album")){
                        items.add(Metadata.Album.parseFrom(reply.getReply(i).getBody()));
                    }else if(new String(reply.getReply(i).getContentType().toByteArray()).equals("vnd.spotify/metadata-artist")){
                        items.add(Metadata.Artist.parseFrom(reply.getReply(i).getBody()));
                    }else if (new String(reply.getReply(i).getContentType().toByteArray()).equals("vnd.spotify/metadata-track")){
                        items.add(Metadata.Track.parseFrom(reply.getReply(i).getBody()));
                    }else{
                        log.error("Wrong Type");
                    }
                }
                ret = items;
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }else{
            if(mercuryReply.getStatusMessage().equals("vnd.spotify/metadata-album")){
                Metadata.Album album = Metadata.Album.getDefaultInstance();
                ret.add(album.parseFrom(Base64.decode(resp.getString(1), Base64.DEFAULT)));
            }else if(mercuryReply.getStatusMessage().equals("vnd.spotify/metadata-artist")){
                Metadata.Artist artist = Metadata.Artist.getDefaultInstance();
                ret.add(artist.parseFrom(Base64.decode(resp.getString(1), Base64.DEFAULT)));
            }else if (mercuryReply.getStatusMessage().equals("vnd.spotify/metadata-track")){
                Metadata.Track track = Metadata.Track.getDefaultInstance();
                ret.add(track.parseFrom(Base64.decode(resp.getString(1), Base64.DEFAULT)));
            }else{
                log.error("Unrecognised metadata type " + mercuryReply.getStatusMessage());
            }
        }
        return ret;
    }

    /**
     * should work
     * @param track
     * @return
     */
    public Metadata.Track recurseAlternatives(Metadata.Track track){
        if(is_track_available(track, country)){
            log.info("Track Available");
            return track;
        }else{
            log.info("Track not Available");
            log.info("Alternbative Count: " + track.getAlternativeCount());

            SpotifyUtil spotifyUtil = new SpotifyUtil();

            for(Metadata.Track track1 : track.getAlternativeList()){
                log.debug("track test " + is_track_available(track1, country));
                if(is_track_available(track1, country)){
                    track = track1;
                    break;
                }
            }
        }
        return track;
    }

    /**
     * works
     * @param stringCallback
     */
    public void metadata_request(String[] uris, WebSocket.StringCallback stringCallback){
        SpotifyUtil spotify = new SpotifyUtil();

        Mercury.MercuryMultiGetRequest requests = Mercury.MercuryMultiGetRequest.getDefaultInstance();
        Mercury.MercuryMultiGetRequest.Builder builder = Mercury.MercuryMultiGetRequest.newBuilder();

        for(Integer i = 0; i < uris.length; i++){
            String uri_type = spotify.get_uri_type(uris[i]);
            if(uri_type.equals("local")){
                log.debug("Track with URI " + uris[i] + " is a local track, we can't request metadata, skipping");
            }else{
                String id = spotify.uri2id(uris[i]);
                Mercury.MercuryRequest request = Mercury.MercuryRequest.getDefaultInstance();
                request = request.newBuilderForType()
                        .setBody(ByteString.copyFromUtf8("GET"))
                        .setUri("hm://metadata/" + uri_type + "/" + id).build();

                builder = builder.addRequest(request);
            }
        }

        requests = builder.build();

        Map metadata_request = new LinkedHashMap();
        metadata_request.put("args", generate_multiget_args(spotify.get_uri_type(uris[0]), requests));
        metadata_request.put("name", "sp/hm_b64");
        metadata_request.put("id", String.valueOf(seq));

        sendWebsocketRequest(JSONValue.toJSONString(metadata_request), stringCallback);
    }

    /**
     * works
     * @param method
     * @return
     */
    public Integer getNumber(String method){
        if(method.equals("SUB")){
            return 1;
        }else if(method.equals("UNSUB")){
            return 2;
        }else{
            return 0;
        }
    }

    /**
     * Track Available WORKS
     * @param track
     * @param country
     * @return
     */
    public Boolean is_track_available(Metadata.Track track, String country){
        ArrayList<String> allowed_countries = new ArrayList<String>();
        ArrayList<String> forbidden_countries = new ArrayList<String>();
        Boolean available = false;


        for(Metadata.Restriction restriction : track.getRestrictionList()){

            if(!restriction.getCountriesAllowed().isEmpty()){
                for(String s : splitStringEvery(restriction.getCountriesAllowed(), 2)){
                    allowed_countries.add(s);
                }
            }

            if(!restriction.getCountriesForbidden().isEmpty()){
                for(String s : splitStringEvery(restriction.getCountriesForbidden(), 2)){
                    forbidden_countries.add(s);
                }
            }

            Boolean allowed = restriction.getCountriesAllowed().isEmpty() || allowed_countries.contains(country);
            Boolean forbidden = forbidden_countries.contains(country) && forbidden_countries.size() > 0;

            if(allowed_countries.contains(country) && forbidden_countries.contains(country)){
                allowed = true;
                forbidden = false;
            }

            Map account_type_map = new LinkedHashMap();
            account_type_map.put("premium", "SUBSCRIPTION");
            account_type_map.put("unlimited", "SUBSCRIPTION");
            account_type_map.put("free", "AD");

            Boolean applicable = restriction.getCatalogueList().contains("SUBSCRIPTION");

            for(Metadata.Restriction.Catalogue catalogue : restriction.getCatalogueList()){
                if(catalogue.name().equals(account_type_map.get(account_type))){
                    applicable = true;
                    break;
                }
            }

            if(!allowed){
                log.debug(restriction.toString());
                log.debug(allowed_countries.toString());
                log.debug(forbidden_countries.toString());
                log.debug("allowed: " + allowed);
                log.debug("forbidden: " + forbidden);
                log.debug("applicable: " + applicable);
            }

            available = true == allowed == true && forbidden == false && applicable == true;

            if(available){
                break;
            }
        }

        SpotifyUtil spotifyUtil = new SpotifyUtil();
        if(available){
            log.debug(spotifyUtil.gid2uri("track", track.getGid().toByteArray()) + " is available!");
        }else{
            log.debug(spotifyUtil.gid2uri("track", track.getGid().toByteArray()) + " is NOT available!");
        }

        return available;
    }

    public ArrayList generate_multiget_args(String metadata_type, Mercury.MercuryMultiGetRequest requests){

        log.debug(requests.getRequestCount() + "request count");

        ArrayList args = new ArrayList();
        args.add(0);
        if(requests.getRequestCount() == 1){
            String req = Base64.encodeToString(requests.getRequest(0).toByteArray(), Base64.DEFAULT);
            args.add(req);
        }else{
            Mercury.MercuryRequest header = Mercury.MercuryRequest.getDefaultInstance();
            header = header.newBuilderForType()
                    .setBody(ByteString.copyFromUtf8("GET"))
                    .setUri("hm://metadata/" + metadata_type + "s")
                    .setContentType("vnd.spotify/mercury-mget-request").build();

            String header_str = Base64.encodeToString(header.toByteArray(), Base64.DEFAULT);
            String req = Base64.encodeToString(requests.toByteArray(), Base64.DEFAULT);

            args.add(header_str);
            args.add(req);
        }

        return args;
    }

    /**
     * works
     * @param s
     * @param interval
     * @return
     */
    public String[] splitStringEvery(String s, int interval) {
        int arrayLength = (int) Math.ceil(((s.length() / (double)interval)));
        String[] result = new String[arrayLength];

        int j = 0;
        int lastIndex = result.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            result[i] = s.substring(j, j + interval);
            j += interval;
        } //Add the last bit
        result[lastIndex] = s.substring(j);

        return result;
    }

    /**
     *  Heartbeat Keep Websocket Alive
     */
    public class HeartBeat extends Thread
    {
        public HeartBeat (){}

        private long HeartBeatTime = 18000;

        public void run(){
            while (true){

                try {
                    sleep(HeartBeatTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Map json = new LinkedHashMap();
                json.put("args", "h");
                json.put("name", "sp/echo");
                json.put("id", String.valueOf(seq));

                sendWebsocketRequest(JSONValue.toJSONString(json), new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        log.debug("HeartBeat: " + s);
                    }
                });
            }
        }
    }
}
