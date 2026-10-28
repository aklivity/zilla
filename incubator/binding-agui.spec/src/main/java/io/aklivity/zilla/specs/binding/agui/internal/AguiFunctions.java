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

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.agui.internal.types.stream.AguiBeginExFW;

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
