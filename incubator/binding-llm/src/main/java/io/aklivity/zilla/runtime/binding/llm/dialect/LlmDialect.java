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
 * A pluggable native wire format for an LLM API -- request/response framing and payload shape -- mapped to
 * and from this binding's canonical JSON representation.
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
     * {@link #detect(String, HttpHeaders)}.
     *
     * @return the dialect name
     */
    String name();

    /**
     * Determines whether this dialect recognizes a request from its path and headers, for a route that
     * selects a dialect automatically rather than by explicit configuration.
     *
     * @param path     the request path
     * @param headers  the request headers
     * @return {@code true} if this dialect matches
     */
    boolean detect(
        String path,
        HttpHeaders headers);

    /**
     * Returns the content-type of this dialect's native wire format for the given direction of one
     * exchange, e.g. to select a matching content decoder or encoder.
     * <p>
     * Resolved per request rather than fixed once for the dialect instance, since a dialect's native format
     * can depend on the request itself -- e.g. a streaming-capable API whose request body carries a flag
     * selecting streaming (event-stream framing) versus non-streaming (a single JSON document) delivery for
     * its response, while the request body's own content-type stays constant regardless of that flag.
     * {@code headers} and {@code body} are the same request signals {@link #detect(String, HttpHeaders)}
     * and {@link HttpRequestBody} expose elsewhere; either may be {@code null} when unavailable to the
     * caller, and implementations that need no request context to decide simply ignore them.
     * </p>
     *
     * @param kind     the request or response direction
     * @param headers  the request headers, or {@code null} if unavailable
     * @param body     the request body, or {@code null} if unavailable
     * @return the content-type
     */
    String contentType(
        Kind kind,
        HttpHeaders headers,
        HttpRequestBody body);

    /**
     * Creates a new {@link JsonTransform} decoding one stream's native {@code kind} payload into this
     * binding's canonical representation.
     *
     * @param kind  the request or response direction
     * @return a new decoding transform
     */
    JsonTransform supplyDecoder(
        Kind kind);

    /**
     * Creates a new {@link JsonTransform} encoding one stream's canonical {@code kind} payload into this
     * dialect's native representation.
     *
     * @param kind  the request or response direction
     * @return a new encoding transform
     */
    JsonTransform supplyEncoder(
        Kind kind);
}
