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
package io.aklivity.zilla.runtime.binding.llm.internal;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;

public class LlmBindingContextTest
{
    private final LlmBinding binding = new LlmBinding(new LlmConfiguration(new Configuration()));
    private final LlmBindingContext context = binding.supply((EngineContext) null);

    @Test
    public void shouldResolveNoHandlerUntilWired()
    {
        final BindingConfig config = new TestBindingConfig();

        final BindingHandler handler = context.attach(config);

        assertNull(handler);

        context.detach(config);
    }

    @Test
    public void shouldResolveToString()
    {
        assertThat(context.toString(), not(nullValue()));
    }

    private static final class TestBindingConfig extends BindingConfig
    {
        private TestBindingConfig()
        {
            super("test", "llm0", LlmBinding.NAME, SERVER, null, null, null,
                List.<CatalogedConfig>of(), List.<RouteConfig>of(), null);
        }
    }
}
