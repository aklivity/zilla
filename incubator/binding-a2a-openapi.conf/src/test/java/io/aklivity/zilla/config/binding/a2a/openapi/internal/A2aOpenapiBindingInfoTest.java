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
package io.aklivity.zilla.config.binding.a2a.openapi.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.feature.Incubating;

public class A2aOpenapiBindingInfoTest
{
    private final A2aOpenapiBindingInfo info = new A2aOpenapiBindingInfo();

    @Test
    public void shouldResolveType()
    {
        assertThat(info.type(), equalTo("a2a-openapi"));
    }

    @Test
    public void shouldResolveSchema()
    {
        assertThat(info.schema(), not(nullValue()));
    }

    @Test
    public void shouldResolveOptionsAdapter()
    {
        assertThat(info.options(), instanceOf(A2aOpenapiOptionsConfigAdapter.class));
    }

    @Test
    public void shouldResolveConditionAdapter()
    {
        assertThat(info.condition(), instanceOf(A2aOpenapiConditionConfigAdapter.class));
    }

    @Test
    public void shouldResolveWithAdapter()
    {
        assertThat(info.with(), instanceOf(A2aOpenapiWithConfigAdapter.class));
    }

    @Test
    public void shouldBeIncubating()
    {
        assertThat(A2aOpenapiBindingInfo.class.isAnnotationPresent(Incubating.class), equalTo(true));
    }
}
