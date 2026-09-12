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
 * A test-only dialect whose response content-type depends on a {@code stream} member of the
 * request body, mirroring the real {@code openai} dialect's behavior. Exercises that a client
 * binding actually supplies request-body content to {@link #contentType(Kind, HttpHeaders,
 * HttpRequestBody)} before selecting the response decoder, rather than resolving it eagerly with
 * no body to peek.
 */
public final class LlmTestConditionalDialect implements LlmDialect
{
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_SSE = "text/event-stream";
    private static final String STREAM_FIELD = "stream";
    private static final String STREAM_TRUE = "true";

    @Override
    public String name()
    {
        return "test-conditional";
    }

    @Override
    public boolean detect(
        String path,
        HttpHeaders headers)
    {
        return "test-conditional".equals(headers.header("x-llm-dialect"));
    }

    @Override
    public String contentType(
        Kind kind,
        HttpHeaders headers,
        HttpRequestBody body)
    {
        return kind == Kind.RESPONSE && streaming(body) ? CONTENT_TYPE_SSE : CONTENT_TYPE_JSON;
    }

    @Override
    public JsonTransform supplyDecoder(
        Kind kind)
    {
        return new LlmTestConditionalDialectTransform();
    }

    @Override
    public JsonTransform supplyEncoder(
        Kind kind)
    {
        return new LlmTestConditionalDialectTransform();
    }

    private static boolean streaming(
        HttpRequestBody body)
    {
        return body != null && STREAM_TRUE.equals(body.value(STREAM_FIELD));
    }

    private static final class LlmTestConditionalDialectTransform implements JsonTransform
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
