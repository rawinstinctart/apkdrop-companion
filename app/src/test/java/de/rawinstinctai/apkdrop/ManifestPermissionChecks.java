package de.rawinstinctai.apkdrop;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Standalone JVM regression checks, also invoked by the JUnit adapter. */
public final class ManifestPermissionChecks {
    private static final String ANDROID="http://schemas.android.com/apk/res/android";
    private static final String MIC="android.permission.RECORD_AUDIO";
    private static int passed;

    public static void main(String[] args) throws Exception {
        run();
        if(args.length>0) {
            Set<String> actual=ManifestPermissions.read(new File(args[0]));
            Set<String> expected=new LinkedHashSet<>(Arrays.asList("android.permission.INTERNET",
                    "android.permission.REQUEST_INSTALL_PACKAGES","android.permission.QUERY_ALL_PACKAGES"));
            ManifestPermissions.requireExact(actual,expected);
            System.out.println("Uploaded Companion APK manifest: PASS "+actual);
        }
        System.out.println("Manifest permission regression checks: "+passed+" PASS");
    }

    static void run() throws Exception {
        passed=0;
        Fixture utf16=new Fixture(false);
        byte[] declared=utf16.manifest(new String[]{"uses-permission"},new String[]{MIC},true,false);
        equal(Collections.singleton(MIC),ManifestPermissions.parse(declared)); // maxSdk still declared
        passed++;

        Fixture utf8=new Fixture(true);
        byte[] sdk23=utf8.manifest(new String[]{"uses-permission-sdk-23"},new String[]{MIC},false,false);
        equal(Collections.singleton(MIC),ManifestPermissions.parse(sdk23)); passed++;

        byte[] frey=new Fixture(false).manifest(
                new String[]{"uses-permission","uses-permission","uses-permission","uses-permission","uses-permission"},
                new String[]{"android.permission.INTERNET","android.permission.POST_NOTIFICATIONS",MIC,
                        "android.permission.WAKE_LOCK","art.rawinstinct.frey.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"},false,false);
        equal(5,ManifestPermissions.parse(frey).size()); passed++;

        byte[] duplicates=new Fixture(true).manifest(new String[]{"uses-permission","uses-permission"},
                new String[]{MIC,MIC},false,false);
        equal(Collections.singleton(MIC),ManifestPermissions.parse(duplicates)); passed++;

        byte[] empty=new Fixture(true).manifest(new String[0],new String[0],false,false);
        equal(Collections.emptySet(),ManifestPermissions.parse(empty)); passed++;

        ManifestPermissions.requireExact(Collections.singleton(MIC),Collections.singleton(MIC)); passed++;
        blocked(()->ManifestPermissions.requireExact(Collections.singleton(MIC),Collections.emptySet()));
        blocked(()->ManifestPermissions.requireExact(Collections.emptySet(),Collections.singleton(MIC)));
        blocked(()->ManifestPermissions.parse(Arrays.copyOf(declared,declared.length-1)));
        blocked(()->ManifestPermissions.parse(new byte[8]));
        byte[] noName=new Fixture(true).manifest(new String[]{"uses-permission"},new String[]{MIC},false,true);
        blocked(()->ManifestPermissions.parse(noName));
        byte[] incomplete=Arrays.copyOf(declared,declared.length-24);
        ByteBuffer.wrap(incomplete).order(ByteOrder.LITTLE_ENDIAN).putInt(4,incomplete.length);
        blocked(()->ManifestPermissions.parse(incomplete));

        File archive=File.createTempFile("apkdrop-permissions-",".apk");
        try {
            try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(archive))) {
                zip.putNextEntry(new ZipEntry("AndroidManifest.xml")); zip.write(frey); zip.closeEntry();
            }
            equal(ManifestPermissions.parse(frey),ManifestPermissions.read(archive)); passed++;
        } finally { archive.delete(); }

        File absent=File.createTempFile("apkdrop-missing-",".apk");
        try {
            try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(absent))) {
                zip.putNextEntry(new ZipEntry("classes.dex")); zip.write(1); zip.closeEntry();
            }
            blocked(()->ManifestPermissions.read(absent));
        } finally { absent.delete(); }
    }

    private interface Check { void run() throws Exception; }
    private static void blocked(Check check) throws Exception {
        try { check.run(); } catch(Exception expected) { passed++; return; }
        throw new AssertionError("Expected malformed/mismatching manifest to be blocked");
    }
    private static void equal(Object expected,Object actual) {
        if(!expected.equals(actual)) throw new AssertionError("Expected "+expected+", got "+actual);
    }

    private static final class Fixture {
        final boolean utf8;
        final List<String> strings=new ArrayList<>();
        Fixture(boolean utf8) { this.utf8=utf8; }
        int index(String value) {
            if(!strings.contains(value)) strings.add(value);
            return strings.indexOf(value);
        }
        byte[] manifest(String[] tags,String[] permissions,boolean maxSdk,boolean noName) throws Exception {
            index("manifest"); index(ANDROID); index("name"); index("maxSdkVersion");
            for(int i=0;i<tags.length;i++) { index(tags[i]); index(permissions[i]); }
            ByteArrayOutputStream content=new ByteArrayOutputStream();
            content.write(pool());
            content.write(element("manifest",null,false,false,false));
            for(int i=0;i<tags.length;i++) {
                content.write(element(tags[i],permissions[i],maxSdk,noName,false));
                content.write(element(tags[i],null,false,false,true));
            }
            content.write(element("manifest",null,false,false,true));
            ByteBuffer result=ByteBuffer.allocate(8+content.size()).order(ByteOrder.LITTLE_ENDIAN);
            result.putShort((short)3).putShort((short)8).putInt(result.capacity()).put(content.toByteArray());
            return result.array();
        }
        byte[] pool() throws Exception {
            ByteArrayOutputStream body=new ByteArrayOutputStream();
            List<Integer> offsets=new ArrayList<>();
            for(String value:strings) {
                offsets.add(body.size());
                byte[] encoded=value.getBytes(utf8?StandardCharsets.UTF_8:StandardCharsets.UTF_16LE);
                if(utf8) {
                    if(encoded.length>127 || value.length()>127) throw new AssertionError("Fixture string too long");
                    body.write(value.length()); body.write(encoded.length); body.write(encoded); body.write(0);
                } else {
                    body.write(value.length()&255); body.write(value.length()>>>8); body.write(encoded); body.write(0); body.write(0);
                }
            }
            while(body.size()%4!=0) body.write(0);
            int start=28+4*strings.size();
            ByteBuffer pool=ByteBuffer.allocate(start+body.size()).order(ByteOrder.LITTLE_ENDIAN);
            pool.putShort((short)1).putShort((short)28).putInt(pool.capacity());
            pool.putInt(strings.size()).putInt(0).putInt(utf8?0x100:0).putInt(start).putInt(0);
            for(int offset:offsets) pool.putInt(offset);
            pool.put(body.toByteArray()); return pool.array();
        }
        byte[] element(String tag,String permission,boolean maxSdk,boolean noName,boolean end) {
            int count=permission==null?0:(noName?0:1)+(maxSdk?1:0);
            ByteBuffer out=ByteBuffer.allocate(end?24:36+20*count).order(ByteOrder.LITTLE_ENDIAN);
            out.putShort((short)(end?0x103:0x102)).putShort((short)16).putInt(out.capacity());
            out.putInt(1).putInt(-1).putInt(-1).putInt(index(tag));
            if(!end) {
                out.putShort((short)20).putShort((short)20).putShort((short)count);
                out.putShort((short)0).putShort((short)0).putShort((short)0);
                if(permission!=null && !noName) attribute(out,"name",3,index(permission));
                if(permission!=null && maxSdk) attribute(out,"maxSdkVersion",0x10,32);
            }
            return out.array();
        }
        void attribute(ByteBuffer out,String name,int type,int value) {
            out.putInt(index(ANDROID)).putInt(index(name)).putInt(-1);
            out.putShort((short)8).put((byte)0).put((byte)type).putInt(value);
        }
    }
}
