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
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectFactorySpi;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmLegacyDialectFactorySpi;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;
import io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmLegacyDialectAdapter;

/**
 * Loads every {@link LlmDialect} registered via {@link LlmDialectFactorySpi}, and adapts each dialect that is only
 * registered via {@link LlmLegacyDialectFactorySpi} so that it is handled by the same dispatch.
 */
public final class LlmDialectFactory
{
    private final Map<String, LlmDialect> dialects;

    public LlmDialectFactory(
        LlmConfiguration config)
    {
        this.dialects = new LinkedHashMap<>();

        ServiceLoader
            .load(LlmDialectFactorySpi.class)
            .stream()
            .map(Supplier::get)
            .map(f -> f.create(config))
            .forEach(d -> dialects.put(d.name(), d));

        ServiceLoader
            .load(LlmLegacyDialectFactorySpi.class)
            .stream()
            .map(Supplier::get)
            .map(LlmLegacyDialectFactorySpi::name)
            .forEach(name -> dialects.computeIfAbsent(name, n -> new LlmLegacyDialectAdapter(n, config)));
    }

    public Map<String, LlmDialect> dialects()
    {
        return dialects;
    }
}
