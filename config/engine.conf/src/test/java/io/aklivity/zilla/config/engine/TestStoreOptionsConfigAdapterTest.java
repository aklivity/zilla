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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.io.StringReader;
import java.time.Duration;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.Test;

import io.aklivity.zilla.config.engine.test.internal.store.config.TestStoreOptionsConfig;
import io.aklivity.zilla.config.engine.test.internal.store.config.TestStoreOptionsConfig.Assertion;
import io.aklivity.zilla.config.engine.test.internal.store.config.TestStoreOptionsConfigAdapter;

public class TestStoreOptionsConfigAdapterTest
{
    private final TestStoreOptionsConfigAdapter adapter = new TestStoreOptionsConfigAdapter();

    @Test
    public void shouldReadAssertions()
    {
        String text =
            "{" +
                "\"assertions\":" +
                "[" +
                    "{\"op\": \"put\", \"key\": \"key\", \"value\": \"value\", \"ttl\": \"PT1M\"}," +
                    "{\"op\": \"delete\", \"key\": \"key\"}" +
                "]" +
            "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        TestStoreOptionsConfig options = (TestStoreOptionsConfig) adapter.adaptFromJson(object);

        assertThat(options.entries, nullValue());
        assertThat(options.assertions, hasSize(2));
        Assertion put = options.assertions.get(0);
        assertThat(put.op, equalTo("put"));
        assertThat(put.key, equalTo("key"));
        assertThat(put.value, equalTo("value"));
        assertThat(put.ttl, equalTo(Duration.ofMinutes(1)));
        Assertion delete = options.assertions.get(1);
        assertThat(delete.op, equalTo("delete"));
        assertThat(delete.value, nullValue());
        assertThat(delete.ttl, nullValue());
    }

    @Test
    public void shouldReadEmptyAssertions()
    {
        JsonObject object = Json.createReader(new StringReader("{\"assertions\": []}")).readObject();
        TestStoreOptionsConfig options = (TestStoreOptionsConfig) adapter.adaptFromJson(object);

        assertThat(options.assertions, empty());
    }

    @Test
    public void shouldReadNoAssertions()
    {
        JsonObject object = Json.createReader(new StringReader("{}")).readObject();
        TestStoreOptionsConfig options = (TestStoreOptionsConfig) adapter.adaptFromJson(object);

        assertThat(options.assertions, nullValue());
    }

    @Test
    public void shouldWriteAssertions()
    {
        TestStoreOptionsConfig options = TestStoreOptionsConfig.builder()
            .entry("seed", "value")
            .assertion("put", "key", "value", Duration.ofMinutes(1))
            .assertion("delete", "key", null, null)
            .build();

        JsonObject object = adapter.adaptToJson(options);

        assertThat(object.toString(), equalTo(
            "{\"entries\":{\"seed\":\"value\"}," +
            "\"assertions\":[" +
                "{\"op\":\"put\",\"key\":\"key\",\"value\":\"value\",\"ttl\":\"PT1M\"}," +
                "{\"op\":\"delete\",\"key\":\"key\"}]}"));
    }
}
