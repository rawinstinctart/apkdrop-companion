package de.rawinstinctai.apkdrop;

import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Only APKDrop's scoped radar and explicit private-import endpoints. No GitHub token, cookies, or redirects. */
final class RadarClient {
    static final class Unauthorized extends IOException {Unauthorized(){super("GitHub Radar bitte neu verbinden.");}}
    static String verificationPath(String id){if(id==null||!id.matches("[a-f0-9]{32}"))throw new SecurityException("Ungültige Verbindung.");return "/companion/connect?code="+id;}
    static String importPath(String repository){if(repository==null||!repository.matches("[A-Za-z0-9-]{1,39}/[A-Za-z0-9_.-]{1,100}"))throw new SecurityException("Ungültiges Repository.");return "/onboarding?repo="+android.net.Uri.encode("https://github.com/"+repository);}
    static JSONObject request(String path,JSONObject body,String token) throws Exception {
        if(!path.matches("/api/companion/(?:pair(?:/status)?|disconnect|apps|import(?:/preview)?|radar\\?offset=[0-9]{1,4})"))throw new SecurityException("Unzulässiger Radar-Pfad.");
        HttpURLConnection c=(HttpURLConnection)new URL(StoreClient.ORIGIN+path).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(60000);
        c.setRequestProperty("Accept","application/json");c.setRequestProperty("Accept-Encoding","identity");
        if(token!=null){if(!token.matches("[a-f0-9]{64}"))throw new SecurityException("Ungültige Radar-Verbindung.");c.setRequestProperty("Authorization","Bearer "+token);}
        try {
            if(body!=null){c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int code=c.getResponseCode();if(code==401)throw new Unauthorized();
            if(code!=200)throw new IOException(code==410?"Verbindung abgelaufen. Bitte erneut starten.":code==429?"Kurz warten und erneut prüfen.":"Radar nicht erreichbar. Bitte erneut versuchen.");
            if(c.getContentType()==null||!c.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("application/json")||c.getContentLengthLong()>1048576)throw new SecurityException("Ungültige Radar-Antwort.");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>1048576)throw new SecurityException("Radar-Antwort zu groß.");out.write(b,0,n);}return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            }
        }finally{c.disconnect();}
    }
}
