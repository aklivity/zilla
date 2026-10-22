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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectFactorySpi;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;

/**
 * Loads every {@link LlmDialect} registered via {@link LlmDialectFactorySpi}.
 */
public final class LlmDialectFactory
{
    static final String SHARED_NAME = "x";

    private final Map<String, LlmDialect> dialects;

    public LlmDialectFactory(
        LlmConfiguration config)
    {
        this(config, ServiceLoader.load(LlmDialectFactorySpi.class).stream().map(Supplier::get).toList());
    }

    LlmDialectFactory(
        LlmConfiguration config,
        List<LlmDialectFactorySpi> factories)
    {
        this.dialects = new LinkedHashMap<>();

        factories.stream()
            .peek(f -> reserve(f.name()))
            .map(f -> f.create(config))
            .forEach(d -> dialects.put(d.name(), d));
    }

    public Map<String, LlmDialect> dialects()
    {
        return dialects;
    }

    private static void reserve(
        String name)
    {
        if (SHARED_NAME.equals(name))
        {
            throw new IllegalArgumentException("dialect name \"%s\" is reserved for shared members".formatted(name));
        }
    }
}
