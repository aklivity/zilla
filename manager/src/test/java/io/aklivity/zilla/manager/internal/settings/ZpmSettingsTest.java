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
package io.aklivity.zilla.manager.internal.settings;

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

public class ZpmSettingsTest
{
    @Test
    public void shouldReadEmptySettings()
    {
        String text =
                "{" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        ZpmSettings settings = builder.fromJson(text, ZpmSettings.class);

        assertThat(settings, not(nullValue()));
        assertThat(settings.credentials, nullValue());
    }

    @Test
    public void shouldWriteEmptySettings()
    {
        String expected =
                "{" +
                "}";

        ZpmSettings settings = new ZpmSettings();

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(settings);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadEmptyCredentials()
    {
        String text =
                "{" +
                    "\"credentials\":" +
                    "[" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        ZpmSettings settings = builder.fromJson(text, ZpmSettings.class);

        assertThat(settings, not(nullValue()));
        assertThat(settings.credentials, not(nullValue()));
        assertThat(settings.credentials, emptyCollectionOf(ZpmCredentials.class));
    }

    @Test
    public void shouldWriteEmptyCredentials()
    {
        String expected =
                "{" +
                    "\"credentials\":" +
                    "[" +
                    "]" +
                "}";

        ZpmSettings settings = new ZpmSettings();
        settings.credentials = Collections.emptyList();

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(settings);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadCredential()
    {
        String text =
                "{" +
                    "\"credentials\":" +
                    "[" +
                        "{" +
                            "\"realm\": \"HTTP Realm\"," +
                            "\"host\": \"repo1.maven.org\"," +
                            "\"username\": \"user\"," +
                            "\"password\": \"pass\"" +
                        "}" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        ZpmSettings settings = builder.fromJson(text, ZpmSettings.class);

        assertThat(settings, not(nullValue()));
        assertThat(settings.credentials, not(nullValue()));
        assertThat(settings.credentials, equalTo(singletonList(
                new ZpmCredentials("HTTP Realm", "repo1.maven.org", "user", "pass"))));
    }

    @Test
    public void shouldWriteCredential()
    {
        String expected =
                "{" +
                    "\"credentials\":" +
                    "[" +
                        "{" +
                            "\"host\":\"repo1.maven.org\"," +
                            "\"password\":\"pass\"," +
                            "\"realm\":\"HTTP Realm\"," +
                            "\"username\":\"user\"" +
                        "}" +
                    "]" +
                "}";

        ZpmSettings settings = new ZpmSettings();
        settings.credentials = Collections.singletonList(
            new ZpmCredentials("HTTP Realm", "repo1.maven.org", "user", "pass"));

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(settings);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadCredentials()
    {
        String text =
                "{" +
                    "\"credentials\":" +
                    "[" +
                        "{" +
                            "\"realm\": \"HTTP Realm\"," +
                            "\"host\": \"repo1.maven.org\"," +
                            "\"username\": \"user\"," +
                            "\"password\": \"pass\"" +
                        "}," +
                        "{" +
                            "\"realm\": \"HTTP Realm 2\"," +
                            "\"host\": \"repo2.maven.org\"," +
                            "\"username\": \"user2\"," +
                            "\"password\": \"pass2\"" +
                        "}" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        ZpmSettings settings = builder.fromJson(text, ZpmSettings.class);

        assertThat(settings, not(nullValue()));
        assertThat(settings.credentials, not(nullValue()));
        assertThat(settings.credentials, equalTo(asList(
                new ZpmCredentials("HTTP Realm", "repo1.maven.org", "user", "pass"),
                new ZpmCredentials("HTTP Realm 2", "repo2.maven.org", "user2", "pass2"))));
    }

    @Test
    public void shouldWriteCredentials()
    {
        String expected =
                "{" +
                    "\"credentials\":" +
                    "[" +
                        "{" +
                            "\"host\":\"repo1.maven.org\"," +
                            "\"password\":\"pass\"," +
                            "\"realm\":\"HTTP Realm\"," +
                            "\"username\":\"user\"" +
                        "}," +
                        "{" +
                            "\"host\":\"repo2.maven.org\"," +
                            "\"password\":\"pass2\"," +
                            "\"realm\":\"HTTP Realm 2\"," +
                            "\"username\":\"user2\"" +
                        "}" +
                    "]" +
                "}";

        ZpmSettings settings = new ZpmSettings();
        settings.credentials = Arrays.asList(
                new ZpmCredentials("HTTP Realm", "repo1.maven.org", "user", "pass"),
                new ZpmCredentials("HTTP Realm 2", "repo2.maven.org", "user2", "pass2"));

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(settings);

        assertEquals(expected, actual);
    }
}
