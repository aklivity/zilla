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
package io.aklivity.zilla.runtime.binding.llm.internal.dialect;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmLegacyDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmLegacyDialectContext;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmLegacyDialectFactorySpi;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;

/**
 * Resolves the {@link LlmLegacyDialect} for an inbound request, either from a fixed configured dialect name or by
 * dispatching {@link LlmLegacyDialect#detect(JsonEnvelope)} across every dialect registered via
 * {@link LlmLegacyDialectFactorySpi}.
 * <p>
 * A configured fixed dialect name bypasses detection entirely, including when the name matches no registered
 * dialect. Otherwise, when detection matches more than one registered dialect, or none, resolution is
 * ambiguous and this returns {@code null} so the caller can reject the request rather than guess.
 * </p>
 */
public final class LlmLegacyDialectResolver
{
    private final Map<String, LlmLegacyDialect> dialectsByName;
    private final LlmLegacyDialect fixedDialect;
    private final boolean dialectFixed;

    public LlmLegacyDialectResolver(
        String dialect,
        LlmLegacyDialectContext context)
    {
        this(dialect, loadDialects(context));
    }

    LlmLegacyDialectResolver(
        String dialect,
        Collection<LlmLegacyDialect> dialects)
    {
        this.dialectsByName = dialects.stream().collect(toMap(LlmLegacyDialect::name, identity()));
        this.dialectFixed = dialect != null;
        this.fixedDialect = dialectFixed ? dialectsByName.get(dialect) : null;
    }

    public LlmLegacyDialect resolve(
        JsonEnvelope headers)
    {
        return dialectFixed ? fixedDialect : detect(headers);
    }

    public Collection<LlmLegacyDialect> dialects()
    {
        return dialectsByName.values();
    }

    public LlmLegacyDialect dialectNamed(
        String name)
    {
        return dialectsByName.get(name);
    }

    private LlmLegacyDialect detect(
        JsonEnvelope headers)
    {
        LlmLegacyDialect matched = null;
        boolean ambiguous = false;

        for (LlmLegacyDialect dialect : dialectsByName.values())
        {
            if (dialect.detect(headers))
            {
                ambiguous |= matched != null;
                matched = dialect;
            }
        }

        return ambiguous ? null : matched;
    }

    private static Collection<LlmLegacyDialect> loadDialects(
        LlmLegacyDialectContext context)
    {
        return ServiceLoader
            .load(LlmLegacyDialectFactorySpi.class)
            .stream()
            .map(Supplier::get)
            .map(f -> f.create(context))
            .toList();
    }
}
