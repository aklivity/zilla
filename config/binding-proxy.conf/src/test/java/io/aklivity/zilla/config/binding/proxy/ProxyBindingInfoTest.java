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
package io.aklivity.zilla.config.binding.proxy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.config.binding.proxy.internal.ProxyConditionConfigAdapter;
import io.aklivity.zilla.config.binding.proxy.internal.ProxyOptionsConfigAdapter;

public class ProxyBindingInfoTest
{
    private final ProxyBindingInfo info = new ProxyBindingInfo();

    @Test
    public void shouldResolveType()
    {
        assertThat(info.type(), equalTo("proxy"));
    }

    @Test
    public void shouldResolveSchema()
    {
        assertThat(info.schema(), not(nullValue()));
    }

    @Test
    public void shouldResolveOptionsAdapter()
    {
        assertThat(info.options(), instanceOf(ProxyOptionsConfigAdapter.class));
    }

    @Test
    public void shouldResolveConditionAdapter()
    {
        assertThat(info.condition(), instanceOf(ProxyConditionConfigAdapter.class));
    }
}
