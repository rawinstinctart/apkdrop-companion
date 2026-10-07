package de.rawinstinctai.apkdrop;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

final class InstallContract {
    static final long MAX_BYTES = 250L * 1024 * 1024;
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9-]{3,40}$");
    private static final Pattern PACKAGE = Pattern.compile("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$");
    private static final Pattern HEX = Pattern.compile("^[a-f0-9]{64}$");

    final String slug, appName, releaseId, version, packageName, sha256, channel, notes, downloadUrl, receiptUrl, showcaseUrl;
    final long versionCode, size;
    final int minSdk, targetSdk;
    final List<String> abis;
    final Set<String> permissions, signers;

    InstallContract(String slug,String appName,String releaseId,String version,long versionCode,String packageName,
                    int minSdk,int targetSdk,List<String> abis,Set<String> permissions,Set<String> signers,
                    String sha256,long size,String channel,String notes,String downloadUrl,String receiptUrl,String showcaseUrl) {
        this.slug=slug; this.appName=appName; this.releaseId=releaseId; this.version=version; this.versionCode=versionCode;
        this.packageName=packageName; this.minSdk=minSdk; this.targetSdk=targetSdk;
        this.abis=Collections.unmodifiableList(new ArrayList<>(abis));
        this.permissions=Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
        this.signers=Collections.unmodifiableSet(new LinkedHashSet<>(signers));
        this.sha256=sha256; this.size=size; this.channel=channel; this.notes=notes;
        this.downloadUrl=downloadUrl; this.receiptUrl=receiptUrl; this.showcaseUrl=showcaseUrl;
    }

    static InstallContract parse(String body) throws Exception {
        if(body==null || body.length()>131072) throw new SecurityException("APKDrop-Antwort ist zu groß.");
        JSONObject root=new JSONObject(body);
        if(!"apkdrop.install.v1".equals(root.optString("schema"))) throw new SecurityException("Unbekannter APKDrop-Installationsvertrag.");
        if(!"ready".equals(root.optString("status"))) {
            throw new SecurityException("verification_incomplete".equals(root.optString("reason"))
                    ? "Dieser Release ist noch nicht vollständig geprüft." : "Noch kein geprüfter Release verfügbar.");
        }
        JSONObject policy=root.getJSONObject("policy");
        if(!"sha256".equals(policy.optString("hash")) || !"exact".equals(policy.optString("signers"))
                || !"package+versionCode".equals(policy.optString("identity")) || policy.optBoolean("keyRotation",true))
            throw new SecurityException("Unbekannte APKDrop-Prüfregeln.");

        JSONObject app=root.getJSONObject("app"), r=root.getJSONObject("release");
        String slug=app.getString("slug"), name=app.getString("name"), id=r.getString("releaseId"),
                version=r.getString("version"), pkg=r.getString("packageName"),
                sha=r.getString("sha256").toLowerCase(Locale.ROOT);
        long code=r.getLong("versionCode"), size=r.getLong("size");
        int min=r.getInt("minSdk"), target=r.isNull("targetSdk")?0:r.optInt("targetSdk",0);

        if(!SLUG.matcher(slug).matches() || name.isBlank() || name.length()>160 || id.isBlank() || id.length()>100
                || version.isBlank() || version.length()>120 || code<1 || !PACKAGE.matcher(pkg).matches()
                || min<1 || size<1 || size>MAX_BYTES || !HEX.matcher(sha).matches())
            throw new SecurityException("Ungültige Release-Daten.");

        List<String> abis=readArray(r.getJSONArray("abis"),8,80,false);
        Set<String> permissions=new LinkedHashSet<>(readArray(r.getJSONArray("permissions"),512,220,false));
        List<String> signerList=readArray(r.getJSONArray("signers"),8,64,true);
        Set<String> signers=new LinkedHashSet<>(signerList);
        if(signers.isEmpty() || signers.size()!=signerList.size()) throw new SecurityException("Ungültige Signaturdaten.");

        String download=trusted(r.getString("downloadUrl"),slug,true);
        String receipt=trusted(r.getString("receiptUrl"),slug,false);
        String showcase=trusted(r.getString("showcaseUrl"),slug,false);
        String notes=r.optString("notes","");
        if(notes.length()>12000) throw new SecurityException("Release-Notizen sind zu groß.");

        return new InstallContract(slug,name,id,version,code,pkg,min,target,abis,permissions,signers,sha,size,
                r.optString("channel","stable"),notes,download,receipt,showcase);
    }

    private static List<String> readArray(JSONArray array,int max,int maxLen,boolean hex) throws Exception {
        if(array.length()>max) throw new SecurityException("Zu viele Release-Merkmale.");
        List<String> out=new ArrayList<>();
        for(int i=0;i<array.length();i++) {
            String value=array.getString(i);
            if(value.isBlank() || value.length()>maxLen) throw new SecurityException("Ungültige Release-Merkmale.");
            if(hex) {
                value=value.toLowerCase(Locale.ROOT);
                if(!HEX.matcher(value).matches()) throw new SecurityException("Ungültiger Signatur-Fingerprint.");
            }
            out.add(value);
        }
        return out;
    }

    private static String trusted(String value,String slug,boolean immutableApk) {
        URI uri=URI.create(value);
        if(!"https".equals(uri.getScheme()) || !"apkdrop.rawinstinctai.de".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo()!=null || uri.getFragment()!=null || uri.getQuery()!=null
                || (uri.getPort()!=-1 && uri.getPort()!=443))
            throw new SecurityException("Unzulässige APKDrop-Adresse.");
        if(immutableApk && !uri.getPath().matches("^/"+Pattern.quote(slug)+"/releases/[A-Za-z0-9-]+\\.apk$"))
            throw new SecurityException("APK-Download ist nicht unveränderlich.");
        return uri.toString();
    }
}
