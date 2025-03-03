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

// Generated code added here as there is an issue with Java 18.

package net.openhft.chronicle.wire.channel.book;

import static net.openhft.chronicle.wire.channel.book.PerfTopOfBookMain.ONE__NEW_OBJECT;

/**
 * Echo each POJO as it gets it, the real work is in the deserialization and serialization.
 */
public class EchoTopOfBookHandler implements ITopOfBookHandler {
    private TopOfBookListener topOfBookListener;

    @Override
    public void topOfBook(TopOfBook topOfBook) {
        if (ONE__NEW_OBJECT)
            topOfBook = topOfBook.deepCopy();
        topOfBookListener.topOfBook(topOfBook);
    }

    @Override
    public EchoTopOfBookHandler out(TopOfBookListener topOfBookListener) {
        this.topOfBookListener = topOfBookListener;
        return this;
    }
}
