package de.rawinstinctai.apkdrop;

import org.json.JSONObject;

/** Display-only persisted release state. Never passed to an installer or downloader. */
final class ReleaseSnapshot {
    final InstallContract release,previous;
    final long checkedAt;
    final String error;
    final boolean blocked;
    ReleaseSnapshot(InstallContract release,InstallContract previous,long checkedAt,String error,boolean blocked) {
        this.release=release; this.previous=previous; this.checkedAt=checkedAt; this.error=error; this.blocked=blocked;
    }
    ReleaseSnapshot success(InstallContract next,long time) {
        InstallContract baseline=release!=null && release.versionCode<next.versionCode?release:previous;
        return new ReleaseSnapshot(next,baseline,time,null,false);
    }
    ReleaseSnapshot failed(String message,boolean security,long time) {
        return new ReleaseSnapshot(release,previous,time,message,security);
    }
    String encode() throws Exception {
        JSONObject out=new JSONObject().put("schema",1).put("checkedAt",checkedAt).put("blocked",blocked);
        out.put("error",error==null?JSONObject.NULL:error);
        out.put("release",release==null?JSONObject.NULL:release.json());
        out.put("previous",previous==null?JSONObject.NULL:previous.json());
        String body=out.toString(); if(body.length()>280000) throw new IllegalArgumentException("Prüfstand zu groß.");
        return body;
    }
    static ReleaseSnapshot decode(String body,AppLibrary.Entry entry,long now) throws Exception {
        if(body==null) return null;
        if(body.length()>280000) throw new IllegalArgumentException("Prüfstand zu groß.");
        JSONObject data=new JSONObject(body);
        long at=data.getLong("checkedAt");
        if(data.getInt("schema")!=1 || at<1 || at>now+300000) throw new IllegalArgumentException("Ungültiger Prüfstand.");
        InstallContract release=data.isNull("release")?null:InstallContract.parse(data.getJSONObject("release").toString());
        InstallContract previous=data.isNull("previous")?null:InstallContract.parse(data.getJSONObject("previous").toString());
        if(release!=null) entry.requireIdentity(release.slug,release.packageName,release.signers);
        if(previous!=null) entry.requireIdentity(previous.slug,previous.packageName,previous.signers);
        String error=data.isNull("error")?null:data.getString("error");
        if(error!=null && error.length()>500) throw new IllegalArgumentException("Ungültiger Prüfstand.");
        return new ReleaseSnapshot(release,previous,at,error,data.getBoolean("blocked"));
    }
}
