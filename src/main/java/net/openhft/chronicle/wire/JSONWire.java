/*
 * Copyright 2016-2020 chronicle.software
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
import net.openhft.chronicle.bytes.BytesStore;
import net.openhft.chronicle.bytes.StopCharTesters;
import net.openhft.chronicle.bytes.StopCharsTester;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.core.util.ClassNotFoundRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.nio.BufferUnderflowException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static net.openhft.chronicle.bytes.NativeBytes.nativeBytes;

/**
 * JSON wire format
 * <p>
 * At the moment, this is a cut down version of the YAML wire format.
 */
public class JSONWire extends TextWire {

    public static final @NotNull Bytes<byte[]> ULL = Bytes.from("ull");
    @SuppressWarnings("rawtypes")
    static final BytesStore COMMA = BytesStore.from(",");
    static final Supplier<StopCharsTester> STRICT_END_OF_TEXT_JSON_ESCAPING = TextStopCharsTesters.STRICT_END_OF_TEXT_JSON::escaping;
    boolean useTypes;

    @SuppressWarnings("rawtypes")
    public JSONWire() {
        this(Bytes.allocateElasticOnHeap());
    }

    public JSONWire(@NotNull Bytes<?> bytes, boolean use8bit) {
        super(bytes, use8bit);
        trimFirstCurly(false);
    }

    @SuppressWarnings("rawtypes")
    public JSONWire(@NotNull Bytes<?> bytes) {
        this(bytes, false);
    }

    @NotNull
    public static JSONWire from(@NotNull String text) {
        return new JSONWire(Bytes.from(text));
    }

    public static String asText(@NotNull Wire wire) {
        long pos = wire.bytes().readPosition();
        @NotNull JSONWire tw = new JSONWire(nativeBytes());
        wire.copyTo(tw);
        wire.bytes().readPosition(pos);

        return tw.toString();
    }

    static boolean isWrapper(Class<?> type) {
        return type == Integer.class || type == Long.class || type == Float.class ||
                type == Double.class || type == Short.class || type == Character.class ||
                type == Byte.class || type == Boolean.class || type == Void.class;
    }

    @Override
    protected Class defaultKeyClass() {
        return String.class;
    }

    public JSONWire useTypes(boolean outputTypes) {
        this.useTypes = outputTypes;
        return this;
    }

    public boolean useTypes() {
        return useTypes;
    }

    @Override
    public @NotNull TextWire useTextDocuments() {
        readContext = new JSONReadDocumentContext(this);
        writeContext = trimFirstCurly()
                ? new TextWriteDocumentContext(this)
                : new JSONWriteDocumentContext(this);
        return this;
    }

    @NotNull
    @Override
    protected JSONValueOut createValueOut() {
        return new JSONValueOut();
    }

    @NotNull
    @Override
    protected TextValueIn createValueIn() {
        return new JSONValueIn() {

            @Override
            public double float64() {
                consumePadding();
                valueIn.skipType();
                switch (peekCode()) {
                    case '[':
                    case '{':
                        Jvm.warn().on(getClass(), "Unable to read " + valueIn.object() + " as a double.");
                        return 0;
                }

                boolean isNull;

                long l = bytes.readLimit();
                try {
                    bytes.readLimit(bytes.readPosition() + 4);
                    isNull = "null".contentEquals(bytes);
                } finally {
                    bytes.readLimit(l);
                }

                if (isNull) {
                    bytes.readSkip("null".length());
                    consumePadding();
                }

                final double v = isNull ? Double.NaN : bytes.parseDouble();
                checkRewind();
                return v;
            }

            @Override
            public void checkRewind() {
                int ch = peekBack();
                if (ch == ':' || ch == '}' || ch == ']')
                    bytes.readSkip(-1);

                    // !='l' to handle 'null' in JSON wire
                else if (ch != 'l' && (ch > 'F' && (ch < 'a' || ch > 'f'))) {
                    throw new IllegalArgumentException("Unexpected character in number '" + (char) ch + '\'');
                }
            }
        };
    }

