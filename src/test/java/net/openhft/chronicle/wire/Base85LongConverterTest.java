/*
 * Copyright 2016-2022 chronicle.software
 *
 *       https://chronicle.software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.wire;

import net.openhft.chronicle.bytes.Bytes;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class Base85LongConverterTest extends WireTestCommon {

    private static final CharSequence TEST_STRING = "world";

    @Test
    public void parseLeadingZero() {
        LongConverter c = Base85LongConverter.INSTANCE;
        assertEquals(0L, c.parse("0"));
        assertEquals(0L, c.parse("00"));
        assertEquals(0L, c.parse("000"));
        assertEquals(0L, c.parse("0000"));
        assertEquals(0L, c.parse("00000"));
        assertEquals(0L, c.parse("000000"));
        assertEquals(0L, c.parse("0000000"));
        assertEquals(0L, c.parse("00000000"));
        assertEquals(0L, c.parse("000000000"));
        assertEquals(0L, c.parse("0000000000"));
        assertThrows(IllegalArgumentException.class, () -> c.parse("00000000000"));
        assertEquals("", c.asString(0L));
    }

    @Test
    public void parse() {
        LongConverter c = Base85LongConverter.INSTANCE;
        // System.out.println(c.asString(-1L));
        for (String s : ",a,ab,abc,abcd,ab.de,123=56,1234567,12345678,zzzzzzzzz,+ko2&)z.0".split(",")) {
            long v = c.parse(s);
            StringBuilder sb = new StringBuilder();
            c.append(sb, v);
            assertEquals(s, sb.toString());
        }
    }

    @Test
    public void parseSubsequence() {
        LongConverter c = Base85LongConverter.INSTANCE;
        String s = ",a,ab,abc,abcd,ab.de,123=56,1234567,12345678,zzzzzzzzz,+ko2&)z.0,";
        int comparisons = 11;
        subStringParseLoop(s, c, comparisons);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseLengthCheck() {
        Base85LongConverter.INSTANCE.parse(getClass().getCanonicalName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseSubstringLengthCheck() {
        Base85LongConverter.INSTANCE.parse("ABCD", -1, 3);
    }

    @Test
    public void asString() {
        LongConverter c = Base85LongConverter.INSTANCE;
        IntStream.range(0, 10_000_000)
                .parallel()
                .mapToLong(i -> ThreadLocalRandom.current().nextLong())
                .forEach(l -> {
                    String s = c.asString(l);
                    assertEquals(s, l, c.parse(s));
                });
    }

    @Test
    public void testAppend() {
        final Bytes<?> b = Bytes.elasticByteBuffer();
        try {
            final Base85LongConverter idLongConverter = Base85LongConverter.INSTANCE;
            final long helloWorld = idLongConverter.parse(TEST_STRING);
            idLongConverter.append(b, helloWorld);
            assertEquals(TEST_STRING, b.toString());
        } finally {
            b.releaseLast();
        }
    }

    @Test
    public void testAppendWithExistingData() {
        final Bytes<?> b = Bytes.elasticByteBuffer().append("hello");
        try {
            final Base85LongConverter idLongConverter = Base85LongConverter.INSTANCE;
            final long helloWorld = idLongConverter.parse(TEST_STRING);
            idLongConverter.append(b, helloWorld);
            assertEquals("hello" + TEST_STRING, b.toString());
        } finally {
            b.releaseLast();
        }
    }

    @Test
    public void allSafeCharsTextWire() {
        Wire wire = new TextWire(Bytes.allocateElasticOnHeap()).useTextDocuments();
        allSafeChars(wire);
    }

    @Test
    public void allSafeCharsYamlWire() {
        Wire wire = new YamlWire(Bytes.allocateElasticOnHeap()).useTextDocuments();
        allSafeChars(wire);
    }

    private void allSafeChars(Wire wire) {
        final Base85LongConverter converter = Base85LongConverter.INSTANCE;
        for (long i = 0; i <= 85 * 85; i++) {
            wire.clear();
            wire.write("a").writeLong(converter, i);
            wire.write("b").sequence(i, (i2, v) -> {
                v.writeLong(converter, i2);
                v.writeLong(converter, i2);
            });
            assertEquals(wire.toString(),
                    i, wire.read("a").readLong(converter));
            wire.read("b").sequence(i, (i2, v) -> {
                assertEquals((long) i2, v.readLong(converter));
                assertEquals((long) i2, v.readLong(converter));
            });
        }
    }
}
