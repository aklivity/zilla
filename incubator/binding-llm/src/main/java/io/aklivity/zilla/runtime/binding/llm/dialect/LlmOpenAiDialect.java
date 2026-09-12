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

import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * OpenAI Chat Completions dialect: detects {@code POST /v1/chat/completions} and {@code POST
 * /v1/completions} requests, and renames the OpenAI-native request/response members that this dialect's
 * transforms give a canonical synonym for -- see {@link LlmOpenAiRequestTransform} and
 * {@link LlmOpenAiResponseTransform} for exactly which members and the rationale.
 * <p>
 * {@link #contentType(Kind, HttpHeaders, HttpRequestBody)} resolves per request: the request body's own
 * content-type is always {@code application/json} regardless of the {@code stream} flag, while the
 * response's content-type follows that same flag -- {@code text/event-stream} (SSE chunks) when
 * {@code stream: true}, {@code application/json} (a single document) otherwise -- both routed through the
 * same content-decoder abstraction the issue calls for, with no special-casing between them since either
 * way a chunk or the whole document is one JSON value handed to the same {@link LlmOpenAiResponseTransform}.
 * When {@code body} is unavailable to the caller, the response is treated as non-streaming, matching the
 * {@code stream} flag's own default.
 * </p>
 */
public final class LlmOpenAiDialect implements LlmDialect
{
    private static final String NAME = "openai";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_SSE = "text/event-stream";

    private static final String METHOD_HEADER = ":method";
    private static final String METHOD_POST = "POST";

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String COMPLETIONS_PATH = "/v1/completions";

    private static final String STREAM_FIELD = "stream";
    private static final String STREAM_TRUE = "true";

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public boolean detect(
        String path,
        HttpHeaders headers)
    {
        return headers != null &&
            METHOD_POST.equalsIgnoreCase(headers.header(METHOD_HEADER)) &&
            (CHAT_COMPLETIONS_PATH.equals(path) || COMPLETIONS_PATH.equals(path));
    }

    @Override
    public String contentType(
        Kind kind,
        HttpHeaders headers,
        HttpRequestBody body)
    {
        return kind == Kind.RESPONSE && streaming(body) ? CONTENT_TYPE_SSE : CONTENT_TYPE_JSON;
    }

    private static boolean streaming(
        HttpRequestBody body)
    {
        return body != null && STREAM_TRUE.equals(body.value(STREAM_FIELD));
    }

    @Override
    public JsonTransform supplyDecoder(
        Kind kind)
    {
        final JsonTransform transform;
        switch (kind)
        {
        case REQUEST:
            transform = new LlmOpenAiRequestTransform(true);
            break;
        case RESPONSE:
            transform = new LlmOpenAiResponseTransform(true);
            break;
        default:
            transform = null;
            break;
        }
        return transform;
    }

    @Override
    public JsonTransform supplyEncoder(
        Kind kind)
    {
        final JsonTransform transform;
        switch (kind)
        {
        case REQUEST:
            transform = new LlmOpenAiRequestTransform(false);
            break;
        case RESPONSE:
            transform = new LlmOpenAiResponseTransform(false);
            break;
        default:
            transform = null;
            break;
        }
        return transform;
    }
}
