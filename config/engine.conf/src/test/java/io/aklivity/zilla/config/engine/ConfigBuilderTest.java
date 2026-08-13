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
package io.aklivity.zilla.config.engine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class ConfigBuilderTest
{
    @Test
    public void shouldComposeExtensionBuilderIntoOuterBuilder()
    {
        TestRootConfig config = new TestRootConfigBuilder()
            .name("root")
            .ext(TestExtConfigBuilder::new)
                .value("value0")
                .build()
            .build();

        assertThat(config.name, equalTo("root"));
        assertThat(config.ext(TestExtConfig.NAME, TestExtConfig.class).value, equalTo("value0"));
    }

    @Test
    public void shouldBuildWithoutExtension()
    {
        TestRootConfig config = new TestRootConfigBuilder()
            .name("root")
            .build();

        assertThat(config.name, equalTo("root"));
        assertThat(config.ext(TestExtConfig.NAME, TestExtConfig.class), nullValue());
    }
}
