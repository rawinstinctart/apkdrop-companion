package de.rawinstinctai.apkdrop;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** Local evidence precedes any online status query. Registry is supplied only by bundled assets. */
final class DropPassVerifier {
    static final String PREFIX="APKDROP:DROPPASS:V1\n";
    static final String REGISTRY_SCHEMA="apkdrop.droppass-issuer-keys.v1";
    static final String FOOTNOTE="Ein gültiger DropPass belegt Herkunft und Integrität innerhalb der definierten Vertrauenskette, niemals Malware-Freiheit. Android-Signatur, frisches Datei-Hashing und die Zustimmung im Android-Installer bleiben unabhängige Prüfungen.";
    enum State { VERIFIED, UNKNOWN, INVALID, REVOKED, UNVERIFIABLE_NOW }
    interface Status { String query(String passId) throws Exception; }
    static final class Result {
        final State state;
        final String details;
        Result(State state,String details){this.state=state;this.details=details;}
        String badge(){
            switch(state) {
                case VERIFIED:return "DropPass: bestätigt · Herkunft und Integrität belegt";
                case UNKNOWN:return "DropPass: offen · Widerrufsstatus noch offen";
                case INVALID:return "DropPass: ungültig · Nachweis wird nicht anerkannt";
                case REVOKED:return "DropPass: widerrufen · blockiert";
                default:return "DropPass: kein Nachweis";
            }
        }
    }
    static Result verify(String pass,File apk,String packageName,String registry,long now,Status status,boolean forceProvider) {
        if(pass==null || apk==null || !apk.isFile() || packageName==null || registry==null)
            return new Result(State.UNVERIFIABLE_NOW,"Nachweis, APK oder lokale Prüfmöglichkeit fehlt.\n\n"+FOOTNOTE);
        StringBuilder details=new StringBuilder("Registry: gebündeltes Asset droppass-issuer-keys.json\n");
        String id;
        try {
            Map<String,Object> p=DropPassJson.object(DropPassJson.parse(pass));
            require(p.keySet().equals(new HashSet<>(Arrays.asList("schema","algorithm","keyId","passId","issuedAt","expiresAt","receipt","signature"))));
            require("apkdrop.droppass.v1".equals(p.get("schema")) && "Ed25519".equals(p.get("algorithm")));
            String kid=DropPassJson.text(p,"keyId");id=DropPassJson.text(p,"passId");
            String issued=DropPassJson.text(p,"issuedAt"),expires=DropPassJson.text(p,"expiresAt");
            details.append("keyId: ").append(kid).append("\npassId: ").append(id)
                    .append("\nissuedAt: ").append(issued).append("\nexpiresAt: ").append(expires).append('\n');
            long from=date(issued),to=date(expires);
            // No skew allowance: strict issuedAt <= now < expiresAt, including expiry boundary.
            require(from<=now && now<to && to>from && to-from<=2592000000L);
            Map<String,Object> keys=DropPassJson.object(DropPassJson.parse(registry));
            require(REGISTRY_SCHEMA.equals(keys.get("schema")));
            Map<String,Object> issuer=null;
            for(Object entry:(List<?>)keys.get("keys")) {
                Map<String,Object> key=DropPassJson.object(entry);
                if(kid.equals(key.get("keyId"))) {require(issuer==null);issuer=key;}
            }
            require(issuer!=null && "active".equals(issuer.get("status")));
            // Parity with src/droppass-registry.js registryEntry: issuer valid at issuance and verification.
            // now >= validFrom follows transitively from from <= now above and from >= validFrom below.
            require(from>=date(DropPassJson.text(issuer,"validFrom")));
            if(issuer.containsKey("retiredAt")) {
                long retiredAt=date(DropPassJson.text(issuer,"retiredAt"));
                require(from<retiredAt && now<retiredAt);
            }
            Map<String,Object> jwk=DropPassJson.object(issuer.get("jwk"));
            require(!jwk.containsKey("d") && "OKP".equals(jwk.get("kty")) && "Ed25519".equals(jwk.get("crv")));
            byte[] raw=base64(DropPassJson.text(jwk,"x"),32);
            Map<String,Object> unsigned=new TreeMap<>(p);unsigned.remove("signature");
            require(verifySignature(raw,(PREFIX+DropPassJson.canonical(unsigned)).getBytes(StandardCharsets.UTF_8),
                    base64(DropPassJson.text(p,"signature"),64),forceProvider));
            Map<String,Object> receipt=DropPassJson.object(p.get("receipt"));
            require(receipt.keySet().equals(new HashSet<>(Arrays.asList("schema","receiptId","receiptSha256","payload"))));
            require("apkdrop.release-receipt.v1".equals(receipt.get("schema")));
            Map<String,Object> hashed=new TreeMap<>();hashed.put("schema",receipt.get("schema"));hashed.put("payload",receipt.get("payload"));
            String receiptHash=sha(DropPassJson.canonical(hashed).getBytes(StandardCharsets.UTF_8));
            require(receiptHash.equals(receipt.get("receiptSha256")) && ("sha256:"+receiptHash).equals(receipt.get("receiptId")));
            Map<String,Object> payload=DropPassJson.object(receipt.get("payload"));
            String apkHash=hashFile(apk);
            require(apkHash.equals(DropPassJson.text(DropPassJson.object(payload.get("apk")),"sha256")));
            require(packageName.equals(DropPassJson.text(DropPassJson.object(payload.get("android")),"packageName")));
            details.append("Receipt SHA-256: ").append(receiptHash.substring(0,16)).append("…\nAPK SHA-256: ")
                    .append(apkHash.substring(0,16)).append("…\nLokale Prüfungen: gültig\n");
        } catch(NoSuchAlgorithmException unavailable) {
            return new Result(State.UNVERIFIABLE_NOW,details+"Lokale Kryptografie nicht verfügbar.\n\n"+FOOTNOTE);
        } catch(Exception invalid) {
            return new Result(State.INVALID,details+"Lokale Prüfung fehlgeschlagen. Widerrufsprüfung: nicht angefragt.\n\n"+FOOTNOTE);
        }
        String revocation;
        try {revocation=status==null?"unavailable":status.query(id);}
        catch(Exception offline){revocation="unavailable";}
        State state="revoked".equals(revocation)?State.REVOKED:"ok".equals(revocation)?State.VERIFIED:State.UNKNOWN;
        details.append("Widerrufsprüfung: ").append(Arrays.asList("ok","revoked","unknown").contains(revocation)?revocation:"nicht verfügbar")
                .append("\n\n").append(FOOTNOTE);
        return new Result(state,details.toString());
    }
    static boolean verifySignature(byte[] raw,byte[] message,byte[] signature,boolean forceProvider) throws GeneralSecurityException {
        Signature verifier;KeyFactory factory;
        try {
            if(forceProvider)throw new NoSuchAlgorithmException("Forced fallback");
            verifier=Signature.getInstance("Ed25519");factory=KeyFactory.getInstance("Ed25519");
        } catch(NoSuchAlgorithmException absent) {
            Provider provider=new BouncyCastleProvider();
            verifier=Signature.getInstance("Ed25519",provider);factory=KeyFactory.getInstance("Ed25519",provider);
        }
        // RFC 8410 SubjectPublicKeyInfo prefix for a raw Ed25519 public key (not curve arithmetic).
        byte[] spki=new byte[44];byte[] prefix={0x30,0x2a,0x30,0x05,0x06,0x03,0x2b,0x65,0x70,0x03,0x21,0x00};
        System.arraycopy(prefix,0,spki,0,12);System.arraycopy(raw,0,spki,12,32);
        verifier.initVerify(factory.generatePublic(new X509EncodedKeySpec(spki)));verifier.update(message);return verifier.verify(signature);
    }
    static long date(String s) {
        require(s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z"));
        Instant instant=Instant.parse(s);
        require(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(java.time.ZoneOffset.UTC).format(instant).equals(s));
        return instant.toEpochMilli();
    }
    private static byte[] base64(String s,int length) {
        require(s.matches("[A-Za-z0-9_-]+"));byte[] b=Base64.getUrlDecoder().decode(s);
        require(b.length==length && Base64.getUrlEncoder().withoutPadding().encodeToString(b).equals(s));return b;
    }
    static String hashFile(File file) throws IOException,NoSuchAlgorithmException {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;){if(Thread.currentThread().isInterrupted())throw new IOException("Interrupted");md.update(b,0,n);}}
        return hex(md.digest());
    }
    static String sha(byte[] bytes) throws NoSuchAlgorithmException {return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    private static void require(boolean condition){if(!condition)throw new IllegalArgumentException("DropPass contract");}
}
