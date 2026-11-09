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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aDiscoveryConfig;
import io.aklivity.zilla.config.binding.a2a.A2aKeywordSearchIndexConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class A2aDiscoveryConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(new JsonbConfig().withAdapters(new A2aDiscoveryConfigAdapter()))
            .build();
    }

    @Test
    public void shouldReadEmptyDiscoveryWithDefaultAgent()
    {
        A2aDiscoveryConfig discovery = jsonb.fromJson("{}", A2aDiscoveryConfig.class);

        assertThat(discovery.agent, equalTo("discovery"));
        assertThat(discovery.search, nullValue());
    }

    @Test
    public void shouldReadSearchWithDefaults()
    {
        String text =
            """
            search:
              limit: 3
            """;

        A2aDiscoveryConfig discovery = jsonb.fromJson(text, A2aDiscoveryConfig.class);

        assertThat(discovery.search.limit, equalTo(3));
        assertThat(discovery.search.fields, contains("name", "description", "skills"));
        assertThat(discovery.search.indexes, hasSize(1));
        assertThat(discovery.search.indexes.get(0).type, equalTo("keyword"));
    }

    @Test
    public void shouldReadSearchWithFlatType()
    {
        String text =
            """
            search:
              type: keyword
            """;

        A2aDiscoveryConfig discovery = jsonb.fromJson(text, A2aDiscoveryConfig.class);

        assertThat(discovery.search.indexes, hasSize(1));
        assertThat(discovery.search.indexes.get(0), instanceOf(A2aKeywordSearchIndexConfig.class));
    }

    @Test
    public void shouldReadSearchWithIndexArray()
    {
        String text =
            """
            search:
              index:
                - type: keyword
            """;

        A2aDiscoveryConfig discovery = jsonb.fromJson(text, A2aDiscoveryConfig.class);

        assertThat(discovery.search.indexes, hasSize(1));
        assertThat(discovery.search.indexes.get(0), instanceOf(A2aKeywordSearchIndexConfig.class));
    }

    @Test(expected = JsonbException.class)
    public void shouldRejectUnrecognizedSearchIndexType()
    {
        String text =
            """
            search:
              index:
                - type: semantic
            """;

        jsonb.fromJson(text, A2aDiscoveryConfig.class);
    }

    @Test
    public void shouldWriteSearchWithSingleKeywordIndexAsFlatType()
    {
        A2aDiscoveryConfig discovery = A2aDiscoveryConfig.builder()
            .agent("discovery")
            .search()
                .limit(3)
                .fields(List.of("name", "skills"))
                .weight("name", 2.0)
                .index(A2aKeywordSearchIndexConfig.builder().build())
                .build()
            .build();

        assertThat(jsonb.toJson(discovery), equalTo(
            """
            agent: discovery
            search:
              limit: 3
              fields:
                - name
                - skills
              weights:
                name: 2
              type: keyword
            """));
    }

    @Test
    public void shouldWriteSearchWithMultipleIndexesAsIndexArray()
    {
        A2aDiscoveryConfig discovery = A2aDiscoveryConfig.builder()
            .search()
                .limit(3)
                .index(A2aKeywordSearchIndexConfig.builder().build())
                .index(A2aKeywordSearchIndexConfig.builder().build())
                .build()
            .build();

        assertThat(jsonb.toJson(discovery), equalTo(
            """
            search:
              limit: 3
              index:
                - type: keyword
                - type: keyword
            """));
    }
}
