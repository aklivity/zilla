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
package io.aklivity.zilla.runtime.binding.llm.internal.sign;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.binding.llm.sign.LlmRequestSigner;
import io.aklivity.zilla.runtime.binding.llm.sign.LlmRequestSignerContext;
import io.aklivity.zilla.runtime.binding.llm.sign.LlmRequestSignerFactorySpi;

/**
 * Resolves the {@link LlmRequestSigner} configured by name, from every signer registered via
 * {@link LlmRequestSignerFactorySpi}.
 * <p>
 * A {@code null} configured name resolves to {@code null} -- no signer configured. An unregistered
 * configured name also resolves to {@code null}, the same as an unresolved fixed dialect name.
 * </p>
 */
public final class LlmRequestSignerResolver
{
    private final LlmRequestSigner signer;

    public LlmRequestSignerResolver(
        String sign,
        LlmRequestSignerContext context)
    {
        this(sign, context, loadFactories());
    }

    LlmRequestSignerResolver(
        String sign,
        LlmRequestSignerContext context,
        Collection<LlmRequestSignerFactorySpi> factories)
    {
        final Map<String, LlmRequestSignerFactorySpi> factoriesByName = factories.stream()
            .collect(toMap(LlmRequestSignerFactorySpi::name, identity()));
        this.signer = sign != null
            ? Optional.ofNullable(factoriesByName.get(sign)).map(f -> f.create(context)).orElse(null)
            : null;
    }

    public LlmRequestSigner resolve()
    {
        return signer;
    }

    private static Collection<LlmRequestSignerFactorySpi> loadFactories()
    {
        return ServiceLoader
            .load(LlmRequestSignerFactorySpi.class)
            .stream()
            .map(Supplier::get)
            .toList();
    }
}
