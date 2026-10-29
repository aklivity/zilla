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
package io.aklivity.zilla.specs.binding.agui.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.util.function.Predicate;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.agui.internal.types.OctetsFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiAbortExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiActivityDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiActivityType;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiBeginExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiContentType;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiCustomDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiEncryptedSubtype;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiEndExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiEventMetaFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiRawDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiReasoningDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiReasoningEncryptedDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiResetExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiRole;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiStateDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiStateType;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiStepDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiStepType;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiSubagentDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiSubagentType;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiTextDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiToolCallDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiToolResultDataExFW;

public final class AguiFunctions
{
    @Function
    public static AguiBeginExBuilder beginEx()
    {
        return new AguiBeginExBuilder();
    }

    @Function
    public static AguiBeginExMatcherBuilder matchBeginEx()
    {
        return new AguiBeginExMatcherBuilder();
    }

    @Function
    public static AguiDataExBuilder dataEx()
    {
        return new AguiDataExBuilder();
    }

    @Function
    public static AguiDataExMatcherBuilder matchDataEx()
    {
        return new AguiDataExMatcherBuilder();
    }

    @Function
    public static AguiAbortExBuilder abortEx()
    {
        return new AguiAbortExBuilder();
    }

    @Function
    public static AguiAbortExMatcherBuilder matchAbortEx()
    {
        return new AguiAbortExMatcherBuilder();
    }

    @Function
    public static AguiResetExBuilder resetEx()
    {
        return new AguiResetExBuilder();
    }

    @Function
    public static AguiResetExMatcherBuilder matchResetEx()
    {
        return new AguiResetExMatcherBuilder();
    }

    @Function
    public static AguiEndExBuilder endEx()
    {
        return new AguiEndExBuilder();
    }

    @Function
    public static AguiEndExMatcherBuilder matchEndEx()
    {
        return new AguiEndExMatcherBuilder();
    }

    public static final class AguiBeginExBuilder
    {
        private final AguiBeginExFW.Builder beginExRW;

