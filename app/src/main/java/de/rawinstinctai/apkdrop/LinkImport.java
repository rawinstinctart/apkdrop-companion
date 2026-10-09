package de.rawinstinctai.apkdrop;

import java.net.URI;
import java.util.Locale;
import java.util.regex.*;
import org.json.*;

/** Resolves a shared repository through public, fresh DropID evidence. Never downloads from GitHub. */
final class LinkImport {
    static String repository(String raw) {
        if(raw==null||raw.length()>4096)throw new IllegalArgumentException("Bitte einen einzelnen Release-Link teilen.");
        String value=raw.trim();
        // A surrounding Android share message may contain exactly one HTTPS URL.
        Matcher links=Pattern.compile("https://[^\\s<>]+",Pattern.CASE_INSENSITIVE).matcher(value);
        if(!links.find())throw new IllegalArgumentException("Kein unterstützter GitHub-Release-Link.");
        String link=links.group();if(links.find())throw new IllegalArgumentException("Bitte nur einen Link gleichzeitig teilen.");
        URI uri;
        try {uri=URI.create(link);}catch(Exception bad){throw new IllegalArgumentException("Ungültiger GitHub-Link.");}
        if(!"https".equalsIgnoreCase(uri.getScheme())||!"github.com".equalsIgnoreCase(uri.getHost())||uri.getUserInfo()!=null
                ||uri.getPort()!=-1||uri.getQuery()!=null||uri.getFragment()!=null||uri.getRawPath().contains("%"))
            throw new IllegalArgumentException("Unzulässiger GitHub-Release-Link.");
        Matcher path=Pattern.compile("^/([A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?)/([A-Za-z0-9_.-]{1,100})(?:/releases(?:/latest|/tag/[A-Za-z0-9_.+-]+|/download/[A-Za-z0-9_.+-]+/[A-Za-z0-9_.+-]+\\.apk)?)?/?$").matcher(uri.getPath());
        if(!path.matches()||path.group(2).equals(".")||path.group(2).equals(".."))throw new IllegalArgumentException("Teile ein GitHub-Repository, eine Release-Seite oder APK-Adresse.");
        return (path.group(1)+"/"+path.group(2)).toLowerCase(Locale.ROOT);
    }
    static String match(String repository,JSONObject profile) throws Exception {
        String owner=repository.split("/")[0];
        if(!"apkdrop.dropid.v1".equals(profile.optString("schema"))||!profile.optBoolean("published")
                ||!owner.equals(profile.optString("handle"))||!"verified".equals(profile.getJSONObject("github").optString("status")))
            throw new SecurityException("Keine aktuell bestätigte öffentliche DropID für diesen Repository-Eigentümer.");
        JSONArray apps=profile.getJSONArray("apps");if(apps.length()>100)throw new SecurityException("Zu viele App-Zuordnungen.");
        String found=null;
        for(int i=0;i<apps.length();i++) {
            JSONObject app=apps.getJSONObject(i),evidence=app.optJSONObject("repository");
            if(repository.equalsIgnoreCase(app.optString("repo"))&&evidence!=null&&"verified".equals(evidence.optString("status"))) {
                String slug=StoreClient.slug(app.getString("slug"));
                if(found!=null&&!found.equals(slug))throw new SecurityException("Mehrere Apps gehören zu diesem Repository. Bitte den APKDrop-Link nutzen.");
                found=slug;
            }
        }
        if(found==null)throw new SecurityException("Für dieses Repository gibt es keine bestätigte öffentliche APKDrop-App. Bitte ihren APKDrop-Link nutzen.");
        return found;
    }
    static String resolve(String raw) throws Exception {
        String repo=repository(raw);
        return match(repo,StoreClient.get("/api/dropid/"+StoreClient.handle(repo.split("/")[0])+".json"));
    }
}
