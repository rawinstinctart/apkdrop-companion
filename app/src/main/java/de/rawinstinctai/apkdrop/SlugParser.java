package de.rawinstinctai.apkdrop;

import android.net.Uri;
import java.util.regex.Pattern;

final class SlugParser {
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9-]{3,40}$");
    private SlugParser() {}

    /** Accept text shared from Android without trusting arbitrary hosts or URL suffixes. */
    static String parseShared(String raw) {
        if(raw==null || raw.length()>4096)
            throw new IllegalArgumentException("Der geteilte Text ist zu lang.");
        String text=raw.trim();
        java.util.regex.Matcher urls=Pattern.compile("[a-z][a-z0-9+.-]*://[^\\s<>]+",Pattern.CASE_INSENSITIVE).matcher(text);
        if(urls.find()&&urls.find()) throw new IllegalArgumentException("Bitte nur einen Link gleichzeitig teilen.");
        try { return parse(text); } catch(IllegalArgumentException invalid) { /* Optional surrounding message. */ }
        java.util.regex.Matcher links=Pattern.compile(
                "https://apkdrop\\.rawinstinctai\\.de/(?:install/)?[a-z0-9-]{3,40}/?(?=$|\\s|[!)}](?=\\s|$))",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if(!links.find()) throw new IllegalArgumentException("Teile einen gültigen APKDrop-Link.");
        String match=links.group();
        if(links.find()) throw new IllegalArgumentException("Bitte nur einen APKDrop-Link gleichzeitig teilen.");
        return parse(match);
    }

    static String parse(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (SLUG.matcher(value).matches()) return value;

        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme(), host = uri.getHost(), path = uri.getPath();

        if(uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || (uri.getPort()!=-1 && !("https".equals(scheme) && uri.getPort()==443)))
            throw new IllegalArgumentException("Unzulässiger APKDrop-Link.");
        if ("apkdrop".equals(scheme) && "install".equals(host) && path != null && path.matches("^/[a-z0-9-]{3,40}$")) {
            return path.substring(1);
        }
        if ("https".equals(scheme) && "apkdrop.rawinstinctai.de".equalsIgnoreCase(host) && path != null) {
            if (path.matches("^/install/[a-z0-9-]{3,40}/?$")) return path.replaceFirst("^/install/", "").replace("/", "");
            if (path.matches("^/[a-z0-9-]{3,40}/?$")) return path.substring(1).replace("/", "");
        }
        throw new IllegalArgumentException("Füge einen APKDrop-Link oder einen gültigen App-Slug ein.");
    }
}
