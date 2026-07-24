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
package io.aklivity.zilla.config.binding.grpc.kafka;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;

import org.junit.Test;

public class GrpcKafkaWithFetchConfigTest
{
    @Test
    public void shouldConstructFetchWithFilters()
    {
        GrpcKafkaWithFetchFilterHeaderConfig header = new GrpcKafkaWithFetchFilterHeaderConfig("name1", "value1");
        GrpcKafkaWithFetchFilterConfig filter = new GrpcKafkaWithFetchFilterConfig("key1", List.of(header));
        GrpcKafkaWithFetchConfig fetch = new GrpcKafkaWithFetchConfig("events", List.of(filter));

        assertThat(fetch.topic, equalTo("events"));
        assertThat(fetch.filters.get().get(0).key.get(), equalTo("key1"));
        assertThat(fetch.filters.get().get(0).headers.get().get(0).name, equalTo("name1"));
        assertThat(fetch.filters.get().get(0).headers.get().get(0).value, equalTo("value1"));
    }

    @Test
    public void shouldConstructFetchWithoutFilters()
    {
        GrpcKafkaWithFetchConfig fetch = new GrpcKafkaWithFetchConfig("events", null);

        assertThat(fetch.topic, equalTo("events"));
        assertThat(fetch.filters.isPresent(), equalTo(false));
    }

    @Test
    public void shouldConstructFilterWithoutHeaders()
    {
        GrpcKafkaWithFetchFilterConfig filter = new GrpcKafkaWithFetchFilterConfig(null, null);

        assertThat(filter.key.isPresent(), equalTo(false));
        assertThat(filter.headers.isPresent(), equalTo(false));
    }

    @Test
    public void shouldConstructProduceOverride()
    {
        GrpcKafkaWithProduceOverrideConfig override = new GrpcKafkaWithProduceOverrideConfig("name1", "value1");

        assertThat(override.name, equalTo("name1"));
        assertThat(override.value, equalTo("value1"));
    }
}
