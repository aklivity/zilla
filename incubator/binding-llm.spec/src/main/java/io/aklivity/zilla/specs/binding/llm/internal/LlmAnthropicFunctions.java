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
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmAnthropicRequestExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmAnthropicResponseExFW;

public final class LlmAnthropicFunctions
{
    @Function
    public static LlmAnthropicResponseExBuilder responseEx()
    {
        return new LlmAnthropicResponseExBuilder();
    }

    @Function
    public static LlmAnthropicRequestExBuilder requestEx()
    {
        return new LlmAnthropicRequestExBuilder();
    }

    public static final class LlmAnthropicRequestExBuilder
    {
        private final LlmAnthropicRequestExFW.Builder requestExRW;

        private LlmAnthropicRequestExBuilder()
        {
            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.requestExRW = new LlmAnthropicRequestExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmAnthropicRequestExBuilder stream(
            String stream)
        {
            requestExRW.stream(stream);
            return this;
        }

        public LlmAnthropicRequestExBuilder temperature(
            String temperature)
        {
            requestExRW.temperature(temperature);
            return this;
        }

        public LlmAnthropicRequestExBuilder topP(
            String topP)
        {
            requestExRW.topP(topP);
            return this;
        }

        public LlmAnthropicRequestExBuilder topK(
            String topK)
        {
            requestExRW.topK(topK);
            return this;
        }

        public LlmAnthropicRequestExBuilder maxTokens(
            String maxTokens)
        {
            requestExRW.maxTokens(maxTokens);
            return this;
        }

        public LlmAnthropicRequestExBuilder stopSequences(
            String stopSequences)
        {
            requestExRW.stopSequences(stopSequences);
            return this;
        }

        public byte[] build()
        {
            final LlmAnthropicRequestExFW requestEx = requestExRW.build();
            final byte[] array = new byte[requestEx.sizeof()];
            requestEx.buffer().getBytes(requestEx.offset(), array);
            return array;
        }
    }

    public static final class LlmAnthropicResponseExBuilder
    {
        private final LlmAnthropicResponseExFW.Builder responseExRW;

        private LlmAnthropicResponseExBuilder()
        {
            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.responseExRW = new LlmAnthropicResponseExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmAnthropicResponseExBuilder stopSequence(
            String stopSequence)
        {
            responseExRW.stopSequence(stopSequence);
            return this;
        }

        public LlmAnthropicResponseExBuilder containerId(
            String containerId)
        {
            responseExRW.containerId(containerId);
            return this;
        }

        public byte[] build()
        {
            final LlmAnthropicResponseExFW responseEx = responseExRW.build();
            final byte[] array = new byte[responseEx.sizeof()];
            responseEx.buffer().getBytes(responseEx.offset(), array);
            return array;
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(LlmAnthropicFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "llm_anthropic";
        }
    }

    private LlmAnthropicFunctions()
    {
        // utility
    }
}
