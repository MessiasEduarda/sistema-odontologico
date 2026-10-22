package br.com.odontosaas.shared;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Clock CLOCK = Clock.systemUTC();

    private Ids() {}

    public static UUID newId() {
        long millis = CLOCK.millis();
        long randA = RANDOM.nextLong();
        long randB = RANDOM.nextLong();
        long msb = (millis << 16) | 0x7000L | (randA & 0x0FFFL);
        long lsb = (randB & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
