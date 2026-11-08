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
package io.aklivity.zilla.config.engine.test.internal.exporter.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;

import org.junit.Test;

import io.aklivity.zilla.config.engine.OptionsConfig;

public class TestExporterOptionsConfigAdapterTest
{
    private final TestExporterOptionsConfigAdapter adapter = new TestExporterOptionsConfigAdapter();

    @Test
    public void shouldReadFilterExclude()
    {
        String text =
            "{" +
                "\"filter\":" +
                "{" +
                    "\"exclude\":" +
                    "[" +
                        "{\"qname\":\"engine:events\"}," +
                        "{\"qname\":\"test:net0\",\"id\":\"binding.test.connected\"}," +
                        "{\"name\":\"ENGINE_STOPPED\",\"message\":\"Engine Stopped.\"}" +
                    "]" +
                "}," +
                "\"events\":" +
                "[" +
                    "{\"qname\":\"test:net0\",\"id\":\"binding.test.connected\"," +
                    "\"name\":\"BINDING_TEST_CONNECTED\",\"message\":\"test event message\"}" +
                "]" +
            "}";

        TestExporterOptionsConfig options = read(text);

        assertThat(options.filter, not(nullValue()));
        assertThat(options.filter.exclude.size(), equalTo(3));
        assertThat(options.filter.exclude.get(0).qName, equalTo("engine:events"));
        assertThat(options.filter.exclude.get(0).id, nullValue());
        assertThat(options.filter.exclude.get(0).name, nullValue());
        assertThat(options.filter.exclude.get(0).message, nullValue());
        assertThat(options.filter.exclude.get(1).qName, equalTo("test:net0"));
        assertThat(options.filter.exclude.get(1).id, equalTo("binding.test.connected"));
        assertThat(options.filter.exclude.get(1).name, nullValue());
        assertThat(options.filter.exclude.get(2).qName, nullValue());
        assertThat(options.filter.exclude.get(2).name, equalTo("ENGINE_STOPPED"));
        assertThat(options.filter.exclude.get(2).message, equalTo("Engine Stopped."));
        assertThat(options.events.size(), equalTo(1));
    }

    @Test
    public void shouldReadWithoutFilter()
    {
        TestExporterOptionsConfig options = read("{\"mode\":\"test42\"}");

        assertThat(options.filter, nullValue());
    }

    @Test
    public void shouldWriteFilterExclude()
    {
        TestExporterOptionsConfig options = TestExporterOptionsConfig.builder()
            .exclude("engine:events", null, null, null)
            .exclude(null, "binding.test.connected", "BINDING_TEST_CONNECTED", "test event message")
            .build();

        String text = adapter.adaptToJson(options).toString();

        assertThat(text, equalTo(
            "{\"filter\":{\"exclude\":[" +
                "{\"qname\":\"engine:events\"}," +
                "{\"id\":\"binding.test.connected\",\"name\":\"BINDING_TEST_CONNECTED\",\"message\":\"test event message\"}" +
            "]}}"));
    }

    @Test
    public void shouldWriteWithoutFilter()
    {
        TestExporterOptionsConfig options = TestExporterOptionsConfig.builder()
            .mode("test42")
            .build();

        String text = adapter.adaptToJson(options).toString();

        assertThat(text, equalTo("{\"mode\":\"test42\"}"));
    }

    @Test
    public void shouldRoundTripFilterExclude()
    {
        String text =
            "{\"filter\":{\"exclude\":[" +
                "{\"qname\":\"engine:events\"}," +
                "{\"qname\":\"test:net0\",\"id\":\"binding.test.connected\"," +
                "\"name\":\"BINDING_TEST_CONNECTED\",\"message\":\"test event message\"}" +
            "]}}";

        String actual = adapter.adaptToJson(read(text)).toString();

        assertThat(actual, equalTo(text));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectFilterWithInclude()
    {
        read("{\"filter\":{\"include\":[{\"qname\":\"engine:events\"}]}}");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectFilterWithUnknownKeyAlongsideExclude()
    {
        read("{\"filter\":{\"exclude\":[{\"qname\":\"engine:events\"}],\"include\":[{\"qname\":\"test:net0\"}]}}");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectEmptyMatcher()
    {
        read("{\"filter\":{\"exclude\":[{}]}}");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectMatcherWithUnknownKey()
    {
        read("{\"filter\":{\"exclude\":[{\"qname\":\"engine:events\",\"namespace\":\"engine\"}]}}");
    }

    private TestExporterOptionsConfig read(
        String text)
    {
        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        OptionsConfig options = adapter.adaptFromJson(object);
        return (TestExporterOptionsConfig) options;
    }
}
