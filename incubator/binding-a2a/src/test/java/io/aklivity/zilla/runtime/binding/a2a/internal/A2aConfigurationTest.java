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

import static io.aklivity.zilla.runtime.binding.a2a.internal.A2aConfiguration.A2A_CARD_MAX_AGE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;

import org.junit.Test;

public class A2aConfigurationTest
{
    public static final String A2A_CARD_MAX_AGE_NAME = "zilla.binding.a2a.card.max.age";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertThat(A2A_CARD_MAX_AGE.name(), equalTo(A2A_CARD_MAX_AGE_NAME));
    }

    @Test
    public void shouldDefaultCardMaxAge() throws Exception
    {
        assertThat(new A2aConfiguration().cardMaxAge(), equalTo(Duration.ofMinutes(5)));
    }
}
