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

import org.junit.Test;

import io.aklivity.zilla.config.engine.EngineConfigReader;
import io.aklivity.zilla.config.engine.EngineInfo;
import io.aklivity.zilla.runtime.common.feature.FeatureFilter;

public class A2aOpenapiIncubatorDisabledTest
{
    private final EngineConfigReader reader = new EngineConfigReader(
        text -> text, new EngineInfo(), A2aOpenapiIncubatorDisabledTest::noop, A2aOpenapiIncubatorDisabledTest::noop);

    private static void noop(
        String value)
    {
    }

    @Test
    public void shouldDisableIncubator()
    {
        assertThat(FeatureFilter.isIncubatorEnabled(), equalTo(false));
        assertThat(FeatureFilter.featureEnabled(A2aOpenapiBindingInfo.class), equalTo(false));
    }

    @Test(expected = RuntimeException.class)
    public void shouldNotRecognizeTypeA2aOpenapi()
    {
        String text =
            """
            name: test
            bindings:
              net0:
                type: a2a-openapi
                kind: proxy
                exit: app0
            """;

        reader.read(text);
    }
}
