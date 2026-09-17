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
     * {@link #detect(ModelEnvelope)}.
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
        ModelEnvelope headers);

    /**
     * Creates a new {@link ModelTransform} decoding one stream's native {@code kind} payload into this
     * binding's canonical representation, field by field.
     * <p>
     * {@code envelope} is the same per-stream metadata channel {@link #detect(ModelEnvelope)} reads request
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
    ModelTransform supplyDecoder(
        Kind kind,
        ModelEnvelope envelope);

    /**
     * Creates a new {@link ModelTransform} that validates one stream's native {@code kind} payload against
     * this dialect's own schema -- the same schema mechanism {@link LlmDialectFactorySpi#schema(Kind)}
     * registers for {@link #supplyDecoder(Kind, ModelEnvelope)} -- without any canonical rewriting: every
     * field passes through unchanged.
     * <p>
     * A binding with no target dialect to bridge toward (e.g. a {@code kind: server} accepting a native
     * request it only needs to detect, validate, and forward byte-for-byte to its own application-facing
     * side) uses this instead of {@link #supplyDecoder(Kind, ModelEnvelope)}: canonical rewriting is
     * meaningful only when bridging between two different dialects, which is a {@code kind: client}
     * binding's job alone.
     * </p>
     * <p>
     * {@code envelope} is the same per-stream metadata channel {@link #detect(ModelEnvelope)} reads from. A
     * validator may still extract a signal (e.g. {@code model}) into it as the payload streams through,
     * exactly as {@link #supplyDecoder(Kind, ModelEnvelope)} does, but performs no field substitution.
     * </p>
     *
     * @param kind      the request or response direction
     * @param envelope  the per-stream metadata channel
     * @return a new validating transform
     */
    ModelTransform supplyValidator(
        Kind kind,
        ModelEnvelope envelope);

    /**
     * Creates a new {@link ModelTransform} encoding one stream's canonical {@code kind} payload into this
     * dialect's native representation, field by field.
     *
     * @param kind      the request or response direction
     * @param envelope  the per-stream metadata channel
     * @return a new encoding transform
     */
    ModelTransform supplyEncoder(
        Kind kind,
        ModelEnvelope envelope);

    /**
     * Returns the literal byte sequence this dialect's {@code kind} stream uses to signal completion out
     * of band from any document -- e.g. OpenAI's response stream ends with the SSE data value
     * {@code [DONE]}, which is not JSON and never reaches a {@link #supplyDecoder(Kind, ModelEnvelope)}
     * transform -- or {@code null} when this dialect's {@code kind} stream has no such terminator and every
     * value is a document.
     *
     * @param kind  the request or response direction
     * @return the terminator bytes, or {@code null}
     */
    DirectBufferEx terminator(
        Kind kind);
}
