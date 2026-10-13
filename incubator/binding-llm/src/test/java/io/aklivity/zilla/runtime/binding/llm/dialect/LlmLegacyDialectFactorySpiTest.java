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

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import org.junit.Test;

public class LlmLegacyDialectFactorySpiTest
{
    private final Map<String, LlmLegacyDialectFactorySpi> factoriesByName = ServiceLoader
        .load(LlmLegacyDialectFactorySpi.class)
        .stream()
        .map(Supplier::get)
        .collect(toMap(LlmLegacyDialectFactorySpi::name, identity()));

    @Test
    public void shouldResolveRegisteredDialects()
    {
        assertThat(factoriesByName.keySet(), hasItem("openai"));
        assertThat(factoriesByName.keySet(), hasItem("anthropic"));
    }

    @Test
    public void shouldConvertKindValueOf()
    {
        assertThat(LlmLegacyDialect.Kind.valueOf("REQUEST"), equalTo(LlmLegacyDialect.Kind.REQUEST));
        assertThat(LlmLegacyDialect.Kind.valueOf("RESPONSE"), equalTo(LlmLegacyDialect.Kind.RESPONSE));
    }

    @Test
    public void shouldReturnKindValues()
    {
        assertThat(LlmLegacyDialect.Kind.values(),
            arrayContaining(LlmLegacyDialect.Kind.REQUEST, LlmLegacyDialect.Kind.RESPONSE));
    }
}
