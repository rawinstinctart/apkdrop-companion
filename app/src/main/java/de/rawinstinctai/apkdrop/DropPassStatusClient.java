package de.rawinstinctai.apkdrop;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Anonymous POST only. Total wall-clock deadline includes DNS/connect/read; errors remain UNKNOWN. */
final class DropPassStatusClient implements DropPassVerifier.Status {
    static final String ORIGIN="https://apkdrop.rawinstinctai.de";
    static final String STAGING="https://apkdrop-staging-droppass.rawinstinctart.workers.dev";
    private final String origin;
    interface Connections { HttpURLConnection open(String url) throws IOException; }
    private final Connections connections;
    DropPassStatusClient(Connections connections) {this.origin=ORIGIN;this.connections=connections;}
    String origin() {return origin;}
    DropPassStatusClient() {
        connections=url->(HttpURLConnection)new URL(url).openConnection();
        origin=BuildConfig.DEBUG && STAGING.equals(BuildConfig.DROPPASS_DEBUG_ORIGIN)?STAGING:ORIGIN;
    }
    @Override public String query(String passId) {
        ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"droppass-status");t.setDaemon(true);return t;});
        Future<String> task=executor.submit(()->request(passId));
        try {return task.get(2800,TimeUnit.MILLISECONDS);}
        catch(Exception failure){task.cancel(true);return "unavailable";}
        finally {executor.shutdownNow();}
    }
    private String request(String passId) throws Exception {
        // The app has no cookie manager. Fail closed if one is introduced, rather than use ambient cookies.
        if(CookieHandler.getDefault()!=null)return "unavailable";
        HttpURLConnection c=connections.open(origin+"/api/droppass/status.json");
        try {
            c.setConnectTimeout(1200);c.setReadTimeout(1200);c.setInstanceFollowRedirects(false);
            c.setRequestMethod("POST");c.setDoOutput(true);c.setUseCaches(false);
            c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
            // Empty auth and fixed-length streaming prevent authentication retries; no redirect follow-up.
            c.setRequestProperty("Cookie","");c.setRequestProperty("Authorization","");
            byte[] body=("{\"passId\":"+DropPassJson.canonical(passId)+"}").getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=c.getOutputStream()){out.write(body);}
            if(c.getResponseCode()!=200)return "unavailable";
            try(InputStream in=c.getInputStream()) {
                ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] chunk=new byte[256];
                for(int n;(n=in.read(chunk))!=-1;){if(b.size()+n>4096)throw new IOException("Status too large");b.write(chunk,0,n);}
                return parse(StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(b.toByteArray())).toString());
            }
        } finally {c.disconnect();}
    }
    static String parse(String json) {
        try {
            Map<String,Object> value=DropPassJson.object(DropPassJson.parse(json));
            if(value.size()==1 && Arrays.asList("ok","revoked","unknown").contains(value.get("status")))return (String)value.get("status");
        } catch(Exception malformed) { /* UNKNOWN */ }
        return "unavailable";
    }
}
