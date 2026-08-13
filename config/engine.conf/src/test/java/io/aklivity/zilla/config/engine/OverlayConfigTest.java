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

import org.junit.Test;

public class OverlayConfigTest
{
    @Test
    public void shouldWork()
    {
        SchemaConfig schema = SchemaConfig.builder()
                    .subject("echo")
                    .version("1")
                    .build();
        OverlayConfig overlay = OverlayConfig.builder()
                    .name("test")
                    .schema(schema)
                    .build();

        assertThat(overlay.name, equalTo("test"));
        assertThat(overlay.schema.subject, equalTo("echo"));
        assertThat(overlay.schema.version, equalTo("1"));
    }

    @Test
    public void shouldWorkWithNestedBuilder()
    {
        OverlayConfig overlay = OverlayConfig.builder()
                    .name("test")
                    .schema()
                        .subject("echo")
                        .version("1")
                        .build()
                    .build();

        assertThat(overlay.name, equalTo("test"));
        assertThat(overlay.schema.subject, equalTo("echo"));
        assertThat(overlay.schema.version, equalTo("1"));
    }
}
