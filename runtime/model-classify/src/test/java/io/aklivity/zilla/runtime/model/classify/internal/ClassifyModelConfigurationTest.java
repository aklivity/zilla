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
package io.aklivity.zilla.runtime.model.classify.internal;

import static io.aklivity.zilla.runtime.model.classify.internal.ClassifyModelConfiguration.MAX_LENGTH;
import static io.aklivity.zilla.runtime.model.classify.internal.ClassifyModelConfiguration.OVERLAP;
import static io.aklivity.zilla.runtime.model.classify.internal.ClassifyModelConfiguration.WINDOW;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class ClassifyModelConfigurationTest
{
    public static final String CLASSIFY_WINDOW_NAME = "zilla.model.classify.window";
    public static final String CLASSIFY_OVERLAP_NAME = "zilla.model.classify.overlap";

    @Test
    public void shouldVerifyConstants()
    {
        assertThat(MAX_LENGTH.name(), equalTo("zilla.model.classify.max.length"));
        assertThat(WINDOW.name(), equalTo(CLASSIFY_WINDOW_NAME));
        assertThat(OVERLAP.name(), equalTo(CLASSIFY_OVERLAP_NAME));
    }

    @Test
    public void shouldDefaultMaxLength()
    {
        assertThat(new ClassifyModelConfiguration(new Configuration()).maxLength(), equalTo(1048576));
    }

    @Test
    public void shouldDefaultWindowAndOverlap()
    {
        ClassifyModelConfiguration config = new ClassifyModelConfiguration(new Configuration());

        assertThat(config.window(), equalTo(1024));
        assertThat(config.overlap(), equalTo(128));
    }
}
