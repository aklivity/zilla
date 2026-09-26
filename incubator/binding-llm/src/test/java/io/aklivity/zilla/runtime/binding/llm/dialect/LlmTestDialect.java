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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

// A minimal third LlmDialect implementation standing in for a dialect contributed from outside this
// module -- exactly the scenario LlmDialect's own javadoc advertises. Its supplyResponseDecodeTransform/
// supplyResponseEncodeSink return distinctive marker types, never LlmOpenaiDecodeTransform/
// LlmAnthropicEncodeSink or their counterparts, so a caller that dispatches through LlmDialect itself
// (rather than a hardcoded name-based lookup that only recognizes "openai"/"anthropic") is provably using
// this dialect's own response mapping.
final class LlmTestDialect implements LlmDialect
{
    private final boolean modelPlaceholder;

    LlmTestDialect()
    {
        this(false);
    }

    LlmTestDialect(
        boolean modelPlaceholder)
    {
        this.modelPlaceholder = modelPlaceholder;
    }

    @Override
    public String name()
    {
        return "test";
    }

    @Override
    public boolean detect(
        JsonEnvelope headers)
    {
        return false;
    }

    @Override
    public String requestPath(
        String basePath)
    {
        return modelPlaceholder ? basePath + "/models/" + MODEL_PLACEHOLDER : basePath;
    }

    @Override
    public String credentialsHeader()
    {
        return "x-test-key";
    }

    @Override
    public String unauthorizedBody()
    {
        return "{}";
    }

    @Override
    public JsonTransform supplyDecoder(
        Kind kind,
        JsonEnvelope envelope)
    {
        return passthrough();
    }

    @Override
    public JsonTransform supplyExtractor(
        Kind kind,
        JsonEnvelope envelope)
    {
        return passthrough();
    }

    @Override
    public JsonTransform supplyEncoder(
        Kind kind,
        JsonEnvelope envelope)
    {
        return passthrough();
    }

    @Override
    public JsonTransform supplyResponseDecodeTransform()
    {
        return new LlmTestResponseDecodeTransform();
    }

    @Override
    public JsonSink supplyResponseEncodeSink(
        JsonEnvelope envelope,
        LlmNativeEventOutput output)
    {
        return new LlmTestResponseEncodeSink();
    }

    @Override
    public JsonTransform supplySchemaValidator(
        Kind kind)
    {
        return passthrough();
    }

    @Override
    public DirectBufferEx terminator(
        Kind kind)
    {
        return null;
    }

    private static JsonTransform passthrough()
    {
        return new JsonTransform()
        {
            @Override
            public Status transform(
                JsonController control,
                JsonSource source,
                JsonEvent event,
                JsonSink sink)
            {
                return sink.transform(control, source, event);
            }

            @Override
            public boolean identity()
            {
                return true;
            }
        };
    }

    static final class LlmTestResponseDecodeTransform implements JsonTransform, LlmDialectEvent
    {
        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            return sink.transform(control, source, event);
        }

        @Override
        public void event(
            String name)
        {
        }
    }

    static final class LlmTestResponseEncodeSink implements JsonSink, LlmDialectTerminator
    {
        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event)
        {
            return Status.ADVANCED;
        }

        @Override
        public boolean identity()
        {
            return true;
        }

        @Override
        public void terminate()
        {
        }
    }
}
