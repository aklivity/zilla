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
     * Returns the content-type of this dialect's native wire format, e.g. to select a matching content
     * decoder.
     *
     * @return the content-type
     */
    String contentType();

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
