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
package io.aklivity.zilla.config.binding.a2a.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aDiscoveryConfig;
import io.aklivity.zilla.config.binding.a2a.A2aKeywordSearchIndexConfig;
import io.aklivity.zilla.config.binding.a2a.A2aOptionsConfig;
import io.aklivity.zilla.config.binding.a2a.A2aTestSearchIndexConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class A2aSearchIndexExtensionTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(new JsonbConfig().withAdapters(new A2aBindingInfo().options()))
            .build();
    }

    @Test
    public void shouldReadFlatTypeFromExtension()
    {
        String text =
            """
            discovery:
              search:
                type: test
            """;

        A2aOptionsConfig options = (A2aOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options.discovery.search.indexes, hasSize(1));
        assertThat(options.discovery.search.indexes.get(0), instanceOf(A2aTestSearchIndexConfig.class));
    }

    @Test
    public void shouldReadIndexListWithKeywordAndExtension()
    {
        String text =
            """
            discovery:
              search:
                index:
                  - type: keyword
                  - type: test
            """;

        A2aOptionsConfig options = (A2aOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        A2aDiscoveryConfig discovery = options.discovery;
        assertThat(discovery.search.indexes, hasSize(2));
        assertThat(discovery.search.indexes.get(0).type, equalTo(A2aKeywordSearchIndexConfig.NAME));
        assertThat(discovery.search.indexes.get(1).type, equalTo(A2aTestSearchIndexConfig.NAME));
    }

    @Test
    public void shouldWriteIndexListWithExtension()
    {
        A2aOptionsConfig options = A2aOptionsConfig.builder()
            .discovery()
                .search()
                    .limit(3)
                    .index(A2aKeywordSearchIndexConfig.builder().build())
                    .index(new A2aTestSearchIndexConfig())
                    .build()
                .build()
            .build();

        String text = jsonb.toJson(options, OptionsConfig.class);

        assertThat(text, equalTo(
            """
            discovery:
              search:
                limit: 3
                index:
                  - type: keyword
                  - type: test
            """));
    }

    @Test(expected = JsonbException.class)
    public void shouldRejectUnrecognizedType()
    {
        jsonb.fromJson("discovery: { search: { type: unknown } }", OptionsConfig.class);
    }
}
