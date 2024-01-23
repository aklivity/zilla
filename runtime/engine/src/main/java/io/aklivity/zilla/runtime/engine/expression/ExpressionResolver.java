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
package io.aklivity.zilla.runtime.engine.expression;

import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.engine.factory.Factory;

public final class ExpressionResolver extends Factory
{
    private static final Pattern EXPRESSION_PATTERN =
            Pattern.compile("\\$\\{\\{\\s*([^\\s\\}]*)\\.([^\\s\\}]*)\\s*\\}\\}");

    private final Map<String, ExpressionResolverSpi> resolverSpis;
    private Matcher matcher;

    public static ExpressionResolver instantiate()
    {
        return instantiate(load(ExpressionResolverSpi.class), ExpressionResolver::new);
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
        ExpressionResolverSpi resolver = requireNonNull(resolverSpis.get(context), "Unrecognized resolver name: " + context);
        String value = resolver.resolve(var);
        return value != null ? value : "";
    }

    private ExpressionResolver(
        Map<String, ExpressionResolverSpi> resolverSpis)
    {
        this.resolverSpis = resolverSpis;
        this.matcher = EXPRESSION_PATTERN.matcher("");
    }

}
