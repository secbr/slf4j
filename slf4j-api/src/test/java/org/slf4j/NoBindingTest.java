package org.slf4j;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;
import org.slf4j.helpers.BasicMarker;
import org.slf4j.helpers.NOPLogger;

public class NoBindingTest {

    int diff = new Random().nextInt(10000);

    @Test
    public void testLogger() {
        Logger logger = LoggerFactory.getLogger(NoBindingTest.class);
        logger.debug("hello" + diff);
        assertTrue(logger instanceof NOPLogger);
    }

    @Test
    public void testMDC() {
        MDC.put("k" + diff, "v");
        assertNull(MDC.get("k"));
    }

    @Test
    public void testMarker() {
        Marker m = MarkerFactory.getMarker("a" + diff);
        assertTrue(m instanceof BasicMarker);
    }
}
