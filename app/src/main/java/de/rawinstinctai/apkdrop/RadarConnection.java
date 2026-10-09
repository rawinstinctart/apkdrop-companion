package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.security.keystore.*;
import org.json.JSONObject;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.*;

/** Device-scoped radar credentials, encrypted by a non-exportable Android Keystore key. */
final class RadarConnection {
    private final android.content.SharedPreferences prefs;
    private static final String ALIAS="apkdrop-github-radar-v1";
    RadarConnection(Context context){prefs=context.getSharedPreferences("github-radar-v1",Context.MODE_PRIVATE);}
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    JSONObject read() throws Exception {
        String encoded=prefs.getString("connection",null);if(encoded==null)return new JSONObject();
        byte[] raw=android.util.Base64.decode(encoded,android.util.Base64.NO_WRAP);
        if(raw.length<29)throw new SecurityException("Radar-Verbindung ungültig. Bitte neu verbinden.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,java.util.Arrays.copyOf(raw,12)));
        return new JSONObject(new String(cipher.doFinal(raw,12,raw.length-12),StandardCharsets.UTF_8));
    }
    void save(JSONObject data) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)),iv=cipher.getIV();
        byte[] raw=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,raw,0,iv.length);System.arraycopy(encrypted,0,raw,iv.length,encrypted.length);
        if(!prefs.edit().putString("connection",android.util.Base64.encodeToString(raw,android.util.Base64.NO_WRAP)).commit())throw new java.io.IOException("Verbindung konnte nicht gespeichert werden.");
    }
    void clear(){prefs.edit().clear().commit();}
    static String secret(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
}
