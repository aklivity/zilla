/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.grpc.config.GrpcOptionsConfig;

public class GrpcOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new GrpcOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
            "{" +
                "\"services\": [\"grpc.health.v1.Health\"]" +
            "}";

        GrpcOptionsConfig options = jsonb.fromJson(text, GrpcOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.services, equalTo(List.of("grpc.health.v1.Health")));
    }

    @Test
    public void shouldWriteOptions()
    {
        GrpcOptionsConfig options = GrpcOptionsConfig.builder()
                .services(List.of("grpc.health.v1.Health"))
                .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertEquals("{\"services\":[\"grpc.health.v1.Health\"]}", text);
    }

}
