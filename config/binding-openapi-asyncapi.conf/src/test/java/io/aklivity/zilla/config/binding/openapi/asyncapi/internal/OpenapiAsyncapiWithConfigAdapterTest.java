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
package io.aklivity.zilla.config.binding.openapi.asyncapi.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.openapi.asyncapi.OpenapiAsyncapiWithConfig;

public class OpenapiAsyncapiWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new OpenapiAsyncapiWithConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadWith()
    {
        String text = "{\"spec\":\"test\",\"operation\":\"o-id\"}";

        OpenapiAsyncapiWithConfig with = jsonb.fromJson(text, OpenapiAsyncapiWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.spec, equalTo("test"));
        assertThat(with.operation, equalTo("o-id"));
    }

    @Test
    public void shouldReadWithTag()
    {
        String text = "{\"spec\":\"test\",\"tag\":\"pets\"}";

        OpenapiAsyncapiWithConfig with = jsonb.fromJson(text, OpenapiAsyncapiWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.spec, equalTo("test"));
        assertThat(with.tag, equalTo("pets"));
    }

    @Test
    public void shouldWriteWith()
    {
        OpenapiAsyncapiWithConfig with = OpenapiAsyncapiWithConfig.builder()
            .spec("test")
            .operation("o-id")
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"spec\":\"test\",\"operation\":\"o-id\"}"));
    }
}
