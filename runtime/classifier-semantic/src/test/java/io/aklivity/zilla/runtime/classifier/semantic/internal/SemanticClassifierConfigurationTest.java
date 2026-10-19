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
package io.aklivity.zilla.runtime.classifier.semantic.internal;

import static io.aklivity.zilla.runtime.classifier.semantic.internal.SemanticClassifierConfiguration.LOCK_TTL_SECONDS;
import static io.aklivity.zilla.runtime.classifier.semantic.internal.SemanticClassifierConfiguration.TIMEOUT_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class SemanticClassifierConfigurationTest
{
    @Test
    public void shouldVerifyConstants()
    {
        assertThat(LOCK_TTL_SECONDS.name(), equalTo("zilla.classifier.semantic.lock.ttl.seconds"));
        assertThat(TIMEOUT_SECONDS.name(), equalTo("zilla.classifier.semantic.timeout.seconds"));
    }

    @Test
    public void shouldDefaultDurations()
    {
        SemanticClassifierConfiguration config = new SemanticClassifierConfiguration(new Configuration());

        assertThat(config.lockTtl(), equalTo(Duration.ofSeconds(30)));
        assertThat(config.timeout(), equalTo(Duration.ofSeconds(30)));
    }
}
