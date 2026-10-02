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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;
import io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmLegacyDialectAdapter;
import io.aklivity.zilla.runtime.engine.Configuration;

public class LlmDialectFactoryTest
{
    private final LlmDialectFactory factory = new LlmDialectFactory(new LlmConfiguration(new Configuration()));

    @Test
    public void shouldAdaptLegacyDialects()
    {
        assertThat(factory.dialects(), hasKey("anthropic"));
        assertThat(factory.dialects().get("anthropic"), instanceOf(LlmLegacyDialectAdapter.class));
    }

    @Test
    public void shouldRegisterDialectWithoutAdapting()
    {
        assertThat(factory.dialects(), hasKey("openai"));
        assertThat(factory.dialects().get("openai"), not(instanceOf(LlmLegacyDialectAdapter.class)));
    }

    @Test
    public void shouldNameDialectsAfterRegistration()
    {
        factory.dialects().forEach((name, dialect) -> assertThat(dialect.name(), equalTo(name)));
    }
}
