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
package io.aklivity.zilla.specs.binding.llm.internal;

import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmOllamaRequestExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmOllamaResponseExFW;

public final class LlmOllamaFunctions
{
    @Function
    public static LlmOllamaResponseExBuilder responseEx()
    {
        return new LlmOllamaResponseExBuilder();
    }

    @Function
    public static LlmOllamaRequestExBuilder requestEx()
    {
        return new LlmOllamaRequestExBuilder();
    }

    public static final class LlmOllamaRequestExBuilder
    {
        private final LlmOllamaRequestExFW.Builder requestExRW;

        private LlmOllamaRequestExBuilder()
        {
            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.requestExRW = new LlmOllamaRequestExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmOllamaRequestExBuilder stream(
            String stream)
        {
            requestExRW.stream(stream);
            return this;
        }

        public LlmOllamaRequestExBuilder options(
            String options)
        {
            requestExRW.options(options);
            return this;
        }

        public LlmOllamaRequestExBuilder format(
            String format)
        {
            requestExRW.format(format);
            return this;
        }

        public LlmOllamaRequestExBuilder think(
            String think)
        {
            requestExRW.think(think);
            return this;
        }

        public LlmOllamaRequestExBuilder keepAlive(
            String keepAlive)
        {
            requestExRW.keepAlive(keepAlive);
            return this;
        }

        public byte[] build()
        {
            final LlmOllamaRequestExFW requestEx = requestExRW.build();
            final byte[] array = new byte[requestEx.sizeof()];
            requestEx.buffer().getBytes(requestEx.offset(), array);
            return array;
        }
    }

    public static final class LlmOllamaResponseExBuilder
    {
        private final LlmOllamaResponseExFW.Builder responseExRW;

        private LlmOllamaResponseExBuilder()
        {
            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.responseExRW = new LlmOllamaResponseExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmOllamaResponseExBuilder createdAt(
            String createdAt)
        {
            responseExRW.createdAt(createdAt);
            return this;
        }

        public LlmOllamaResponseExBuilder totalDuration(
            String totalDuration)
        {
            responseExRW.totalDuration(totalDuration);
            return this;
        }

        public LlmOllamaResponseExBuilder loadDuration(
            String loadDuration)
        {
            responseExRW.loadDuration(loadDuration);
            return this;
        }

        public byte[] build()
        {
            final LlmOllamaResponseExFW responseEx = responseExRW.build();
            final byte[] array = new byte[responseEx.sizeof()];
            responseEx.buffer().getBytes(responseEx.offset(), array);
            return array;
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(LlmOllamaFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "llm_ollama";
        }
    }

    private LlmOllamaFunctions()
    {
        // utility
    }
}
