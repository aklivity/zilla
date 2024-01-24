/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.resolver;

import static io.aklivity.zilla.runtime.common.feature.FeatureFilter.filter;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.engine.Configuration;

public final class Resolver
{
    private static final Pattern EXPRESSION_PATTERN =
            Pattern.compile("\\$\\{\\{\\s*([^\\s\\}]*)\\.([^\\s\\}]*)\\s*\\}\\}");

    private final Map<String, ResolverSpi> resolverSpis;
    private Matcher matcher;

    public static Resolver instantiate(
        Configuration config)
    {
        return instantiate(config, filter(load(ResolverFactorySpi.class)));
    }

    public String resolve(
        String template)
    {
        return matcher.reset(template)
                .replaceAll(r -> resolve(r.group(1), r.group(2)));
    }

    private String resolve(
        String context,
        String var)
    {
        ResolverSpi resolver = requireNonNull(resolverSpis.get(context), "Unrecognized resolver name: " + context);
        String value = resolver.resolve(var);
        return value != null ? value : "";
    }

    private static Resolver instantiate(
        Configuration config,
        Iterable<ResolverFactorySpi> factories)
    {
        Map<String, ResolverSpi> resolversByName = new HashMap<>();
        factories.forEach(f -> resolversByName.put(f.type(), f.create(config)));
        return new Resolver(unmodifiableMap(resolversByName));
    }

    private Resolver(
        Map<String, ResolverSpi> resolverSpis)
    {
        this.resolverSpis = resolverSpis;
        this.matcher = EXPRESSION_PATTERN.matcher("");
    }

}
