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
package io.aklivity.zilla.runtime.classifier.patterns.internal;

import static io.aklivity.zilla.runtime.classifier.patterns.internal.PatternsClassifierConfiguration.INPUT_MAX_LENGTH;
import static io.aklivity.zilla.runtime.classifier.patterns.internal.PatternsClassifierConfiguration.MATCH_MAX_STEPS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class PatternsClassifierConfigurationTest
{
    @Test
    public void shouldVerifyConstants()
    {
        assertThat(INPUT_MAX_LENGTH.name(), equalTo("zilla.classifier.patterns.input.max.length"));
        assertThat(MATCH_MAX_STEPS.name(), equalTo("zilla.classifier.patterns.match.max.steps"));
    }

    @Test
    public void shouldDefaultLimits()
    {
        PatternsClassifierConfiguration config = new PatternsClassifierConfiguration(new Configuration());

        assertThat(config.inputMaxLength(), equalTo(65536));
        assertThat(config.matchMaxSteps(), equalTo(2097152L));
    }
}
