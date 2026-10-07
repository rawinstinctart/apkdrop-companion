package de.rawinstinctai.apkdrop;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Each installer URI names one completed download; another download cannot replace its bytes. */
final class VerifiedApkFiles {
    private static final Pattern NAME=Pattern.compile("verified-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.apk");
    private VerifiedApkFiles() {}
    static String newName() { return "verified-"+UUID.randomUUID()+".apk"; }
    static boolean allowed(String name) { return name!=null && NAME.matcher(name).matches(); }

    /** Called under the downloader's process-wide lock. Keep at most one previous APK. */
    static void prepare(File dir) throws IOException {
        File[] files=dir.listFiles();
        if(files==null) throw new IOException("Privater APK-Cache kann nicht gelesen werden.");
        List<File> completed=new ArrayList<>();
        for(File file:files) {
            String name=file.getName();
            if(name.equals("verified.apk") || name.equals("verified.apk.part")
                    || (name.endsWith(".part")&&allowed(name.substring(0,name.length()-5)))) {
                if(!file.delete()) throw new IOException("Alter APK-Download kann nicht verworfen werden.");
            } else if(file.isFile() && allowed(name)) completed.add(file);
        }
        completed.sort(Comparator.comparingLong(File::lastModified));
        while(completed.size()>1) {
            if(!completed.remove(0).delete()) throw new IOException("Alte APK kann nicht aus dem Cache entfernt werden.");
        }
    }
}
