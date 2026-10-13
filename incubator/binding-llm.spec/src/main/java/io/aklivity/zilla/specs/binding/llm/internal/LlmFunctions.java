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
import java.util.LinkedHashMap;
import java.util.Map;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmAbortExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmChallengeExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmEndExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmErrorFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmResetExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmSectionType;

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
    public static LlmChallengeExBuilder challengeEx()
    {
        return new LlmChallengeExBuilder();
    }

    @Function
    public static LlmChallengeExMatcherBuilder matchChallengeEx()
    {
        return new LlmChallengeExMatcherBuilder();
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

    @Function
    public static LlmResetExBuilder resetEx()
    {
        return new LlmResetExBuilder();
    }

    @Function
    public static LlmResetExMatcherBuilder matchResetEx()
    {
        return new LlmResetExMatcherBuilder();
    }

    @Function
    public static LlmDialectDataExBuilder dialectDataEx()
    {
        return new LlmDialectDataExBuilder();
    }

    @Function
    public static LlmDialectDataExMatcherBuilder matchDialectDataEx()
    {
        return new LlmDialectDataExMatcherBuilder();
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

        public LlmBeginExBuilder id(
            String id)
        {
            beginExRW.id(id);
            return this;
        }

        public LlmBeginExBuilder extension(
            String extension)
        {
            final byte[] bytes = extension.getBytes(StandardCharsets.UTF_8);
            beginExRW.extension(e -> e.set(bytes));
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
        private String id;
        private String extension;
        private boolean extensionNull;

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

        public LlmBeginExMatcherBuilder id(
            String id)
        {
            this.id = id;
            return this;
        }

        public LlmBeginExMatcherBuilder extension(
            String extension)
        {
            this.extension = extension;
            return this;
        }

        public LlmBeginExMatcherBuilder extensionNull()
        {
            this.extensionNull = true;
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
            final LlmBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.limit());

            if (beginEx != null &&
                matchTypeId(beginEx) &&
                matchDialect(beginEx) &&
                matchContentType(beginEx) &&
                matchModel(beginEx) &&
                matchId(beginEx) &&
                matchExtension(beginEx))
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

        private boolean matchId(
            LlmBeginExFW beginEx)
        {
            return id == null || beginEx.id() != null && id.equals(beginEx.id().asString());
        }

        private boolean matchExtension(
            LlmBeginExFW beginEx)
        {
            final String actual = asString(beginEx.extension());
            return extensionNull ? actual == null : extension == null || extension.equals(actual);
        }
    }

    public static final class LlmDataExBuilder
    {
        private final MutableDirectBufferEx writeBuffer;
        private final LlmDataExFW.Builder dataExRW;
        private final Map<String, String> attributes;

        private LlmSectionType type;
        private Integer message;
        private String extension;

        private LlmDataExBuilder()
        {
            this.writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.dataExRW = new LlmDataExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
            this.attributes = new LinkedHashMap<>();
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
            this.type = LlmSectionType.valueOf(type);
            return this;
        }

        public LlmDataExBuilder message(
            int message)
        {
            this.message = message;
            return this;
        }

        public LlmDataExBuilder attribute(
            String name,
            String value)
        {
            attributes.put(name, value);
            return this;
        }

        public LlmDataExBuilder extension(
            String extension)
        {
            this.extension = extension;
            return this;
        }

        public byte[] build()
        {
            dataExRW.section(s ->
            {
                if (type != null)
                {
                    s.type(t -> t.set(type));
                }

                if (message != null)
                {
                    s.message(message);
                }

                attributes.forEach((name, value) -> s.attributesItem(a -> a.name(name).value(value)));

                if (extension != null)
                {
                    final byte[] bytes = extension.getBytes(StandardCharsets.UTF_8);
                    s.extension(e -> e.set(bytes));
                }
            });

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
        private LlmSectionType type;
        private boolean typeNull;
        private Integer message;
        private Map<String, String> attributes;
        private boolean attributesNull;
        private String extension;
        private boolean extensionNull;

        public LlmDataExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public LlmDataExMatcherBuilder type(
            String type)
        {
            this.type = LlmSectionType.valueOf(type);
            return this;
        }

        public LlmDataExMatcherBuilder typeNull()
        {
            this.typeNull = true;
            return this;
        }

        public LlmDataExMatcherBuilder message(
            int message)
        {
            this.message = message;
            return this;
        }

        public LlmDataExMatcherBuilder attribute(
            String name,
            String value)
        {
            if (attributes == null)
            {
                attributes = new LinkedHashMap<>();
            }

            attributes.put(name, value);
            return this;
        }

        public LlmDataExMatcherBuilder attributesNull()
        {
            this.attributesNull = true;
            return this;
        }

        public LlmDataExMatcherBuilder extension(
            String extension)
        {
            this.extension = extension;
            return this;
        }

        public LlmDataExMatcherBuilder extensionNull()
        {
            this.extensionNull = true;
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
            final LlmDataExFW dataEx = dataExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.limit());

            if (dataEx != null &&
                dataEx.kind() == LlmDataExFW.KIND_SECTION &&
                matchTypeId(dataEx) &&
                matchType(dataEx) &&
                matchMessage(dataEx) &&
                matchAttributes(dataEx) &&
                matchExtension(dataEx))
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
            return typeNull ? dataEx.section().type().get() == LlmSectionType.NONE
                : type == null || type == dataEx.section().type().get();
        }

        private boolean matchMessage(
            LlmDataExFW dataEx)
        {
            return message == null || message == dataEx.section().message();
        }

        private boolean matchAttributes(
            LlmDataExFW dataEx)
        {
            final Map<String, String> actual = new LinkedHashMap<>();
            dataEx.section().attributes().forEach(a -> actual.put(a.name().asString(), a.value().asString()));

            return attributesNull ? actual.isEmpty() : attributes == null || attributes.equals(actual);
        }

        private boolean matchExtension(
            LlmDataExFW dataEx)
        {
            final String actual = asString(dataEx.section().extension());
            return extensionNull ? actual == null : extension == null || extension.equals(actual);
        }

    }

    public static final class LlmDialectDataExBuilder
    {
        private final LlmDataExFW.Builder dataExRW;

        private final Map<String, String> attributes;

        private boolean complete;

        private LlmDialectDataExBuilder()
        {
            this.attributes = new LinkedHashMap<>();
            final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
            this.dataExRW = new LlmDataExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmDialectDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public LlmDialectDataExBuilder complete(
            boolean complete)
        {
            this.complete = complete;
            return this;
        }

        public LlmDialectDataExBuilder attribute(
            String name,
            String value)
        {
            attributes.put(name, value);
            return this;
        }

        public byte[] build()
        {
            final LlmDataExFW dataEx = dataExRW.dialect(d ->
            {
                d.complete(complete ? 1 : 0);
                attributes.forEach((name, value) -> d.attributesItem(a -> a.name(name).value(value)));
            }).build();
            final byte[] array = new byte[dataEx.sizeof()];
            dataEx.buffer().getBytes(dataEx.offset(), array);
            return array;
        }
    }

    public static final class LlmDialectDataExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();

        private final LlmDataExFW dataExRO = new LlmDataExFW();

        private Integer typeId;
        private Boolean complete;
        private Map<String, String> attributes;

        public LlmDialectDataExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public LlmDialectDataExMatcherBuilder complete(
            boolean complete)
        {
            this.complete = complete;
            return this;
        }

        public LlmDialectDataExMatcherBuilder attribute(
            String name,
            String value)
        {
            if (attributes == null)
            {
                attributes = new LinkedHashMap<>();
            }

            attributes.put(name, value);
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
            final LlmDataExFW dataEx = dataExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.limit());

            if (dataEx != null &&
                dataEx.kind() == LlmDataExFW.KIND_DIALECT &&
                (typeId == null || typeId == dataEx.typeId()) &&
                (complete == null || complete == (dataEx.dialect().complete() != 0)) &&
                matchAttributes(dataEx))
            {
                byteBuf.position(byteBuf.position() + dataEx.sizeof());
                return dataEx;
            }

            throw new Exception(String.valueOf(dataEx));
        }

        private boolean matchAttributes(
            LlmDataExFW dataEx)
        {
            final Map<String, String> actual = new LinkedHashMap<>();
            dataEx.dialect().attributes().forEach(a -> actual.put(a.name().asString(), a.value().asString()));

            return attributes == null || attributes.equals(actual);
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
        private String finishReason;

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

        public LlmEndExBuilder finishReason(
            String finishReason)
        {
            this.finishReason = finishReason;
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
            endExRW.finishReason(finishReason);
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
        private String finishReason;
        private boolean finishReasonNull;

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

        public LlmEndExMatcherBuilder finishReason(
            String finishReason)
        {
            this.finishReason = finishReason;
            return this;
        }

        public LlmEndExMatcherBuilder finishReasonNull()
        {
            this.finishReasonNull = true;
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
                matchNativeUsage(endEx) &&
                matchFinishReason(endEx))
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

        private boolean matchFinishReason(
            LlmEndExFW endEx)
        {
            final String actual = endEx.finishReason().asString();
            return finishReasonNull ? actual == null : finishReason == null || finishReason.equals(actual);
        }

    }

    public static final class LlmChallengeExBuilder
    {
        private final LlmChallengeExFW.Builder challengeExRW;

        private LlmChallengeExBuilder()
        {
            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
            this.challengeExRW = new LlmChallengeExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmChallengeExBuilder typeId(
            int typeId)
        {
            challengeExRW.typeId(typeId);
            return this;
        }

        public LlmChallengeExBuilder dialect(
            String dialect)
        {
            challengeExRW.dialect(dialect);
            return this;
        }

        public byte[] build()
        {
            final LlmChallengeExFW challengeEx = challengeExRW.build();
            final byte[] array = new byte[challengeEx.sizeof()];
            challengeEx.buffer().getBytes(challengeEx.offset(), array);
            return array;
        }
    }

    public static final class LlmChallengeExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();

        private final LlmChallengeExFW challengeExRO = new LlmChallengeExFW();

        private Integer typeId;
        private String dialect;

        public LlmChallengeExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public LlmChallengeExMatcherBuilder dialect(
            String dialect)
        {
            this.dialect = dialect;
            return this;
        }

        public BytesMatcher build()
        {
            return this::match;
        }

        private LlmChallengeExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final LlmChallengeExFW challengeEx = challengeExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.limit());

            if (challengeEx != null &&
                matchTypeId(challengeEx) &&
                matchDialect(challengeEx))
            {
                byteBuf.position(byteBuf.position() + challengeEx.sizeof());
                return challengeEx;
            }

            throw new Exception(challengeEx.toString());
        }

        private boolean matchTypeId(
            LlmChallengeExFW challengeEx)
        {
            return typeId == null || typeId == challengeEx.typeId();
        }

        private boolean matchDialect(
            LlmChallengeExFW challengeEx)
        {
            return dialect == null || dialect.equals(challengeEx.dialect().asString());
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
        private int status = -1;
        private String type;
        private String param;
        private String message;

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

        public LlmAbortExBuilder status(
            int status)
        {
            this.status = status;
            return this;
        }

        public LlmAbortExBuilder type(
            String type)
        {
            this.type = type;
            return this;
        }

        public LlmAbortExBuilder param(
            String param)
        {
            this.param = param;
            return this;
        }

        public LlmAbortExBuilder message(
            String message)
        {
            this.message = message;
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
            abortExRW.error(e -> e.status(status).type(type).param(param).message(message));
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
        private final LlmErrorMatcher error = new LlmErrorMatcher();

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

        public LlmAbortExMatcherBuilder status(
            int status)
        {
            error.status = status;
            return this;
        }

        public LlmAbortExMatcherBuilder type(
            String type)
        {
            error.type = type;
            return this;
        }

        public LlmAbortExMatcherBuilder param(
            String param)
        {
            error.param = param;
            return this;
        }

        public LlmAbortExMatcherBuilder paramNull()
        {
            error.paramNull = true;
            return this;
        }

        public LlmAbortExMatcherBuilder message(
            String message)
        {
            error.message = message;
            return this;
        }

        public LlmAbortExMatcherBuilder errorNone()
        {
            error.none = true;
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
                matchNativeUsage(abortEx) &&
                error.matches(abortEx.error()))
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

    public static final class LlmResetExBuilder
    {
        private final MutableDirectBufferEx writeBuffer;
        private final LlmResetExFW.Builder resetExRW;

        private int status = -1;
        private String type;
        private String param;
        private String message;

        private LlmResetExBuilder()
        {
            this.writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.resetExRW = new LlmResetExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmResetExBuilder typeId(
            int typeId)
        {
            resetExRW.typeId(typeId);
            return this;
        }

        public LlmResetExBuilder status(
            int status)
        {
            this.status = status;
            return this;
        }

        public LlmResetExBuilder type(
            String type)
        {
            this.type = type;
            return this;
        }

        public LlmResetExBuilder param(
            String param)
        {
            this.param = param;
            return this;
        }

        public LlmResetExBuilder message(
            String message)
        {
            this.message = message;
            return this;
        }

        public byte[] build()
        {
            final LlmResetExFW resetEx = resetExRW
                .error(e -> e.status(status).type(type).param(param).message(message))
                .build();
            final byte[] array = new byte[resetEx.sizeof()];
            resetEx.buffer().getBytes(resetEx.offset(), array);
            return array;
        }
    }

    public static final class LlmResetExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();

        private final LlmResetExFW resetExRO = new LlmResetExFW();

        private final LlmErrorMatcher error = new LlmErrorMatcher();

        private Integer typeId;

        public LlmResetExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public LlmResetExMatcherBuilder status(
            int status)
        {
            error.status = status;
            return this;
        }

        public LlmResetExMatcherBuilder type(
            String type)
        {
            error.type = type;
            return this;
        }

        public LlmResetExMatcherBuilder typeNull()
        {
            error.typeNull = true;
            return this;
        }

        public LlmResetExMatcherBuilder param(
            String param)
        {
            error.param = param;
            return this;
        }

        public LlmResetExMatcherBuilder paramNull()
        {
            error.paramNull = true;
            return this;
        }

        public LlmResetExMatcherBuilder message(
            String message)
        {
            error.message = message;
            return this;
        }

        public LlmResetExMatcherBuilder messageNull()
        {
            error.messageNull = true;
            return this;
        }

        public BytesMatcher build()
        {
            return this::match;
        }

        private LlmResetExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final LlmResetExFW resetEx = resetExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (resetEx != null &&
                (typeId == null || typeId == resetEx.typeId()) &&
                error.matches(resetEx.error()))
            {
                byteBuf.position(byteBuf.position() + resetEx.sizeof());
                return resetEx;
            }

            throw new Exception(String.valueOf(resetEx));
        }
    }

    private static final class LlmErrorMatcher
    {
        private Integer status;
        private String type;
        private boolean typeNull;
        private String param;
        private boolean paramNull;
        private String message;
        private boolean messageNull;
        private boolean none;

        private boolean matches(
            LlmErrorFW error)
        {
            final String errorType = error.type().asString();
            final String errorParam = error.param().asString();
            final String errorMessage = error.message().asString();

            return none
                ? error.status() == -1 && errorType == null && errorParam == null && errorMessage == null
                : (status == null || status == error.status()) &&
                    (typeNull ? errorType == null : type == null || type.equals(errorType)) &&
                    (paramNull ? errorParam == null : param == null || param.equals(errorParam)) &&
                    (messageNull ? errorMessage == null : message == null || message.equals(errorMessage));
        }
    }

    private static String asString(
        OctetsFW octets)
    {
        return octets != null && octets.sizeof() > 0
            ? octets.get((buffer, offset, limit) -> buffer.getStringWithoutLengthUtf8(offset, limit - offset))
            : null;
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
