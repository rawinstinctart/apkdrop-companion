package de.rawinstinctai.apkdrop;

import org.json.JSONObject;

/** Frozen, freshly loaded private-import selection. Never an install or publication authorization. */
final class ImportPreview {
    final String repository,name,description,version,filename;
    final long size;
    final boolean privateRepository,prerelease;
    private ImportPreview(String repository,JSONObject repo,JSONObject selected) throws Exception {
        this.repository=repository;
        name=bounded(repo.optString("name"),160);
        description=bounded(repo.optString("description"),1000);
        privateRepository=repo.optBoolean("private");
        version=selected.getString("version");filename=selected.getString("filename");size=selected.getLong("size");
        prerelease=selected.optBoolean("prerelease");
        if(version.isBlank()||version.length()>120||filename.isBlank()||filename.length()>255
                ||version.codePoints().anyMatch(Character::isISOControl)||filename.codePoints().anyMatch(Character::isISOControl)
                ||filename.contains("/")||filename.contains("\\")||!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")
                ||size<1||size>InstallContract.MAX_BYTES)throw new SecurityException("APK-Auswahl ungültig. Bitte erneut prüfen.");
    }
    static ImportPreview parse(String repository,JSONObject data) throws Exception {
        RadarClient.importPath(repository);
        if(!"ready".equals(data.optString("state")))throw new SecurityException("Keine passende APK verfügbar. Bitte Radar erneut prüfen.");
        // Both repository and selection belong to the preview request for this exact repository.
        return new ImportPreview(repository,data.getJSONObject("repository"),data.getJSONObject("selection"));
    }
    String summary(){return (description.isBlank()?"":description+"\n\n")+DisplayText.version(version)
            +" · "+(prerelease?"Beta":"Stable")+"\nDatei: "+filename+"\nGröße: "+String.format(java.util.Locale.GERMANY,"%.1f MB",size/1048576.0)
            +"\nQuelle: github.com/"+repository+"\nRepository: "+(privateRepository?"Privat":"Öffentlich")
            +"\n\nDie App wird als privater Entwurf übernommen. APK, Paket und Signatur werden erst danach geprüft. Veröffentlichung und Installation bestätigst du getrennt.";}
    JSONObject request() throws Exception {return new JSONObject().put("repo",repository).put("version",version)
            .put("filename",filename).put("includeBeta",prerelease).put("importConsent",true);}
    private static String bounded(String text,int limit){return text.substring(0,Math.min(limit,text.length()));}
}
