package de.rawinstinctai.apkdrop;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public final class DropPassVerifierTest {
    private String resource(String name) throws Exception {
        try(InputStream in=getClass().getResourceAsStream("/droppass-vectors/"+name)) {
            assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private File apk() throws Exception {return new File(getClass().getResource("/droppass-vectors/apk-fixture.bin").toURI());}
    private long now() {return DropPassVerifier.date("2026-10-15T03:00:00.000Z");}
    private DropPassVerifier.Result check(String pass,File apk,String pkg,String registry,long now,DropPassVerifier.Status status,boolean fallback) {
        return DropPassVerifier.verify(pass,apk,pkg,registry,now,status,fallback);
    }
    private DropPassVerifier.Result check(String vector,DropPassVerifier.Status status,boolean fallback) throws Exception {
        return check(resource(vector),apk(),"de.rawinstinctai.testapp",resource("registry.json"),now(),status,fallback);
    }
    @Test public void badgesUseExactGermanStatusText() {
        assertEquals("DropPass: bestätigt · Herkunft und Integrität belegt",new DropPassVerifier.Result(DropPassVerifier.State.VERIFIED,"").badge());
        assertEquals("DropPass: offen · Widerrufsstatus noch offen",new DropPassVerifier.Result(DropPassVerifier.State.UNKNOWN,"").badge());
        assertEquals("DropPass: ungültig · Nachweis wird nicht anerkannt",new DropPassVerifier.Result(DropPassVerifier.State.INVALID,"").badge());
        assertEquals("DropPass: widerrufen · blockiert",new DropPassVerifier.Result(DropPassVerifier.State.REVOKED,"").badge());
        assertEquals("DropPass: kein Nachweis",new DropPassVerifier.Result(DropPassVerifier.State.UNVERIFIABLE_NOW,"").badge());
    }
    @Test public void backendGoldenMatrixPlatform() throws Exception {matrix(false);}
    @Test public void backendGoldenMatrixExplicitProvider() throws Exception {matrix(true);}
    private void matrix(boolean fallback) throws Exception {
        assertEquals(DropPassVerifier.State.VERIFIED,check("pass-valid.json",p->"ok",fallback).state);
        for(String name:List.of("pass-expired.json","pass-revoked-key.json","pass-tampered.json")) {
            AtomicInteger calls=new AtomicInteger();
            assertEquals(name,DropPassVerifier.State.INVALID,check(name,p->{calls.incrementAndGet();return "revoked";},fallback).state);
            assertEquals(0,calls.get());
        }
        Map<String,Object> expected=DropPassJson.object(DropPassJson.parse(resource("expected.json")));
        assertEquals(expected.get("apkSha256"),DropPassVerifier.hashFile(apk()));
        Map<String,Object> receipt=DropPassJson.object(DropPassJson.parse(resource("receipt.json")));
        assertEquals(expected.get("receiptSha256"),receipt.get("receiptSha256"));
    }
    @Test public void statusOkVerifiedAndPassIdOnly() throws Exception {
        String id=DropPassJson.text(DropPassJson.object(DropPassJson.parse(resource("pass-valid.json"))),"passId");
        assertEquals(DropPassVerifier.State.VERIFIED,check("pass-valid.json",p->{assertEquals(id,p);return "ok";},false).state);
    }
    @Test public void networkFailureUnknown() throws Exception {assertEquals(DropPassVerifier.State.UNKNOWN,check("pass-valid.json",p->{throw new IOException("offline");},false).state);}
    @Test public void unknownStatusUnknown() throws Exception {assertEquals(DropPassVerifier.State.UNKNOWN,check("pass-valid.json",p->"unknown",false).state);}
    @Test public void noNetworkCapabilityDoesNotCrash() throws Exception {assertEquals(DropPassVerifier.State.UNKNOWN,check("pass-valid.json",null,false).state);}
    @Test public void malformedStatusNeverVerified() throws Exception {assertEquals(DropPassVerifier.State.UNKNOWN,check("pass-valid.json",p->"OK",false).state);}
    @Test public void revocationBlocks() throws Exception {assertEquals(DropPassVerifier.State.REVOKED,check("pass-valid.json",p->"revoked",false).state);}
    @Test public void wrongApkInvalidBeforeStatus() throws Exception {
        Path temp=Files.createTempFile("synthetic-wrong-apk",".bin");
        try {
            Files.write(temp,"synthetic wrong bytes".getBytes(StandardCharsets.UTF_8));
            assertEquals(DropPassVerifier.State.INVALID,check(resource("pass-valid.json"),temp.toFile(),"de.rawinstinctai.testapp",resource("registry.json"),now(),p->{fail("Network before local validation");return "ok";},false).state);
        } finally {Files.delete(temp);}
    }
    @Test public void wrongPackageInvalid() throws Exception {
        assertEquals(DropPassVerifier.State.INVALID,check(resource("pass-valid.json"),apk(),"wrong.package",resource("registry.json"),now(),p->"ok",false).state);
    }
    @Test public void missingInputsUnverifiable() throws Exception {
        String pass=resource("pass-valid.json"),registry=resource("registry.json");
        assertEquals(DropPassVerifier.State.UNVERIFIABLE_NOW,check(null,apk(),"pkg",registry,now(),null,false).state);
        assertEquals(DropPassVerifier.State.UNVERIFIABLE_NOW,check(pass,null,"pkg",registry,now(),null,false).state);
        assertEquals(DropPassVerifier.State.UNVERIFIABLE_NOW,check(pass,apk(),"pkg",null,now(),null,false).state);
    }
    @Test public void expiryAndIssueBoundariesHaveNoSkew() throws Exception {
        Map<String,Object> pass=DropPassJson.object(DropPassJson.parse(resource("pass-valid.json")));
        long issued=DropPassVerifier.date((String)pass.get("issuedAt")),expires=DropPassVerifier.date((String)pass.get("expiresAt"));
        for(long t:new long[]{issued,expires-1})assertEquals(DropPassVerifier.State.VERIFIED,check(resource("pass-valid.json"),apk(),"de.rawinstinctai.testapp",resource("registry.json"),t,p->"ok",false).state);
        for(long t:new long[]{issued-1,expires,expires+1})assertEquals(DropPassVerifier.State.INVALID,check(resource("pass-valid.json"),apk(),"de.rawinstinctai.testapp",resource("registry.json"),t,p->"ok",false).state);
    }
    @Test public void unknownOrNonactiveRegistryKeyInvalid() throws Exception {
        for(String replacement:List.of("unknown-key","vec-key-revoked"))
            assertEquals(DropPassVerifier.State.INVALID,check(resource("pass-valid.json"),apk(),"de.rawinstinctai.testapp",resource("registry.json").replace("vec-key-active",replacement),now(),p->"ok",false).state);
    }
    @Test public void issuerValidityRules() throws Exception {
        for(String registry:List.of(resource("registry.json").replace("2026-01-01","2026-10-16"),resource("registry.json").replace("\"validFrom\":", "\"retiredAt\":\"2026-10-15T02:34:46.122Z\",\"validFrom\":")))
            assertEquals(DropPassVerifier.State.INVALID,check(resource("pass-valid.json"),apk(),"de.rawinstinctai.testapp",registry,now(),p->"ok",false).state);
    }
    @Test public void issuerRetiredAfterIssuanceBeforeNowInvalid() throws Exception {
        String registry=resource("registry.json").replace("\"validFrom\":", "\"retiredAt\":\"2026-10-15T02:45:00.000Z\",\"validFrom\":");
        assertEquals(DropPassVerifier.State.INVALID,check(resource("pass-valid.json"),apk(),"de.rawinstinctai.testapp",registry,now(),p->{fail("Status queried for retired issuer");return "ok";},false).state);
    }
    @Test public void issuerRetirementBoundaryHasNoSkew() throws Exception {
        String registry=resource("registry.json").replace("\"validFrom\":", "\"retiredAt\":\"2026-10-15T02:45:00.000Z\",\"validFrom\":");
        long retiredAt=DropPassVerifier.date("2026-10-15T02:45:00.000Z");
        for(boolean fallback:new boolean[]{false,true}) {
            assertEquals(DropPassVerifier.State.VERIFIED,check(resource("pass-valid.json"),apk(),"de.rawinstinctai.testapp",registry,retiredAt-1,p->"ok",fallback).state);
            assertEquals(DropPassVerifier.State.INVALID,check(resource("pass-valid.json"),apk(),"de.rawinstinctai.testapp",registry,retiredAt,p->{fail("Status queried at issuer retirement");return "ok";},fallback).state);
        }
    }
    @Test public void fieldOrderIndependent() throws Exception {
        Map<String,Object> pass=DropPassJson.object(DropPassJson.parse(resource("pass-valid.json")));
        List<String> fields=new ArrayList<>(pass.keySet());Collections.reverse(fields);
        List<String> entries=new ArrayList<>();for(String field:fields)entries.add(DropPassJson.canonical(field)+":"+DropPassJson.canonical(pass.get(field)));
        assertEquals(DropPassVerifier.State.VERIFIED,check("{"+String.join(",",entries)+"}",apk(),"de.rawinstinctai.testapp",resource("registry.json"),now(),p->"ok",false).state);
    }
    @Test public void extraEnvelopeFieldRejected() throws Exception {
        assertEquals(DropPassVerifier.State.INVALID,check(resource("pass-valid.json").replaceFirst("\\{","{\"extra\":true,"),apk(),"de.rawinstinctai.testapp",resource("registry.json"),now(),p->"ok",false).state);
    }
    @Test public void wrongDomainPrefixRejectedByBackendSignature() throws Exception {
        Map<String,Object> p=DropPassJson.object(DropPassJson.parse(resource("pass-valid.json")));
        byte[] sig=Base64.getUrlDecoder().decode((String)p.remove("signature"));
        Map<String,Object> key=DropPassJson.object(DropPassJson.parse(resource("public-active.jwk.json")));
        byte[] raw=Base64.getUrlDecoder().decode((String)key.get("x"));
        for(boolean fallback:new boolean[]{false,true})assertFalse(DropPassVerifier.verifySignature(raw,("WRONG\n"+DropPassJson.canonical(p)).getBytes(StandardCharsets.UTF_8),sig,fallback));
    }
    @Test public void mutationsFailClosed() throws Exception {
        String pass=resource("pass-valid.json");
        for(String changed:List.of(pass.replace("apkdrop.droppass.v1","wrong"),pass.replace("Ed25519","Ed448"),pass.replace("sha256:d5","sha256:a5"),pass.replace("d5fe2244","a5fe2244"),pass.replace("77ca5dbf","a7ca5dbf"),pass.replace("testapp","evilapp"),pass.replace("2026-11-14","2026-12-14"),pass.replace("46.122Z","46.12Z"),"{}","null","not json"))
            assertEquals(DropPassVerifier.State.INVALID,check(changed,apk(),"de.rawinstinctai.testapp",resource("registry.json"),now(),p->"ok",false).state);
    }
    @Test public void canonicalizerMatchesJsEscapesAndNestedSort() {
        assertEquals("{\"a\":[true,null,1,\"é/\\n\\u0000\"],\"z\":{\"a\":0,\"b\":2}}",DropPassJson.canonical(DropPassJson.parse("{\"z\":{\"b\":2,\"a\":0},\"a\":[true,null,1,\"é/\\n\\u0000\"]}")));
        assertEquals("\"\\ud800\"",DropPassJson.canonical(DropPassJson.parse("\"\\ud800\"")));
        assertEquals("\"😀\"",DropPassJson.canonical(DropPassJson.parse("\"\\ud83d\\ude00\"")));
    }
    @Test public void ambiguousJsonRejected() {
        for(String json:List.of("{\"x\":1,\"x\":2}","{\"a\":1,\"\\u0061\":2}","1.0","1e2","-0","NaN","Infinity","9007199254740992","01","{\"0\":true}","[1,]","{'x':1}","{}true","\"\\x00\"","\"\n\""))
            assertThrows(json,IllegalArgumentException.class,()->DropPassJson.parse(json));
    }
    @Test public void exactDateFormatRequired() {
        for(String date:List.of("2026-10-15T02:34:46Z","2026-10-15T02:34:46.122+00:00","2026-02-30T02:34:46.122Z","2026-10-15T02:34:60.122Z"))
            assertThrows(Exception.class,()->DropPassVerifier.date(date));
    }
    @Test public void statusShapeOnlyThreeAllowedValues() {
        for(String status:List.of("ok","revoked","unknown"))assertEquals(status,DropPassStatusClient.parse("{\"status\":\""+status+"\"}"));
        for(String invalid:List.of("{}","null","[]","{\"status\":true}","{\"status\":\"OK\"}","{\"status\":\"ok\",\"extra\":1}","{\"status\":\"ok\",\"status\":\"revoked\"}","<html>404</html>"))assertEquals("unavailable",DropPassStatusClient.parse(invalid));
    }
    // Ephemeral synthetic key only: proves receipt/time failures independently of signature rejection.
    private DropPassVerifier.State synthetic(java.util.function.Consumer<Map<String,Object>> mutation) throws Exception {
        Map<String,Object> pass=DropPassJson.object(DropPassJson.parse(resource("pass-valid.json")));
        pass.remove("signature");mutation.accept(pass);
        java.security.KeyPair pair=java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        java.security.Signature signer=java.security.Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update((DropPassVerifier.PREFIX+DropPassJson.canonical(pass)).getBytes(StandardCharsets.UTF_8));
        pass.put("signature",Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        Map<String,Object> registry=DropPassJson.object(DropPassJson.parse(resource("registry.json")));
        Map<String,Object> issuer=DropPassJson.object(((List<?>)registry.get("keys")).get(0));
        byte[] encoded=pair.getPublic().getEncoded();
        DropPassJson.object(issuer.get("jwk")).put("x",Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOfRange(encoded,encoded.length-32,encoded.length)));
        return check(DropPassJson.canonical(pass),apk(),"de.rawinstinctai.testapp",DropPassJson.canonical(registry),now(),p->"ok",false).state;
    }
    @Test public void syntheticControlPassVerifies() throws Exception {assertEquals(DropPassVerifier.State.VERIFIED,synthetic(p->{}));}
    @Test public void signedReceiptDigestMismatchInvalid() throws Exception {
        assertEquals(DropPassVerifier.State.INVALID,synthetic(p->DropPassJson.object(DropPassJson.object(p.get("receipt")).get("payload")).put("extra",true)));
    }
    @Test public void signedReceiptIdMismatchInvalid() throws Exception {
        assertEquals(DropPassVerifier.State.INVALID,synthetic(p->DropPassJson.object(p.get("receipt")).put("receiptId","sha256:wrong")));
    }
    @Test public void signedOverlongValidityInvalid() throws Exception {
        assertEquals(DropPassVerifier.State.INVALID,synthetic(p->p.put("expiresAt","2026-11-14T02:34:46.123Z")));
    }
    @Test public void signedWrongReceiptSchemaInvalid() throws Exception {
        assertEquals(DropPassVerifier.State.INVALID,synthetic(p->DropPassJson.object(p.get("receipt")).put("schema","wrong")));
    }

    @Test public void bundledRegistryMatchesBackendAndContainsOnlyPublicMaterial() throws Exception {
        Path asset=Path.of("src/main/assets/droppass-issuer-keys.json");
        byte[] bundled=Files.readAllBytes(asset);
        // Verified backend snapshot: config/droppass-issuer-keys.json in the private backend repo.
        // Keep this pin in sync with reviewed backend registry updates, including in CI without backend-config.
        assertEquals("Bundled registry differs from the documented backend snapshot",
                "47b4a56e8b20af58d2e4020f3773ffb71d63e021e5e3b1af0360580bfa5be59d",DropPassVerifier.sha(bundled));
        Path backend=Path.of("../backend-config/droppass-issuer-keys.json");
        if(Files.exists(backend))assertArrayEquals("Bundled registry differs from the live backend registry",Files.readAllBytes(backend),bundled);
        String json=new String(bundled,StandardCharsets.UTF_8);
        Map<String,Object> registry=DropPassJson.object(DropPassJson.parse(json));
        assertEquals(DropPassVerifier.REGISTRY_SCHEMA,registry.get("schema"));
        assertTrue(((List<?>)registry.get("keys")).stream().anyMatch(k->"active".equals(DropPassJson.object(k).get("status"))));
        noPrivateMaterial(registry);
    }
    private void noPrivateMaterial(Object value) {
        if(value instanceof Map){Map<String,Object> m=DropPassJson.object(value);assertFalse(m.containsKey("d"));for(Object v:m.values())noPrivateMaterial(v);}
        if(value instanceof List)for(Object v:(List<?>)value)noPrivateMaterial(v);
    }
}
