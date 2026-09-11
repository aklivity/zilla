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

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * A test-only dialect whose content-type ({@code text/event-stream}) matches the SSE content
 * decoder, distinct from {@link LlmTestDialect}, so server-side framing decode can be exercised
 * without depending on a real dialect implementation (e.g. openai, anthropic).
 */
public final class LlmTestSseDialect implements LlmDialect
{
    @Override
    public String name()
    {
        return "test-sse";
    }

    @Override
    public boolean detect(
        String path,
        HttpHeaders headers)
    {
        return "test-sse".equals(headers.header("x-llm-dialect"));
    }

    @Override
    public String contentType()
    {
        return "text/event-stream";
    }

    @Override
    public JsonTransform supplyDecoder(
        Kind kind)
    {
        return new LlmTestSseDialectTransform();
    }

    @Override
    public JsonTransform supplyEncoder(
        Kind kind)
    {
        return new LlmTestSseDialectTransform();
    }

    private static final class LlmTestSseDialectTransform implements JsonTransform
    {
        @Override
        public JsonPipeline.Status transform(
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
    }
}
