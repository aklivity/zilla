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
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmOpenaiRequestExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmOpenaiResponseExFW;

public final class LlmOpenaiFunctions
{
    @Function
    public static LlmOpenaiResponseExBuilder responseEx()
    {
        return new LlmOpenaiResponseExBuilder();
    }

    @Function
    public static LlmOpenaiRequestExBuilder requestEx()
    {
        return new LlmOpenaiRequestExBuilder();
    }

    public static final class LlmOpenaiRequestExBuilder
    {
        private final LlmOpenaiRequestExFW.Builder requestExRW;

        private LlmOpenaiRequestExBuilder()
        {
            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.requestExRW = new LlmOpenaiRequestExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmOpenaiRequestExBuilder stream(
            String stream)
        {
            requestExRW.stream(stream);
            return this;
        }

        public LlmOpenaiRequestExBuilder streamOptions(
            String streamOptions)
        {
            requestExRW.streamOptions(streamOptions);
            return this;
        }

        public LlmOpenaiRequestExBuilder temperature(
            String temperature)
        {
            requestExRW.temperature(temperature);
            return this;
        }

        public LlmOpenaiRequestExBuilder topP(
            String topP)
        {
            requestExRW.topP(topP);
            return this;
        }

        public LlmOpenaiRequestExBuilder maxTokens(
            String maxTokens)
        {
            requestExRW.maxTokens(maxTokens);
            return this;
        }

        public LlmOpenaiRequestExBuilder seed(
            String seed)
        {
            requestExRW.seed(seed);
            return this;
        }

        public LlmOpenaiRequestExBuilder stop(
            String stop)
        {
            requestExRW.stop(stop);
            return this;
        }

        public byte[] build()
        {
            final LlmOpenaiRequestExFW requestEx = requestExRW.build();
            final byte[] array = new byte[requestEx.sizeof()];
            requestEx.buffer().getBytes(requestEx.offset(), array);
            return array;
        }
    }

    public static final class LlmOpenaiResponseExBuilder
    {
        private final LlmOpenaiResponseExFW.Builder responseExRW;

        private LlmOpenaiResponseExBuilder()
        {
            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.responseExRW = new LlmOpenaiResponseExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public LlmOpenaiResponseExBuilder created(
            String created)
        {
            responseExRW.created(created);
            return this;
        }

        public LlmOpenaiResponseExBuilder serviceTier(
            String serviceTier)
        {
            responseExRW.serviceTier(serviceTier);
            return this;
        }

        public LlmOpenaiResponseExBuilder systemFingerprint(
            String systemFingerprint)
        {
            responseExRW.systemFingerprint(systemFingerprint);
            return this;
        }

        public byte[] build()
        {
            final LlmOpenaiResponseExFW responseEx = responseExRW.build();
            final byte[] array = new byte[responseEx.sizeof()];
            responseEx.buffer().getBytes(responseEx.offset(), array);
            return array;
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(LlmOpenaiFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "llm_openai";
        }
    }

    private LlmOpenaiFunctions()
    {
        // utility
    }
}
