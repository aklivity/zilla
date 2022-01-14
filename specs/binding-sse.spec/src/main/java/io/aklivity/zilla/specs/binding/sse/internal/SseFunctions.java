/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.specs.binding.sse.internal;

import static java.lang.ThreadLocal.withInitial;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.sse.internal.types.stream.SseBeginExFW;
import io.aklivity.zilla.specs.binding.sse.internal.types.stream.SseDataExFW;
import io.aklivity.zilla.specs.binding.sse.internal.types.stream.SseEndExFW;

public final class SseFunctions
{
    private static final ThreadLocal<DirectBuffer> DIRECT_BUFFER = withInitial(UnsafeBuffer::new);

    @Function
    public static SseBeginExBuilder beginEx()
    {
        return new SseBeginExBuilder();
    }

    @Function
    public static SseDataExBuilder dataEx()
    {
        return new SseDataExBuilder();
    }

    @Function
    public static SseEndExBuilder endEx()
    {
        return new SseEndExBuilder();
    }

    public static final class SseBeginExBuilder
    {
        private final SseBeginExFW.Builder beginExRW;

        private SseBeginExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.beginExRW = new SseBeginExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public SseBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public SseBeginExBuilder pathInfo(
            String pathInfo)
        {
            beginExRW.pathInfo(pathInfo);
            return this;
        }

        public SseBeginExBuilder lastEventId(
            String lastEventId)
        {
            beginExRW.lastEventId(lastEventId);
            return this;
        }

        public byte[] build()
        {
            final SseBeginExFW beginEx = beginExRW.build();
            final byte[] array = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), array);
            return array;
        }
    }

    public static final class SseDataExBuilder
    {
        private final SseDataExFW.Builder dataExRW;

        private SseDataExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.dataExRW = new SseDataExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public SseDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public SseDataExBuilder timestamp(
            long timestamp)
        {
            dataExRW.timestamp(timestamp);
            return this;
        }

        public SseDataExBuilder id(
            String id)
        {
            dataExRW.id(id);
            return this;
        }

        public SseDataExBuilder idAsRawBytes(
            byte[] id)
        {
            final DirectBuffer buffer = DIRECT_BUFFER.get();
            buffer.wrap(id);
            dataExRW.id(buffer, 0, buffer.capacity());
            return this;
        }

        public SseDataExBuilder type(
            String type)
        {
            dataExRW.type(type);
            return this;
        }

        public SseDataExBuilder typeAsRawBytes(
            byte[] type)
        {
            final DirectBuffer buffer = DIRECT_BUFFER.get();
            buffer.wrap(type);
            dataExRW.type(buffer, 0, buffer.capacity());
            return this;
        }

        public byte[] build()
        {
            final SseDataExFW dataEx = dataExRW.build();
            final byte[] array = new byte[dataEx.sizeof()];
            dataEx.buffer().getBytes(dataEx.offset(), array);
            return array;
        }
    }

    public static final class SseEndExBuilder
    {
        private final SseEndExFW.Builder endExRW;

        private SseEndExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.endExRW = new SseEndExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public SseEndExBuilder typeId(
            int typeId)
        {
            endExRW.typeId(typeId);
            return this;
        }

        public SseEndExBuilder id(
            String id)
        {
            endExRW.id(id);
            return this;
        }

        public byte[] build()
        {
            final SseEndExFW endEx = endExRW.build();
            final byte[] array = new byte[endEx.sizeof()];
            endEx.buffer().getBytes(endEx.offset(), array);
            return array;
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(SseFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "sse";
        }
    }

    private SseFunctions()
    {
        // utility
    }
}
