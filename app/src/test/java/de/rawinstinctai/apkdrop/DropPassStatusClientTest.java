package de.rawinstinctai.apkdrop;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public final class DropPassStatusClientTest {
    private static final class Connection extends HttpURLConnection {
        final ByteArrayOutputStream sent=new ByteArrayOutputStream();
        final int code;
        final String response;
        boolean disconnected;
        Connection(int code,String response) throws Exception {super(new URL("https://example.invalid"));this.code=code;this.response=response;}
        @Override public void connect() {}
        @Override public void disconnect(){disconnected=true;}
        @Override public boolean usingProxy(){return false;}
        @Override public OutputStream getOutputStream(){return sent;}
        @Override public int getResponseCode(){return code;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));}
    }
    @Test public void anonymousPostHasProductionDefaultAndNoRedirects() throws Exception {
        Connection connection=new Connection(200,"{\"status\":\"ok\"}");
        DropPassStatusClient client=new DropPassStatusClient(url->{assertEquals(DropPassStatusClient.ORIGIN+"/api/droppass/status.json",url);return connection;});
        assertEquals("ok",client.query("synthetic-pass"));
        assertEquals("POST",connection.getRequestMethod());
        assertEquals("{\"passId\":\"synthetic-pass\"}",connection.sent.toString("UTF-8"));
        assertFalse(connection.getInstanceFollowRedirects());
        assertEquals("",connection.getRequestProperty("Cookie"));
        assertEquals("",connection.getRequestProperty("Authorization"));
        assertTrue(connection.getConnectTimeout()<=3000);
        assertTrue(connection.getReadTimeout()<=3000);
        assertTrue(connection.disconnected);
        assertEquals(DropPassStatusClient.ORIGIN,new DropPassStatusClient().origin());
    }
    @Test public void httpErrorsMalformedAndRedirectsNeverOk() throws Exception {
        for(int code:new int[]{404,500,302,401})assertEquals("unavailable",new DropPassStatusClient(url->newConnection(code,"{\"status\":\"ok\"}")).query("synthetic"));
        for(String body:new String[]{"{\"status\":\"ok\",\"extra\":true}","html",new String(new char[4097])})
            assertEquals("unavailable",new DropPassStatusClient(url->newConnection(200,body)).query("synthetic"));
    }
    private Connection newConnection(int code,String body) throws IOException {
        try {return new Connection(code,body);}catch(Exception e){throw new IOException(e);}
    }
    @Test public void networkUnavailableDoesNotCrash() {
        assertEquals("unavailable",new DropPassStatusClient(url->{throw new IOException("offline");}).query("synthetic"));
    }
    @Test public void totalDeadlineIncludesConnectionCreation() {
        long start=System.nanoTime();
        assertEquals("unavailable",new DropPassStatusClient(url->{
            try {Thread.sleep(10000);}catch(InterruptedException stopped){throw new IOException(stopped);}
            throw new IOException("timeout");
        }).query("synthetic"));
        assertTrue("Status deadline must be <= 3s",(System.nanoTime()-start)/1000000<3000);
    }
    @Test public void ambientCookieHandlerFailsClosed() {
        CookieHandler previous=CookieHandler.getDefault();
        try {
            CookieHandler.setDefault(new CookieManager());
            assertEquals("unavailable",new DropPassStatusClient(url->{fail("Must not connect with ambient cookies");return null;}).query("synthetic"));
        } finally {CookieHandler.setDefault(previous);}
    }
}
