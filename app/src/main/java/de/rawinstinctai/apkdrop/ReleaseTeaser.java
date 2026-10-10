package de.rawinstinctai.apkdrop;
final class ReleaseTeaser {
    private static final int MAX_LENGTH=135;
    /** One scannable update note; source comes from the shared, filtered backend API. */
    static String summary(String source) {
        if(source==null || source.isBlank())return "Neue Version verfügbar.";
        for(String raw:source.split("\\r?\\n")) {
            String line=raw.trim();
            if(line.isEmpty())continue;
            if(line.length()<=MAX_LENGTH)return line;
            int end=MAX_LENGTH-1;
            if(Character.isHighSurrogate(line.charAt(end-1)))end--;
            return line.substring(0,end).replaceFirst("\\s+\\S*$","").stripTrailing()+"…";
        }
        return "Neue Version verfügbar.";
    }
}
