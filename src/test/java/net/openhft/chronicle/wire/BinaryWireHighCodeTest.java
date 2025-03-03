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

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BinaryWireHighCodeTest extends WireTestCommon {
    @Test
    public void testUnique() throws IllegalAccessException {
        assertEquals(0, BinaryWireHighCode.values().length);
        Set<Integer> values = new HashSet<>();
        for (Field field : BinaryWireHighCode.class.getFields()) {
            int value = (Integer) field.get(null);
            assertTrue(values.add(value));
        }
    }
}
