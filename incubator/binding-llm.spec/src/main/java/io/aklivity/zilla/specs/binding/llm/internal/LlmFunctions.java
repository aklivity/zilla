/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.specs.binding.llm.internal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmFlushExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmNativeFlushExFW;

public final class LlmFunctions
{
    @Function
    public static LlmBeginExBuilder beginEx()
    {
        return new LlmBeginExBuilder();
    }

    @Function
    public static LlmBeginExMatcherBuilder matchBeginEx()
    {
        return new LlmBeginExMatcherBuilder();
    }

    @Function
    public static LlmDataExBuilder dataEx()
    {
        return new LlmDataExBuilder();
    }

    @Function
    public static LlmDataExMatcherBuilder matchDataEx()
    {
        return new LlmDataExMatcherBuilder();
    }

    @Function
    public static LlmFlushExBuilder flushEx()
    {
        return new LlmFlushExBuilder();
    }

    @Function
    public static LlmFlushExMatcherBuilder matchFlushEx()
    {
        return new LlmFlushExMatcherBuilder();
    }

    public static final class LlmBeginExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
        private final LlmBeginExFW.Builder beginExRW = new LlmBeginExFW.Builder();

        private LlmBeginExBuilder()
        {
            beginExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public LlmBeginExBuilder dialect(
            String dialect)
        {
            beginExRW.dialect(dialect);
            return this;
        }

        public byte[] build()
        {
            final LlmBeginExFW beginEx = beginExRW.build();
            final byte[] array = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), array);
            return array;
        }
    }

    public static final class LlmBeginExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final LlmBeginExFW beginExRO = new LlmBeginExFW();

        private Integer typeId;
        private String dialect;

        public LlmBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public LlmBeginExMatcherBuilder dialect(
            String dialect)
        {
            this.dialect = dialect;
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null ? this::match : buf -> null;
        }

        private LlmBeginExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final LlmBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (beginEx != null &&
                matchTypeId(beginEx) &&
                matchDialect(beginEx))
            {
                byteBuf.position(byteBuf.position() + beginEx.sizeof());
                return beginEx;
            }

            throw new Exception(beginEx != null ? beginEx.toString() : "null");
        }

        private boolean matchTypeId(
            LlmBeginExFW beginEx)
        {
            return typeId == null || typeId == beginEx.typeId();
        }

        private boolean matchDialect(
            LlmBeginExFW beginEx)
        {
            return dialect == null || dialect.equals(beginEx.dialect().asString());
        }
    }

    public static final class LlmDataExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
        private final LlmDataExFW.Builder dataExRW = new LlmDataExFW.Builder();

        private LlmDataExBuilder()
        {
            dataExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public LlmDataExBuilder logProbability(
            String logProbability)
        {
            dataExRW.logProbability(logProbability);
            return this;
        }

        public byte[] build()
        {
            final LlmDataExFW dataEx = dataExRW.build();
            final byte[] array = new byte[dataEx.sizeof()];
            dataEx.buffer().getBytes(dataEx.offset(), array);
            return array;
        }
    }

    public static final class LlmDataExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final LlmDataExFW dataExRO = new LlmDataExFW();

        private Integer typeId;
        private String logProbability;
        private boolean logProbabilityNull;

        public LlmDataExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public LlmDataExMatcherBuilder logProbability(
            String logProbability)
        {
            this.logProbability = logProbability;
            return this;
        }

        public LlmDataExMatcherBuilder logProbabilityNull()
        {
            this.logProbabilityNull = true;
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null ? this::match : buf -> null;
        }

        private LlmDataExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final LlmDataExFW dataEx = dataExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (dataEx != null &&
                matchTypeId(dataEx) &&
                matchLogProbability(dataEx))
            {
                byteBuf.position(byteBuf.position() + dataEx.sizeof());
                return dataEx;
            }

            throw new Exception(dataEx != null ? dataEx.toString() : "null");
        }

        private boolean matchTypeId(
            LlmDataExFW dataEx)
        {
            return typeId == null || typeId == dataEx.typeId();
        }

        private boolean matchLogProbability(
            LlmDataExFW dataEx)
        {
            return logProbabilityNull && dataEx.logProbability().asString() == null ||
                logProbability == null || logProbability.equals(dataEx.logProbability().asString());
        }
    }

    public static final class LlmFlushExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
        private final LlmFlushExFW.Builder flushExRW = new LlmFlushExFW.Builder();

        private LlmFlushExBuilder()
        {
            flushExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmFlushExBuilder typeId(
            int typeId)
        {
            flushExRW.typeId(typeId);
            return this;
        }

        public LlmNativeFlushExBuilder raw()
        {
            return new LlmNativeFlushExBuilder();
        }

        public byte[] build()
        {
            final LlmFlushExFW flushEx = flushExRW.build();
            final byte[] array = new byte[flushEx.sizeof()];
            flushEx.buffer().getBytes(flushEx.offset(), array);
            return array;
        }

        public final class LlmNativeFlushExBuilder
        {
            private int choiceIndex;
            private String type;
            private String payload;
            private boolean payloadSet;

            public LlmNativeFlushExBuilder choiceIndex(
                int choiceIndex)
            {
                this.choiceIndex = choiceIndex;
                return this;
            }

            public LlmNativeFlushExBuilder type(
                String type)
            {
                this.type = type;
                return this;
            }

            public LlmNativeFlushExBuilder payload(
                String payload)
            {
                this.payload = payload;
                this.payloadSet = true;
                return this;
            }

            public LlmFlushExBuilder build()
            {
                flushExRW.raw(b ->
                {
                    b.choiceIndex(choiceIndex).type(type);
                    if (payloadSet)
                    {
                        final byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                        b.payload(new UnsafeBufferEx(bytes), 0, bytes.length);
                    }
                });
                return LlmFlushExBuilder.this;
            }
        }
    }

    public static final class LlmFlushExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final LlmFlushExFW flushExRO = new LlmFlushExFW();

        private Integer typeId;
        private Integer kind;
        private LlmNativeFlushExMatcherBuilder rawMatcher;

        public LlmFlushExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public LlmNativeFlushExMatcherBuilder raw()
        {
            this.kind = LlmFlushExFW.KIND_RAW;
            this.rawMatcher = new LlmNativeFlushExMatcherBuilder();
            return rawMatcher;
        }

        public BytesMatcher build()
        {
            return typeId != null || kind != null ? this::match : buf -> null;
        }

        private LlmFlushExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final LlmFlushExFW flushEx = flushExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (flushEx != null &&
                matchTypeId(flushEx) &&
                matchKind(flushEx) &&
                matchRaw(flushEx))
            {
                byteBuf.position(byteBuf.position() + flushEx.sizeof());
                return flushEx;
            }

            throw new Exception(flushEx != null ? flushEx.toString() : "null");
        }

        private boolean matchTypeId(
            LlmFlushExFW flushEx)
        {
            return typeId == null || typeId == flushEx.typeId();
        }

        private boolean matchKind(
            LlmFlushExFW flushEx)
        {
            return kind == null || kind == flushEx.kind();
        }

        private boolean matchRaw(
            LlmFlushExFW flushEx)
        {
            return rawMatcher == null || rawMatcher.match(flushEx.raw());
        }

        public final class LlmNativeFlushExMatcherBuilder
        {
            private Integer choiceIndex;
            private String type;
            private boolean typeNull;
            private String payload;
            private boolean payloadNull;

            public LlmNativeFlushExMatcherBuilder choiceIndex(
                int choiceIndex)
            {
                this.choiceIndex = choiceIndex;
                return this;
            }

            public LlmNativeFlushExMatcherBuilder type(
                String type)
            {
                this.type = type;
                return this;
            }

            public LlmNativeFlushExMatcherBuilder typeNull()
            {
                this.typeNull = true;
                return this;
            }

            public LlmNativeFlushExMatcherBuilder payload(
                String payload)
            {
                this.payload = payload;
                return this;
            }

            public LlmNativeFlushExMatcherBuilder payloadNull()
            {
                this.payloadNull = true;
                return this;
            }

            public LlmFlushExMatcherBuilder build()
            {
                return LlmFlushExMatcherBuilder.this;
            }

            private boolean match(
                LlmNativeFlushExFW raw)
            {
                return matchChoiceIndex(raw) && matchType(raw) && matchPayload(raw);
            }

            private boolean matchChoiceIndex(
                LlmNativeFlushExFW raw)
            {
                return choiceIndex == null || choiceIndex == raw.choiceIndex();
            }

            private boolean matchType(
                LlmNativeFlushExFW raw)
            {
                return typeNull && raw.type().asString() == null ||
                    type == null || type.equals(raw.type().asString());
            }

            private boolean matchPayload(
                LlmNativeFlushExFW raw)
            {
                final boolean matched;
                if (payloadNull)
                {
                    matched = raw.payload() == null;
                }
                else if (payload != null)
                {
                    matched = raw.payload() != null &&
                        payload.equals(raw.payload().buffer()
                            .getStringWithoutLengthUtf8(raw.payload().offset(), raw.payload().sizeof()));
                }
                else
                {
                    matched = true;
                }
                return matched;
            }
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(LlmFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "llm";
        }
    }

    private LlmFunctions()
    {
        // utility
    }
}
