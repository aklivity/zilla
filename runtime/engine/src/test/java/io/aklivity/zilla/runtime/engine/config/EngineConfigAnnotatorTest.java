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
package io.aklivity.zilla.runtime.engine.config;

import static org.junit.Assert.assertTrue;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.junit.Test;

public class EngineConfigAnnotatorTest
{
    @Test
    public void shouldAnnotateJsonSchema()
    {
        final String json = new String("{" +
            "    \"title\": \"Port\"," +
            "    \"oneOf\":" +
            "    [" +
            "        {" +
            "            \"type\": \"integer\"" +
            "        }," +
            "        {" +
            "            \"type\": \"string\"," +
            "            \"pattern\": \"^\\\\d+(-\\\\d+)?$\"" +
            "        }" +
            "    ]" +
            "}");
        try (JsonReader jsonReader = Json.createReader(new StringReader(json)))
        {
            JsonObject jsonObject = jsonReader.readObject();
            EngineConfigAnnotator annotator = new EngineConfigAnnotator();
            final JsonObject annotated = annotator.annotate(jsonObject);

            JsonArray jsonArray = annotator.annotate(annotated).getJsonArray("oneOf");

            assertTrue(jsonArray.get(0).asJsonObject().asJsonObject().getJsonArray("anyOf").size() == 2);
            assertTrue(jsonArray.get(1).asJsonObject().asJsonObject().getJsonArray("anyOf").size() == 1);

        }
    }
}
