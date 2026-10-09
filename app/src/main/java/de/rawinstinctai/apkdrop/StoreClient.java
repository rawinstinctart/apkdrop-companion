package de.rawinstinctai.apkdrop;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Display-only public data. Nothing in this client authorizes installation. */
final class StoreClient {
    static final String ORIGIN="https://apkdrop.rawinstinctai.de";
    private StoreClient() {}
    static String slug(String value) {
        if(value==null || !value.matches("[a-z0-9-]{3,40}")) throw new SecurityException("Ungültige App.");
        return value;
    }
    static String handle(String value) {
        if(value==null || !value.matches("[a-z0-9](?:[a-z0-9-]{0,37}[a-z0-9])?")) throw new SecurityException("Ungültiges Entwicklerprofil.");
        return value;
    }
    static URI imageUri(String value) {
        URI u=URI.create(value.startsWith("/")?ORIGIN+value:value);
        if(!"https".equals(u.getScheme()) || !"apkdrop.rawinstinctai.de".equals(u.getHost())
                || u.getUserInfo()!=null || u.getFragment()!=null || (u.getPort()!=-1 && u.getPort()!=443)
                || !(u.getPath().matches("/[a-z0-9-]{3,40}/icon") || u.getPath().matches("/api/[a-z0-9-]{3,40}/screenshot/[0-5]")))
            throw new SecurityException("Unzulässige Bildadresse.");
        return u;
    }
    /** Only the public DropID owner's immutable GitHub ID may select a GitHub avatar. */
    static URI avatarUri(String value, String githubId) {
        if(githubId==null || !githubId.matches("[1-9][0-9]{0,19}") || value==null)
            throw new SecurityException("Unzulässige Entwicklerbild-Adresse.");
        final URI u;
        try { u=URI.create(value); }catch(IllegalArgumentException malformed) {
            throw new SecurityException("Unzulässige Entwicklerbild-Adresse.");
        }
        if(!"https".equals(u.getScheme()) || !"avatars.githubusercontent.com".equals(u.getHost())
                || u.getUserInfo()!=null || u.getPort()!=-1 || u.getFragment()!=null
                || !("/u/"+githubId).equals(u.getRawPath())
                || (u.getRawQuery()!=null && !"v=4".equals(u.getRawQuery())))
            throw new SecurityException("Unzulässige Entwicklerbild-Adresse.");
        return u;
    }
    static JSONObject get(String path) throws Exception { return json(path,null); }
    static JSONObject following(org.json.JSONArray ids) throws Exception {
        return json("/api/following",new JSONObject().put("slugs",new org.json.JSONArray()).put("developerIds",ids));
    }
    static URI apiUri(String path) {
        if(path==null || !path.matches("/api/(?:discover(?:\\?(?:page=[0-9]+|q=[^#&]*&category=[a-z]*&page=[0-9]+(?:&sort=(?:new|updated))?))?|dropid/[a-z0-9-]+\\.json|[a-z0-9-]{3,40}/store\\.json|following)"))
            throw new SecurityException("Unzulässiger API-Pfad.");
        return URI.create(ORIGIN+path);
    }
    private static JSONObject json(String path,JSONObject data) throws Exception {
        HttpURLConnection c=connect(apiUri(path));
        try {
            c.setRequestProperty("Accept","application/json");
            if(data!=null) {
                c.setRequestMethod("POST"); c.setRequestProperty("Origin",ORIGIN);
                c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);
                try(OutputStream out=c.getOutputStream()) {out.write(data.toString().getBytes(StandardCharsets.UTF_8));}
            }
            requireResponse(c,"application/json");
            return new JSONObject(new String(read(c,1048576),StandardCharsets.UTF_8));
        } finally {c.disconnect();}
    }
    static byte[] image(String value) throws Exception {return imageBytes(imageUri(value));}
    static byte[] avatar(String value,String githubId) throws Exception {return imageBytes(avatarUri(value,githubId));}
    private static byte[] imageBytes(URI checkedUri) throws Exception {
        HttpURLConnection c=connect(checkedUri);
        try {
            c.setRequestProperty("Accept","image/png,image/jpeg,image/webp");requireResponse(c,"image/");
            String type=c.getContentType().split(";")[0].toLowerCase(Locale.ROOT);
            if(!java.util.Set.of("image/png","image/jpeg","image/webp").contains(type)) throw new SecurityException("Unbekannter Bildtyp.");
            return read(c,4*1024*1024);
        } finally {c.disconnect();}
    }
    private static HttpURLConnection connect(URI uri) throws Exception {
        if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(15000);
        c.setRequestProperty("Accept-Encoding","identity");return c;
    }
    private static void requireResponse(HttpURLConnection c,String type) throws Exception {
        int code=c.getResponseCode();
        if(code==404 || code==410)throw new SecurityException("Aktuell nicht öffentlich verfügbar.");
        if(code!=200) throw new IOException("Verbindung fehlgeschlagen. Bitte erneut versuchen.");
        if(c.getContentType()==null || !c.getContentType().toLowerCase(Locale.ROOT).startsWith(type)) throw new SecurityException("Unerwarteter Antworttyp.");
    }
    private static byte[] read(HttpURLConnection c,int max) throws Exception {
        if(c.getContentLengthLong()>max) throw new SecurityException("Antwort ist zu groß.");
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;) {
                if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                if(out.size()+n>max)throw new SecurityException("Antwort ist zu groß.");out.write(b,0,n);
            }return out.toByteArray();
        }
    }
}
