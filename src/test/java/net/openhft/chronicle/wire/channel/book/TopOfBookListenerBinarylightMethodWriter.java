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

package net.openhft.chronicle.wire.channel.book;

import net.openhft.chronicle.bytes.MethodId;
import net.openhft.chronicle.bytes.UpdateInterceptor;
import net.openhft.chronicle.core.io.Closeable;
import net.openhft.chronicle.wire.MarshallableOut;
import net.openhft.chronicle.wire.MethodWriter;
import net.openhft.chronicle.wire.WriteDocumentContext;

import java.util.function.Supplier;

public final class TopOfBookListenerBinarylightMethodWriter implements net.openhft.chronicle.wire.channel.book.TopOfBookListener, MethodWriter {

    // result
    private transient final Closeable closeable;
    private transient MarshallableOut out;

    // constructor
    public TopOfBookListenerBinarylightMethodWriter(Supplier<MarshallableOut> out, Closeable closeable, UpdateInterceptor updateInterceptor) {
        this.out = out.get();
        this.closeable = closeable;
    }

    @Override
    public void marshallableOut(MarshallableOut out) {
        this.out = out;
    }

    @MethodId(116)
    public void topOfBook(final net.openhft.chronicle.wire.channel.book.TopOfBook topOfBook) {
        MarshallableOut out = this.out;
        try (final WriteDocumentContext dc = (WriteDocumentContext) out.acquireWritingDocument(false)) {
            dc.wire()
                    .writeEventId("topOfBook", 116)
                    .marshallable(topOfBook);
        }
    }
}
