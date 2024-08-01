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
package io.aklivity.zilla.specs.binding.asyncapi;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.specs.binding.asyncapi.internal.types.stream.AsyncapiBeginExFW;

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

        public AsyncapiBeginExBuilder apiId(
            long apiId)
        {
            beginExRW.apiId(apiId);
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
        private Long apiId;
        private String operationId;
        private BytesMatcher extension;

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
            long apiId)
        {
            this.apiId = apiId;
            return this;
        }

        public AsyncapiBeginExMatcherBuilder extension(
            BytesMatcher extension)
        {
            this.extension = extension;
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
                matchOperationId(beginEx))
            {
                byteBuf.position(byteBuf.position() + beginEx.sizeof() - beginEx.extension().sizeof());

                if (matchExtension(beginEx))
                {
                    return beginEx;
                }
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
            return apiId == null || apiId == beginEx.apiId();
        }

        private boolean matchOperationId(
            AsyncapiBeginExFW beginEx)
        {
            return operationId == null || operationId.equals(beginEx.operationId().asString());
        }

        private boolean matchExtension(
            AsyncapiBeginExFW beginEx) throws Exception
        {
            return extension == null || extension.match(bufferRO.byteBuffer()) != null;
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
