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
package io.aklivity.zilla.runtime.common.json;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.junit.jupiter.api.Test;

class JsonStringsTest
{
    @Test
    void shouldReturnNullWhenNameAbsent()
    {
        JsonObject object = Json.createObjectBuilder().build();

        assertNull(JsonStrings.asStringOrArray(object, "tool"));
    }

    @Test
    void shouldReadBareStringAsSingletonList()
    {
        JsonObject object = Json.createObjectBuilder()
            .add("tool", "produce")
            .build();

        assertEquals(List.of("produce"), JsonStrings.asStringOrArray(object, "tool"));
    }

    @Test
    void shouldReadArrayAsList()
    {
        JsonObject object = Json.createObjectBuilder()
            .add("tool", Json.createArrayBuilder().add("produce").add("consume"))
            .build();

        assertEquals(List.of("produce", "consume"), JsonStrings.asStringOrArray(object, "tool"));
    }

    @Test
    void shouldNotAddAnythingWhenValuesNull()
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        JsonStrings.addStringOrArray(builder, "tool", null);

        assertEquals(Json.createObjectBuilder().build(), builder.build());
    }

    @Test
    void shouldWriteEmptyListAsEmptyArray()
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        JsonStrings.addStringOrArray(builder, "tool", List.of());

        JsonObject expected = Json.createObjectBuilder().add("tool", Json.createArrayBuilder()).build();
        assertEquals(expected, builder.build());
    }

    @Test
    void shouldWriteSingleValueAsBareString()
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        JsonStrings.addStringOrArray(builder, "tool", List.of("produce"));

        JsonObject expected = Json.createObjectBuilder().add("tool", "produce").build();
        assertEquals(expected, builder.build());
    }

    @Test
    void shouldWriteMultipleValuesAsArray()
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        JsonStrings.addStringOrArray(builder, "tool", asList("produce", "consume"));

        JsonObject expected = Json.createObjectBuilder()
            .add("tool", Json.createArrayBuilder().add("produce").add("consume"))
            .build();
        assertEquals(expected, builder.build());
    }
}