    @Override
    public void copyTo(@NotNull WireOut wire) {
        if (wire.getClass() == getClass()) {
            final Bytes<?> bytes0 = bytes();
            final long length = bytes0.readRemaining();
            wire.bytes().write(this.bytes, bytes0.readPosition(), length);
            this.bytes.readSkip(length);
            return;
        }

        consumePadding();
        trimCurlyBrackets();
        while (bytes.readRemaining() > 1) {
            copyOne(wire, true, true);
            consumePadding();
        }
    }

    private void trimCurlyBrackets() {
        if (peekNextByte() == '}') {
            bytes.readSkip(1);
            consumePadding();
            while (peekPreviousByte() <= ' ')
                bytes.writeSkip(-1);
            if (peekPreviousByte() == '}')
                bytes.writeSkip(-1);
            // TODO else error?
        }
    }

    private int peekPreviousByte() {
        return bytes.peekUnsignedByte(bytes.readLimit() - 1);
    }

    public void copyOne(@NotNull WireOut wire, boolean inMap, boolean topLevel) {
        int ch = bytes.readUnsignedByte();
        switch (ch) {
            case '\'':
            case '"':
                copyQuote(wire, ch, inMap, topLevel);
                if (inMap) {
                    consumePadding();
                    int ch2 = bytes.readUnsignedByte();
                    if (ch2 != ':')
                        throw new IORuntimeException("Expected a ':' but got a '" + (char) ch);
                    // copy the value
                    copyOne(wire, false, false);
                }
                return;

            case '{':
                if (isTypePrefix())
                    copyTypePrefix(wire);
                else
                    copyMap(wire);
                return;

            case '[':
                copySequence(wire);
                return;

            case '+':
            case '-':
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case '.':
                copyNumber(wire);
                return;

            case 'n':
                if (bytes.startsWith(ULL) && !Character.isLetterOrDigit(bytes.peekUnsignedByte(bytes.readPosition() + 3))) {
                    bytes.readSkip(3);
                    consumePadding();
                    wire.getValueOut().nu11();
                    return;
                }
                break;

            default:
                break;
        }
        bytes.readSkip(-1);
        throw new IORuntimeException("Unexpected chars '" + bytes.parse8bit(StopCharTesters.CONTROL_STOP) + "'");
    }

    private void copyTypePrefix(WireOut wire) {
        final StringBuilder sb = acquireStringBuilder();
        // the type literal
        getValueIn().text(sb);
        // drop the '@
        sb.deleteCharAt(0);
        wire.getValueOut().typePrefix(sb);
        consumePadding();
        int ch = bytes.readUnsignedByte();
        if (ch != ':')
            throw new IORuntimeException("Expected a ':' after the type " + sb + " but got a " + (char) ch);
        copyOne(wire, true, false);

        consumePadding();
        int ch2 = bytes.readUnsignedByte();
        if (ch2 != '}')
            throw new IORuntimeException("Expected a '}' after the type " + sb + " but got a " + (char) ch);
    }

    private boolean isTypePrefix() {
        final long rp = bytes.readPosition();
        return bytes.peekUnsignedByte(rp) == '"'
                && bytes.peekUnsignedByte(rp + 1) == '@';
    }

    private void copyQuote(WireOut wire, int ch, boolean inMap, boolean topLevel) {
        final StringBuilder sb = acquireStringBuilder();
        while (bytes.readRemaining() > 0) {
            int ch2 = bytes.readUnsignedByte();
            if (ch2 == ch)
                break;
            sb.append((char) ch2);
            if (ch2 == '\\')
                sb.append((char) bytes.readUnsignedByte());
        }
        unescape(sb);
        if (topLevel) {
            wire.writeEvent(String.class, sb);
        } else if (inMap) {
            wire.write(sb);
        } else {
            wire.getValueOut().text(sb);
        }
    }

    private void copyMap(WireOut wire) {
        wire.getValueOut().marshallable(out -> {
            consumePadding();

            while (bytes.readRemaining() > 0) {
                final int ch = peekNextByte();
                if (ch == '}') {
                    bytes.readSkip(1);
                    return;
                }
                copyOne(wire, true, false);
                expectComma('}');
            }
        });
    }

