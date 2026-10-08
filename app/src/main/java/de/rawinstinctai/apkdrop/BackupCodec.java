package de.rawinstinctai.apkdrop;

import android.content.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Offline backup of explicitly saved pins and developer follows, never APKs or passwords. */
final class BackupCodec {
    private static final int ITERATIONS=210000, MAX=160000;
    private static final byte[] AAD="APKDROP-BACKUP-V1".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM=new SecureRandom();
    private BackupCodec(){}

    static final class Plan {
        final AppLibrary incoming;
        final JSONObject developers;
        final int apps, follows;
        private Plan(AppLibrary a,JSONObject d){incoming=a;developers=d;apps=a.entries().size();follows=d.length();}
    }
    static String export(Context context,char[] password) throws Exception {
        requirePassword(password);
        JSONObject payload=new JSONObject().put("apps",new AppLibraryStore(context).read().encode())
                .put("developers",new DeveloperFollows(context).read());
        byte[] plain=payload.toString().getBytes(StandardCharsets.UTF_8);
        if(plain.length>MAX)throw new SecurityException("Sicherung ist zu groß.");
        byte[] salt=new byte[16],nonce=new byte[12];RANDOM.nextBytes(salt);RANDOM.nextBytes(nonce);
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key(password,salt),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(AAD);
            String body=Base64.getEncoder().encodeToString(cipher.doFinal(plain));
            return new JSONObject().put("schema","apkdrop.backup.v1").put("iterations",ITERATIONS)
                    .put("salt",Base64.getEncoder().encodeToString(salt))
                    .put("nonce",Base64.getEncoder().encodeToString(nonce))
                    .put("ciphertext",body).toString();
        }finally {Arrays.fill(plain,(byte)0);}
    }
    static Plan preview(String raw,char[] password) throws Exception {
        requirePassword(password);
        if(raw==null||raw.length()>MAX*2)throw new SecurityException("Ungültige oder zu große Sicherung.");
        JSONObject envelope=new JSONObject(raw);
        if(!"apkdrop.backup.v1".equals(envelope.optString("schema"))||envelope.getInt("iterations")!=ITERATIONS)
            throw new SecurityException("Unbekanntes Sicherungsformat.");
        byte[] salt=Base64.getDecoder().decode(envelope.getString("salt"));
        byte[] nonce=Base64.getDecoder().decode(envelope.getString("nonce"));
        byte[] ciphertext=Base64.getDecoder().decode(envelope.getString("ciphertext"));
        if(salt.length!=16||nonce.length!=12||ciphertext.length<16||ciphertext.length>MAX+32)
            throw new SecurityException("Beschädigte Sicherung.");
        byte[] plaintext;
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(password,salt),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(AAD);
            plaintext=cipher.doFinal(ciphertext);
        } catch(AEADBadTagException mismatch) {
            throw new SecurityException("Falsches Passwort oder Sicherung wurde verändert.");
        }
        try {
            JSONObject data=new JSONObject(new String(plaintext,StandardCharsets.UTF_8));
            AppLibrary apps=AppLibrary.decode(data.getString("apps"));
            JSONObject developers=data.getJSONObject("developers");
            DeveloperFollows.requireValid(developers);
            return new Plan(apps,developers);
        }finally {Arrays.fill(plaintext,(byte)0);}
    }
    static void apply(Context context,Plan plan) throws Exception {
        AppLibraryStore library=new AppLibraryStore(context);
        AppLibrary old=library.read(), merged=old;
        for(AppLibrary.Entry entry:plan.incoming.entries()) merged=merged.add(entry);
        DeveloperFollows follows=new DeveloperFollows(context);
        JSONObject oldDevelopers=follows.read(), next=new JSONObject(oldDevelopers.toString());
        for(java.util.Iterator<String> it=plan.developers.keys();it.hasNext();) {
            String id=it.next();
            if(!next.has(id))next.put(id,plan.developers.getJSONObject(id));
        }
        DeveloperFollows.requireValid(next);
        library.save(merged);
        try {follows.replace(next);}
        catch(Exception error) {
            try {library.save(old);}catch(Exception rollback) {error.addSuppressed(rollback);}
            throw error;
        }
        UpdateScheduler.reconcile(context);
        DropPilot.reconcile(context);
    }
    private static void requirePassword(char[] password) {
        if(password==null||password.length<10||password.length>256)
            throw new IllegalArgumentException("Die Sicherung benötigt ein Passwort mit mindestens 10 Zeichen.");
    }
    private static SecretKey key(char[] password,byte[] salt) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);
        try {
            byte[] derived=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            try{return new SecretKeySpec(derived,"AES");}
            finally {Arrays.fill(derived,(byte)0);}
        }finally {spec.clearPassword();}
    }
}
