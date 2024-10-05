/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.serializer;

import java.io.StringReader;
import java.util.stream.IntStream;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonReader;
import jakarta.json.stream.JsonParsingException;

public class UnregisterSchemaRequest
{
    public static final int[] NO_VERSIONS = new int[0];

    public int[] resolveResponse(
        String response)
    {
        try
        {
            JsonReader reader = Json.createReader(new StringReader(response));
            JsonArray array = reader.readArray();

            return IntStream.range(0, array.size())
                .map(array::getInt)
                .toArray();
        }
        catch (JsonParsingException ex)
        {
            return NO_VERSIONS;
        }
    }
}