        private AguiBeginExBuilder()
        {
            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
            this.beginExRW = new AguiBeginExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public AguiBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public AguiBeginExBuilder threadId(
            String threadId)
        {
            beginExRW.threadId(threadId);
            return this;
        }

        public AguiBeginExBuilder runId(
            String runId)
        {
            beginExRW.runId(runId);
            return this;
        }

        public AguiBeginExBuilder parentRunId(
            String parentRunId)
        {
            beginExRW.parentRunId(parentRunId);
            return this;
        }

        public byte[] build()
        {
            final AguiBeginExFW beginEx = beginExRW.build();
            final byte[] array = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), array);
            return array;
        }
    }

    public static final class AguiBeginExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();

        private final AguiBeginExFW beginExRO = new AguiBeginExFW();

        private Integer typeId;
        private String threadId;
        private String runId;
        private String parentRunId;

        public AguiBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public AguiBeginExMatcherBuilder threadId(
            String threadId)
        {
            this.threadId = threadId;
            return this;
        }

        public AguiBeginExMatcherBuilder runId(
            String runId)
        {
            this.runId = runId;
            return this;
        }

        public AguiBeginExMatcherBuilder parentRunId(
            String parentRunId)
        {
            this.parentRunId = parentRunId;
            return this;
        }

        public BytesMatcher build()
        {
            return this::match;
        }

        private AguiBeginExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final AguiBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.limit());

            if (beginEx != null &&
                matchTypeId(beginEx) &&
                matchThreadId(beginEx) &&
                matchRunId(beginEx) &&
                matchParentRunId(beginEx))
            {
                byteBuf.position(byteBuf.position() + beginEx.sizeof());
                return beginEx;
            }

            throw new Exception(String.valueOf(beginEx));
        }

        private boolean matchTypeId(
            AguiBeginExFW beginEx)
        {
            return typeId == null || typeId == beginEx.typeId();
        }

        private boolean matchThreadId(
            AguiBeginExFW beginEx)
        {
            return threadId == null || threadId.equals(beginEx.threadId().asString());
        }

        private boolean matchRunId(
            AguiBeginExFW beginEx)
        {
            return runId == null || runId.equals(beginEx.runId().asString());
        }

        private boolean matchParentRunId(
            AguiBeginExFW beginEx)
        {
            return parentRunId == null || beginEx.parentRunId() != null && parentRunId.equals(beginEx.parentRunId().asString());
        }
    }

    public abstract static class AguiEventMetaBuilder<B extends AguiEventMetaBuilder<B>>
    {
        private long timestamp = -1;
        private String subagent;
        private byte[] metadata;

        public final B timestamp(
            long timestamp)
        {
            this.timestamp = timestamp;
            return self();
        }

        public final B subagent(
            String subagent)
        {
            this.subagent = subagent;
            return self();
        }

        public final B metadata(
            String metadata)
        {
            this.metadata = metadata.getBytes(UTF_8);
            return self();
        }

        protected final AguiEventMetaFW.Builder meta(
            AguiEventMetaFW.Builder builder)
        {
            final AguiEventMetaFW.Builder meta = builder.timestamp(timestamp).subagentRunId(subagent);

            return metadata == null
                ? meta
                : meta.metadataLength(metadata.length).metadata(new UnsafeBufferEx(metadata), 0, metadata.length);
        }

        @SuppressWarnings("unchecked")
        private B self()
        {
            return (B) this;
        }
    }

    public abstract static class AguiEventMetaMatcherBuilder<M extends AguiEventMetaMatcherBuilder<M>>
    {
        private Long timestamp;
        private String subagent;
        private String metadata;

        public final M timestamp(
            long timestamp)
        {
            this.timestamp = timestamp;
            return self();
        }

        public final M subagent(
            String subagent)
        {
            this.subagent = subagent;
            return self();
        }

        public final M metadata(
            String metadata)
        {
            this.metadata = metadata;
            return self();
        }

        protected final boolean matchMeta(
            AguiEventMetaFW meta)
        {
            return (timestamp == null || timestamp == meta.timestamp()) &&
                (subagent == null || subagent.equals(meta.subagentRunId().asString())) &&
                (metadata == null || metadata.equals(asString(meta.metadata())));
        }

        @SuppressWarnings("unchecked")
        private M self()
        {
            return (M) this;
        }
    }

    public static final class AguiDataExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
        private final AguiDataExFW.Builder dataExRW = new AguiDataExFW.Builder();

        private AguiDataExBuilder()
        {
            dataExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public AguiDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public AguiTextDataExBuilder text()
        {
            return new AguiTextDataExBuilder();
        }

        public AguiReasoningDataExBuilder reasoning()
        {
            return new AguiReasoningDataExBuilder();
        }

        public AguiReasoningEncryptedDataExBuilder reasoningEncrypted()
        {
            return new AguiReasoningEncryptedDataExBuilder();
        }

        public AguiToolCallDataExBuilder toolCall()
        {
            return new AguiToolCallDataExBuilder();
        }

        public AguiToolResultDataExBuilder toolResult()
        {
            return new AguiToolResultDataExBuilder();
        }

        public AguiStateDataExBuilder state()
        {
            return new AguiStateDataExBuilder();
        }

        public AguiActivityDataExBuilder activity()
        {
            return new AguiActivityDataExBuilder();
        }

        public AguiStepDataExBuilder step()
        {
            return new AguiStepDataExBuilder();
        }

        public AguiSubagentDataExBuilder subagent()
        {
            return new AguiSubagentDataExBuilder();
        }

        public AguiRawDataExBuilder raw()
        {
            return new AguiRawDataExBuilder();
        }

        public AguiCustomDataExBuilder custom()
        {
            return new AguiCustomDataExBuilder();
        }

        public byte[] build()
        {
            final AguiDataExFW dataEx = dataExRW.build();
            final byte[] array = new byte[dataEx.sizeof()];
            writeBuffer.getBytes(dataEx.offset(), array);
            return array;
        }

        public final class AguiTextDataExBuilder extends AguiEventMetaBuilder<AguiTextDataExBuilder>
        {
            private String messageId;
            private AguiRole role = AguiRole.ASSISTANT;

            private AguiTextDataExBuilder()
            {
            }

            public AguiTextDataExBuilder messageId(
                String messageId)
            {
                this.messageId = messageId;
                return this;
            }

            public AguiTextDataExBuilder role(
                String role)
            {
                this.role = AguiRole.valueOf(role);
                return this;
            }

            public AguiDataExBuilder build()
            {
                dataExRW.text(t -> t
                    .meta(m -> meta(m))
                    .messageId(messageId)
                    .role(x -> x.set(role)));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiReasoningDataExBuilder extends AguiEventMetaBuilder<AguiReasoningDataExBuilder>
        {
            private String messageId;
            private String groupId;

            private AguiReasoningDataExBuilder()
            {
            }

            public AguiReasoningDataExBuilder messageId(
                String messageId)
            {
                this.messageId = messageId;
                return this;
            }

            public AguiReasoningDataExBuilder groupId(
                String groupId)
            {
                this.groupId = groupId;
                return this;
            }

            public AguiDataExBuilder build()
            {
                dataExRW.reasoning(t -> t
                    .meta(m -> meta(m))
                    .messageId(messageId)
                    .groupId(groupId));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiReasoningEncryptedDataExBuilder extends AguiEventMetaBuilder<AguiReasoningEncryptedDataExBuilder>
        {
            private AguiEncryptedSubtype subtype;
            private String entityId;

            private AguiReasoningEncryptedDataExBuilder()
            {
            }

            public AguiReasoningEncryptedDataExBuilder subtype(
                String subtype)
            {
                this.subtype = AguiEncryptedSubtype.valueOf(subtype);
                return this;
            }

            public AguiReasoningEncryptedDataExBuilder entityId(
                String entityId)
            {
                this.entityId = entityId;
                return this;
            }

            public AguiDataExBuilder build()
            {
                dataExRW.reasoningEncrypted(t -> t
                    .meta(m -> meta(m))
                    .subtype(x -> x.set(subtype))
                    .entityId(entityId));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiToolCallDataExBuilder extends AguiEventMetaBuilder<AguiToolCallDataExBuilder>
        {
            private String toolCallId;
            private String toolCallName;
            private String parentMessageId;

            private AguiToolCallDataExBuilder()
            {
            }

            public AguiToolCallDataExBuilder toolCallId(
                String toolCallId)
            {
                this.toolCallId = toolCallId;
                return this;
            }

            public AguiToolCallDataExBuilder toolCallName(
                String toolCallName)
            {
                this.toolCallName = toolCallName;
                return this;
            }

            public AguiToolCallDataExBuilder parentMessageId(
                String parentMessageId)
            {
                this.parentMessageId = parentMessageId;
                return this;
            }

            public AguiDataExBuilder build()
            {
                dataExRW.toolCall(t -> t
                    .meta(m -> meta(m))
                    .toolCallId(toolCallId)
                    .toolCallName(toolCallName)
                    .parentMessageId(parentMessageId));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiToolResultDataExBuilder extends AguiEventMetaBuilder<AguiToolResultDataExBuilder>
        {
            private String messageId;
            private String toolCallId;
            private AguiContentType content = AguiContentType.TEXT;

            private AguiToolResultDataExBuilder()
            {
            }

            public AguiToolResultDataExBuilder messageId(
                String messageId)
            {
                this.messageId = messageId;
                return this;
            }

            public AguiToolResultDataExBuilder toolCallId(
                String toolCallId)
            {
                this.toolCallId = toolCallId;
                return this;
            }

            public AguiToolResultDataExBuilder content(
                String content)
            {
                this.content = AguiContentType.valueOf(content);
                return this;
            }

            public AguiDataExBuilder build()
            {
                dataExRW.toolResult(t -> t
                    .meta(m -> meta(m))
                    .messageId(messageId)
                    .toolCallId(toolCallId)
                    .content(x -> x.set(content)));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiStateDataExBuilder extends AguiEventMetaBuilder<AguiStateDataExBuilder>
        {
            private AguiStateType type;

            private AguiStateDataExBuilder()
            {
            }

            public AguiStateDataExBuilder type(
                String type)
            {
                this.type = AguiStateType.valueOf(type);
                return this;
            }

            public AguiDataExBuilder build()
            {
                dataExRW.state(t -> t
                    .meta(m -> meta(m))
                    .type(x -> x.set(type)));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiActivityDataExBuilder extends AguiEventMetaBuilder<AguiActivityDataExBuilder>
        {
            private AguiActivityType type;
            private String messageId;
            private String activityType;
            private int replace = 1;

            private AguiActivityDataExBuilder()
            {
            }

            public AguiActivityDataExBuilder type(
                String type)
            {
                this.type = AguiActivityType.valueOf(type);
                return this;
            }

            public AguiActivityDataExBuilder messageId(
                String messageId)
            {
                this.messageId = messageId;
                return this;
            }

            public AguiActivityDataExBuilder activityType(
                String activityType)
            {
                this.activityType = activityType;
                return this;
            }

            public AguiActivityDataExBuilder replace(
                int replace)
            {
                this.replace = replace;
                return this;
            }

            public AguiDataExBuilder build()
            {
                dataExRW.activity(t -> t
                    .meta(m -> meta(m))
                    .type(x -> x.set(type))
                    .messageId(messageId)
                    .activityType(activityType)
                    .replace(replace));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiStepDataExBuilder extends AguiEventMetaBuilder<AguiStepDataExBuilder>
        {
            private AguiStepType type;
            private String stepName;

            private AguiStepDataExBuilder()
            {
            }

            public AguiStepDataExBuilder type(
                String type)
            {
                this.type = AguiStepType.valueOf(type);
                return this;
            }

            public AguiStepDataExBuilder stepName(
                String stepName)
            {
                this.stepName = stepName;
                return this;
            }

            public AguiDataExBuilder build()
            {
                dataExRW.step(t -> t
                    .meta(m -> meta(m))
                    .type(x -> x.set(type))
                    .stepName(stepName));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiSubagentDataExBuilder extends AguiEventMetaBuilder<AguiSubagentDataExBuilder>
        {
            private AguiSubagentType type;
            private String subagentRunId;
            private String name;
            private String description;
            private String parentSubagentRunId;
            private String parentToolCallId;
            private String parentMessageId;
            private String code;
            private String message;

            private AguiSubagentDataExBuilder()
            {
            }

            public AguiSubagentDataExBuilder type(
                String type)
            {
                this.type = AguiSubagentType.valueOf(type);
                return this;
            }

            public AguiSubagentDataExBuilder subagentRunId(
                String subagentRunId)
            {
                this.subagentRunId = subagentRunId;
                return this;
            }

            public AguiSubagentDataExBuilder name(
                String name)
            {
                this.name = name;
                return this;
            }

            public AguiSubagentDataExBuilder description(
                String description)
            {
                this.description = description;
                return this;
            }

            public AguiSubagentDataExBuilder parentSubagentRunId(
                String parentSubagentRunId)
            {
                this.parentSubagentRunId = parentSubagentRunId;
                return this;
            }

            public AguiSubagentDataExBuilder parentToolCallId(
                String parentToolCallId)
            {
                this.parentToolCallId = parentToolCallId;
                return this;
            }

            public AguiSubagentDataExBuilder parentMessageId(
                String parentMessageId)
            {
                this.parentMessageId = parentMessageId;
                return this;
            }

            public AguiSubagentDataExBuilder code(
                String code)
            {
                this.code = code;
                return this;
            }

            public AguiSubagentDataExBuilder message(
                String message)
            {
                this.message = message;
                return this;
            }

            public AguiDataExBuilder build()
            {
                dataExRW.subagent(t -> t
                    .meta(m -> meta(m))
                    .type(x -> x.set(type))
                    .subagentRunId(subagentRunId)
                    .name(name)
                    .description(description)
                    .parentSubagentRunId(parentSubagentRunId)
                    .parentToolCallId(parentToolCallId)
                    .parentMessageId(parentMessageId)
                    .code(code)
                    .message(message));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiRawDataExBuilder extends AguiEventMetaBuilder<AguiRawDataExBuilder>
        {
            private String source;

            private AguiRawDataExBuilder()
            {
            }

            public AguiRawDataExBuilder source(
                String source)
            {
                this.source = source;
                return this;
            }

            public AguiDataExBuilder build()
            {
                dataExRW.raw(t -> t
                    .meta(m -> meta(m))
                    .source(source));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiCustomDataExBuilder extends AguiEventMetaBuilder<AguiCustomDataExBuilder>
        {
            private String name;

            private AguiCustomDataExBuilder()
            {
            }

            public AguiCustomDataExBuilder name(
                String name)
            {
                this.name = name;
                return this;
            }

            public AguiDataExBuilder build()
            {
                dataExRW.custom(t -> t
                    .meta(m -> meta(m))
                    .name(name));
                return AguiDataExBuilder.this;
            }
        }
    }

    public static final class AguiDataExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final AguiDataExFW dataExRO = new AguiDataExFW();

        private Integer typeId;
        private Integer kind;
        private Predicate<AguiDataExFW> caseMatcher;

        public AguiDataExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public AguiTextDataExMatcherBuilder text()
        {
            this.kind = AguiDataExFW.KIND_TEXT;
            final AguiTextDataExMatcherBuilder matcher = new AguiTextDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public AguiReasoningDataExMatcherBuilder reasoning()
        {
            this.kind = AguiDataExFW.KIND_REASONING;
            final AguiReasoningDataExMatcherBuilder matcher = new AguiReasoningDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public AguiReasoningEncryptedDataExMatcherBuilder reasoningEncrypted()
        {
            this.kind = AguiDataExFW.KIND_REASONING_ENCRYPTED;
            final AguiReasoningEncryptedDataExMatcherBuilder matcher = new AguiReasoningEncryptedDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public AguiToolCallDataExMatcherBuilder toolCall()
        {
            this.kind = AguiDataExFW.KIND_TOOL_CALL;
            final AguiToolCallDataExMatcherBuilder matcher = new AguiToolCallDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public AguiToolResultDataExMatcherBuilder toolResult()
        {
            this.kind = AguiDataExFW.KIND_TOOL_RESULT;
            final AguiToolResultDataExMatcherBuilder matcher = new AguiToolResultDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public AguiStateDataExMatcherBuilder state()
        {
            this.kind = AguiDataExFW.KIND_STATE;
            final AguiStateDataExMatcherBuilder matcher = new AguiStateDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public AguiActivityDataExMatcherBuilder activity()
        {
            this.kind = AguiDataExFW.KIND_ACTIVITY;
            final AguiActivityDataExMatcherBuilder matcher = new AguiActivityDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public AguiStepDataExMatcherBuilder step()
        {
            this.kind = AguiDataExFW.KIND_STEP;
            final AguiStepDataExMatcherBuilder matcher = new AguiStepDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public AguiSubagentDataExMatcherBuilder subagent()
        {
            this.kind = AguiDataExFW.KIND_SUBAGENT;
            final AguiSubagentDataExMatcherBuilder matcher = new AguiSubagentDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public AguiRawDataExMatcherBuilder raw()
        {
            this.kind = AguiDataExFW.KIND_RAW;
            final AguiRawDataExMatcherBuilder matcher = new AguiRawDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public AguiCustomDataExMatcherBuilder custom()
        {
            this.kind = AguiDataExFW.KIND_CUSTOM;
            final AguiCustomDataExMatcherBuilder matcher = new AguiCustomDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public BytesMatcher build()
        {
            return this::match;
        }

        private AguiDataExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final AguiDataExFW dataEx = dataExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.limit());

            if (dataEx != null &&
                matchTypeId(dataEx) &&
                matchKind(dataEx) &&
                matchCase(dataEx))
            {
                byteBuf.position(byteBuf.position() + dataEx.sizeof());
                return dataEx;
            }

            throw new Exception(String.valueOf(dataEx));
        }

        private boolean matchTypeId(
            AguiDataExFW dataEx)
        {
            return typeId == null || typeId == dataEx.typeId();
        }

        private boolean matchKind(
            AguiDataExFW dataEx)
        {
            return kind == null || kind == dataEx.kind();
        }

        private boolean matchCase(
            AguiDataExFW dataEx)
        {
            return caseMatcher == null || caseMatcher.test(dataEx);
        }

        public final class AguiTextDataExMatcherBuilder extends AguiEventMetaMatcherBuilder<AguiTextDataExMatcherBuilder>
        {
            private String messageId;
            private AguiRole role;

            private AguiTextDataExMatcherBuilder()
            {
            }

            public AguiTextDataExMatcherBuilder messageId(
                String messageId)
            {
                this.messageId = messageId;
                return this;
            }

            public AguiTextDataExMatcherBuilder role(
                String role)
            {
                this.role = AguiRole.valueOf(role);
                return this;
            }

            public AguiDataExMatcherBuilder build()
            {
                return AguiDataExMatcherBuilder.this;
            }

            private boolean match(
                AguiDataExFW dataEx)
            {
                final AguiTextDataExFW text = dataEx.text();
                return matchMeta(text.meta()) &&
                    matchMessageId(text) &&
                    matchRole(text);
            }

            private boolean matchMessageId(
                AguiTextDataExFW text)
            {
                return messageId == null || messageId.equals(text.messageId().asString());
            }

            private boolean matchRole(
                AguiTextDataExFW text)
            {
                return role == null || role == text.role().get();
            }
        }

        public final class AguiReasoningDataExMatcherBuilder
            extends AguiEventMetaMatcherBuilder<AguiReasoningDataExMatcherBuilder>
        {
            private String messageId;
            private String groupId;

            private AguiReasoningDataExMatcherBuilder()
            {
            }

            public AguiReasoningDataExMatcherBuilder messageId(
                String messageId)
            {
                this.messageId = messageId;
                return this;
            }

            public AguiReasoningDataExMatcherBuilder groupId(
                String groupId)
            {
                this.groupId = groupId;
                return this;
            }

            public AguiDataExMatcherBuilder build()
            {
                return AguiDataExMatcherBuilder.this;
            }

            private boolean match(
                AguiDataExFW dataEx)
            {
                final AguiReasoningDataExFW reasoning = dataEx.reasoning();
                return matchMeta(reasoning.meta()) &&
                    matchMessageId(reasoning) &&
                    matchGroupId(reasoning);
            }

            private boolean matchMessageId(
                AguiReasoningDataExFW reasoning)
            {
                return messageId == null || messageId.equals(reasoning.messageId().asString());
            }

            private boolean matchGroupId(
                AguiReasoningDataExFW reasoning)
            {
                return groupId == null || groupId.equals(reasoning.groupId().asString());
            }
        }

        public final class AguiReasoningEncryptedDataExMatcherBuilder
            extends AguiEventMetaMatcherBuilder<AguiReasoningEncryptedDataExMatcherBuilder>
        {
            private AguiEncryptedSubtype subtype;
            private String entityId;

            private AguiReasoningEncryptedDataExMatcherBuilder()
            {
            }

            public AguiReasoningEncryptedDataExMatcherBuilder subtype(
                String subtype)
            {
                this.subtype = AguiEncryptedSubtype.valueOf(subtype);
                return this;
            }

            public AguiReasoningEncryptedDataExMatcherBuilder entityId(
                String entityId)
            {
                this.entityId = entityId;
                return this;
            }

            public AguiDataExMatcherBuilder build()
            {
                return AguiDataExMatcherBuilder.this;
            }

            private boolean match(
                AguiDataExFW dataEx)
            {
                final AguiReasoningEncryptedDataExFW reasoningEncrypted = dataEx.reasoningEncrypted();
                return matchMeta(reasoningEncrypted.meta()) &&
                    matchSubtype(reasoningEncrypted) &&
                    matchEntityId(reasoningEncrypted);
            }

            private boolean matchSubtype(
                AguiReasoningEncryptedDataExFW reasoningEncrypted)
            {
                return subtype == null || subtype == reasoningEncrypted.subtype().get();
            }

            private boolean matchEntityId(
                AguiReasoningEncryptedDataExFW reasoningEncrypted)
            {
                return entityId == null || entityId.equals(reasoningEncrypted.entityId().asString());
            }
        }

        public final class AguiToolCallDataExMatcherBuilder extends AguiEventMetaMatcherBuilder<AguiToolCallDataExMatcherBuilder>
        {
            private String toolCallId;
            private String toolCallName;
            private String parentMessageId;

            private AguiToolCallDataExMatcherBuilder()
            {
            }

            public AguiToolCallDataExMatcherBuilder toolCallId(
                String toolCallId)
            {
                this.toolCallId = toolCallId;
                return this;
            }

            public AguiToolCallDataExMatcherBuilder toolCallName(
                String toolCallName)
            {
                this.toolCallName = toolCallName;
                return this;
            }

            public AguiToolCallDataExMatcherBuilder parentMessageId(
                String parentMessageId)
            {
                this.parentMessageId = parentMessageId;
                return this;
            }

            public AguiDataExMatcherBuilder build()
            {
                return AguiDataExMatcherBuilder.this;
            }

            private boolean match(
                AguiDataExFW dataEx)
            {
                final AguiToolCallDataExFW toolCall = dataEx.toolCall();
                return matchMeta(toolCall.meta()) &&
                    matchToolCallId(toolCall) &&
                    matchToolCallName(toolCall) &&
                    matchParentMessageId(toolCall);
            }

            private boolean matchToolCallId(
                AguiToolCallDataExFW toolCall)
            {
                return toolCallId == null || toolCallId.equals(toolCall.toolCallId().asString());
            }

            private boolean matchToolCallName(
                AguiToolCallDataExFW toolCall)
            {
                return toolCallName == null || toolCallName.equals(toolCall.toolCallName().asString());
            }

            private boolean matchParentMessageId(
                AguiToolCallDataExFW toolCall)
            {
                return parentMessageId == null || parentMessageId.equals(toolCall.parentMessageId().asString());
            }
        }

        public final class AguiToolResultDataExMatcherBuilder
            extends AguiEventMetaMatcherBuilder<AguiToolResultDataExMatcherBuilder>
        {
            private String messageId;
            private String toolCallId;
            private AguiContentType content;

            private AguiToolResultDataExMatcherBuilder()
            {
            }

            public AguiToolResultDataExMatcherBuilder messageId(
                String messageId)
            {
                this.messageId = messageId;
                return this;
            }

            public AguiToolResultDataExMatcherBuilder toolCallId(
                String toolCallId)
            {
                this.toolCallId = toolCallId;
                return this;
            }

            public AguiToolResultDataExMatcherBuilder content(
                String content)
            {
                this.content = AguiContentType.valueOf(content);
                return this;
            }

            public AguiDataExMatcherBuilder build()
            {
                return AguiDataExMatcherBuilder.this;
            }

            private boolean match(
                AguiDataExFW dataEx)
            {
                final AguiToolResultDataExFW toolResult = dataEx.toolResult();
                return matchMeta(toolResult.meta()) &&
                    matchMessageId(toolResult) &&
                    matchToolCallId(toolResult) &&
                    matchContent(toolResult);
            }

            private boolean matchMessageId(
                AguiToolResultDataExFW toolResult)
            {
                return messageId == null || messageId.equals(toolResult.messageId().asString());
            }

            private boolean matchToolCallId(
                AguiToolResultDataExFW toolResult)
            {
                return toolCallId == null || toolCallId.equals(toolResult.toolCallId().asString());
            }

            private boolean matchContent(
                AguiToolResultDataExFW toolResult)
            {
                return content == null || content == toolResult.content().get();
            }
        }

        public final class AguiStateDataExMatcherBuilder extends AguiEventMetaMatcherBuilder<AguiStateDataExMatcherBuilder>
        {
            private AguiStateType type;

            private AguiStateDataExMatcherBuilder()
            {
            }

            public AguiStateDataExMatcherBuilder type(
                String type)
            {
                this.type = AguiStateType.valueOf(type);
                return this;
            }

            public AguiDataExMatcherBuilder build()
            {
                return AguiDataExMatcherBuilder.this;
            }

            private boolean match(
                AguiDataExFW dataEx)
            {
                final AguiStateDataExFW state = dataEx.state();
                return matchMeta(state.meta()) &&
                    matchType(state);
            }

            private boolean matchType(
                AguiStateDataExFW state)
            {
                return type == null || type == state.type().get();
            }
        }

        public final class AguiActivityDataExMatcherBuilder extends AguiEventMetaMatcherBuilder<AguiActivityDataExMatcherBuilder>
        {
            private AguiActivityType type;
            private String messageId;
            private String activityType;
            private Integer replace;

            private AguiActivityDataExMatcherBuilder()
            {
            }

            public AguiActivityDataExMatcherBuilder type(
                String type)
            {
                this.type = AguiActivityType.valueOf(type);
                return this;
            }

            public AguiActivityDataExMatcherBuilder messageId(
                String messageId)
            {
                this.messageId = messageId;
                return this;
            }

            public AguiActivityDataExMatcherBuilder activityType(
                String activityType)
            {
                this.activityType = activityType;
                return this;
            }

            public AguiActivityDataExMatcherBuilder replace(
                int replace)
            {
                this.replace = replace;
                return this;
            }

            public AguiDataExMatcherBuilder build()
            {
                return AguiDataExMatcherBuilder.this;
            }

            private boolean match(
                AguiDataExFW dataEx)
            {
                final AguiActivityDataExFW activity = dataEx.activity();
                return matchMeta(activity.meta()) &&
                    matchType(activity) &&
                    matchMessageId(activity) &&
                    matchActivityType(activity) &&
                    matchReplace(activity);
            }

            private boolean matchType(
                AguiActivityDataExFW activity)
            {
                return type == null || type == activity.type().get();
            }

            private boolean matchMessageId(
                AguiActivityDataExFW activity)
            {
                return messageId == null || messageId.equals(activity.messageId().asString());
            }

            private boolean matchActivityType(
                AguiActivityDataExFW activity)
            {
                return activityType == null || activityType.equals(activity.activityType().asString());
            }

            private boolean matchReplace(
                AguiActivityDataExFW activity)
            {
                return replace == null || replace == activity.replace();
            }
        }

        public final class AguiStepDataExMatcherBuilder extends AguiEventMetaMatcherBuilder<AguiStepDataExMatcherBuilder>
        {
            private AguiStepType type;
            private String stepName;

            private AguiStepDataExMatcherBuilder()
            {
            }

            public AguiStepDataExMatcherBuilder type(
                String type)
            {
                this.type = AguiStepType.valueOf(type);
                return this;
            }

            public AguiStepDataExMatcherBuilder stepName(
                String stepName)
            {
                this.stepName = stepName;
                return this;
            }

            public AguiDataExMatcherBuilder build()
            {
                return AguiDataExMatcherBuilder.this;
            }

            private boolean match(
                AguiDataExFW dataEx)
            {
                final AguiStepDataExFW step = dataEx.step();
                return matchMeta(step.meta()) &&
                    matchType(step) &&
                    matchStepName(step);
            }

            private boolean matchType(
                AguiStepDataExFW step)
            {
                return type == null || type == step.type().get();
            }

            private boolean matchStepName(
                AguiStepDataExFW step)
            {
                return stepName == null || stepName.equals(step.stepName().asString());
            }
        }

        public final class AguiSubagentDataExMatcherBuilder extends AguiEventMetaMatcherBuilder<AguiSubagentDataExMatcherBuilder>
        {
            private AguiSubagentType type;
            private String subagentRunId;
            private String name;
            private String description;
            private String parentSubagentRunId;
            private String parentToolCallId;
            private String parentMessageId;
            private String code;
            private String message;

            private AguiSubagentDataExMatcherBuilder()
            {
            }

            public AguiSubagentDataExMatcherBuilder type(
                String type)
            {
                this.type = AguiSubagentType.valueOf(type);
                return this;
            }

            public AguiSubagentDataExMatcherBuilder subagentRunId(
                String subagentRunId)
            {
                this.subagentRunId = subagentRunId;
                return this;
            }

            public AguiSubagentDataExMatcherBuilder name(
                String name)
            {
                this.name = name;
                return this;
            }

            public AguiSubagentDataExMatcherBuilder description(
                String description)
            {
                this.description = description;
                return this;
            }

            public AguiSubagentDataExMatcherBuilder parentSubagentRunId(
                String parentSubagentRunId)
            {
                this.parentSubagentRunId = parentSubagentRunId;
                return this;
            }

            public AguiSubagentDataExMatcherBuilder parentToolCallId(
                String parentToolCallId)
            {
                this.parentToolCallId = parentToolCallId;
                return this;
            }

            public AguiSubagentDataExMatcherBuilder parentMessageId(
                String parentMessageId)
            {
                this.parentMessageId = parentMessageId;
                return this;
            }

            public AguiSubagentDataExMatcherBuilder code(
                String code)
            {
                this.code = code;
                return this;
            }

            public AguiSubagentDataExMatcherBuilder message(
                String message)
            {
                this.message = message;
                return this;
            }

            public AguiDataExMatcherBuilder build()
            {
                return AguiDataExMatcherBuilder.this;
            }

            private boolean match(
                AguiDataExFW dataEx)
            {
                final AguiSubagentDataExFW subagent = dataEx.subagent();
                return matchMeta(subagent.meta()) &&
                    matchType(subagent) &&
                    matchSubagentRunId(subagent) &&
                    matchName(subagent) &&
                    matchDescription(subagent) &&
                    matchParentSubagentRunId(subagent) &&
                    matchParentToolCallId(subagent) &&
                    matchParentMessageId(subagent) &&
                    matchCode(subagent) &&
                    matchMessage(subagent);
            }

            private boolean matchType(
                AguiSubagentDataExFW subagent)
            {
                return type == null || type == subagent.type().get();
            }

            private boolean matchSubagentRunId(
                AguiSubagentDataExFW subagent)
            {
                return subagentRunId == null || subagentRunId.equals(subagent.subagentRunId().asString());
            }

            private boolean matchName(
                AguiSubagentDataExFW subagent)
            {
                return name == null || name.equals(subagent.name().asString());
            }

            private boolean matchDescription(
                AguiSubagentDataExFW subagent)
            {
                return description == null || description.equals(subagent.description().asString());
            }

            private boolean matchParentSubagentRunId(
                AguiSubagentDataExFW subagent)
            {
                return parentSubagentRunId == null || parentSubagentRunId.equals(subagent.parentSubagentRunId().asString());
            }

            private boolean matchParentToolCallId(
                AguiSubagentDataExFW subagent)
            {
                return parentToolCallId == null || parentToolCallId.equals(subagent.parentToolCallId().asString());
            }

            private boolean matchParentMessageId(
                AguiSubagentDataExFW subagent)
            {
                return parentMessageId == null || parentMessageId.equals(subagent.parentMessageId().asString());
            }

            private boolean matchCode(
                AguiSubagentDataExFW subagent)
            {
                return code == null || code.equals(subagent.code().asString());
            }

            private boolean matchMessage(
                AguiSubagentDataExFW subagent)
            {
                return message == null || message.equals(subagent.message().asString());
            }
        }

        public final class AguiRawDataExMatcherBuilder extends AguiEventMetaMatcherBuilder<AguiRawDataExMatcherBuilder>
        {
            private String source;

            private AguiRawDataExMatcherBuilder()
            {
            }

            public AguiRawDataExMatcherBuilder source(
                String source)
            {
                this.source = source;
                return this;
            }

            public AguiDataExMatcherBuilder build()
            {
                return AguiDataExMatcherBuilder.this;
            }

            private boolean match(
                AguiDataExFW dataEx)
            {
                final AguiRawDataExFW raw = dataEx.raw();
                return matchMeta(raw.meta()) &&
                    matchSource(raw);
            }

            private boolean matchSource(
                AguiRawDataExFW raw)
            {
                return source == null || source.equals(raw.source().asString());
            }
        }

        public final class AguiCustomDataExMatcherBuilder extends AguiEventMetaMatcherBuilder<AguiCustomDataExMatcherBuilder>
        {
            private String name;

            private AguiCustomDataExMatcherBuilder()
            {
            }

            public AguiCustomDataExMatcherBuilder name(
                String name)
            {
                this.name = name;
                return this;
            }

            public AguiDataExMatcherBuilder build()
            {
                return AguiDataExMatcherBuilder.this;
            }

            private boolean match(
                AguiDataExFW dataEx)
            {
                final AguiCustomDataExFW custom = dataEx.custom();
                return matchMeta(custom.meta()) &&
                    matchName(custom);
            }

            private boolean matchName(
                AguiCustomDataExFW custom)
            {
                return name == null || name.equals(custom.name().asString());
            }
        }
    }

    public static final class AguiEndExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
        private final AguiEndExFW.Builder endExRW = new AguiEndExFW.Builder();

        private AguiOutcomeType outcome = AguiOutcomeType.NONE;
        private byte[] detail;

        private AguiEndExBuilder()
        {
            endExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public AguiEndExBuilder typeId(
            int typeId)
        {
            endExRW.typeId(typeId);
            return this;
        }

        public AguiEndExBuilder outcome(
            String outcome)
        {
            this.outcome = AguiOutcomeType.valueOf(outcome);
            return this;
        }

        public AguiEndExBuilder detail(
            String detail)
        {
            this.detail = detail.getBytes(UTF_8);
            return this;
        }

        public byte[] build()
        {
            final AguiEndExFW.Builder builder = endExRW.outcome(o -> o.set(outcome));

            final AguiEndExFW endEx = detail == null
                ? builder.build()
                : builder.detailLength(detail.length).detail(new UnsafeBufferEx(detail), 0, detail.length).build();
            final byte[] array = new byte[endEx.sizeof()];
            writeBuffer.getBytes(endEx.offset(), array);
            return array;
        }
    }

    public static final class AguiEndExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final AguiEndExFW endExRO = new AguiEndExFW();

        private Integer typeId;
        private AguiOutcomeType outcome;
        private String detail;

        public AguiEndExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public AguiEndExMatcherBuilder outcome(
            String outcome)
        {
            this.outcome = AguiOutcomeType.valueOf(outcome);
            return this;
        }

        public AguiEndExMatcherBuilder detail(
            String detail)
        {
            this.detail = detail;
            return this;
        }

        public BytesMatcher build()
        {
            return this::match;
        }

        private AguiEndExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final AguiEndExFW endEx = endExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.limit());

            if (endEx != null &&
                matchTypeId(endEx) &&
                matchOutcome(endEx) &&
                matchDetail(endEx))
            {
                byteBuf.position(byteBuf.position() + endEx.sizeof());
                return endEx;
            }

            throw new Exception(String.valueOf(endEx));
        }

        private boolean matchTypeId(
            AguiEndExFW endEx)
        {
            return typeId == null || typeId == endEx.typeId();
        }

        private boolean matchOutcome(
            AguiEndExFW endEx)
        {
            return outcome == null || outcome == endEx.outcome().get();
        }

        private boolean matchDetail(
            AguiEndExFW endEx)
        {
            return detail == null || detail.equals(asString(endEx.detail()));
        }
    }

    public static final class AguiAbortExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
        private final AguiAbortExFW.Builder abortExRW = new AguiAbortExFW.Builder();

        private String code;
        private String message;

        private AguiAbortExBuilder()
        {
            abortExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public AguiAbortExBuilder typeId(
            int typeId)
        {
            abortExRW.typeId(typeId);
            return this;
        }

        public AguiAbortExBuilder code(
            String code)
        {
            this.code = code;
            return this;
        }

        public AguiAbortExBuilder message(
            String message)
        {
            this.message = message;
            return this;
        }

        public byte[] build()
        {
            abortExRW.error(e -> e.code(code).message(message));

            final AguiAbortExFW abortEx = abortExRW.build();
            final byte[] array = new byte[abortEx.sizeof()];
            writeBuffer.getBytes(abortEx.offset(), array);
            return array;
        }
    }

    public static final class AguiAbortExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final AguiAbortExFW abortExRO = new AguiAbortExFW();

        private Integer typeId;
        private String code;
        private String message;

        public AguiAbortExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public AguiAbortExMatcherBuilder code(
            String code)
        {
            this.code = code;
            return this;
        }

        public AguiAbortExMatcherBuilder message(
            String message)
        {
            this.message = message;
            return this;
        }

        public BytesMatcher build()
        {
            return this::match;
        }

        private AguiAbortExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final AguiAbortExFW abortEx = abortExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.limit());

            if (abortEx != null &&
                matchTypeId(abortEx) &&
                matchCode(abortEx) &&
                matchMessage(abortEx))
            {
                byteBuf.position(byteBuf.position() + abortEx.sizeof());
                return abortEx;
            }

            throw new Exception(String.valueOf(abortEx));
        }

        private boolean matchTypeId(
            AguiAbortExFW abortEx)
        {
            return typeId == null || typeId == abortEx.typeId();
        }

        private boolean matchCode(
            AguiAbortExFW abortEx)
        {
            return code == null || code.equals(abortEx.error().code().asString());
        }

        private boolean matchMessage(
            AguiAbortExFW abortEx)
        {
            return message == null || message.equals(abortEx.error().message().asString());
        }
    }

    public static final class AguiResetExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
        private final AguiResetExFW.Builder resetExRW = new AguiResetExFW.Builder();

        private int status = -1;
        private String code;
        private String message;

        private AguiResetExBuilder()
        {
            resetExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public AguiResetExBuilder typeId(
            int typeId)
        {
            resetExRW.typeId(typeId);
            return this;
        }

        public AguiResetExBuilder status(
            int status)
        {
            this.status = status;
            return this;
        }

        public AguiResetExBuilder code(
            String code)
        {
            this.code = code;
            return this;
        }

        public AguiResetExBuilder message(
            String message)
        {
            this.message = message;
            return this;
        }

        public byte[] build()
        {
            resetExRW.error(e -> e.status(status).code(code).message(message));

            final AguiResetExFW resetEx = resetExRW.build();
            final byte[] array = new byte[resetEx.sizeof()];
            writeBuffer.getBytes(resetEx.offset(), array);
            return array;
        }
    }

    public static final class AguiResetExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final AguiResetExFW resetExRO = new AguiResetExFW();

        private Integer typeId;
        private Integer status;
        private String code;
        private String message;

        public AguiResetExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public AguiResetExMatcherBuilder status(
            int status)
        {
            this.status = status;
            return this;
        }

        public AguiResetExMatcherBuilder code(
            String code)
        {
            this.code = code;
            return this;
        }

        public AguiResetExMatcherBuilder message(
            String message)
        {
            this.message = message;
            return this;
        }

        public BytesMatcher build()
        {
            return this::match;
        }

        private AguiResetExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final AguiResetExFW resetEx = resetExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.limit());

            if (resetEx != null &&
                matchTypeId(resetEx) &&
                matchStatus(resetEx) &&
                matchCode(resetEx) &&
                matchMessage(resetEx))
            {
                byteBuf.position(byteBuf.position() + resetEx.sizeof());
                return resetEx;
            }

            throw new Exception(String.valueOf(resetEx));
        }

        private boolean matchTypeId(
            AguiResetExFW resetEx)
        {
            return typeId == null || typeId == resetEx.typeId();
        }

        private boolean matchStatus(
            AguiResetExFW resetEx)
        {
            return status == null || status == resetEx.error().status();
        }

        private boolean matchCode(
            AguiResetExFW resetEx)
        {
            return code == null || code.equals(resetEx.error().code().asString());
        }

        private boolean matchMessage(
            AguiResetExFW resetEx)
        {
            return message == null || message.equals(resetEx.error().message().asString());
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
            super(AguiFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "agui";
        }
    }

    private AguiFunctions()
    {
        // utility
    }
}
