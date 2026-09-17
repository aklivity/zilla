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

import static java.nio.charset.StandardCharsets.UTF_8;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

/**
 * OpenAI Chat Completions dialect: detects a {@code POST /v1/chat/completions} or {@code POST
 * /v1/completions} request carrying {@code application/json}, and renames the OpenAI-native
 * request/response members that this dialect's transforms give a canonical synonym for -- see
 * {@link LlmOpenaiRequestTransform} and {@link LlmOpenaiResponseTransform} for exactly which members and
 * the rationale.
 * <p>
 * The request content-type is part of detection (not just the method/path pair) because a request routed
 * to this same path with some other content-type is a different dialect's own traffic, not this one's --
 * without it, this dialect would ambiguously co-match any such request purely on path and method.
 * </p>
 * <p>
 * Response content-type resolution (a streaming response's {@code text/event-stream} chunks versus a
 * single {@code application/json} document) is a transport-layer concern, resolved from the real upstream
 * {@code Content-Type} response header rather than predicted here.
 * </p>
 */
public final class LlmOpenaiDialect implements LlmDialect
{
    private static final String NAME = "openai";

    private static final String METHOD_HEADER = ":method";
    private static final String PATH_HEADER = ":path";
    private static final String CONTENT_TYPE_HEADER = "content-type";
    private static final String METHOD_POST = "POST";

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String COMPLETIONS_PATH = "/v1/completions";
    private static final String CONTENT_TYPE_JSON = "application/json";

    private static final DirectBufferEx RESPONSE_TERMINATOR = new UnsafeBufferEx("[DONE]".getBytes(UTF_8));

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public boolean detect(
        ModelEnvelope headers)
    {
        final String method = header(headers, METHOD_HEADER);
        final String path = header(headers, PATH_HEADER);
        final String contentType = header(headers, CONTENT_TYPE_HEADER);
        return METHOD_POST.equalsIgnoreCase(method) &&
            (CHAT_COMPLETIONS_PATH.equals(path) || COMPLETIONS_PATH.equals(path)) &&
            CONTENT_TYPE_JSON.equals(contentType);
    }

    @Override
    public ModelTransform supplyDecoder(
        Kind kind,
        ModelEnvelope envelope)
    {
        final ModelTransform transform;
        switch (kind)
        {
        case REQUEST:
            transform = new LlmOpenaiRequestTransform(true, envelope);
            break;
        case RESPONSE:
            transform = new LlmOpenaiResponseTransform(true);
            break;
        default:
            transform = ModelTransform.NONE;
            break;
        }
        return transform;
    }

    @Override
    public ModelTransform supplyEncoder(
        Kind kind,
        ModelEnvelope envelope)
    {
        final ModelTransform transform;
        switch (kind)
        {
        case REQUEST:
            transform = new LlmOpenaiRequestTransform(false, envelope);
            break;
        case RESPONSE:
            transform = new LlmOpenaiResponseTransform(false);
            break;
        default:
            transform = ModelTransform.NONE;
            break;
        }
        return transform;
    }

    @Override
    public DirectBufferEx terminator(
        Kind kind)
    {
        return kind == Kind.RESPONSE ? RESPONSE_TERMINATOR : null;
    }

    private static String header(
        ModelEnvelope headers,
        String name)
    {
        final DirectBufferEx value = headers.get(name, 0);
        return value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
    }
}
