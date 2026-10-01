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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLEncoder;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Resolves a {@link LlmDialect#requestPath(String)} template's {@link LlmDialect#MODEL_PLACEHOLDER} token,
 * when present, against a request's own selected model -- the one substitution a cached request path
 * template defers until that model is known.
 */
final class LlmRequestPathResolver
{
    private LlmRequestPathResolver()
    {
    }

    /**
     * Returns {@code template} unchanged when it carries no {@link LlmDialect#MODEL_PLACEHOLDER}, the
     * template with that token replaced by {@code model} percent-encoded as a single URL path segment when
     * {@code model} is not {@code null}, or {@code null} when the template needs a model but {@code model}
     * is {@code null}.
     *
     * @param template  the cached request path, possibly carrying {@link LlmDialect#MODEL_PLACEHOLDER}
     * @param model     the request's own selected model, or {@code null} when none was extracted
     * @return the resolved path, or {@code null} when a required model was not given
     */
    static String resolve(
        String template,
        DirectBufferEx model)
    {
        String resolved = template;
        if (template.contains(LlmDialect.MODEL_PLACEHOLDER))
        {
            resolved = model != null
                ? template.replace(LlmDialect.MODEL_PLACEHOLDER, encodeSegment(model))
                : null;
        }
        return resolved;
    }

    private static String encodeSegment(
        DirectBufferEx model)
    {
        final String value = model.getStringWithoutLengthUtf8(0, model.capacity());
        return URLEncoder.encode(value, UTF_8).replace("+", "%20");
    }
}
