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

import java.io.StringReader;
import java.net.URL;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonPatch;
import jakarta.json.spi.JsonProvider;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.common.feature.FeatureFilter;
import io.aklivity.zilla.runtime.common.json.JsonSchema;

public class EngineConfigReaderTest
{
    private EngineConfigReader reader;

    @Before
    public void initReader()
    {
        reader = new EngineConfigReader(
            text -> text, new EngineInfo(), EngineConfigReaderTest::noop, EngineConfigReaderTest::noop);
    }

    @Test
    public void shouldStripTopLevelIncubatingPropertyAndCleanRequired()
    {
        JsonObject schema = Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", Json.createObjectBuilder()
                .add("stable", Json.createObjectBuilder().add("type", "string"))
                .add("incubating", Json.createObjectBuilder().add("type", "string").add("x-incubating", true)))
            .add("required", Json.createArrayBuilder().add("stable").add("incubating"))
            .build();

        JsonObject stripped = reader.stripIncubatingSchema(schema);

        assertThat(stripped.getJsonObject("properties").containsKey("incubating"), equalTo(false));
        assertThat(stripped.getJsonObject("properties").containsKey("stable"), equalTo(true));
        assertThat(stripped.getJsonArray("required"), equalTo(Json.createArrayBuilder().add("stable").build()));
    }

    @Test
    public void shouldStripNestedDefsIncubatingEntry()
    {
        JsonObject schema = Json.createObjectBuilder()
            .add("$defs", Json.createObjectBuilder()
                .add("stable-entry", Json.createObjectBuilder().add("type", "string"))
                .add("incubating-entry", Json.createObjectBuilder().add("type", "string").add("x-incubating", true)))
            .build();

        JsonObject stripped = reader.stripIncubatingSchema(schema);

        assertThat(stripped.getJsonObject("$defs").containsKey("incubating-entry"), equalTo(false));
        assertThat(stripped.getJsonObject("$defs").containsKey("stable-entry"), equalTo(true));
    }

    @Test
    public void shouldLeaveSchemaWithoutIncubatingUnchanged()
    {
        JsonObject schema = Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", Json.createObjectBuilder()
                .add("stable", Json.createObjectBuilder().add("type", "string")))
            .add("required", Json.createArrayBuilder().add("stable"))
            .build();

        JsonObject stripped = reader.stripIncubatingSchema(schema);

        assertThat(stripped, equalTo(schema));
    }

    @Test
    public void shouldDispatchStripIncubatingAccordingToIncubatorMode()
    {
        JsonObject schema = Json.createObjectBuilder()
            .add("properties", Json.createObjectBuilder()
                .add("incubating", Json.createObjectBuilder().add("type", "string").add("x-incubating", true)))
            .build();

        JsonObject result = reader.stripIncubating(schema);

        if (FeatureFilter.isIncubatorEnabled())
        {
            assertThat(result, equalTo(schema));
        }
        else
        {
            assertThat(result, not(equalTo(schema)));
        }
    }

    @Test
    public void shouldValidateIncubatingPropertyAccordingToIncubatorMode() throws Exception
    {
        JsonProvider schemaProvider = JsonProvider.provider();
        URL schemaUrl = new EngineInfo().schema();
        JsonObject schemaObject = schemaProvider.createReader(schemaUrl.openStream()).readObject();

        for (URL patchUrl : new EngineInfo().patches())
        {
            JsonArray patchArray = schemaProvider.createReader(patchUrl.openStream()).readArray();
            schemaObject = schemaProvider.createPatch(patchArray).apply(schemaObject);
        }

        JsonPatch addIncubatingProperty = Json.createPatchBuilder()
            .add("/properties/x-incubating-test", Json.createObjectBuilder()
                .add("type", "string")
                .add("x-incubating", true)
                .build())
            .build();
        schemaObject = addIncubatingProperty.apply(schemaObject);

        String document = "{\"name\":\"test\",\"x-incubating-test\":\"enabled\"}";

        JsonSchema unstrippedSchema = JsonSchema.of(schemaObject.toString());
        assertThat(unstrippedSchema.validate(schemaProvider.createParser(new StringReader(document))), equalTo(true));

        JsonSchema strippedSchema = JsonSchema.of(reader.stripIncubatingSchema(schemaObject).toString());
        assertThat(strippedSchema.validate(schemaProvider.createParser(new StringReader(document))), equalTo(false));
    }

    private static void noop(
        String value)
    {
    }
}
