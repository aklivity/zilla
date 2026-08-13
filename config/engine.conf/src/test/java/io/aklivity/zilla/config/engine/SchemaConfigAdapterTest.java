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
package io.aklivity.zilla.config.engine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.Before;
import org.junit.Test;

public class SchemaConfigAdapterTest
{
    private SchemaConfigAdapter adapter;

    @Before
    public void initJson()
    {
        adapter = new SchemaConfigAdapter();
    }

    @Test
    public void shouldReadSchema()
    {
        String text =
            "{" +
                "\"subject\": \"echo\"," +
                "\"version\": \"1\"" +
            "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        SchemaConfig schema = adapter.adaptFromJson(object);

        assertThat(schema, not(nullValue()));
        assertThat(schema.subject, equalTo("echo"));
        assertThat(schema.version, equalTo("1"));
    }

    @Test
    public void shouldWriteSchema()
    {
        SchemaConfig schema = SchemaConfig.builder()
            .subject("echo")
            .version("1")
            .build();

        JsonObject object = adapter.adaptToJson(schema);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"subject\":\"echo\",\"version\":\"1\"}"));
    }
}
