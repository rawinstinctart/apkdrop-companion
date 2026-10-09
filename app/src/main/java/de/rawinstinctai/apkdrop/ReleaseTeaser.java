package de.rawinstinctai.apkdrop;
final class ReleaseTeaser {
    static String summary(String source) {
        if(source==null || source.isBlank())return "Neue Version verfügbar.";
        String[] lines=source.split("\\r?\\n");StringBuilder out=new StringBuilder();
        for(String line:lines){if(line.isBlank())continue;if(out.length()>0)out.append(" · ");out.append(line.trim());if(out.length()>=240 || out.indexOf(" · ")>=0)break;}
        if(out.length()>240){int end=239;if(Character.isHighSurrogate(out.charAt(end-1)))end--;return out.substring(0,end)+"…";}
        return out.toString();
    }
}
