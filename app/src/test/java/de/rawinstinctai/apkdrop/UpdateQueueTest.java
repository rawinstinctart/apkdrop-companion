package de.rawinstinctai.apkdrop;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class UpdateQueueTest {
    @Test public void roundTripKeepsOrderAndRemainingItems() {
        UpdateQueue queue=UpdateQueue.decode(new UpdateQueue(List.of("test-app","other-app")).encode());
        assertEquals("test-app",queue.current()); assertEquals("other-app",queue.next().current()); assertNull(queue.next().next().current());
    }
    @Test public void unchangedQueueKeepsCancelledInstall() {
        UpdateQueue queue=new UpdateQueue(List.of("test-app","other-app"));
        assertEquals("test-app",UpdateQueue.decode(queue.encode()).current());
    }
    @Test public void emptyAndMissingQueueAreSafe() { assertNull(UpdateQueue.decode(null).current()); assertNull(UpdateQueue.decode("1:").current()); }
    @Test public void duplicatesAndInvalidSlugsRejected() {
        assertThrows(IllegalArgumentException.class,()->new UpdateQueue(List.of("test-app","test-app")));
        assertThrows(IllegalArgumentException.class,()->UpdateQueue.decode("1:https://evil.example"));
        assertThrows(IllegalArgumentException.class,()->UpdateQueue.decode("1:test-app,"));
    }
    @Test public void unknownSchemaAndOversizedQueueRejected() {
        assertThrows(IllegalArgumentException.class,()->UpdateQueue.decode("2:test-app"));
        assertThrows(IllegalArgumentException.class,()->UpdateQueue.decode("1:"+"a".repeat(2201)));
    }
}
