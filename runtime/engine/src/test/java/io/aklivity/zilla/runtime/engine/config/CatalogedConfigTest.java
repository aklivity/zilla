/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.agrona.collections.ObjectHashSet;
import org.junit.Test;

public class CatalogedConfigTest
{
    @Test
    public void shouldWork()
    {
        SchemaConfig schema = SchemaConfig.builder()
                    .strategy("strategy")
                    .subject(null)
                    .version("version")
                    .id(42)
                    .build();
        ObjectHashSet<SchemaConfig> schemas = new ObjectHashSet<>();
        schemas.add(schema);

        CatalogedConfig cataloged = new CatalogedConfig("test", schemas);

        assertThat(cataloged.name, equalTo("test"));
        assertThat(cataloged.schemas.stream().findFirst().get().strategy, equalTo("strategy"));
        assertThat(cataloged.schemas.stream().findFirst().get().version, equalTo("version"));
        assertThat(cataloged.schemas.stream().findFirst().get().id, equalTo(42));
    }
}
