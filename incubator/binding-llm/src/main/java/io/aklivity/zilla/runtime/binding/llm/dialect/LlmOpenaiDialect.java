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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * OpenAI Chat Completions dialect: detects a {@code POST /v1/chat/completions} or {@code POST
 * /v1/completions} request carrying {@code application/json}, and renames the OpenAI-native
 * request/response members that this dialect's transforms give a canonical synonym for -- see
 * {@link LlmOpenaiRequestTransform} for exactly which request members and the rationale (response-side
 * streaming dialect translation lives in {@code internal.mapper.LlmOpenaiDecodeTransform}/
 * {@code LlmOpenaiEncodeSink}).
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
    private static final String AUTHORIZATION_HEADER = "authorization";
    private static final String METHOD_POST = "POST";

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String COMPLETIONS_PATH = "/v1/completions";
    private static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";
    private static final String CONTENT_TYPE_JSON = "application/json";

    private static final String REQUEST_SCHEMA_RESOURCE = "openai.request.schema.json";
    private static final String RESPONSE_SCHEMA_RESOURCE = "openai.response.schema.json";

    private static final DirectBufferEx RESPONSE_TERMINATOR = new UnsafeBufferEx("[DONE]".getBytes(UTF_8));

    private final JsonTransform requestValidator;
    private final JsonTransform responseValidator;

    public LlmOpenaiDialect()
    {
        this.requestValidator = JsonSchema.of(readResource(REQUEST_SCHEMA_RESOURCE)).validator();
        this.responseValidator = JsonSchema.of(readResource(RESPONSE_SCHEMA_RESOURCE)).validator();
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public boolean detect(
        JsonEnvelope headers)
    {
        final String method = header(headers, METHOD_HEADER);
        final String path = header(headers, PATH_HEADER);
        final String contentType = header(headers, CONTENT_TYPE_HEADER);
        return METHOD_POST.equalsIgnoreCase(method) &&
            (CHAT_COMPLETIONS_PATH.equals(path) || COMPLETIONS_PATH.equals(path)) &&
            CONTENT_TYPE_JSON.equals(contentType);
    }

    @Override
    public String requestPath(
        String basePath)
    {
        return basePath + CHAT_COMPLETIONS_SUFFIX;
    }

    @Override
    public String credentialsHeader()
    {
        return AUTHORIZATION_HEADER;
    }

    @Override
    public String unauthorizedBody()
    {
        return "{\"error\":{\"message\":\"Incorrect API key provided.\"," +
            "\"type\":\"invalid_request_error\",\"param\":null,\"code\":\"invalid_api_key\"}}";
    }

    @Override
    public JsonTransform supplyDecoder(
        Kind kind,
        JsonEnvelope envelope)
    {
        return kind == Kind.REQUEST ? new LlmOpenaiRequestTransform(true, envelope) : LlmDialectTransforms.identity();
    }

    @Override
    public JsonTransform supplyExtractor(
        Kind kind,
        JsonEnvelope envelope)
    {
        return kind == Kind.REQUEST
            ? new LlmModelExtractTransform(envelope)
            : new LlmOpenaiUsageExtractTransform(envelope);
    }

    @Override
    public JsonTransform supplyEncoder(
        Kind kind,
        JsonEnvelope envelope)
    {
        return kind == Kind.REQUEST ? new LlmOpenaiRequestTransform(false, envelope) : LlmDialectTransforms.identity();
    }

    @Override
    public JsonTransform supplySchemaValidator(
        Kind kind)
    {
        return kind == Kind.REQUEST ? requestValidator : responseValidator;
    }

    @Override
    public DirectBufferEx terminator(
        Kind kind)
    {
        return kind == Kind.RESPONSE ? RESPONSE_TERMINATOR : null;
    }

    private static String header(
        JsonEnvelope headers,
        String name)
    {
        final DirectBufferEx value = headers.get(name, 0);
        return value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
    }

    private static String readResource(
        String name)
    {
        URL resource = LlmOpenaiDialect.class.getResource(name);
        String text;
        try (InputStream input = resource.openStream())
        {
            text = new String(input.readAllBytes(), UTF_8);
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
        return text;
    }
}
