package de.rawinstinctai.apkdrop;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class ContractClient {
    private static final String ORIGIN="https://apkdrop.rawinstinctai.de";
    private ContractClient() {}

    static InstallContract fetch(String slug) throws Exception {
        String endpoint=ORIGIN+"/api/"+slug+"/install.json";
        URI uri=URI.create(endpoint);
        if(!"https".equals(uri.getScheme()) || !"apkdrop.rawinstinctai.de".equalsIgnoreCase(uri.getHost()))
            throw new SecurityException("Nur APKDrop-Verträge sind erlaubt.");

        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        c.setInstanceFollowRedirects(false); c.setConnectTimeout(10000); c.setReadTimeout(20000);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("Accept-Encoding","identity");
        c.setRequestProperty("User-Agent","APKDrop-Companion/0.1");

        try {
            int status=c.getResponseCode();
            if(status!=200) throw new IllegalStateException(status==404
                    ? "Diese App ist noch nicht als geprüfter APKDrop verfügbar."
                    : "APKDrop ist gerade nicht erreichbar ("+status+").");
            String type=c.getHeaderField("Content-Type");
            if(type==null || !type.toLowerCase(Locale.ROOT).startsWith("application/json"))
                throw new SecurityException("Unerwarteter Antworttyp.");

            try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];
                for(int n;(n=in.read(buffer))!=-1;) {
                    if(out.size()+n>131072) throw new SecurityException("APKDrop-Antwort ist zu groß.");
                    out.write(buffer,0,n);
                }
                return InstallContract.parse(out.toString(StandardCharsets.UTF_8));
            }
        } finally { c.disconnect(); }
    }
}
