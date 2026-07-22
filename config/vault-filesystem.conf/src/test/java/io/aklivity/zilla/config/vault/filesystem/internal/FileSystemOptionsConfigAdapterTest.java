/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.config.vault.filesystem.internal;

import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.vault.filesystem.FileSystemOptionsConfig;
import io.aklivity.zilla.config.vault.filesystem.FileSystemStoreConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class FileSystemOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new FileSystemOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
                .withProvider(YamlJson.provider())
                .withConfig(config)
                .build();
    }

    @Test
    public void shouldReadOptions()
    {
        String yaml =
                "{}";

        FileSystemOptionsConfig options = jsonb.fromJson(yaml, FileSystemOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.keys, nullValue(FileSystemStoreConfig.class));
    }

    @Test
    public void shouldReadOptionsWithKeys()
    {
        String yaml =
                """
                keys:
                  store: localhost.p12
                  type: pkcs12
                  password: generated
                """;

        FileSystemOptionsConfig options = jsonb.fromJson(yaml, FileSystemOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.keys, not(nullValue()));
        assertThat(options.keys.store, equalTo("localhost.p12"));
        assertThat(options.keys.type, equalTo("pkcs12"));
        assertThat(options.keys.password, equalTo("generated"));
    }

    @Test
    public void shouldReadOptionsWithKeysEntries()
    {
        String yaml =
                """
                keys:
                  store: localhost.p12
                  type: pkcs12
                  password: generated
                  entries:
                    - localhost
                """;

        FileSystemOptionsConfig options = jsonb.fromJson(yaml, FileSystemOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.keys, not(nullValue()));
        assertThat(options.keys.entries, contains("localhost"));
    }

    @Test
    public void shouldWriteOptions()
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo("{}\n"));
    }

    @Test
    public void shouldWriteOptionsWithKeys()
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .inject(identity())
            .keys()
                .inject(identity())
                .store("localhost.p12")
                .type("pkcs12")
                .password("generated")
                .build()
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
                """
                keys:
                  store: localhost.p12
                  type: pkcs12
                  password: generated
                """));
    }

    @Test
    public void shouldWriteOptionsWithKeysEntries()
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .inject(identity())
            .keys()
                .inject(identity())
                .store("localhost.p12")
                .type("pkcs12")
                .password("generated")
                .entries(List.of("localhost"))
                .build()
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
                """
                keys:
                  store: localhost.p12
                  type: pkcs12
                  password: generated
                  entries:
                    - localhost
                """));
    }
}
