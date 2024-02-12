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
package io.aklivity.zilla.specs.binding.asyncapi.internal;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.asyncapi.internal.types.OctetsFW;
import io.aklivity.zilla.specs.binding.asyncapi.internal.types.stream.AsyncapiBeginExFW;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class AsyncapiFunctions
{
    @Function
    public static AsyncapiBeginExBuilder beginEx()
    {
        return new AsyncapiBeginExBuilder();
    }

    @Function
    public static AsyncapiBeginExMatcherBuilder matchBeginEx()
    {
        return new AsyncapiBeginExMatcherBuilder();
    }

    public static final class AsyncapiBeginExBuilder
    {
        private final AsyncapiBeginExFW.Builder beginExRW;

        private AsyncapiBeginExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.beginExRW = new AsyncapiBeginExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public AsyncapiBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public AsyncapiBeginExBuilder operationId(
            String operationId)
        {
            beginExRW.operationId(operationId);
            return this;
        }

        public AsyncapiBeginExBuilder extension(
            byte[] extension)
        {
            beginExRW.extension(e -> e.set(extension));
            return this;
        }

        public byte[] build()
        {
            final AsyncapiBeginExFW beginEx = beginExRW.build();
            final byte[] array = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), array);
            return array;
        }
    }

    public static final class AsyncapiBeginExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final AsyncapiBeginExFW beginExRO = new AsyncapiBeginExFW();

        private Integer typeId;
        private int apiId;
        private String operationId;
        private OctetsFW.Builder extensionRW;

        public AsyncapiBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public AsyncapiBeginExMatcherBuilder operationId(
            String operationId)
        {
            this.operationId = operationId;
            return this;
        }

        public AsyncapiBeginExMatcherBuilder apiId(
            int apiId)
        {
            this.apiId = apiId;
            return this;
        }

        public AsyncapiBeginExMatcherBuilder extension(
            byte[] extension)
        {
            assert extensionRW == null;
            extensionRW = new OctetsFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

            extensionRW.set(Objects.requireNonNullElseGet(extension, () -> new byte[] {}));

            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null ? this::match : buf -> null;
        }

        private AsyncapiBeginExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final AsyncapiBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.limit());

            if (beginEx != null &&
                matchTypeId(beginEx) &&
                matchApiId(beginEx) &&
                matchOperationId(beginEx) &&
                matchExtension(beginEx))
            {
                byteBuf.position(byteBuf.position() + beginEx.sizeof());
                return beginEx;
            }

            throw new Exception(beginEx.toString());
        }

        private boolean matchTypeId(
            AsyncapiBeginExFW beginEx)
        {
            return typeId == beginEx.typeId();
        }

        private boolean matchApiId(
            AsyncapiBeginExFW beginEx)
        {
            return apiId == beginEx.apiId();
        }

        private boolean matchOperationId(
            AsyncapiBeginExFW beginEx)
        {
            return operationId == null || operationId.equals(beginEx.operationId().asString());
        }

        private boolean matchExtension(
            final AsyncapiBeginExFW beginEx)
        {
            return extensionRW == null || extensionRW.build().equals(beginEx.extension());
        }
    }

    private AsyncapiFunctions()
    {
        // utility
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(AsyncapiFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "asyncapi";
        }
    }
}
