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
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmDataExFW;

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

    public static final class LlmDataExBuilder
    {
        private final MutableDirectBufferEx writeBuffer;
        private final LlmDataExFW.Builder dataExRW;

        private LlmDataExBuilder()
        {
            this.writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.dataExRW = new LlmDataExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public LlmDataExBuilder type(
            String type)
        {
            dataExRW.type(type);
            return this;
        }

        public LlmDataExBuilder logProbability(
            String logProbability)
        {
            dataExRW.logProbability(logProbability);
            return this;
        }

        public LlmDataExBuilder inputTokens(
            int inputTokens)
        {
            dataExRW.inputTokens(inputTokens);
            return this;
        }

        public LlmDataExBuilder outputTokens(
            int outputTokens)
        {
            dataExRW.outputTokens(outputTokens);
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
        private String type;
        private boolean typeNull;
        private String logProbability;
        private boolean logProbabilityNull;
        private Integer inputTokens;
        private Integer outputTokens;

        public LlmDataExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public LlmDataExMatcherBuilder type(
            String type)
        {
            this.type = type;
            return this;
        }

        public LlmDataExMatcherBuilder typeNull()
        {
            this.typeNull = true;
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

        public LlmDataExMatcherBuilder inputTokens(
            int inputTokens)
        {
            this.inputTokens = inputTokens;
            return this;
        }

        public LlmDataExMatcherBuilder outputTokens(
            int outputTokens)
        {
            this.outputTokens = outputTokens;
            return this;
        }

        public BytesMatcher build()
        {
            return this::match;
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
                matchType(dataEx) &&
                matchLogProbability(dataEx) &&
                matchInputTokens(dataEx) &&
                matchOutputTokens(dataEx))
            {
                byteBuf.position(byteBuf.position() + dataEx.sizeof());
                return dataEx;
            }

            throw new Exception(dataEx.toString());
        }

        private boolean matchTypeId(
            LlmDataExFW dataEx)
        {
            return typeId == null || typeId == dataEx.typeId();
        }

        private boolean matchType(
            LlmDataExFW dataEx)
        {
            return typeNull ? dataEx.type().asString() == null
                : type == null || type.equals(dataEx.type().asString());
        }

        private boolean matchLogProbability(
            LlmDataExFW dataEx)
        {
            return logProbabilityNull ? dataEx.logProbability().asString() == null
                : logProbability == null || logProbability.equals(dataEx.logProbability().asString());
        }

        private boolean matchInputTokens(
            LlmDataExFW dataEx)
        {
            return inputTokens == null || inputTokens == dataEx.inputTokens();
        }

        private boolean matchOutputTokens(
            LlmDataExFW dataEx)
        {
            return outputTokens == null || outputTokens == dataEx.outputTokens();
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
