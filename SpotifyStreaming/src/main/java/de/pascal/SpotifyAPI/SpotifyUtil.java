package de.pascal.SpotifyAPI;

import android.util.Log;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class SpotifyUtil {

    public static final String DIGITS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     *
     * @param gid
     * @return
     */
    public String gid2id(byte[] gid){
        return byteArrayToHexString(gid);
    }

    /**
     *
     * @param uriType
     * @param v
     * @return
     */
    public String id2uri(String uriType, String v){
        BigInteger vInt = new BigInteger(v, 16);
        List<BigInteger> list = new ArrayList<BigInteger>();
        while (!BigInteger.ZERO.equals(vInt)){
            list.add(vInt.mod(BigInteger.valueOf(62)));
            vInt = vInt.divide(BigInteger.valueOf(62));
        }
        StringBuilder URI = new StringBuilder();
        for(Integer i = 0; i < list.size(); i++){
            URI.append(DIGITS.charAt(list.get(i).intValue()));
        }
        return "spotify:" + uriType + ":" + URI.reverse();
    }

    /**
     *
     * @param uri
     * @return
     */
    public String uri2id(String uri){
        String[] parts = uri.split(":");
        String s = "";
        if(parts.length > 3 && parts[3] == "playlist"){
            s = parts[4];
        }else{
            s = parts[2];
        }
        Log.d("SpotifyAPI", s);
        BigInteger v = BigInteger.valueOf(0);
        for (char c: s.toCharArray()) {
            v = v.multiply(BigInteger.valueOf(62));
            v = v.add(BigInteger.valueOf(DIGITS.indexOf(c)));
        }

        //append a zero to the string ig the string is not 32 length
        StringBuffer sb = new StringBuffer();
        sb.append(v.toString(16));
        while (sb.length() < 32) {
            sb.append(0);
        }
        return sb.toString();
    }

    /**
     *
     * @param uriType
     * @param gid
     * @return
     */
    public String gid2uri(String uriType, byte[] gid){
        String id = gid2id(gid);
        return id2uri(uriType, id);
    }

    /**
     *
     * @param uri
     * @return
     */
    public String get_uri_type(String uri){
        String[] uri_parts = uri.split(":");
        if(uri_parts.length >= 3 && uri_parts[1] == "local"){
            return "local";
        }else if(uri_parts.length >= 5){
            return uri_parts[3];
        }else if (uri_parts.length >= 4 && uri_parts[3] == "starred"){
            return "playlist";
        }else if(uri_parts.length >= 3){
            return uri_parts[1];
        }else{
            return null;
        }
    }

    /**
     *
     * @param uri
     * @return
     */
    public Boolean is_local(String uri){
        return get_uri_type(uri) == "local";
    }



    /**
     *
     * @param array
     * @return
     */
    public static String byteArrayToHexString(byte[] array) {
        StringBuffer hexString = new StringBuffer();
        for (byte b : array) {
            int intVal = b & 0xff;
            if (intVal < 0x10)
                hexString.append("0");
            hexString.append(Integer.toHexString(intVal));
        }
        return hexString.toString();
    }
}
