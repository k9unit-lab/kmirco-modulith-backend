package com.kmicro.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Makes version 7 UUIDs (RFC 9562): a 48 bit millisecond timestamp, then a 12 bit counter, then random bits.
 * IDs made by one app instance always sort in creation order, even inside the same millisecond.
 * Postgres 17 has no {@code uuidv7()}, so every new row gets its id from here.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_COUNTER = 0xFFF;

    private static long lastMillis = -1;
    private static int counter;

    private UuidV7() {}

    public static UUID next() {
        long random = RANDOM.nextLong();
        long millis;
        int count;
        synchronized (UuidV7.class) {
            long now = System.currentTimeMillis();
            if (now > lastMillis) {
                lastMillis = now;
                // Start low in the counter range so many IDs fit into the same millisecond.
                counter = (int) (random >>> 54) & 0x1FF;
            } else if (++counter > MAX_COUNTER) {
                // Counter full or clock moved back: borrow the next millisecond to keep the order.
                lastMillis++;
                counter = 0;
            }
            millis = lastMillis;
            count = counter;
        }
        long msb = (millis << 16) | 0x7000L | count;
        long lsb = (random & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }
}
