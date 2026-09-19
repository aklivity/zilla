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

import static io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectJson.compact;
import static io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectJson.getString;
import static io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectJson.orDefault;
import static io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectJson.readObject;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmCanonicalFinishReason;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Anthropic Messages API dialect: detects a {@code POST /v1/messages} request, an {@code anthropic-version}
 * request header, or an {@code x-api-key} request header carried without an {@code Authorization} header --
 * any one of these three signals on its own confirms this dialect, since a client that only ever sends one of
 * them (e.g. a proxy that always stamps {@code anthropic-version} regardless of path) is still unambiguously
 * Anthropic's own traffic. {@code x-api-key} accompanied by {@code Authorization} is not itself a signal --
 * that combination is at least as consistent with some other dialect stacking its own bearer credential on
 * top of a forwarded Anthropic API key, so {@link #detect(JsonEnvelope)} does not treat it as a hint.
 * <p>
 * {@link #supplyDecoder(Kind, JsonEnvelope)}/{@link #supplyEncoder(Kind, JsonEnvelope)} rename the
 * Anthropic-native request members that {@link LlmAnthropicRequestTransform} gives a canonical synonym for;
 * the response direction is always identity here, since genuine cross-dialect response streaming translation
 * lives entirely in {@code internal.mapper.LlmAnthropicDecodeTransform}/{@code LlmAnthropicEncodeSink}, and a
 * same-dialect response needs no rename at all. {@link #supplyValidator(Kind, JsonEnvelope)} performs no such renaming,
 * only {@code model} extraction. Anthropic's own streaming block lifecycle ({@code message_start}/
 * {@code content_block_start}/{@code content_block_delta}/{@code content_block_stop}/{@code message_delta}/
 * {@code message_stop}) is already the skeleton this binding's canonical representation is modeled on, so
 * far fewer request members need renaming here than {@link LlmOpenaiDialect} requires.
 * </p>
 * <p>
 * Response content-type resolution (a streaming response's {@code text/event-stream} chunks versus a single
 * {@code application/json} document) is a transport-layer concern, resolved from the real upstream
 * {@code Content-Type} response header rather than predicted here.
 * </p>
 * <p>
 * Unlike OpenAI's {@code [DONE]} sentinel, Anthropic's stream termination ({@code message_stop}) is itself a
 * JSON document, so {@link #terminator(Kind)} returns {@code null} for both directions -- there is no
 * out-of-band value to recognize.
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

    private static final String REQUEST_SCHEMA_RESOURCE = "anthropic.request.schema.json";
    private static final String RESPONSE_SCHEMA_RESOURCE = "anthropic.response.schema.json";

    private final JsonTransform requestValidator;
    private final JsonTransform responseValidator;

    public LlmAnthropicDialect()
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
        final boolean pathMatched = METHOD_POST.equalsIgnoreCase(header(headers, METHOD_HEADER)) &&
            MESSAGES_PATH.equals(header(headers, PATH_HEADER));
        final boolean versionHeaderPresent = header(headers, ANTHROPIC_VERSION_HEADER) != null;
        final boolean apiKeyWithoutAuthorization = header(headers, API_KEY_HEADER) != null &&
            header(headers, AUTHORIZATION_HEADER) == null;
        return pathMatched || versionHeaderPresent || apiKeyWithoutAuthorization;
    }

    @Override
    public String requestPath()
    {
        return MESSAGES_PATH;
    }

    @Override
    public String credentialsHeader()
    {
        return API_KEY_HEADER;
    }

    @Override
    public String unauthorizedBody()
    {
        return "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"," +
            "\"message\":\"invalid x-api-key\"}}";
    }

    @Override
    public JsonTransform supplyDecoder(
        Kind kind,
        JsonEnvelope envelope)
    {
        return kind == Kind.REQUEST ? new LlmAnthropicRequestTransform(true, envelope) : LlmDialectTransforms.identity();
    }

    @Override
    public JsonTransform supplyValidator(
        Kind kind,
        JsonEnvelope envelope)
    {
        return kind == Kind.REQUEST ? new LlmModelExtractTransform(envelope) : LlmDialectTransforms.identity();
    }

    @Override
    public JsonTransform supplyEncoder(
        Kind kind,
        JsonEnvelope envelope)
    {
        return kind == Kind.REQUEST ? new LlmAnthropicRequestTransform(false, envelope) : LlmDialectTransforms.identity();
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
        return null;
    }

    @Override
    public JsonObject decodeMessage(
        String data)
    {
        JsonObject root = readObject(data);
        JsonArray blocks = root.getJsonArray("content");

        JsonArrayBuilder content = Json.createArrayBuilder();
        if (blocks != null)
        {
            for (int i = 0; i < blocks.size(); i++)
            {
                JsonObject block = blocks.getJsonObject(i);
                if ("tool_use".equals(getString(block, "type", null)))
                {
                    JsonObject input = block.getJsonObject("input");
                    JsonObjectBuilder canonicalBlock = Json.createObjectBuilder().add("type", "tool_call");
                    addIfPresent(canonicalBlock, "toolId", getString(block, "id", null));
                    addIfPresent(canonicalBlock, "toolName", getString(block, "name", null));
                    canonicalBlock.add("arguments", input != null ? compact(input) : "{}");
                    content.add(canonicalBlock);
                }
                else
                {
                    content.add(Json.createObjectBuilder().add("type", "text").add("text", getString(block, "text", "")));
                }
            }
        }

        JsonObject usage = root.getJsonObject("usage");

        JsonObjectBuilder canonical = Json.createObjectBuilder();
        addIfPresent(canonical, "id", getString(root, "id", null));
        addIfPresent(canonical, "model", getString(root, "model", null));
        canonical.add("role", orDefault(getString(root, "role", null), "assistant"));
        canonical.add("content", content);
        canonical.add("finishReason", finishReason(getString(root, "stop_reason", null)).name());
        canonical.add("usage", Json.createObjectBuilder()
            .add("inputTokens", usage != null ? usage.getInt("input_tokens", -1) : -1)
            .add("outputTokens", usage != null ? usage.getInt("output_tokens", -1) : -1));

        return canonical.build();
    }

    @Override
    public String encodeMessage(
        JsonObject message)
    {
        JsonArray content = message.getJsonArray("content");
        JsonArrayBuilder blocks = Json.createArrayBuilder();

        for (int i = 0; i < content.size(); i++)
        {
            JsonObject block = content.getJsonObject(i);
            if ("tool_call".equals(block.getString("type")))
            {
                JsonObjectBuilder toolUse = Json.createObjectBuilder().add("type", "tool_use");
                addIfPresent(toolUse, "id", getString(block, "toolId", null));
                addIfPresent(toolUse, "name", getString(block, "toolName", null));
                String arguments = getString(block, "arguments", "");
                toolUse.add("input", arguments.isEmpty() ? Json.createObjectBuilder().build() : readObject(arguments));
                blocks.add(toolUse);
            }
            else
            {
                blocks.add(Json.createObjectBuilder().add("type", "text").add("text", getString(block, "text", "")));
            }
        }

        JsonObjectBuilder root = Json.createObjectBuilder();
        addIfPresent(root, "id", getString(message, "id", null));
        root.add("type", "message");
        root.add("role", getString(message, "role", "assistant"));
        addIfPresent(root, "model", getString(message, "model", null));
        root.add("content", blocks);
        root.add("stop_reason", stopReason(LlmCanonicalFinishReason.valueOf(message.getString("finishReason"))));

        JsonObject usage = message.getJsonObject("usage");
        root.add("usage", Json.createObjectBuilder()
            .add("input_tokens", usage != null ? usage.getInt("inputTokens", -1) : -1)
            .add("output_tokens", usage != null ? usage.getInt("outputTokens", -1) : -1));

        return compact(root.build());
    }

    private static void addIfPresent(
        JsonObjectBuilder builder,
        String name,
        String value)
    {
        if (value != null)
        {
            builder.add(name, value);
        }
    }

    private static LlmCanonicalFinishReason finishReason(
        String stopReason)
    {
        LlmCanonicalFinishReason reason;
        if ("max_tokens".equals(stopReason))
        {
            reason = LlmCanonicalFinishReason.LENGTH;
        }
        else if ("tool_use".equals(stopReason))
        {
            reason = LlmCanonicalFinishReason.TOOL_CALL;
        }
        else
        {
            reason = LlmCanonicalFinishReason.STOP;
        }
        return reason;
    }

    private static String stopReason(
        LlmCanonicalFinishReason reason)
    {
        String stopReason;
        switch (reason)
        {
        case LENGTH:
            stopReason = "max_tokens";
            break;
        case TOOL_CALL:
            stopReason = "tool_use";
            break;
        default:
            stopReason = "end_turn";
            break;
        }
        return stopReason;
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
        URL resource = LlmAnthropicDialect.class.getResource(name);
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
