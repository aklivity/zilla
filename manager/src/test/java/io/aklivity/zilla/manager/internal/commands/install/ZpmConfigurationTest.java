/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.manager.internal.commands.install;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.junit.Test;

public class ZpmConfigurationTest
{
    @Test
    public void shouldReadEmptyConfiguration()
    {
        String text =
                "{" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        ZpmConfiguration config = builder.fromJson(text, ZpmConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.dependencies, nullValue());
    }

    @Test
    public void shouldWriteEmptyConfiguration()
    {
        String expected =
                "{" +
                "}";

        ZpmConfiguration config = new ZpmConfiguration();

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadEmptyRepositories()
    {
        String text =
                "{" +
                    "\"repositories\":" +
                    "[" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        ZpmConfiguration config = builder.fromJson(text, ZpmConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.repositories, not(nullValue()));
        assertThat(config.repositories, emptyCollectionOf(ZpmRepository.class));
    }

    @Test
    public void shouldWriteEmptyRepositories()
    {
        String expected =
                "{" +
                    "\"repositories\":" +
                    "[" +
                    "]" +
                "}";

        ZpmConfiguration config = new ZpmConfiguration();
        config.repositories = Collections.emptyList();

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadRespository()
    {
        String text =
                "{" +
                    "\"repositories\":" +
                    "[" +
                        "\"https://repo1.maven.org/maven2/\"" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        ZpmConfiguration config = builder.fromJson(text, ZpmConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.repositories, not(nullValue()));
        assertThat(config.repositories, equalTo(singletonList(new ZpmRepository("https://repo1.maven.org/maven2/"))));
    }

    @Test
    public void shouldWriteRepository()
    {
        String expected =
                "{" +
                    "\"repositories\":" +
                    "[" +
                        "\"https://repo1.maven.org/maven2/\"" +
                    "]" +
                "}";

        ZpmConfiguration config = new ZpmConfiguration();
        config.repositories = Collections.singletonList(
                new ZpmRepository("https://repo1.maven.org/maven2/"));

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadRepositories()
    {
        String text =
                "{" +
                    "\"repositories\":" +
                    "[" +
                        "\"https://maven.example.com/maven2/\"," +
                        "\"https://repo1.maven.org/maven2/\"" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        ZpmConfiguration config = builder.fromJson(text, ZpmConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.repositories, not(nullValue()));
        assertThat(config.repositories, equalTo(asList(new ZpmRepository("https://maven.example.com/maven2/"),
                new ZpmRepository("https://repo1.maven.org/maven2/"))));
    }

    @Test
    public void shouldWriteRepositories()
    {
        String expected =
                "{" +
                    "\"repositories\":" +
                    "[" +
                        "\"https://maven.example.com/maven2/\"," +
                        "\"https://repo1.maven.org/maven2/\"" +
                    "]" +
                "}";

        ZpmConfiguration config = new ZpmConfiguration();
        config.repositories = Arrays.asList(
                new ZpmRepository("https://maven.example.com/maven2/"),
                new ZpmRepository("https://repo1.maven.org/maven2/"));

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadEmptyDependencies()
    {
        String text =
                "{" +
                    "\"dependencies\":" +
                    "[" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        ZpmConfiguration config = builder.fromJson(text, ZpmConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.dependencies, not(nullValue()));
        assertThat(config.dependencies, emptyCollectionOf(ZpmDependency.class));
    }

    @Test
    public void shouldWriteEmptyDependencies()
    {
        String expected =
                "{" +
                    "\"dependencies\":" +
                    "[" +
                    "]" +
                "}";

        ZpmConfiguration config = new ZpmConfiguration();
        config.dependencies = Collections.emptyList();

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadDependency()
    {
        String text =
                "{" +
                    "\"dependencies\":" +
                    "[" +
                        "\"io.aklivity.zilla:engine:1.0.0\"" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        ZpmConfiguration config = builder.fromJson(text, ZpmConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.dependencies, equalTo(singletonList(new ZpmDependency("io.aklivity.zilla", "engine", "1.0.0"))));
    }

    @Test
    public void shouldWriteDependency()
    {
        String expected =
                "{" +
                    "\"dependencies\":" +
                    "[" +
                        "\"io.aklivity.zilla:engine:1.0.0\"" +
                    "]" +
                "}";

        ZpmConfiguration config = new ZpmConfiguration();
        config.dependencies = Collections.singletonList(
                new ZpmDependency("io.aklivity.zilla", "engine", "1.0.0"));

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadDependencies()
    {
        String text =
                "{" +
                    "\"dependencies\":" +
                    "[" +
                        "\"io.aklivity.zilla:engine:1.0.0\"," +
                        "\"io.aklivity.zilla:cog-tcp:1.0.0\"" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        ZpmConfiguration config = builder.fromJson(text, ZpmConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.dependencies, equalTo(asList(
                new ZpmDependency("io.aklivity.zilla", "engine", "1.0.0"),
                new ZpmDependency("io.aklivity.zilla", "cog-tcp", "1.0.0"))));
    }

    @Test
    public void shouldWriteDependencies()
    {
        String expected =
                "{" +
                    "\"dependencies\":" +
                    "[" +
                        "\"io.aklivity.zilla:engine:1.0.0\"," +
                        "\"io.aklivity.zilla:cog-tcp:1.0.0\"" +
                    "]" +
                "}";

        ZpmConfiguration config = new ZpmConfiguration();
        config.dependencies = Arrays.asList(
                new ZpmDependency("io.aklivity.zilla", "engine", "1.0.0"),
                new ZpmDependency("io.aklivity.zilla", "cog-tcp", "1.0.0"));

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }
}
