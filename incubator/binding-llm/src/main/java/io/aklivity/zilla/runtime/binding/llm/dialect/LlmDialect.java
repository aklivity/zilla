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

import java.util.Set;

import io.aklivity.zilla.runtime.binding.llm.sign.LlmRequestSigner;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonSink;
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
     * Literal token a {@link #requestPath(String)} result may contain, resolved per request -- not by the
     * dialect itself -- from the request's own selected model, percent-encoded as a URL path segment. A
     * dialect that never needs this simply never includes the token, at no cost beyond a single substring
     * check for the caller resolving it.
     */
    String MODEL_PLACEHOLDER = "{model}";

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
     * <p>
     * The returned path may carry the literal token {@link #MODEL_PLACEHOLDER}, for an upstream whose own
     * path names the model rather than carrying it only in the request body -- resolved once the request's
     * model is known, since this method is called once per stream before any request body byte arrives.
     * </p>
     *
     * @param basePath  the configured base path preceding this dialect's operation suffix
     * @return the request path, possibly carrying {@link #MODEL_PLACEHOLDER}
     */
    String requestPath(
        String basePath);

    /**
     * Returns the request path this dialect's API expects a request at, same as {@link #requestPath(String)},
     * but additionally distinguishing a streaming request from a non-streaming one -- e.g. for an upstream
     * whose streaming operation lives at an entirely different path than its non-streaming one, rather than
     * differing only in the request body. A dialect whose streaming and non-streaming requests share one path
     * never needs to override this default, which simply delegates to {@link #requestPath(String)}.
     *
     * @param basePath   the configured base path preceding this dialect's operation suffix
     * @param streaming  {@code true} for a streaming request, {@code false} otherwise
     * @return the request path, possibly carrying {@link #MODEL_PLACEHOLDER}
     */
    default String requestPath(
        String basePath,
        boolean streaming)
    {
        return requestPath(basePath);
    }

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
     * Returns this dialect's own JSON error body reporting that a request failed with {@code status}, shaped the
     * way this dialect's own API reports an error of that status, for a {@code kind: server} binding answering a
     * request its application-facing side rejected. The dialect derives its own native error kind from
     * {@code status}; {@code type} is the canonical error type observed wherever the failure originated,
     * possibly from another dialect's vocabulary, so a dialect may ignore it. Either of {@code type} and
     * {@code message} may be {@code null} when unknown, and a dialect then supplies its own fallback message.
     * Like {@link #unauthorizedBody()}, this is dialect-specific with no generally-valid fallback, so every
     * implementation must declare its own.
     *
     * @param status   the status the request failed with
     * @param type     the canonical error type, or {@code null}
     * @param message  the canonical error message, or {@code null}
     * @return the error response body, JSON-encoded
     */
    String errorBody(
        int status,
        String type,
        String message);

    /**
     * Returns the content type of this dialect's request body, e.g. {@code application/json}. A request
     * declaring any other content type is not this dialect's traffic and is rejected rather than forwarded.
     *
     * @return the request content type
     */
    String requestContentType();

    /**
     * Returns every content type this dialect's successful response may carry, e.g. {@code application/json}
     * for a whole response and {@code text/event-stream} for a streaming one. A successful response carrying
     * any other content type is treated as a failed response rather than decoded.
     *
     * @return the response content types
     */
    Set<String> responseContentTypes();

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
     * <p>
     * The {@code RESPONSE} extractor also reports whether the response failed, by recording the error the
     * response carries -- see {@link LlmResponseExtractTransform}, which a dialect typically extends. It runs
     * over a non-2xx response body as well as over every document of a successful one, and must implement
     * {@link LlmDialectEvent} -- as a no-op when the native out-of-band event name carries no error signal --
     * since a caller delivers each document's event name to it uniformly, with no {@code instanceof} check.
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
     * Creates a new {@link JsonTransform} decoding this dialect's native RESPONSE events into the canonical
     * representation, for a kind: client binding proxying a response to a differently-dialected caller. A
     * fresh instance backs each cross-dialect response stream. The returned instance must also implement
     * {@link LlmDialectEvent} -- as a no-op when this dialect's decode behavior does not depend on the native
     * out-of-band event name -- since a caller drives every dialect's transform through that interface
     * uniformly, with no {@code instanceof} check.
     *
     * @return a new decoding transform
     */
    JsonTransform supplyResponseDecodeTransform();

    /**
     * Creates a new {@link JsonSink} encoding canonical events into this dialect's native RESPONSE events,
     * for a kind: client binding proxying a response to a differently-dialected caller. {@code envelope} is
     * the same per-stream metadata channel {@link #detect(JsonEnvelope)} reads from; {@code output} receives
     * the encoded native event name/bytes as they're produced. A fresh instance backs each cross-dialect
     * response stream. The returned instance must also implement {@link LlmDialectTerminator} -- as a no-op
     * when this dialect has no literal, non-JSON completion terminator -- for the same reason
     * {@link #supplyResponseDecodeTransform()}'s result must implement {@link LlmDialectEvent}.
     *
     * @param envelope  the per-stream metadata channel
     * @param output    receives each encoded native event
     * @return a new encoding sink
     */
    JsonSink supplyResponseEncodeSink(
        JsonEnvelope envelope,
        LlmNativeEventOutput output);

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

    /**
     * Returns the {@link LlmRequestSigner} this dialect's upstream requires for a {@code kind: client}
     * binding dialing out to it -- e.g. a dialect whose upstream requires a signature computed over the
     * complete request rather than a single static credential value carried in one header -- or
     * {@code null} when this dialect needs no such signer, the same {@code null}-when-unneeded convention
     * {@link #terminator(Kind)} follows.
     *
     * @return the request signer, or {@code null}
     */
    default LlmRequestSigner signer()
    {
        return null;
    }
}
