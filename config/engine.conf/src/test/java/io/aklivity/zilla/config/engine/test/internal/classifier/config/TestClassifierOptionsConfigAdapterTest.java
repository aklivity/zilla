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
package io.aklivity.zilla.config.engine.test.internal.classifier.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.Test;

public class TestClassifierOptionsConfigAdapterTest
{
    private final TestClassifierOptionsConfigAdapter adapter = new TestClassifierOptionsConfigAdapter();

    @Test
    public void shouldReadDefaults()
    {
        TestClassifierOptionsConfig options = (TestClassifierOptionsConfig) adapter.adaptFromJson(
            Json.createObjectBuilder().build());

        assertThat(options.failure, is(false));
        assertThat(options.failureOn, empty());
        assertThat(options.delay, equalTo(0));
    }

    @Test
    public void shouldReadOptions()
    {
        JsonObject object = Json.createObjectBuilder()
            .add("failure", true)
            .add("failure-on", Json.createArrayBuilder().add("boom").add("bang"))
            .add("delay", 3)
            .build();

        TestClassifierOptionsConfig options = (TestClassifierOptionsConfig) adapter.adaptFromJson(object);

        assertThat(options.failure, is(true));
        assertThat(options.failureOn, contains("boom", "bang"));
        assertThat(options.delay, equalTo(3));
    }

    @Test
    public void shouldWriteOptions()
    {
        JsonObject object = Json.createObjectBuilder()
            .add("failure", true)
            .add("failure-on", Json.createArrayBuilder().add("boom"))
            .add("delay", 3)
            .build();

        assertThat(adapter.adaptToJson(adapter.adaptFromJson(object)), equalTo(object));
    }

    @Test
    public void shouldWriteNoOptionsForDefaults()
    {
        assertThat(adapter.adaptToJson(TestClassifierOptionsConfig.builder().build()),
            equalTo(Json.createObjectBuilder().build()));
    }
}
