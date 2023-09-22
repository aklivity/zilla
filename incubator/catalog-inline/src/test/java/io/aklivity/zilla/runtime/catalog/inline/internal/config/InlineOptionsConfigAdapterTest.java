/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.inline.internal.config;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class InlineOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new InlineOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text = "{" +
                "\"subjects\":" +
                    "{" +
                    "\"subject1\":" +
                        "{" +
                            "\"version\": \"latest\"," +
                            "\"schema\": \"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":" +
                                "{\\\"id\\\":{\\\"type\\\":\\\"string\\\"},\\\"status\\\":{\\\"type\\\":\\\"string\\\"}}," +
                                "\\\"required\\\":[\\\"id\\\",\\\"status\\\"]}\"" +
                        "}" +
                    "}" +
                "}";

        InlineOptionsConfig catalog = jsonb.fromJson(text, InlineOptionsConfig.class);

        assertThat(catalog, not(nullValue()));
        InlineSchemaConfig schema = catalog.subjects.get(0);
        assertThat(schema.subject, equalTo("subject1"));
        assertThat(schema.version, equalTo("latest"));
        assertThat(schema.schema, equalTo("{\"type\":\"object\",\"properties\":" +
                "{\"id\":{\"type\":\"string\"},\"status\":{\"type\":\"string\"}}," +
                "\"required\":[\"id\",\"status\"]}"));
    }

    @Test
    public void shouldWriteCondition()
    {
        InlineOptionsConfig catalog = new InlineOptionsConfig(singletonList(
                new InlineSchemaConfig("subject1", "latest",
                "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},\"status\":{\"type\":\"string\"}}," +
                        "\"required\":[\"id\",\"status\"]}")));

        String text = jsonb.toJson(catalog);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"subject1\":{" +
                "\"schema\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":" +
                "{\\\"id\\\":{\\\"type\\\":\\\"string\\\"},\\\"status\\\":{\\\"type\\\":\\\"string\\\"}}," +
                "\\\"required\\\":[\\\"id\\\",\\\"status\\\"]}\"," +
                "\"version\":\"latest\"}}"));
    }
}