    private void expectComma(char end) {
        consumePadding();
        final int ch = peekNextByte();
        if (ch == end)
            return;
        if (ch == ',') {
            bytes.readSkip(1);
            consumePadding();
        } else {
            throw new IORuntimeException("Expected a comma or '" + end + "' not a '" + (char) ch + "'");
        }
    }

    private void copySequence(WireOut wire) {
        wire.getValueOut().sequence(out -> {
            consumePadding();

            while (bytes.readRemaining() > 1) {
                final int ch = peekNextByte();
                if (ch == ']') {
                    bytes.readSkip(1);
                    return;
                }
                copyOne(wire, false, false);
                expectComma(']');
            }
        });
    }

    private int peekNextByte() {
        return bytes.peekUnsignedByte(bytes.readPosition());
    }

    private void copyNumber(WireOut wire) {
        bytes.readSkip(-1);
        long rp = bytes.readPosition();
        boolean decimal = false;
        while (true) {
            int ch2 = peekNextByte();
            switch (ch2) {
                case '+':
                case '-':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                case '.':
                    bytes.readSkip(1);
                    if (wire.isBinary()) {
                        decimal |= ch2 == '.';
                    } else {
                        wire.bytes().append((char) ch2);
                    }
                    break;
                case '}':
                case ']':
                case ',':
                default:
                    if (wire.isBinary()) {
                        long rl = bytes.readLimit();
                        try {
                            bytes.readPositionRemaining(rp, bytes.readPosition() - rp);
                            if (decimal)
                                wire.getValueOut().float64(bytes.parseDouble());
                            else
                                wire.getValueOut().int64(bytes.parseLong());
                        } finally {
                            bytes.readLimit(rl);
                        }
                    } else {
                        wire.bytes().append(",");
                    }
                    return;
            }
        }
    }

