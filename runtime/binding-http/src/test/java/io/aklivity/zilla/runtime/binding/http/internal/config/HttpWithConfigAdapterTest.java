/*
 * Copyright 2021-2024 Aklivity Inc.
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.aklivity.zilla.runtime.binding.http.config.HttpAffinityConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpAffinitySource;
import io.aklivity.zilla.runtime.binding.http.config.HttpWithConfig;
import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;

public class HttpWithConfigAdapterTest
{
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

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

    @Test
    public void shouldReadWithHeaderAffinity()
    {
        String text =
            "{" +
                "\"affinity\":" +
                "{" +
                    "\"header\":\"session-id\"" +
                "}" +
            "}";

        HttpWithConfig with = jsonb.fromJson(text, HttpWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.affinity, not(nullValue()));
        assertThat(with.affinity.source, equalTo(HttpAffinitySource.HEADER));
        assertThat(with.affinity.name, equalTo("session-id"));
        assertThat(with.affinity.match, nullValue());
    }

    @Test
    public void shouldWriteWithHeaderAffinity()
    {
        HttpWithConfig with = HttpWithConfig.builder()
            .affinity()
                .header("session-id")
                .build()
            .build();

        String text = jsonb.toJson(with);

        String expected =
            "{" +
                "\"affinity\":" +
                "{" +
                    "\"header\":\"session-id\"" +
                "}" +
            "}";

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(expected));
    }

    @Test
    public void shouldReadWithQueryAffinityAndMatch()
    {
        String text =
            "{" +
                "\"affinity\":" +
                "{" +
                    "\"query\":\"state\"," +
                    "\"match\":\"[^.]+\"" +
                "}" +
            "}";

        HttpWithConfig with = jsonb.fromJson(text, HttpWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.affinity, not(nullValue()));
        assertThat(with.affinity.source, equalTo(HttpAffinitySource.QUERY));
        assertThat(with.affinity.name, equalTo("state"));
        assertThat(with.affinity.match, not(nullValue()));
        assertThat(with.affinity.match.pattern(), equalTo("[^.]+"));
    }

    @Test
    public void shouldWriteWithQueryAffinityAndMatch()
    {
        HttpAffinityConfig affinity = HttpAffinityConfig.builder()
            .query("state")
            .match("[^.]+")
            .build();

        HttpWithConfig with = HttpWithConfig.builder()
            .affinity(affinity)
            .build();

        String text = jsonb.toJson(with);

        String expected =
            "{" +
                "\"affinity\":" +
                "{" +
                    "\"query\":\"state\"," +
                    "\"match\":\"[^.]+\"" +
                "}" +
            "}";

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(expected));
    }

    @Test
    public void shouldRejectAffinityWithBothHeaderAndQuery()
    {
        thrown.expect(Exception.class);

        String text =
            "{" +
                "\"affinity\":" +
                "{" +
                    "\"header\":\"session-id\"," +
                    "\"query\":\"state\"" +
                "}" +
            "}";

        jsonb.fromJson(text, HttpWithConfig.class);
    }

    @Test
    public void shouldRejectAffinityWithNeitherHeaderNorQuery()
    {
        thrown.expect(Exception.class);

        String text =
            "{" +
                "\"affinity\":" +
                "{" +
                    "\"match\":\"[^.]+\"" +
                "}" +
            "}";

        jsonb.fromJson(text, HttpWithConfig.class);
    }
}
