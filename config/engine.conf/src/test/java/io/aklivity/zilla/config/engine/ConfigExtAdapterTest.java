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

import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.junit.Test;

public class ConfigExtAdapterTest
{
    private final ConfigExtAdapter<TestRootConfig> adapter =
        new ConfigExtAdapter<>(Map.of(TestExtConfig.NAME, new TestExtConfigAdapter()));

    private final ConfigExtAdapter<TestRootConfig> itemAdapter =
        new ConfigExtAdapter<>(Map.of(), Map.of(), Map.of(TestExtConfig.NAME, new TestExtConfigAdapter()));

    @Test
    public void shouldAdaptToJsonWhenExtensionPresent()
    {
        TestRootConfig config = new TestRootConfigBuilder()
            .name("root")
            .ext(TestExtConfigBuilder::new)
                .value("value0")
                .build()
            .build();

        JsonObjectBuilder object = Json.createObjectBuilder();
        adapter.adaptToJson(config, object);

        assertThat(object.build().getJsonObject(TestExtConfig.NAME).getString("value"), equalTo("value0"));
    }

    @Test
    public void shouldSkipAdaptToJsonWhenExtensionAbsent()
    {
        TestRootConfig config = new TestRootConfigBuilder().name("root").build();

        JsonObjectBuilder object = Json.createObjectBuilder();
        adapter.adaptToJson(config, object);

        assertThat(object.build().containsKey(TestExtConfig.NAME), equalTo(false));
    }

    @Test
    public void shouldAdaptFromJsonWhenExtensionPresent()
    {
        JsonObject object = Json.createObjectBuilder()
            .add(TestExtConfig.NAME, Json.createObjectBuilder().add("value", "value0"))
            .build();

        TestRootConfig config = adapter.adaptFromJson(object, new TestRootConfigBuilder().name("root")).build();

        assertThat(config.ext(TestExtConfig.NAME, TestExtConfig.class).value, equalTo("value0"));
    }

    @Test
    public void shouldSkipAdaptFromJsonWhenExtensionAbsent()
    {
        JsonObject object = Json.createObjectBuilder().build();

        TestRootConfig config = adapter.adaptFromJson(object, new TestRootConfigBuilder().name("root")).build();

        assertThat(config.ext(TestExtConfig.NAME, TestExtConfig.class), nullValue());
    }

    @Test
    public void shouldAdaptFromJsonForSubtypeBuilder()
    {
        JsonObject object = Json.createObjectBuilder()
            .add(TestExtConfig.NAME, Json.createObjectBuilder().add("value", "value0"))
            .build();

        TestLeafConfig config = adapter.adaptFromJson(object, new TestLeafConfigBuilder().name("leaf")).build();

        assertThat(config.ext(TestExtConfig.NAME, TestExtConfig.class).value, equalTo("value0"));
    }

    @Test
    public void shouldAdaptItemToJsonWhenTypeMatches()
    {
        JsonObject object = itemAdapter.adaptItemToJson(TestExtConfig.NAME, new TestExtConfig("value0"));

        assertThat(object, not(nullValue()));
        assertThat(object.getString("value"), equalTo("value0"));
    }

    @Test
    public void shouldReturnNullAdaptingItemToJsonWhenTypeNotHandled()
    {
        JsonObject object = itemAdapter.adaptItemToJson("other", new TestExtConfig("value0"));

        assertThat(object, nullValue());
    }

    @Test
    public void shouldAdaptItemFromJsonWhenTypeMatches()
    {
        JsonObject object = Json.createObjectBuilder().add("value", "value0").build();

        TestExtConfig config = (TestExtConfig) itemAdapter.adaptItemFromJson(TestExtConfig.NAME, object);

        assertThat(config, not(nullValue()));
        assertThat(config.value, equalTo("value0"));
    }

    @Test
    public void shouldReturnNullAdaptingItemFromJsonWhenTypeNotHandled()
    {
        JsonObject object = Json.createObjectBuilder().add("value", "value0").build();

        assertThat(itemAdapter.adaptItemFromJson("other", object), nullValue());
    }
}
