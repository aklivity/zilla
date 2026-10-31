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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.binding.BindingFactorySpi;

public class AguiA2aBindingFactorySpiTest
{
    private final BindingFactorySpi factory = new AguiA2aBindingFactorySpi();

    @Test
    public void shouldCreateBinding()
    {
        assertThat(factory.type(), equalTo("agui-a2a"));
        assertThat(factory.create(new Configuration()), instanceOf(AguiA2aBinding.class));
        assertThat(factory.create(new Configuration()).name(), equalTo("agui-a2a"));
    }
}
