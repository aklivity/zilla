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
package io.aklivity.zilla.config.binding.http.kafka;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;

import org.junit.Test;

public class HttpKafkaWithFetchConfigTest
{
    @Test
    public void shouldBuildFetchWithTopicOnly()
    {
        HttpKafkaWithFetchConfig fetch = HttpKafkaWithFetchConfig.builder()
            .topic("events")
            .build();

        assertThat(fetch.topic, equalTo("events"));
        assertThat(fetch.filters.isPresent(), equalTo(false));
        assertThat(fetch.merge.isPresent(), equalTo(false));
    }

    @Test
    public void shouldBuildFetchWithFilterAndMerge()
    {
        HttpKafkaWithFetchConfig fetch = HttpKafkaWithFetchConfig.builder()
            .topic("events")
            .filter()
                .key("key1")
                .header("name1", "value1")
                .build()
            .merged(HttpKafkaWithFetchMergeConfig.builder()
                .contentType("application/json")
                .initial("[]")
                .path("/-")
                .build())
            .build();

        HttpKafkaWithFetchFilterConfig filter = fetch.filters.get().get(0);
        HttpKafkaWithFetchFilterHeaderConfig header = filter.headers.get().get(0);

        assertThat(filter.key.get(), equalTo("key1"));
        assertThat(header.name, equalTo("name1"));
        assertThat(header.value, equalTo("value1"));
        assertThat(fetch.merge.get().contentType, equalTo("application/json"));
        assertThat(fetch.merge.get().initial, equalTo("[]"));
        assertThat(fetch.merge.get().path, equalTo("/-"));
    }

    @Test
    public void shouldMapFetch()
    {
        String topic = HttpKafkaWithFetchConfig.<String>builder(f -> f.topic)
            .topic("events")
            .build();

        assertThat(topic, equalTo("events"));
    }

    @Test
    public void shouldBuildFilterHeaderDirectly()
    {
        HttpKafkaWithFetchFilterHeaderConfig header = HttpKafkaWithFetchFilterHeaderConfig.builder()
            .name("name1")
            .value("value1")
            .build();

        assertThat(header.name, equalTo("name1"));
        assertThat(header.value, equalTo("value1"));
    }

    @Test
    public void shouldMapFilterHeader()
    {
        String value = HttpKafkaWithFetchFilterHeaderConfig.<String>builder(h -> h.value)
            .name("name1")
            .value("value1")
            .build();

        assertThat(value, equalTo("value1"));
    }

    @Test
    public void shouldBuildFilterWithExplicitHeadersList()
    {
        HttpKafkaWithFetchFilterConfig filter = HttpKafkaWithFetchFilterConfig.builder()
            .headers(List.of(HttpKafkaWithFetchFilterHeaderConfig.builder()
                .name("name1")
                .value("value1")
                .build()))
            .build();

        HttpKafkaWithFetchFilterHeaderConfig header = filter.headers.get().get(0);

        assertThat(header.name, equalTo("name1"));
        assertThat(header.value, equalTo("value1"));
    }

    @Test
    public void shouldMapFilter()
    {
        String key = HttpKafkaWithFetchFilterConfig.<String>builder(f -> f.key.get())
            .key("key1")
            .build();

        assertThat(key, equalTo("key1"));
    }

    @Test
    public void shouldMapMerge()
    {
        String contentType = HttpKafkaWithFetchMergeConfig.<String>builder(m -> m.contentType)
            .contentType("application/json")
            .initial("[]")
            .path("/-")
            .build();

        assertThat(contentType, equalTo("application/json"));
    }

    @Test
    public void shouldBuildProduceOverride()
    {
        HttpKafkaWithProduceOverrideConfig override = HttpKafkaWithProduceOverrideConfig.builder()
            .name("name1")
            .value("value1")
            .build();

        assertThat(override.name, equalTo("name1"));
        assertThat(override.value, equalTo("value1"));
    }

    @Test
    public void shouldMapProduceOverride()
    {
        String value = HttpKafkaWithProduceOverrideConfig.<String>builder(o -> o.value)
            .name("name1")
            .value("value1")
            .build();

        assertThat(value, equalTo("value1"));
    }

    @Test
    public void shouldBuildProduceAsyncHeader()
    {
        HttpKafkaWithProduceAsyncHeaderConfig header = HttpKafkaWithProduceAsyncHeaderConfig.builder()
            .name("name1")
            .value("value1")
            .build();

        assertThat(header.name, equalTo("name1"));
        assertThat(header.value, equalTo("value1"));
    }

    @Test
    public void shouldMapProduceAsyncHeader()
    {
        String value = HttpKafkaWithProduceAsyncHeaderConfig.<String>builder(h -> h.value)
            .name("name1")
            .value("value1")
            .build();

        assertThat(value, equalTo("value1"));
    }
}
