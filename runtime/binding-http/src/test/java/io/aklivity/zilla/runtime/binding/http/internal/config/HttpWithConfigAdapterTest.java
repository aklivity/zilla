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
package io.aklivity.zilla.runtime.binding.http.internal.config;

import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.http.config.HttpWithConfig;
import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;

public class HttpWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new HttpWithConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadWith()
    {
        String text =
            "{" +
                "\"headers\":" +
                "{" +
                    "\"overrides\":" +
                    "{" +
                        "\":authority\":\"example.com:443\"" +
                    "}" +
                "}" +
            "}";

        HttpWithConfig with = jsonb.fromJson(text, HttpWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.overrides, equalTo(singletonMap(new String8FW(":authority"), new String16FW("example.com:443"))));
    }

    @Test
    public void shouldWriteWith()
    {
        HttpWithConfig with = HttpWithConfig.builder()
            .override(new String8FW(":authority"), new String16FW("example.com:443")).build();

        String text = jsonb.toJson(with);

        String expected =
            "{" +
                "\"headers\":" +
                "{" +
                    "\"overrides\":" +
                    "{" +
                        "\":authority\":\"example.com:443\"" +
                    "}" +
                "}" +
            "}";

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(expected));
    }
}
