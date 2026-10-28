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
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiBeginExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiEndExFW;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiRole;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiTextDataExFW;

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
                    .role(r -> r.set(role)));
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
