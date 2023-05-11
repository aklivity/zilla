/*
 * Copyright 2021-2023 Aklivity Inc.
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

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.sse.internal.types.String8FW;
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
    public static SseDataExMatcherBuilder matchDataEx()
    {
        return new SseDataExMatcherBuilder();
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

        public SseBeginExBuilder scheme(
            String scheme)
        {
            beginExRW.scheme(scheme);
            return this;
        }

        public SseBeginExBuilder authority(
            String authority)
        {
            beginExRW.authority(authority);
            return this;
        }

        public SseBeginExBuilder path(
            String path)
        {
            beginExRW.path(path);
            return this;
        }

        public SseBeginExBuilder lastId(
            String lastEventId)
        {
            beginExRW.lastId(lastEventId);
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

    public static final class SseDataExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final SseDataExFW dataExRO = new SseDataExFW();

        private Integer typeId;
        private Long timestamp;
        private String8FW id;
        private String8FW type;

        public SseDataExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public SseDataExMatcherBuilder timestamp(
            long timestamp)
        {
            this.timestamp = timestamp;
            return this;
        }

        public SseDataExMatcherBuilder id(
            String id)
        {
            this.id = new String8FW(id);
            return this;
        }

        public SseDataExMatcherBuilder idAsRawBytes(
            byte[] id)
        {
            final DirectBuffer buffer = DIRECT_BUFFER.get();
            buffer.wrap(id);
            this.id = new String8FW.Builder()
                    .wrap(new UnsafeBuffer(new byte[1 + id.length]), 0, 1 + id.length)
                    .set(buffer, 0, id.length)
                    .build();
            return this;
        }

        public SseDataExMatcherBuilder type(
            String type)
        {
            this.type = new String8FW(type);
            return this;
        }

        public SseDataExMatcherBuilder typeAsRawBytes(
            byte[] type)
        {
            final DirectBuffer buffer = DIRECT_BUFFER.get();
            buffer.wrap(type);
            this.type = new String8FW.Builder()
                    .wrap(new UnsafeBuffer(new byte[1 + type.length]), 0, 1 + type.length)
                    .set(buffer, 0, type.length)
                    .build();
            return this;
        }

        public BytesMatcher build()
        {
            return this::match;
        }

        private SseDataExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final SseDataExFW dataEx = dataExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (dataEx != null &&
                matchTypeId(dataEx) &&
                matchTimestamp(dataEx) &&
                matchId(dataEx) &&
                matchType(dataEx))
            {
                byteBuf.position(byteBuf.position() + dataEx.sizeof());
                return dataEx;
            }

            throw new Exception(dataEx.toString());
        }

        private boolean matchTypeId(
            final SseDataExFW dataEx)
        {
            return typeId == null || typeId == dataEx.typeId();
        }

        private boolean matchTimestamp(
            final SseDataExFW dataEx)
        {
            return timestamp == null || timestamp == dataEx.timestamp();
        }

        private boolean matchId(
            final SseDataExFW dataEx)
        {
            return id == null || id.equals(dataEx.id());
        }

        private boolean matchType(
            final SseDataExFW dataEx)
        {
            return type == null || type.equals(dataEx.type());
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
