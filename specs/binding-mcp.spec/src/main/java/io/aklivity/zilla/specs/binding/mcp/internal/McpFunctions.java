/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.mcp.internal;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.specs.binding.mcp.internal.types.String16FW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpBeginExFW;

public final class McpFunctions
{
    @Function
    public static McpBeginExBuilder beginEx()
    {
        return new McpBeginExBuilder();
    }

    @Function
    public static McpBeginExMatcherBuilder matchBeginEx()
    {
        return new McpBeginExMatcherBuilder();
    }

    public static final class McpBeginExBuilder
    {
        private final McpBeginExFW.Builder beginExRW;

        private McpBeginExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024]);
            this.beginExRW = new McpBeginExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public McpBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public McpBeginExBuilder sessionId(
            String sessionId)
        {
            beginExRW.sessionId(sessionId);
            return this;
        }

        public McpBeginExBuilder method(
            String method)
        {
            beginExRW.method(method);
            return this;
        }

        public byte[] build()
        {
            final McpBeginExFW beginEx = beginExRW.build();
            final byte[] array = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), array);
            return array;
        }
    }

    public static final class McpBeginExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final McpBeginExFW beginExRO = new McpBeginExFW();

        private Integer typeId;
        private String16FW sessionId;
        private String16FW method;

        public McpBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public McpBeginExMatcherBuilder sessionId(
            String sessionId)
        {
            this.sessionId = new String16FW(sessionId);
            return this;
        }

        public McpBeginExMatcherBuilder method(
            String method)
        {
            this.method = new String16FW(method);
            return this;
        }

        public BytesMatcher build()
        {
            return this::match;
        }

        private McpBeginExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final McpBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (beginEx != null &&
                matchTypeId(beginEx) &&
                matchSessionId(beginEx) &&
                matchMethod(beginEx))
            {
                byteBuf.position(byteBuf.position() + beginEx.sizeof());
                return beginEx;
            }

            throw new Exception(beginEx != null ? beginEx.toString() : "null");
        }

        private boolean matchTypeId(
            McpBeginExFW beginEx)
        {
            return typeId == null || typeId == beginEx.typeId();
        }

        private boolean matchSessionId(
            McpBeginExFW beginEx)
        {
            return sessionId == null || sessionId.equals(beginEx.sessionId());
        }

        private boolean matchMethod(
            McpBeginExFW beginEx)
        {
            return method == null || method.equals(beginEx.method());
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(McpFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "mcp";
        }
    }

    private McpFunctions()
    {
        // utility
    }
}
