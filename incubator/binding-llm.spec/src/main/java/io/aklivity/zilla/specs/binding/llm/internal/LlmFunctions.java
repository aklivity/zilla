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

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmBeginExFW;
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
        private final LlmBeginExFW.Builder beginExRW;

        private LlmBeginExBuilder()
        {
            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.beginExRW = new LlmBeginExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
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

        public LlmBeginExBuilder contentType(
            String contentType)
        {
            beginExRW.contentType(contentType);
            return this;
        }

        public LlmBeginExBuilder model(
            String model)
        {
            beginExRW.model(model);
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
        private String contentType;
        private String model;

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

        public LlmBeginExMatcherBuilder contentType(
            String contentType)
        {
            this.contentType = contentType;
            return this;
        }

        public LlmBeginExMatcherBuilder model(
            String model)
        {
            this.model = model;
            return this;
        }

        public BytesMatcher build()
        {
            return this::match;
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
                matchDialect(beginEx) &&
                matchContentType(beginEx) &&
                matchModel(beginEx))
            {
                byteBuf.position(byteBuf.position() + beginEx.sizeof());
                return beginEx;
            }

            throw new Exception(beginEx.toString());
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

        private boolean matchContentType(
            LlmBeginExFW beginEx)
        {
            return contentType == null || beginEx.contentType() != null && contentType.equals(beginEx.contentType().asString());
        }

        private boolean matchModel(
            LlmBeginExFW beginEx)
        {
            return model == null || beginEx.model() != null && model.equals(beginEx.model().asString());
        }
    }

    public static final class LlmFlushExBuilder
    {
        private final MutableDirectBufferEx writeBuffer;
        private final LlmFlushExFW.Builder flushExRW;

        private LlmFlushExBuilder()
        {
            this.writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.flushExRW = new LlmFlushExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
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
            final int limit = flushExRW.limit();
            final byte[] array = new byte[limit];
            writeBuffer.getBytes(0, array);
            return array;
        }

        public final class LlmNativeFlushExBuilder
        {
            private int choiceIndex;
            private String type;
            private byte[] payload;

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
                byte[] payload)
            {
                this.payload = payload;
                return this;
            }

            public LlmFlushExBuilder build()
            {
                flushExRW.raw(r ->
                {
                    r.choiceIndex(choiceIndex);
                    if (type != null)
                    {
                        r.type(type);
                    }
                    if (payload != null)
                    {
                        r.payload(new UnsafeBufferEx(payload), 0, payload.length);
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
        private LlmNativeFlushExMatcherBuilder raw;

        public LlmFlushExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public LlmNativeFlushExMatcherBuilder raw()
        {
            this.raw = new LlmNativeFlushExMatcherBuilder();
            return raw;
        }

        public BytesMatcher build()
        {
            return this::match;
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
                matchRaw(flushEx))
            {
                byteBuf.position(byteBuf.position() + flushEx.sizeof());
                return flushEx;
            }

            throw new Exception(flushEx.toString());
        }

        private boolean matchTypeId(
            LlmFlushExFW flushEx)
        {
            return typeId == null || typeId == flushEx.typeId();
        }

        private boolean matchRaw(
            LlmFlushExFW flushEx)
        {
            return raw == null || raw.match(flushEx.raw());
        }

        public final class LlmNativeFlushExMatcherBuilder
        {
            private Integer choiceIndex;
            private String type;
            private boolean typeNull;
            private byte[] payload;
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
                byte[] payload)
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
                return matchChoiceIndex(raw) &&
                    matchType(raw) &&
                    matchPayload(raw);
            }

            private boolean matchChoiceIndex(
                LlmNativeFlushExFW raw)
            {
                return choiceIndex == null || choiceIndex == raw.choiceIndex();
            }

            private boolean matchType(
                LlmNativeFlushExFW raw)
            {
                return typeNull ? raw.type().asString() == null
                    : type == null || type.equals(raw.type().asString());
            }

            private boolean matchPayload(
                LlmNativeFlushExFW raw)
            {
                return payloadNull ? raw.payload() == null
                    : payload == null || matchesPayload(raw.payload());
            }

            private boolean matchesPayload(
                OctetsFW actual)
            {
                boolean matches = actual != null && actual.sizeof() == payload.length;
                if (matches)
                {
                    for (int i = 0; i < payload.length; i++)
                    {
                        if (actual.buffer().getByte(actual.offset() + i) != payload[i])
                        {
                            matches = false;
                            break;
                        }
                    }
                }
                return matches;
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
