/*
 * Copyright 2021-2022 Aklivity Inc
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
import io.aklivity.zilla.specs.binding.filesystem.internal.types.stream.FileSystemDataExFW;

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

    @Function
    public static FileSystemDataExBuilder dataEx()
    {
        return new FileSystemDataExBuilder();
    }

    @Function
    public static FileSystemDataExMatcherBuilder matchDataEx()
    {
        return new FileSystemDataExMatcherBuilder();
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

        public FileSystemBeginExBuilder path(
            String path)
        {
            beginExRW.path(path);
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

        public FileSystemBeginExBuilder modifiedSince(
            long modifiedSince)
        {
            beginExRW.modifiedSince(modifiedSince);
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
        private String path;
        private Integer capabilities;
        private Long modifiedSince;

        public FileSystemBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public FileSystemBeginExMatcherBuilder path(
            String path)
        {
            this.path = path;
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

        public FileSystemBeginExMatcherBuilder modifiedSince(
            long modifiedSince)
        {
            this.modifiedSince = modifiedSince;
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
                matchPath(beginEx) &&
                matchCapabilities(beginEx) &&
                matchModifiedSince(beginEx))
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

        private boolean matchPath(
            FileSystemBeginExFW beginEx)
        {
            return path == null || path.equals(beginEx.path().asString());
        }

        private boolean matchCapabilities(
            FileSystemBeginExFW beginEx)
        {
            return capabilities == null || capabilities == beginEx.capabilities();
        }

        private boolean matchModifiedSince(
            FileSystemBeginExFW beginEx)
        {
            return modifiedSince == null || modifiedSince == beginEx.modifiedSince();
        }
    }

    public static final class FileSystemDataExBuilder
    {
        private final FileSystemDataExFW.Builder dataExRW;

        private FileSystemDataExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.dataExRW = new FileSystemDataExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public FileSystemDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public FileSystemDataExBuilder deferred(
            long deferred)
        {
            dataExRW.deferred(deferred);
            return this;
        }

        public FileSystemDataExBuilder modifiedTime(
            long modifiedTime)
        {
            dataExRW.modifiedTime(modifiedTime);
            return this;
        }

        public FileSystemDataExBuilder path(
            String path)
        {
            dataExRW.path(path);
            return this;
        }

        public byte[] build()
        {
            final FileSystemDataExFW dataEx = dataExRW.build();
            final byte[] array = new byte[dataEx.sizeof()];
            dataEx.buffer().getBytes(dataEx.offset(), array);
            return array;
        }
    }

    public static final class FileSystemDataExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final FileSystemDataExFW dataExRO = new FileSystemDataExFW();

        private Integer typeId;
        private String path;
        private Long deferred;
        private Long modifiedTime;

        public FileSystemDataExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public FileSystemDataExMatcherBuilder deferred(
            long deferred)
        {
            this.deferred = deferred;
            return this;
        }

        public FileSystemDataExMatcherBuilder modifiedTime(
            long modifiedTime)
        {
            this.modifiedTime = modifiedTime;
            return this;
        }

        public FileSystemDataExMatcherBuilder path(
            String path)
        {
            this.path = path;
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null ? this::match : buf -> null;
        }

        private FileSystemDataExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final FileSystemDataExFW dataEx = dataExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (dataEx != null &&
                matchTypeId(dataEx) &&
                matchDeferred(dataEx) &&
                matchModifiedTime(dataEx) &&
                matchPath(dataEx))
            {
                byteBuf.position(byteBuf.position() + dataEx.sizeof());
                return dataEx;
            }

            throw new Exception(dataEx.toString());
        }

        private boolean matchTypeId(
            FileSystemDataExFW dataEx)
        {
            return typeId == dataEx.typeId();
        }

        private boolean matchDeferred(
            FileSystemDataExFW dataEx)
        {
            return deferred == null || deferred == dataEx.deferred();
        }

        private boolean matchModifiedTime(
            FileSystemDataExFW dataEx)
        {
            return modifiedTime == null || modifiedTime == dataEx.modifiedTime();
        }

        private boolean matchPath(
            FileSystemDataExFW dataEx)
        {
            return path == null || path.equals(dataEx.path().asString());
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
