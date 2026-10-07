package de.rawinstinctai.apkdrop;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public final class VerifiedApkProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private File file(Uri uri) throws FileNotFoundException {
        if(uri==null || !"/verified.apk".equals(uri.getPath())) throw new FileNotFoundException("Unknown APKDrop file.");
        Context context=getContext();
        if(context==null) throw new FileNotFoundException("Provider unavailable.");
        File dir=new File(context.getCacheDir(),"apkdrop");
        File file=new File(dir,"verified.apk");
        try {
            String root=dir.getCanonicalPath()+File.separator;
            String candidate=file.getCanonicalPath();
            if(!candidate.startsWith(root) || !file.isFile()) throw new FileNotFoundException("Verified APK missing.");
            return file;
        } catch(IOException e) {
            FileNotFoundException wrapped=new FileNotFoundException("Verified APK unavailable.");
            wrapped.initCause(e); throw wrapped;
        }
    }

    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }

    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode)) throw new FileNotFoundException("Read-only provider.");
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder) {
        try {
            File file=file(uri);
            MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},1);
            cursor.addRow(new Object[]{"APKDrop-verified.apk",file.length()});
            return cursor;
        } catch(FileNotFoundException e) { return null; }
    }

    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs) { return 0; }
    @Override public int delete(Uri uri,String selection,String[] selectionArgs) { return 0; }
}