    @NotNull
    @Override
    protected Quotes needsQuotes(@NotNull CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch < ' ')
                return Quotes.DOUBLE;
        }
        return Quotes.NONE;
    }

    @Override
    void escape(@NotNull CharSequence s) {
        bytes.writeUnsignedByte('"');
        if (needsQuotes(s) == Quotes.NONE) {
            bytes.appendUtf8(s);
        } else {
            escape0(s, Quotes.DOUBLE);
        }
        bytes.writeUnsignedByte('"');
    }

    @Override
    public ValueOut writeEvent(Class expectedType, Object eventKey) {
        return super.writeEvent(String.class, "" + eventKey);
    }

    @Override
    public void writeStartEvent() {
    }

    @NotNull
    @Override
    protected StringBuilder readField(@NotNull StringBuilder sb) {
        consumePadding();
        int code = peekCode();
        if (code == '}') {
            sb.setLength(0);
            return sb;
        }
        if (code == '{') {
            if (valueIn.stack.level > 0)
                throw new IORuntimeException("Expected field name, but got { at " + bytes.toDebugString(64));
            valueIn.pushState();
            bytes.readSkip(1);
        }
        return super.readField(sb);
    }

    @Override
    @Deprecated(/* To be removed in 2.24 */)
    @NotNull
    protected TextStopCharsTesters strictEndOfText() {
        return TextStopCharsTesters.STRICT_END_OF_TEXT_JSON;
    }

    @NotNull
    protected Supplier<StopCharsTester> strictEndOfTextEscaping() {
        return STRICT_END_OF_TEXT_JSON_ESCAPING;
    }

    class JSONReadDocumentContext extends TextReadDocumentContext {
        private int first;

        public JSONReadDocumentContext(@Nullable AbstractWire wire) {
            super(wire);
        }

        @Override
        public void start() {
            first = bytes.peekUnsignedByte();
            if (first == '{')
                bytes.readSkip(1);
            super.start();
        }

        @Override
        public void close() {
            if (first == '{') {
                consumePadding();
                if (bytes.peekUnsignedByte() == '}')
                    bytes.readSkip(1);
            }
            super.close();
        }
    }

    class JSONWriteDocumentContext extends TextWriteDocumentContext {
        private long start;

        public JSONWriteDocumentContext(Wire wire) {
            super(wire);
        }

        @Override
        public void start(boolean metaData) {
            int count = this.count;
            super.start(metaData);
            if (count == 0) {
                bytes.append('{');
                start = bytes.writePosition();
            }
        }

        @Override
        public void close() {
            super.close();
            if (count == 0) {
                if (bytes.writePosition() == start) {
                    bytes.writeSkip(-1);
                } else {
                    bytes.append('}');
                }
            }
        }
    }

    class JSONValueOut extends YamlValueOut {

        @Override
        protected void trimWhiteSpace() {
            if (bytes.endsWith('\n') || bytes.endsWith(' '))
                bytes.writeSkip(-1);
        }

        @Override
        protected void indent() {
            // No-op.
        }

        @NotNull
        @Override
        public String nullOut() {
            return "null";
        }

        @NotNull
        @Override
        public JSONWire typeLiteral(@Nullable CharSequence type) {
            return (JSONWire) text(type);
        }

        @NotNull
        @Override
        public JSONValueOut typePrefix(@NotNull CharSequence typeName) {
            if (useTypes) {
                startBlock('{');
                bytes.append("\"@");
                bytes.append(typeName);
                bytes.append("\":");
            }
            return this;
        }

        @Override
        public void endTypePrefix() {
            super.endTypePrefix();
            if (useTypes) {
                endBlock('}');
                elementSeparator();
            }
        }

        @Override
        public void elementSeparator() {
            sep = COMMA;
        }

        @Override
        protected void asTestQuoted(String s, Quotes quotes) {
            bytes.append('"');
            escape0(s, quotes);
            bytes.append('"');
        }

        @Override
        protected void popState() {
        }

        @Override
        protected void pushState() {
            leaf = true;
        }

        @Override
        protected void afterOpen() {
            sep = EMPTY;
        }

        @Override
        protected void afterClose() {

        }

        @Override
        protected void addNewLine(long pos) {
        }

        @Override
        protected void newLine() {
        }

        @Override
        protected void endField() {
            sep = COMMA;
        }

        @Override
        protected void fieldValueSeperator() {
            bytes.writeUnsignedByte(':');
        }

        @Override
        public void writeComment(@NotNull CharSequence s) {
        }

        @Override
        protected String doubleToString(double d) {
            return Double.isNaN(d) ? "null" : super.doubleToString(d);
        }

        @Override
        protected String floatToString(float f) {
            return Float.isNaN(f) ? "null" : super.floatToString(f);
        }

        @NotNull
        @Override
        public JSONWire rawText(CharSequence value) {
            bytes.writeByte((byte) '\"');
            super.rawText(value);
            bytes.writeByte((byte) '\"');
            return JSONWire.this;
        }

        @Override
        public @NotNull JSONWire date(LocalDate localDate) {
            return (JSONWire) text(localDate.toString());
        }

        @Override
        public @NotNull JSONWire dateTime(LocalDateTime localDateTime) {
            return (JSONWire) text(localDateTime.toString());
        }

        @Override
        public @NotNull <V> JSONWire object(@NotNull Class<V> expectedType, V v) {
            return (JSONWire) (useTypes ? super.object(v) : super.object(expectedType, v));
        }

        @Override
        public @NotNull JSONValueOut typePrefix(Class type) {
            if (type.isPrimitive() || isWrapper(type) || type.isEnum()) {
                // Do nothing because there are no other alternatives
                // and thus, the type is implicitly given in the declaration.
                return this;
            } else {
                return (JSONValueOut) super.typePrefix(type);
            }
        }

        @Override
        public @NotNull <K, V> JSONWire marshallable(@Nullable Map<K, V> map, @NotNull Class<K> kClass, @NotNull Class<V> vClass, boolean leaf) {
            return (JSONWire) super.marshallable(map, (Class) String.class, vClass, leaf);
        }


        public @NotNull JSONWire time(final LocalTime localTime) {
            // Todo: fix quoted text
            return (JSONWire) super.time(localTime);
            /*return text(localTime.toString());*/
        }
    }

    class JSONValueIn extends TextValueIn {
        /**
         * @return true if !!null "", if {@code true} reads the !!null "" up to the next STOP, if
         * {@code false} no  data is read  ( data is only peaked if {@code false} )
         */
        @Override
        public boolean isNull() {
            consumePadding();

            if (peekStringIgnoreCase("null")) {
                bytes.readSkip(4);
                // Skip to the next token, consuming any padding and/or a comma
                consumePadding(1);

                // discard the text after it.
                //  text(acquireStringBuilder());
                return true;
            }

            return false;
        }

        @Override
        public String text() {
            @Nullable String text = super.text();
            return text == null || text.equals("null") ? null : text;
        }

        @Override
        protected boolean isASeparator(int nextChar) {
            return true;
        }

        @Override
        public @Nullable Object object() {
            return useTypes ? parseType() : super.object();
        }

        @Override
        public <E> @Nullable E object(@Nullable Class<E> clazz) {
            return useTypes ? parseType(null, clazz) : super.object(clazz);
        }

        @Override
        public <E> E object(@Nullable E using, @Nullable Class clazz) {
            return useTypes ? parseType(using, clazz) : super.object(using, clazz);
        }


        @Override
        public Class typePrefix() {
            return super.typePrefix();
        }

        @Override
        public Object typePrefixOrObject(Class tClass) {
            return super.typePrefixOrObject(tClass);
        }

        @Override
        public Type typeLiteral(BiFunction<CharSequence, ClassNotFoundException, Type> unresolvedHandler) {
            consumePadding();
            final StringBuilder stringBuilder = acquireStringBuilder();
            text(stringBuilder);
            try {
                return classLookup().forName(stringBuilder);
            } catch (ClassNotFoundRuntimeException e) {
                return unresolvedHandler.apply(stringBuilder, e.getCause());
            }
        }

        @Override
        public @Nullable Object marshallable(@NotNull Object object, @NotNull SerializationStrategy strategy) throws BufferUnderflowException, IORuntimeException {
            return super.marshallable(object, strategy);
        }

        @Override
        public boolean isTyped() {
            // Either we use types for sure or we might use types...
            return useTypes || super.isTyped();
        }

        private Object parseType() {
            if (!hasTypeDefinition()) {
                return super.object();
            } else {
                final StringBuilder sb = acquireStringBuilder();
                sb.setLength(0);
                this.wireIn().read(sb);
                final Class<?> clazz = classLookup().forName(sb.subSequence(1, sb.length()));
                return parseType(null, clazz);
            }
        }

        private <E> E parseType(@Nullable E using, @Nullable Class clazz) {
            if (!hasTypeDefinition()) {
                return super.object(using, clazz);
            } else {
                final StringBuilder sb = acquireStringBuilder();
                sb.setLength(0);
                readTypeDefinition(sb);
                final Class<?> overrideClass = classLookup().forName(sb.subSequence(1, sb.length()));
                if (clazz != null && !clazz.isAssignableFrom(overrideClass))
                    throw new ClassCastException("Unable to cast " + overrideClass.getName() + " to " + clazz.getName());
                if (using != null && !overrideClass.isInstance(using))
                    throw new ClassCastException("Unable to reuse a " + using.getClass().getName() + " as a " + overrideClass.getName());
                final E result = super.object(using, overrideClass);

                // remove the closing bracket from the type definition
                consumePadding();
                final char endBracket = bytes.readChar();
                assert endBracket == '}' : "Missing end bracket }, got " + endBracket + " from " + bytes;
                consumePadding(1);

                return result;
            }
        }

        boolean hasTypeDefinition() {
            final long readPos = bytes.readPosition();
            try {
                // Match {"@ with any padding in between
                consumePadding();
                if (bytes.readChar() != '{')
                    return false;
                consumePadding();
                if (bytes.readChar() != '"')
                    return false;
                consumePadding();
                return bytes.readChar() == '@';
            } finally {
                bytes.readPosition(readPos);
            }
        }

        void readTypeDefinition(StringBuilder sb) {
            consumePadding();
            if (bytes.readChar() != '{')
                throw new IORuntimeException("Expected { but got " + bytes);
            consumePadding();
            text(sb);
            consumePadding();
            final char colon = bytes.readChar();
            assert colon == ':' : "Expected : but got " + colon;

        }

        public boolean useTypes() {
            return useTypes;
        }
    }
}
