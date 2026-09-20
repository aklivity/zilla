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
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * A pluggable native wire format for an LLM API -- request/response framing and payload shape -- mapped to
 * and from this binding's canonical representation.
 * <p>
 * Implementations are created by a registered {@link LlmDialectFactorySpi}, discovered via
 * {@link java.util.ServiceLoader}, so dialects can be contributed from outside this module.
 * </p>
 */
public interface LlmDialect
{
    /**
     * Distinguishes the request direction from the response direction of an exchange, since each has its
     * own schema and its own mapping to the canonical representation.
     */
    enum Kind
    {
        REQUEST,
        RESPONSE
    }

    /**
     * Returns this dialect's name, used to select it explicitly (e.g. via configuration) independent of
     * {@link #detect(JsonEnvelope)}.
     *
     * @return the dialect name
     */
    String name();

    /**
     * Determines whether this dialect recognizes a request from its metadata, for a route that selects a
     * dialect automatically rather than by explicit configuration.
     * <p>
     * {@code headers} carries the request's {@code :method}/{@code :path} pseudo-headers as ordinary named
     * entries alongside its other headers -- there is no separate path parameter -- so a dialect reads
     * whichever entries it needs the same way regardless of which one it is.
     * </p>
     *
     * @param headers  the request headers, including its {@code :method}/{@code :path} pseudo-headers
     * @return {@code true} if this dialect matches
     */
    boolean detect(
        JsonEnvelope headers);

    /**
     * Returns the request path this dialect's API expects a request at, used by a {@code kind: client}
     * binding dialing out to this dialect's upstream: {@code basePath} followed by this dialect's own
     * fixed operation suffix (e.g. {@code /chat/completions} for OpenAI's Chat Completions API, appended
     * after {@code basePath} to form {@code /v1/chat/completions} when {@code basePath} is {@code /v1}).
     *
     * @param basePath  the configured base path preceding this dialect's operation suffix
     * @return the request path
     */
    String requestPath(
        String basePath);

    /**
     * Returns the name of the request header this dialect's API carries client credentials in, read from
     * a request's {@link JsonEnvelope} to extract credentials for an {@code options.authorization} guard
     * check on a {@code kind: server} binding -- e.g. {@code authorization} for a dialect that follows the
     * bearer-token convention, {@code x-api-key} for one that expects a raw API key of its own. This is a
     * fixed fact of the dialect's real upstream API, not an operator-configurable choice, so every
     * implementation must declare its own -- there is no generally-valid fallback.
     *
     * @return the credentials header name
     */
    String credentialsHeader();

    /**
     * Returns this dialect's own JSON error body for a request an {@code options.authorization} guard
     * rejected on a {@code kind: server} binding, shaped the way this dialect's own API reports an
     * authentication failure. Like {@link #credentialsHeader()}, this is dialect-specific with no
     * generally-valid fallback, so every implementation must declare its own.
     *
     * @return the error response body, JSON-encoded
     */
    String unauthorizedBody();

    /**
     * Creates a new {@link JsonTransform} decoding one stream's native {@code kind} payload into this
     * binding's canonical representation, field by field.
     * <p>
     * {@code envelope} is the same per-stream metadata channel {@link #detect(JsonEnvelope)} reads request
     * headers from. A decoder may also write to it -- e.g. extracting a model name or a streaming flag from
     * a field into a named entry, mirroring how a Kafka cache model's {@code extractKey}/{@code
     * extractHeaders} transform observes a field and copies its value into an envelope while it flows
     * through unchanged -- so a caller reads that signal back off the envelope as decoding proceeds, rather
     * than buffering the whole body first just to peek at one field.
     * </p>
     *
     * @param kind      the request or response direction
     * @param envelope  the per-stream metadata channel
     * @return a new decoding transform
     */
    JsonTransform supplyDecoder(
        Kind kind,
        JsonEnvelope envelope);

    /**
     * Creates a new {@link JsonTransform} that observes one stream's native {@code kind} payload -- e.g.
     * extracting {@code model} from a request into {@code envelope}, or {@code usage} from a response --
     * without any canonical rewriting: every field passes through unchanged. Field name and location are
     * dialect-specific (both current dialects happen to carry {@code model} as a top-level request scalar,
     * but a future dialect is not bound to that shape, and each dialect's {@code usage} object occurs at
     * its own native path), so each implementation supplies its own extractor per {@code kind}.
     * <p>
     * A binding with no target dialect to bridge toward (e.g. a {@code kind: server} accepting a native
     * request it only needs to detect, extract routing signals from, and forward byte-for-byte to its own
     * application-facing side) uses this instead of {@link #supplyDecoder(Kind, JsonEnvelope)}: canonical
     * rewriting is meaningful only when bridging between two different dialects, which is a
     * {@code kind: client} binding's job alone.
     * </p>
     * <p>
     * {@code envelope} is the same per-stream metadata channel {@link #detect(JsonEnvelope)} reads from.
     * </p>
     *
     * @param kind      the request or response direction
     * @param envelope  the per-stream metadata channel
     * @return a new extracting transform
     */
    JsonTransform supplyExtractor(
        Kind kind,
        JsonEnvelope envelope);

    /**
     * Creates a new {@link JsonTransform} encoding one stream's canonical {@code kind} payload into this
     * dialect's native representation, field by field.
     *
     * @param kind      the request or response direction
     * @param envelope  the per-stream metadata channel
     * @return a new encoding transform
     */
    JsonTransform supplyEncoder(
        Kind kind,
        JsonEnvelope envelope);

    /**
     * Returns a {@link JsonTransform} validating one stream's native {@code kind} payload against this
     * dialect's own JSON schema, compiled once from this dialect's bundled schema resource, forwarding
     * every event unchanged.
     *
     * @param kind  the request or response direction
     * @return a schema-validating transform
     */
    JsonTransform supplySchemaValidator(
        Kind kind);

    /**
     * Returns the literal byte sequence this dialect's {@code kind} stream uses to signal completion out
     * of band from any document -- e.g. OpenAI's response stream ends with the SSE data value
     * {@code [DONE]}, which is not JSON and never reaches a {@link #supplyDecoder(Kind, JsonEnvelope)}
     * transform -- or {@code null} when this dialect's {@code kind} stream has no such terminator and every
     * value is a document.
     *
     * @param kind  the request or response direction
     * @return the terminator bytes, or {@code null}
     */
    DirectBufferEx terminator(
        Kind kind);
}
