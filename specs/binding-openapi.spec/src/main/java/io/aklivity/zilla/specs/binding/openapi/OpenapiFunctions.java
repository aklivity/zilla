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
package io.aklivity.zilla.specs.binding.openapi;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.specs.binding.openapi.internal.types.stream.OpenapiBeginExFW;

public final class OpenapiFunctions
{
    @Function
    public static OpenapiBeginExBuilder beginEx()
    {
        return new OpenapiBeginExBuilder();
    }

    @Function
    public static OpenapiBeginExMatcherBuilder matchBeginEx()
    {
        return new OpenapiBeginExMatcherBuilder();
    }

    public static final class OpenapiBeginExBuilder
    {
        private final OpenapiBeginExFW.Builder beginExRW;

        private OpenapiBeginExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.beginExRW = new OpenapiBeginExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public OpenapiBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public OpenapiBeginExBuilder apiId(
            long apiId)
        {
            beginExRW.apiId(apiId);
            return this;
        }

        public OpenapiBeginExBuilder operationId(
            String operationId)
        {
            beginExRW.operationId(operationId);
            return this;
        }

        public OpenapiBeginExBuilder extension(
            byte[] extension)
        {
            beginExRW.extension(e -> e.set(extension));
            return this;
        }

        public byte[] build()
        {
            final OpenapiBeginExFW beginEx = beginExRW.build();
            final byte[] array = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), array);
            return array;
        }
    }

    public static final class OpenapiBeginExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final OpenapiBeginExFW beginExRO = new OpenapiBeginExFW();

        private Integer typeId;
        private Long apiId;
        private String operationId;
        private BytesMatcher extension;

        public OpenapiBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public OpenapiBeginExMatcherBuilder operationId(
            String operationId)
        {
            this.operationId = operationId;
            return this;
        }

        public OpenapiBeginExMatcherBuilder apiId(
            long apiId)
        {
            this.apiId = apiId;
            return this;
        }

        public OpenapiBeginExMatcherBuilder extension(
            BytesMatcher extension)
        {
            this.extension = extension;
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null ? this::match : buf -> null;
        }

        private OpenapiBeginExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final OpenapiBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.limit());

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
            OpenapiBeginExFW beginEx)
        {
            return typeId == beginEx.typeId();
        }

        private boolean matchApiId(
            OpenapiBeginExFW beginEx)
        {
            return apiId == null || apiId == beginEx.apiId();
        }

        private boolean matchOperationId(
            OpenapiBeginExFW beginEx)
        {
            return operationId == null || operationId.equals(beginEx.operationId().asString());
        }

        private boolean matchExtension(
            final OpenapiBeginExFW beginEx) throws Exception
        {
            return extension == null || extension.match(bufferRO.byteBuffer()) != null;
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(OpenapiFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "openapi";
        }
    }

    private OpenapiFunctions()
    {
        // utility
    }
}
