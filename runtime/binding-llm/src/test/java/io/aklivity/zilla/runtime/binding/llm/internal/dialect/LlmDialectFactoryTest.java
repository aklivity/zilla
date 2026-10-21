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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectFactorySpi;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;
import io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmOpenaiDialectFactorySpi;
import io.aklivity.zilla.runtime.engine.Configuration;

public class LlmDialectFactoryTest
{
    private final LlmConfiguration config = new LlmConfiguration(new Configuration());

    @Test
    public void shouldLoadRegisteredDialects()
    {
        final LlmDialectFactory factory = new LlmDialectFactory(config);

        assertThat(factory.dialects().containsKey("openai/ccs"), equalTo(true));
        assertThat(factory.dialects().containsKey("openai/rs"), equalTo(true));
    }

    @Test
    public void shouldNotRegisterVendorAsDialect()
    {
        final LlmDialectFactory factory = new LlmDialectFactory(config);

        assertThat(factory.dialects().containsKey("openai"), equalTo(false));
    }

    @Test
    public void shouldRefuseDialectNamedAsSharedPrefix()
    {
        final LlmDialectFactorySpi shared = new LlmDialectFactorySpi()
        {
            @Override
            public String name()
            {
                return "x";
            }

            @Override
            public LlmDialect create(
                Configuration config)
            {
                return null;
            }
        };

        final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new LlmDialectFactory(config, List.of(new LlmOpenaiDialectFactorySpi(), shared)));

        assertThat(error.getMessage(), containsString("\"x\" is reserved"));
    }
}
