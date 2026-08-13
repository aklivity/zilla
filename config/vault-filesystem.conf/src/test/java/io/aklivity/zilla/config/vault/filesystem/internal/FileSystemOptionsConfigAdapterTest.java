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

    @Test
    public void shouldReadOptionsWithSecretsPlainStringEntry()
    {
        String yaml =
                """
                secrets:
                  store: secrets.p12
                  password: generated
                  entries:
                    app-key: app-key-alias
                """;

        FileSystemOptionsConfig options = jsonb.fromJson(yaml, FileSystemOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.secrets, not(nullValue()));
        assertThat(options.secrets.store, equalTo("secrets.p12"));
        assertThat(options.secrets.password, equalTo("generated"));
        assertThat(options.secrets.entries.get("app-key").active, equalTo("1"));
        assertThat(options.secrets.entries.get("app-key").versions.get("1"), equalTo("app-key-alias"));
        assertThat(options.secrets.entries.get("app-key").algorithm, nullValue());
    }

    @Test
    public void shouldReadOptionsWithSecretsRotatedEntry()
    {
        String yaml =
                """
                secrets:
                  store: secrets.p12
                  password: generated
                  entries:
                    session-key:
                      active: "2"
                      versions:
                        "1": session-key-v1-alias
                        "2": session-key-v2-alias
                      algorithm: AES256_GCM
                """;

        FileSystemOptionsConfig options = jsonb.fromJson(yaml, FileSystemOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.secrets.entries.get("session-key").active, equalTo("2"));
        assertThat(options.secrets.entries.get("session-key").versions.get("1"), equalTo("session-key-v1-alias"));
        assertThat(options.secrets.entries.get("session-key").versions.get("2"), equalTo("session-key-v2-alias"));
        assertThat(options.secrets.entries.get("session-key").algorithm, equalTo("AES256_GCM"));
    }

    @Test
    public void shouldWriteOptionsWithSecretsPlainStringEntry()
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .inject(identity())
            .secrets()
                .inject(identity())
                .store("secrets.p12")
                .password("generated")
                .entry("app-key")
                    .alias("app-key-alias")
                    .build()
                .build()
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
                """
                secrets:
                  store: secrets.p12
                  password: generated
                  entries:
                    app-key: app-key-alias
                """));
    }

    @Test
    public void shouldWriteOptionsWithSecretsRotatedEntry()
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .inject(identity())
            .secrets()
                .inject(identity())
                .store("secrets.p12")
                .password("generated")
                .entry("session-key")
                    .active("2")
                    .version("1", "session-key-v1-alias")
                    .version("2", "session-key-v2-alias")
                    .build()
                .build()
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
                """
                secrets:
                  store: secrets.p12
                  password: generated
                  entries:
                    session-key:
                      active: "2"
                      versions:
                        "1": session-key-v1-alias
                        "2": session-key-v2-alias
                """));
    }
}
