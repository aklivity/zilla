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
import java.util.List;

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
    public void shouldReadAtomicAssertions()
    {
        String text =
            "{" +
                "\"assertions\":" +
                "[" +
                    "{\"op\": \"increment\", \"key\": \"counter\", \"delta\": -2, \"value\": \"3\", \"ttl\": \"PT1M\"}," +
                    "{\"op\": \"replace\", \"key\": \"key\", \"expected\": \"old\", \"value\": \"new\"}," +
                    "{\"op\": \"delete\", \"keys\": [\"a\", \"b\"]}" +
                "]" +
            "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        TestStoreOptionsConfig options = (TestStoreOptionsConfig) adapter.adaptFromJson(object);

        assertThat(options.assertions, hasSize(3));
        Assertion increment = options.assertions.get(0);
        assertThat(increment.op, equalTo("increment"));
        assertThat(increment.key, equalTo("counter"));
        assertThat(increment.delta, equalTo(-2L));
        assertThat(increment.value, equalTo("3"));
        assertThat(increment.ttl, equalTo(Duration.ofMinutes(1)));
        assertThat(increment.keys, nullValue());
        assertThat(increment.expected, nullValue());
        Assertion replace = options.assertions.get(1);
        assertThat(replace.op, equalTo("replace"));
        assertThat(replace.expected, equalTo("old"));
        assertThat(replace.value, equalTo("new"));
        assertThat(replace.delta, equalTo(0L));
        Assertion delete = options.assertions.get(2);
        assertThat(delete.op, equalTo("delete"));
        assertThat(delete.key, nullValue());
        assertThat(delete.keys, equalTo(List.of("a", "b")));
    }

    @Test
    public void shouldWriteAtomicAssertions()
    {
        TestStoreOptionsConfig options = TestStoreOptionsConfig.builder()
            .assertion("increment", "counter", null, "3", null, -2L, Duration.ofMinutes(1))
            .assertion("replace", "key", null, "new", "old", 0L, null)
            .assertion("delete", null, List.of("a", "b"), null, null, 0L, null)
            .build();

        JsonObject object = adapter.adaptToJson(options);

        assertThat(object.toString(), equalTo(
            "{\"assertions\":[" +
                "{\"op\":\"increment\",\"key\":\"counter\",\"delta\":-2,\"value\":\"3\",\"ttl\":\"PT1M\"}," +
                "{\"op\":\"replace\",\"key\":\"key\",\"expected\":\"old\",\"value\":\"new\"}," +
                "{\"op\":\"delete\",\"keys\":[\"a\",\"b\"]}]}"));
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
