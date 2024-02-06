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
package io.aklivity.zilla.specs.binding.openapi.internal;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.openapi.internal.types.stream.OpenapiBeginExFW;

public final class OpenapiFunctions
{
    @Function
    public static OpenapiBeginExBuilder beginEx()
    {
        return new OpenapiBeginExBuilder();
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
