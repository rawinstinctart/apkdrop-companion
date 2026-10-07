package de.rawinstinctai.apkdrop;

import android.net.Uri;
import java.util.regex.Pattern;

final class SlugParser {
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9-]{3,40}$");
    private SlugParser() {}

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
