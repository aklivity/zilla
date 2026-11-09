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
package io.aklivity.zilla.runtime.binding.a2a.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;

import java.util.ServiceLoader;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.binding.BindingFactorySpi;

public class A2aBindingFactorySpiTest
{
    @Test
    public void shouldCreateBinding()
    {
        A2aBindingFactorySpi factory = new A2aBindingFactorySpi();

        Binding binding = factory.create(new Configuration());

        assertThat(factory.type(), equalTo("a2a"));
        assertThat(binding.name(), equalTo("a2a"));
        assertThat(binding.supply(mock(EngineContext.class)), instanceOf(A2aBindingContext.class));
    }

    @Test
    public void shouldLoadFactoryFromServiceLoader()
    {
        boolean found = ServiceLoader.load(BindingFactorySpi.class).stream()
            .anyMatch(provider -> provider.type() == A2aBindingFactorySpi.class);

        assertThat(found, equalTo(true));
    }
}
