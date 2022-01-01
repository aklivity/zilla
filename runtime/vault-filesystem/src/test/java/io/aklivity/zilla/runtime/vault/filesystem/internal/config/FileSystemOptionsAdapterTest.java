/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.vault.filesystem.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class FileSystemOptionsAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new FileSystemOptionsAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                "}";

        FileSystemOptions options = jsonb.fromJson(text, FileSystemOptions.class);

        assertThat(options, not(nullValue()));
        assertThat(options.keys, nullValue(FileSystemStore.class));
    }

    @Test
    public void shouldReadOptionsWithKeys()
    {
        String text =
                "{" +
                    "\"keys\":" +
                    "{" +
                        "\"store\": \"localhost.p12\"," +
                        "\"type\": \"pkcs12\"," +
                        "\"password\": \"generated\"" +
                    "}" +
                "}";

        FileSystemOptions options = jsonb.fromJson(text, FileSystemOptions.class);

        assertThat(options, not(nullValue()));
        assertThat(options.keys, not(nullValue()));
        assertThat(options.keys.store, equalTo("localhost.p12"));
        assertThat(options.keys.type, equalTo("pkcs12"));
        assertThat(options.keys.password, equalTo("generated"));
    }

    @Test
    public void shouldWriteOptions()
    {
        FileSystemOptions options = new FileSystemOptions(null, null, null);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{}"));
    }

    @Test
    public void shouldWriteOptionsWithKeys()
    {
        FileSystemStore keys = new FileSystemStore("localhost.p12", "pkcs12", "generated");
        FileSystemOptions options = new FileSystemOptions(keys, null, null);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"keys\":{\"store\":\"localhost.p12\",\"type\":\"pkcs12\",\"password\":\"generated\"}}"));
    }
}
