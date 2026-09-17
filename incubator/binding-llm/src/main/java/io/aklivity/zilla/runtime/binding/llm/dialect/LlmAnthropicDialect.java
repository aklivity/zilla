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
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

/**
 * Anthropic Messages API dialect: detects a {@code POST /v1/messages} request, an {@code anthropic-version}
 * request header, or an {@code x-api-key} request header carried without an {@code Authorization} header --
 * any one of these three signals on its own confirms this dialect, since a client that only ever sends one of
 * them (e.g. a proxy that always stamps {@code anthropic-version} regardless of path) is still unambiguously
 * Anthropic's own traffic. {@code x-api-key} accompanied by {@code Authorization} is not itself a signal --
 * that combination is at least as consistent with some other dialect stacking its own bearer credential on
 * top of a forwarded Anthropic API key, so {@link #detect(ModelEnvelope)} does not treat it as a hint.
 * <p>
 * Renames the Anthropic-native request/response members that this dialect's transforms give a canonical
 * synonym for -- see {@link LlmAnthropicRequestTransform} and {@link LlmAnthropicResponseTransform} for
 * exactly which members and the rationale. Anthropic's own streaming block lifecycle
 * ({@code message_start}/{@code content_block_start}/{@code content_block_delta}/{@code content_block_stop}/
 * {@code message_delta}/{@code message_stop}) is already the skeleton this binding's canonical representation
 * is modeled on, so far fewer members need renaming here than {@link LlmOpenaiDialect} requires.
 * </p>
 * <p>
 * Response content-type resolution (a streaming response's {@code text/event-stream} chunks versus a single
 * {@code application/json} document) is a transport-layer concern, resolved from the real upstream
 * {@code Content-Type} response header rather than predicted here.
 * </p>
 * <p>
 * Unlike OpenAI's {@code [DONE]} sentinel, Anthropic's stream termination ({@code message_stop}) is itself a
 * JSON document that reaches {@link LlmAnthropicResponseTransform} like any other event, so
 * {@link #terminator(Kind)} returns {@code null} for both directions -- there is no out-of-band value to
 * recognize.
 * </p>
 */
public final class LlmAnthropicDialect implements LlmDialect
{
    private static final String NAME = "anthropic";

    private static final String METHOD_HEADER = ":method";
    private static final String PATH_HEADER = ":path";
    private static final String ANTHROPIC_VERSION_HEADER = "anthropic-version";
    private static final String API_KEY_HEADER = "x-api-key";
    private static final String AUTHORIZATION_HEADER = "authorization";
    private static final String METHOD_POST = "POST";

    private static final String MESSAGES_PATH = "/v1/messages";

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public boolean detect(
        ModelEnvelope headers)
    {
        final boolean pathMatched = METHOD_POST.equalsIgnoreCase(header(headers, METHOD_HEADER)) &&
            MESSAGES_PATH.equals(header(headers, PATH_HEADER));
        final boolean versionHeaderPresent = header(headers, ANTHROPIC_VERSION_HEADER) != null;
        final boolean apiKeyWithoutAuthorization = header(headers, API_KEY_HEADER) != null &&
            header(headers, AUTHORIZATION_HEADER) == null;
        return pathMatched || versionHeaderPresent || apiKeyWithoutAuthorization;
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
            transform = new LlmAnthropicRequestTransform(true, envelope);
            break;
        case RESPONSE:
            transform = new LlmAnthropicResponseTransform(true, envelope);
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
            transform = new LlmAnthropicRequestTransform(false, envelope);
            break;
        case RESPONSE:
            transform = new LlmAnthropicResponseTransform(false, envelope);
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
        return null;
    }

    private static String header(
        ModelEnvelope headers,
        String name)
    {
        final DirectBufferEx value = headers.get(name, 0);
        return value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
    }
}
