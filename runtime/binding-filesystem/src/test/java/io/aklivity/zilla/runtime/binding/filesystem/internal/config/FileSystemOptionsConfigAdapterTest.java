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
package io.aklivity.zilla.runtime.binding.filesystem.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.net.URI;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.filesystem.config.FileSystemOptionsConfig;
import io.aklivity.zilla.runtime.binding.filesystem.config.FileSystemSymbolicLinksConfig;

public class FileSystemOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new FileSystemOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"location\":\"target/files\"," +
                    "\"symlinks\":\"follow\"" +
                "}";

        FileSystemOptionsConfig options = jsonb.fromJson(text, FileSystemOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.location, equalTo(URI.create("target/files")));
        assertThat(options.symlinks, equalTo(FileSystemSymbolicLinksConfig.FOLLOW));
    }

    @Test
    public void shouldWriteOptions()
    {
        FileSystemOptionsConfig options = new FileSystemOptionsConfig(
                URI.create("target/files"),
                FileSystemSymbolicLinksConfig.FOLLOW);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                    "{" +
                        "\"location\":\"target/files\"," +
                        "\"symlinks\":\"follow\"" +
                    "}"));
    }
}
