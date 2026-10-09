package de.rawinstinctai.apkdrop;

final class DisplayText {
    static String version(String value){String v=value==null?"":value.trim();return v.isEmpty()?"Version offen":v.startsWith("v")||v.startsWith("V")?v:"v"+v;}
    static String proposals(int n){return n+" "+(n==1?"APK-Vorschlag":"APK-Vorschläge");}
    static String date(String value){try{return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date.from(java.time.Instant.parse(value)));}catch(Exception e){return value==null?"":value;}}
    private DisplayText(){}
}
