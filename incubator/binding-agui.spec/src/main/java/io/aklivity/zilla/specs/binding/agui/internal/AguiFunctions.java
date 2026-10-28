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

import java.nio.ByteBuffer;
import java.util.function.Predicate;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiAbortExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiBeginExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiEncryptedSubtype;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiEndExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiReasoningDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiReasoningEncryptedDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiRole;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiStateDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiStateType;
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

        public byte[] build()
        {
            final AguiDataExFW dataEx = dataExRW.build();
            final byte[] array = new byte[dataEx.sizeof()];
            writeBuffer.getBytes(dataEx.offset(), array);
            return array;
        }

        public final class AguiTextDataExBuilder
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
                    .meta(m -> m.timestamp(-1))
                    .messageId(messageId)
                    .role(x -> x.set(role)));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiReasoningDataExBuilder
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
                    .meta(m -> m.timestamp(-1))
                    .messageId(messageId)
                    .groupId(groupId));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiReasoningEncryptedDataExBuilder
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
                    .meta(m -> m.timestamp(-1))
                    .subtype(x -> x.set(subtype))
                    .entityId(entityId));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiToolCallDataExBuilder
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
                    .meta(m -> m.timestamp(-1))
                    .toolCallId(toolCallId)
                    .toolCallName(toolCallName)
                    .parentMessageId(parentMessageId));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiToolResultDataExBuilder
        {
            private String messageId;
            private String toolCallId;

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

            public AguiDataExBuilder build()
            {
                dataExRW.toolResult(t -> t
                    .meta(m -> m.timestamp(-1))
                    .messageId(messageId)
                    .toolCallId(toolCallId));
                return AguiDataExBuilder.this;
            }
        }

        public final class AguiStateDataExBuilder
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
                    .meta(m -> m.timestamp(-1))
                    .type(x -> x.set(type)));
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

        public final class AguiTextDataExMatcherBuilder
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
                return matchMessageId(text) &&
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
                return matchMessageId(reasoning) &&
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
                return matchSubtype(reasoningEncrypted) &&
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

        public final class AguiToolCallDataExMatcherBuilder
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
                return matchToolCallId(toolCall) &&
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
        {
            private String messageId;
            private String toolCallId;

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

            public AguiDataExMatcherBuilder build()
            {
                return AguiDataExMatcherBuilder.this;
            }

            private boolean match(
                AguiDataExFW dataEx)
            {
                final AguiToolResultDataExFW toolResult = dataEx.toolResult();
                return matchMessageId(toolResult) &&
                    matchToolCallId(toolResult);
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
        }

        public final class AguiStateDataExMatcherBuilder
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
                return matchType(state);
            }

            private boolean matchType(
                AguiStateDataExFW state)
            {
                return type == null || type == state.type().get();
            }
        }
    }

    public static final class AguiEndExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
        private final AguiEndExFW.Builder endExRW = new AguiEndExFW.Builder();

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
            final AguiOutcomeType resolved = AguiOutcomeType.valueOf(outcome);
            endExRW.outcome(o -> o.set(resolved));
            return this;
        }

        public byte[] build()
        {
            final AguiEndExFW endEx = endExRW.build();
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
                matchOutcome(endEx))
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
