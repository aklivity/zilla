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
