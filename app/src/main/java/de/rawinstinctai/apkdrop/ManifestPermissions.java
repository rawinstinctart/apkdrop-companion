package de.rawinstinctai.apkdrop;

import com.android.apksig.internal.apk.AndroidBinXmlParser;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;

/** Compares declarations in the signed APK, not Android/OEM-normalized package permissions.
 * Uses the binary XML parser already shipped with the pinned apksig dependency. */
final class ManifestPermissions {
    private static final int MAX_MANIFEST_BYTES=1024*1024;
    private static final String ANDROID="http://schemas.android.com/apk/res/android";
    private ManifestPermissions() {}

    static Set<String> read(File apk) throws Exception {
        try(ZipFile zip=new ZipFile(apk)) {
            ZipEntry entry=null;
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry candidate=entries.nextElement();
                if("AndroidManifest.xml".equals(candidate.getName())) {
                    if(entry!=null) throw new SecurityException("APK enthält mehrere Manifeste.");
                    entry=candidate;
                }
            }
            if(entry==null || entry.isDirectory() || entry.getSize()>MAX_MANIFEST_BYTES)
                throw new SecurityException("APK-Manifest fehlt oder ist zu groß.");
            try(InputStream in=zip.getInputStream(entry); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];
                for(int n;(n=in.read(buffer))!=-1;) {
                    if(out.size()+n>MAX_MANIFEST_BYTES) throw new SecurityException("APK-Manifest ist zu groß.");
                    out.write(buffer,0,n);
                }
                return parse(out.toByteArray());
            }
        }
    }

    static Set<String> parse(byte[] data) throws Exception {
        // apksig tolerates some truncated trailing chunks like Android does. This contract does not.
        if(data==null || data.length<8 || data.length>MAX_MANIFEST_BYTES)
            throw new SecurityException("Ungültiges APK-Manifest.");
        ByteBuffer bytes=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int header=Short.toUnsignedInt(bytes.getShort(2));
        if(Short.toUnsignedInt(bytes.getShort(0))!=3 || header!=8 || bytes.getInt(4)!=data.length)
            throw new SecurityException("Ungültiger Manifest-XML-Header.");
        int chunks=0;
        for(int offset=header;offset<data.length;) {
            if(data.length-offset<8 || ++chunks>8192) throw new SecurityException("Unvollständiges APK-Manifest.");
            int chunkHeader=Short.toUnsignedInt(bytes.getShort(offset+2)),size=bytes.getInt(offset+4);
            if(chunkHeader<8 || size<chunkHeader || size>data.length-offset)
                throw new SecurityException("Ungültiger Manifest-XML-Block.");
            offset+=size;
        }

        AndroidBinXmlParser parser=new AndroidBinXmlParser(ByteBuffer.wrap(data));
        Set<String> permissions=new LinkedHashSet<>();
        Deque<String> elements=new ArrayDeque<>();
        boolean manifest=false,closed=false;
        int declarations=0;
        for(int event=parser.next();event!=AndroidBinXmlParser.EVENT_END_DOCUMENT;event=parser.next()) {
            if(event==AndroidBinXmlParser.EVENT_START_ELEMENT) {
                String name=parser.getName(),namespace=parser.getNamespace();
                if(elements.isEmpty()) {
                    if(manifest || closed || !"manifest".equals(name) || !namespace.isEmpty())
                        throw new SecurityException("Ungültige Manifest-Wurzel.");
                    manifest=true;
                }
                if(parser.getDepth()>64 || parser.getAttributeCount()>256)
                    throw new SecurityException("APK-Manifest überschreitet Prüflimits.");
                if(parser.getDepth()==2 && namespace.isEmpty()
                        && ("uses-permission".equals(name) || "uses-permission-sdk-23".equals(name)
                            || "uses-permission-sdk-m".equals(name))) {
                    if(++declarations>512) throw new SecurityException("Zu viele deklarierte Berechtigungen.");
                    String permission=null;
                    for(int i=0;i<parser.getAttributeCount();i++) {
                        if("name".equals(parser.getAttributeName(i)) && ANDROID.equals(parser.getAttributeNamespace(i))) {
                            if(permission!=null || parser.getAttributeValueType(i)!=AndroidBinXmlParser.VALUE_TYPE_STRING
                                    || (parser.getAttributeNameResourceId(i)!=0
                                        && parser.getAttributeNameResourceId(i)!=0x01010003))
                                throw new SecurityException("Ungültiger Berechtigungsname im APK-Manifest.");
                            permission=parser.getAttributeStringValue(i);
                        }
                    }
                    if(permission==null || permission.isEmpty() || permission.length()>220
                            || !permission.matches("[A-Za-z0-9_.]+"))
                        throw new SecurityException("Ungültige deklarierte Berechtigung.");
                    permissions.add(permission);
                }
                elements.push(namespace+"|"+name);
            } else if(event==AndroidBinXmlParser.EVENT_END_ELEMENT) {
                if(elements.isEmpty() || !elements.pop().equals(parser.getNamespace()+"|"+parser.getName()))
                    throw new SecurityException("Unvollständige Manifest-Struktur.");
                if(elements.isEmpty()) closed=true;
            }
        }
        if(!manifest || !closed || !elements.isEmpty()) throw new SecurityException("Unvollständiges APK-Manifest.");
        return Collections.unmodifiableSet(permissions);
    }

    static void requireExact(Set<String> declared,Set<String> expected) {
        if(declared.equals(expected)) return;
        Set<String> missing=new TreeSet<>(expected); missing.removeAll(declared);
        Set<String> added=new TreeSet<>(declared); added.removeAll(expected);
        throw new SecurityException("Berechtigungen der APK stimmen nicht mit APKDrop überein."
                +(missing.isEmpty()?"":"\nFehlen im APK-Manifest: "+missing)
                +(added.isEmpty()?"":"\nZusätzlich im APK-Manifest: "+added));
    }
}
