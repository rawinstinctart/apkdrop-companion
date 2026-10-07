package de.rawinstinctai.apkdrop;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Small, bounded local list; saved identities cannot silently change on a later fetch. */
final class AppLibrary {
    static final int MAX_APPS=50;
    private static final String PREFIX="apkdrop.library.v1:";
    private static final int MAX_BYTES=65536;
    private static final Pattern SLUG=Pattern.compile("[a-z0-9-]{3,40}");
    private static final Pattern PACKAGE=Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+");
    private static final Pattern SHA=Pattern.compile("[a-f0-9]{64}");

    static final class Entry {
        final String slug,name,packageName;
        final Set<String> signers;
        Entry(String slug,String name,String packageName,Set<String> signers) {
            if(slug==null || !SLUG.matcher(slug).matches() || name==null || name.trim().isEmpty() || name.length()>160
                    || packageName==null || packageName.length()>255 || !PACKAGE.matcher(packageName).matches()
                    || signers==null || signers.isEmpty() || signers.size()>8)
                throw new IllegalArgumentException("Ungültiger Eintrag in Meine Apps.");
            for(String signer:signers)
                if(signer==null || !SHA.matcher(signer).matches()) throw new IllegalArgumentException("Ungültiger gespeicherter Signierer.");
            this.slug=slug; this.name=name; this.packageName=packageName;
            this.signers=Collections.unmodifiableSet(new LinkedHashSet<>(signers));
        }

        void requireIdentity(String slug,String packageName,Set<String> signers) {
            if(!this.slug.equals(slug)) throw new SecurityException("Der Release gehört zu einer anderen APKDrop-App.");
            if(!this.packageName.equals(packageName)) throw new SecurityException("Die Package-ID weicht von deiner gespeicherten App ab.");
            if(!this.signers.equals(signers)) throw new SecurityException("Der Signierschlüssel weicht von deiner gespeicherten App ab.");
        }
    }

    private final List<Entry> entries;
    AppLibrary() { entries=Collections.emptyList(); }
    private AppLibrary(List<Entry> entries) { this.entries=Collections.unmodifiableList(new ArrayList<>(entries)); }
    List<Entry> entries() { return entries; }
    Entry find(String slug) {
        for(Entry entry:entries) if(entry.slug.equals(slug)) return entry;
        return null;
    }

    AppLibrary add(Entry next) {
        List<Entry> copy=new ArrayList<>(entries);
        Entry prior=find(next.slug);
        if(prior!=null) {
            prior.requireIdentity(next.slug,next.packageName,next.signers);
            copy.set(copy.indexOf(prior),next);
        } else {
            for(Entry entry:copy)
                if(entry.packageName.equals(next.packageName)) throw new IllegalArgumentException("Diese App ist bereits in Meine Apps gespeichert.");
            if(copy.size()>=MAX_APPS) throw new IllegalArgumentException("Du kannst bis zu 50 Apps hinzufügen.");
            copy.add(next);
        }
        return new AppLibrary(copy);
    }

    AppLibrary remove(String slug) {
        List<Entry> copy=new ArrayList<>();
        for(Entry entry:entries) if(!entry.slug.equals(slug)) copy.add(entry);
        return new AppLibrary(copy);
    }

    String encode() throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.writeInt(1); out.writeInt(entries.size());
            for(Entry entry:entries) {
                out.writeUTF(entry.slug); out.writeUTF(entry.name); out.writeUTF(entry.packageName);
                out.writeInt(entry.signers.size());
                for(String signer:entry.signers) out.writeUTF(signer);
            }
        }
        if(bytes.size()>MAX_BYTES) throw new IOException("Die lokale App-Liste ist zu groß.");
        return PREFIX+Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    static AppLibrary decode(String saved) throws IOException {
        if(saved==null) return new AppLibrary();
        if(saved.length()>100000 || !saved.startsWith(PREFIX)) throw new IOException("Unbekanntes Format der lokalen App-Liste.");
        try {
            byte[] bytes=Base64.getDecoder().decode(saved.substring(PREFIX.length()));
            if(bytes.length>MAX_BYTES) throw new IOException("Die lokale App-Liste ist zu groß.");
            try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))) {
                if(in.readInt()!=1) throw new IOException("Unbekannte Version der lokalen App-Liste.");
                int count=in.readInt();
                if(count<0 || count>MAX_APPS) throw new IOException("Ungültige Anzahl gespeicherter Apps.");
                AppLibrary result=new AppLibrary();
                for(int i=0;i<count;i++) {
                    String slug=in.readUTF(),name=in.readUTF(),pkg=in.readUTF();
                    int n=in.readInt();
                    if(n<1 || n>8) throw new IOException("Ungültige Signiererliste.");
                    Set<String> signers=new LinkedHashSet<>();
                    for(int j=0;j<n;j++) if(!signers.add(in.readUTF())) throw new IOException("Doppelter Signierer.");
                    if(result.find(slug)!=null) throw new IOException("Doppelter App-Eintrag.");
                    result=result.add(new Entry(slug,name,pkg,signers));
                }
                if(in.read()!=-1) throw new IOException("Unerwartete Daten in der lokalen App-Liste.");
                return result;
            }
        } catch(IllegalArgumentException e) { throw new IOException("Die lokale App-Liste kann nicht gelesen werden.",e); }
    }
}
