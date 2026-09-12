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

import io.aklivity.zilla.runtime.binding.llm.dialect.HttpHeaders;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectFactorySpi;

/**
 * Resolves the {@link LlmDialect} for an inbound request, either from a fixed configured dialect name or by
 * dispatching {@link LlmDialect#detect(String, HttpHeaders)} across every dialect registered via
 * {@link LlmDialectFactorySpi}.
 * <p>
 * A configured fixed dialect name bypasses detection entirely, including when the name matches no registered
 * dialect. Otherwise, when detection matches more than one registered dialect, or none, resolution is
 * ambiguous and this returns {@code null} so the caller can reject the request rather than guess.
 * </p>
 */
public final class LlmDialectResolver
{
    private final Map<String, LlmDialect> dialectsByName;
    private final LlmDialect fixedDialect;
    private final boolean dialectFixed;

    public LlmDialectResolver(
        String dialect)
    {
        this(dialect, loadDialects());
    }

    LlmDialectResolver(
        String dialect,
        Collection<LlmDialect> dialects)
    {
        this.dialectsByName = dialects.stream().collect(toMap(LlmDialect::name, identity()));
        this.dialectFixed = dialect != null;
        this.fixedDialect = dialectFixed ? dialectsByName.get(dialect) : null;
    }

    public LlmDialect resolve(
        String path,
        HttpHeaders headers)
    {
        return dialectFixed ? fixedDialect : detect(path, headers);
    }

    public LlmDialect dialectNamed(
        String name)
    {
        return dialectsByName.get(name);
    }

    private LlmDialect detect(
        String path,
        HttpHeaders headers)
    {
        LlmDialect matched = null;
        boolean ambiguous = false;

        for (LlmDialect dialect : dialectsByName.values())
        {
            if (dialect.detect(path, headers))
            {
                ambiguous |= matched != null;
                matched = dialect;
            }
        }

        return ambiguous ? null : matched;
    }

    private static Collection<LlmDialect> loadDialects()
    {
        return ServiceLoader
            .load(LlmDialectFactorySpi.class)
            .stream()
            .map(Supplier::get)
            .map(LlmDialectFactorySpi::create)
            .toList();
    }
}
