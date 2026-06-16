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
package io.aklivity.zilla.runtime.guard.identity.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class IdentityOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new IdentityOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text = "{\"credentials\":\"token\"}";

        IdentityOptionsConfig options = jsonb.fromJson(text, IdentityOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.credentials, equalTo("token"));
    }

    @Test
    public void shouldWriteOptions()
    {
        IdentityOptionsConfig options = new IdentityOptionsConfig("token");

        String json = jsonb.toJson(options);

        assertThat(json, not(nullValue()));
        assertThat(json, equalTo("{\"credentials\":\"token\"}"));
    }

    @Test
    public void shouldWriteOptionsWithNullCredentials()
    {
        IdentityOptionsConfig options = new IdentityOptionsConfig(null);

        String json = jsonb.toJson(options);

        assertThat(json, not(nullValue()));
        assertThat(json, equalTo("{}"));
    }
}
