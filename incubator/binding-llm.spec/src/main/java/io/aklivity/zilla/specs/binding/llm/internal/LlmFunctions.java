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
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmAbortExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmEndExFW;

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
    public static LlmEndExBuilder endEx()
    {
        return new LlmEndExBuilder();
    }

    @Function
    public static LlmEndExMatcherBuilder matchEndEx()
    {
        return new LlmEndExMatcherBuilder();
    }

    @Function
    public static LlmAbortExBuilder abortEx()
    {
        return new LlmAbortExBuilder();
    }

    @Function
    public static LlmAbortExMatcherBuilder matchAbortEx()
    {
        return new LlmAbortExMatcherBuilder();
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
                matchLogProbability(dataEx))
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
    }

    public static final class LlmEndExBuilder
    {
        private final MutableDirectBufferEx writeBuffer;
        private final LlmEndExFW.Builder endExRW;

        private Integer inputTokens;
        private Integer cacheWriteTokens;
        private Integer cacheReadTokens;
        private Integer outputTokens;
        private Integer reasoningTokens;
        private Integer totalTokens;
        private String nativeUsage;

        private LlmEndExBuilder()
        {
            this.writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.endExRW = new LlmEndExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmEndExBuilder typeId(
            int typeId)
        {
            endExRW.typeId(typeId);
            return this;
        }

        public LlmEndExBuilder inputTokens(
            int inputTokens)
        {
            this.inputTokens = inputTokens;
            return this;
        }

        public LlmEndExBuilder cacheWriteTokens(
            int cacheWriteTokens)
        {
            this.cacheWriteTokens = cacheWriteTokens;
            return this;
        }

        public LlmEndExBuilder cacheReadTokens(
            int cacheReadTokens)
        {
            this.cacheReadTokens = cacheReadTokens;
            return this;
        }

        public LlmEndExBuilder outputTokens(
            int outputTokens)
        {
            this.outputTokens = outputTokens;
            return this;
        }

        public LlmEndExBuilder reasoningTokens(
            int reasoningTokens)
        {
            this.reasoningTokens = reasoningTokens;
            return this;
        }

        public LlmEndExBuilder totalTokens(
            int totalTokens)
        {
            this.totalTokens = totalTokens;
            return this;
        }

        public LlmEndExBuilder nativeUsage(
            String nativeUsage)
        {
            this.nativeUsage = nativeUsage;
            return this;
        }

        public byte[] build()
        {
            endExRW.usage(u ->
            {
                u.inputTokens(inputTokens != null ? inputTokens : -1);
                u.cacheWriteTokens(cacheWriteTokens != null ? cacheWriteTokens : -1);
                u.cacheReadTokens(cacheReadTokens != null ? cacheReadTokens : -1);
                u.outputTokens(outputTokens != null ? outputTokens : -1);
                u.reasoningTokens(reasoningTokens != null ? reasoningTokens : -1);
                u.totalTokens(totalTokens != null ? totalTokens : -1);
                u.nativeUsage(nativeUsage);
            });
            final LlmEndExFW endEx = endExRW.build();
            final byte[] array = new byte[endEx.sizeof()];
            endEx.buffer().getBytes(endEx.offset(), array);
            return array;
        }
    }

    public static final class LlmEndExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();

        private final LlmEndExFW endExRO = new LlmEndExFW();

        private Integer typeId;
        private Integer inputTokens;
        private Integer cacheWriteTokens;
        private Integer cacheReadTokens;
        private Integer outputTokens;
        private Integer reasoningTokens;
        private Integer totalTokens;
        private String nativeUsage;
        private boolean nativeUsageNull;

        public LlmEndExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public LlmEndExMatcherBuilder inputTokens(
            int inputTokens)
        {
            this.inputTokens = inputTokens;
            return this;
        }

        public LlmEndExMatcherBuilder cacheWriteTokens(
            int cacheWriteTokens)
        {
            this.cacheWriteTokens = cacheWriteTokens;
            return this;
        }

        public LlmEndExMatcherBuilder cacheReadTokens(
            int cacheReadTokens)
        {
            this.cacheReadTokens = cacheReadTokens;
            return this;
        }

        public LlmEndExMatcherBuilder outputTokens(
            int outputTokens)
        {
            this.outputTokens = outputTokens;
            return this;
        }

        public LlmEndExMatcherBuilder reasoningTokens(
            int reasoningTokens)
        {
            this.reasoningTokens = reasoningTokens;
            return this;
        }

        public LlmEndExMatcherBuilder totalTokens(
            int totalTokens)
        {
            this.totalTokens = totalTokens;
            return this;
        }

        public LlmEndExMatcherBuilder nativeUsage(
            String nativeUsage)
        {
            this.nativeUsage = nativeUsage;
            return this;
        }

        public LlmEndExMatcherBuilder nativeUsageNull()
        {
            this.nativeUsageNull = true;
            return this;
        }

        public BytesMatcher build()
        {
            return this::match;
        }

        private LlmEndExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final LlmEndExFW endEx = endExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (endEx != null &&
                matchTypeId(endEx) &&
                matchInputTokens(endEx) &&
                matchCacheWriteTokens(endEx) &&
                matchCacheReadTokens(endEx) &&
                matchOutputTokens(endEx) &&
                matchReasoningTokens(endEx) &&
                matchTotalTokens(endEx) &&
                matchNativeUsage(endEx))
            {
                byteBuf.position(byteBuf.position() + endEx.sizeof());
                return endEx;
            }

            throw new Exception(endEx.toString());
        }

        private boolean matchTypeId(
            LlmEndExFW endEx)
        {
            return typeId == null || typeId == endEx.typeId();
        }

        private boolean matchInputTokens(
            LlmEndExFW endEx)
        {
            return inputTokens == null || inputTokens == endEx.usage().inputTokens();
        }

        private boolean matchCacheWriteTokens(
            LlmEndExFW endEx)
        {
            return cacheWriteTokens == null || cacheWriteTokens == endEx.usage().cacheWriteTokens();
        }

        private boolean matchCacheReadTokens(
            LlmEndExFW endEx)
        {
            return cacheReadTokens == null || cacheReadTokens == endEx.usage().cacheReadTokens();
        }

        private boolean matchOutputTokens(
            LlmEndExFW endEx)
        {
            return outputTokens == null || outputTokens == endEx.usage().outputTokens();
        }

        private boolean matchReasoningTokens(
            LlmEndExFW endEx)
        {
            return reasoningTokens == null || reasoningTokens == endEx.usage().reasoningTokens();
        }

        private boolean matchTotalTokens(
            LlmEndExFW endEx)
        {
            return totalTokens == null || totalTokens == endEx.usage().totalTokens();
        }

        private boolean matchNativeUsage(
            LlmEndExFW endEx)
        {
            return nativeUsageNull ? endEx.usage().nativeUsage().asString() == null
                : nativeUsage == null || nativeUsage.equals(endEx.usage().nativeUsage().asString());
        }
    }

    public static final class LlmAbortExBuilder
    {
        private final MutableDirectBufferEx writeBuffer;
        private final LlmAbortExFW.Builder abortExRW;

        private Integer inputTokens;
        private Integer cacheWriteTokens;
        private Integer cacheReadTokens;
        private Integer outputTokens;
        private Integer reasoningTokens;
        private Integer totalTokens;
        private String nativeUsage;

        private LlmAbortExBuilder()
        {
            this.writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.abortExRW = new LlmAbortExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmAbortExBuilder typeId(
            int typeId)
        {
            abortExRW.typeId(typeId);
            return this;
        }

        public LlmAbortExBuilder inputTokens(
            int inputTokens)
        {
            this.inputTokens = inputTokens;
            return this;
        }

        public LlmAbortExBuilder cacheWriteTokens(
            int cacheWriteTokens)
        {
            this.cacheWriteTokens = cacheWriteTokens;
            return this;
        }

        public LlmAbortExBuilder cacheReadTokens(
            int cacheReadTokens)
        {
            this.cacheReadTokens = cacheReadTokens;
            return this;
        }

        public LlmAbortExBuilder outputTokens(
            int outputTokens)
        {
            this.outputTokens = outputTokens;
            return this;
        }

        public LlmAbortExBuilder reasoningTokens(
            int reasoningTokens)
        {
            this.reasoningTokens = reasoningTokens;
            return this;
        }

        public LlmAbortExBuilder totalTokens(
            int totalTokens)
        {
            this.totalTokens = totalTokens;
            return this;
        }

        public LlmAbortExBuilder nativeUsage(
            String nativeUsage)
        {
            this.nativeUsage = nativeUsage;
            return this;
        }

        public byte[] build()
        {
            abortExRW.usage(u ->
            {
                u.inputTokens(inputTokens != null ? inputTokens : -1);
                u.cacheWriteTokens(cacheWriteTokens != null ? cacheWriteTokens : -1);
                u.cacheReadTokens(cacheReadTokens != null ? cacheReadTokens : -1);
                u.outputTokens(outputTokens != null ? outputTokens : -1);
                u.reasoningTokens(reasoningTokens != null ? reasoningTokens : -1);
                u.totalTokens(totalTokens != null ? totalTokens : -1);
                u.nativeUsage(nativeUsage);
            });
            final LlmAbortExFW abortEx = abortExRW.build();
            final byte[] array = new byte[abortEx.sizeof()];
            abortEx.buffer().getBytes(abortEx.offset(), array);
            return array;
        }
    }

    public static final class LlmAbortExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();

        private final LlmAbortExFW abortExRO = new LlmAbortExFW();

        private Integer typeId;
        private Integer inputTokens;
        private Integer cacheWriteTokens;
        private Integer cacheReadTokens;
        private Integer outputTokens;
        private Integer reasoningTokens;
        private Integer totalTokens;
        private String nativeUsage;
        private boolean nativeUsageNull;

        public LlmAbortExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public LlmAbortExMatcherBuilder inputTokens(
            int inputTokens)
        {
            this.inputTokens = inputTokens;
            return this;
        }

        public LlmAbortExMatcherBuilder cacheWriteTokens(
            int cacheWriteTokens)
        {
            this.cacheWriteTokens = cacheWriteTokens;
            return this;
        }

        public LlmAbortExMatcherBuilder cacheReadTokens(
            int cacheReadTokens)
        {
            this.cacheReadTokens = cacheReadTokens;
            return this;
        }

        public LlmAbortExMatcherBuilder outputTokens(
            int outputTokens)
        {
            this.outputTokens = outputTokens;
            return this;
        }

        public LlmAbortExMatcherBuilder reasoningTokens(
            int reasoningTokens)
        {
            this.reasoningTokens = reasoningTokens;
            return this;
        }

        public LlmAbortExMatcherBuilder totalTokens(
            int totalTokens)
        {
            this.totalTokens = totalTokens;
            return this;
        }

        public LlmAbortExMatcherBuilder nativeUsage(
            String nativeUsage)
        {
            this.nativeUsage = nativeUsage;
            return this;
        }

        public LlmAbortExMatcherBuilder nativeUsageNull()
        {
            this.nativeUsageNull = true;
            return this;
        }

        public BytesMatcher build()
        {
            return this::match;
        }

        private LlmAbortExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final LlmAbortExFW abortEx = abortExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (abortEx != null &&
                matchTypeId(abortEx) &&
                matchInputTokens(abortEx) &&
                matchCacheWriteTokens(abortEx) &&
                matchCacheReadTokens(abortEx) &&
                matchOutputTokens(abortEx) &&
                matchReasoningTokens(abortEx) &&
                matchTotalTokens(abortEx) &&
                matchNativeUsage(abortEx))
            {
                byteBuf.position(byteBuf.position() + abortEx.sizeof());
                return abortEx;
            }

            throw new Exception(abortEx.toString());
        }

        private boolean matchTypeId(
            LlmAbortExFW abortEx)
        {
            return typeId == null || typeId == abortEx.typeId();
        }

        private boolean matchInputTokens(
            LlmAbortExFW abortEx)
        {
            return inputTokens == null || inputTokens == abortEx.usage().inputTokens();
        }

        private boolean matchCacheWriteTokens(
            LlmAbortExFW abortEx)
        {
            return cacheWriteTokens == null || cacheWriteTokens == abortEx.usage().cacheWriteTokens();
        }

        private boolean matchCacheReadTokens(
            LlmAbortExFW abortEx)
        {
            return cacheReadTokens == null || cacheReadTokens == abortEx.usage().cacheReadTokens();
        }

        private boolean matchOutputTokens(
            LlmAbortExFW abortEx)
        {
            return outputTokens == null || outputTokens == abortEx.usage().outputTokens();
        }

        private boolean matchReasoningTokens(
            LlmAbortExFW abortEx)
        {
            return reasoningTokens == null || reasoningTokens == abortEx.usage().reasoningTokens();
        }

        private boolean matchTotalTokens(
            LlmAbortExFW abortEx)
        {
            return totalTokens == null || totalTokens == abortEx.usage().totalTokens();
        }

        private boolean matchNativeUsage(
            LlmAbortExFW abortEx)
        {
            return nativeUsageNull ? abortEx.usage().nativeUsage().asString() == null
                : nativeUsage == null || nativeUsage.equals(abortEx.usage().nativeUsage().asString());
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
