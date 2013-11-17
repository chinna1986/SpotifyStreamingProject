package de.pascal.SpotifyAPI.JSON;


import org.json.JSONException;
import org.json.simple.JSONValue;

import java.util.LinkedHashMap;
import java.util.Map;

public class Credentials {
    private String ip;
    private Integer timestamp;
    private Integer ttl;
    private String useragent;
    private Integer version;
    private String token;

    public String getIp() {
        return ip;
    }

    public Integer getTimestamp() {
        return timestamp;
    }

    public Integer getTtl() {
        return ttl;
    }

    public String getUseragent() {
        return useragent;
    }

    public Integer getVersion() {
        return version;
    }

    public String getToken() {
        return token;
    }

    public String getJsonObject(){
        Map json = new LinkedHashMap();
        json.put("ip", new String(ip));
        json.put("timestamp", timestamp);
        json.put("ttl", ttl);
        json.put("useragent", "node-spotify-web in python (Chrome/13.37 compatible-ish)");
        json.put("version", version);
        json.put("token", new String(token));

        return JSONValue.toJSONString(json);
    }
}
