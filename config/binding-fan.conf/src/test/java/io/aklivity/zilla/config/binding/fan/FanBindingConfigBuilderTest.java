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
package io.aklivity.zilla.config.binding.fan;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.config.binding.fan.internal.FanBindingInfo;

public class FanBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        FanBindingConfig binding = FanBindingConfig.builder()
            .namespace("test")
            .name("fan0")
            .kind(SERVER)
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("fan0"));
        assertThat(binding.type, equalTo(FanBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(SERVER));
    }
}
