/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.filesystem.internal;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.filesystem.internal.types.FileSystemCapabilities;
import io.aklivity.zilla.specs.binding.filesystem.internal.types.stream.FileSystemBeginExFW;

public final class FileSystemFunctions
{
    @Function
    public static FileSystemBeginExBuilder beginEx()
    {
        return new FileSystemBeginExBuilder();
    }

    @Function
    public static FileSystemBeginExMatcherBuilder matchBeginEx()
    {
        return new FileSystemBeginExMatcherBuilder();
    }

    public static final class FileSystemBeginExBuilder
    {
        private final FileSystemBeginExFW.Builder beginExRW;

        private FileSystemBeginExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.beginExRW = new FileSystemBeginExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public FileSystemBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public FileSystemBeginExBuilder capabilities(
            String... capabilities)
        {
            beginExRW.capabilities(Arrays.asList(capabilities).stream()
                .map(FileSystemCapabilities::valueOf)
                .mapToInt(FileSystemCapabilities::ordinal)
                .map(n -> 1 << n)
                .sum());
            return this;
        }

        public FileSystemBeginExBuilder path(
            String path)
        {
            beginExRW.path(path);
            return this;
        }

        public FileSystemBeginExBuilder type(
            String type)
        {
            beginExRW.type(type);
            return this;
        }

        public FileSystemBeginExBuilder payloadSize(
            long payloadSize)
        {
            beginExRW.payloadSize(payloadSize);
            return this;
        }

        public FileSystemBeginExBuilder tag(
            String tag)
        {
            beginExRW.tag(tag);
            return this;
        }

        public FileSystemBeginExBuilder timeout(
            int timeout)
        {
            beginExRW.timeout(timeout);
            return this;
        }

        public byte[] build()
        {
            final FileSystemBeginExFW beginEx = beginExRW.build();
            final byte[] array = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), array);
            return array;
        }
    }

    public static final class FileSystemBeginExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final FileSystemBeginExFW beginExRO = new FileSystemBeginExFW();

        private Integer typeId;
        private Integer capabilities;
        private String path;
        private String type;
        private Long payloadSize;
        private Long modifiedTime;
        private String tag;
        private Integer timeout;

        public FileSystemBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public FileSystemBeginExMatcherBuilder capabilities(
            String... capabilities)
        {
            this.capabilities = Arrays.asList(capabilities).stream()
                .map(FileSystemCapabilities::valueOf)
                .mapToInt(FileSystemCapabilities::ordinal)
                .map(n -> 1 << n)
                .sum();
            return this;
        }

        public FileSystemBeginExMatcherBuilder path(
            String path)
        {
            this.path = path;
            return this;
        }

        public FileSystemBeginExMatcherBuilder type(
            String type)
        {
            this.type = type;
            return this;
        }

        public FileSystemBeginExMatcherBuilder payloadSize(
            long payloadSize)
        {
            this.payloadSize = payloadSize;
            return this;
        }

        public FileSystemBeginExMatcherBuilder tag(
            String tag)
        {
            this.tag = tag;
            return this;
        }

        public FileSystemBeginExMatcherBuilder timeout(
            int timeout)
        {
            this.timeout = timeout;
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null ? this::match : buf -> null;
        }

        private FileSystemBeginExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final FileSystemBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (beginEx != null &&
                matchTypeId(beginEx) &&
                matchCapabilities(beginEx) &&
                matchPath(beginEx) &&
                matchType(beginEx) &&
                matchPayloadSize(beginEx) &&
                matchTag(beginEx) &&
                matchTimeout(beginEx))
            {
                byteBuf.position(byteBuf.position() + beginEx.sizeof());
                return beginEx;
            }

            throw new Exception(beginEx.toString());
        }

        private boolean matchTypeId(
            FileSystemBeginExFW beginEx)
        {
            return typeId == beginEx.typeId();
        }

        private boolean matchCapabilities(
            FileSystemBeginExFW beginEx)
        {
            return capabilities == null || capabilities == beginEx.capabilities();
        }

        private boolean matchPayloadSize(
            FileSystemBeginExFW beginEx)
        {
            return payloadSize == null || payloadSize == beginEx.payloadSize();
        }

        private boolean matchPath(
            FileSystemBeginExFW beginEx)
        {
            return path == null || path.equals(beginEx.path().asString());
        }

        private boolean matchType(
            FileSystemBeginExFW beginEx)
        {
            return type == null || type.equals(beginEx.type().asString());
        }

        private boolean matchTag(
            FileSystemBeginExFW beginEx)
        {
            return tag == null || tag.equals(beginEx.tag().asString());
        }

        private boolean matchTimeout(
            FileSystemBeginExFW beginEx)
        {
            return timeout == null || timeout == beginEx.timeout();
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(FileSystemFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "filesystem";
        }
    }

    private FileSystemFunctions()
    {
        // utility
    }
}
