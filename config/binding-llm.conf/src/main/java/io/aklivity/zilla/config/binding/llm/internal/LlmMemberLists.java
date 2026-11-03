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
package io.aklivity.zilla.config.binding.llm.internal;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonString;

final class LlmMemberLists
{
    static JsonArrayBuilder unsupportedToJson(
        List<String> unsupported)
    {
        JsonArrayBuilder array = Json.createArrayBuilder();
        unsupported.forEach(array::add);
        return array;
    }

    static List<String> unsupportedFromJson(
        JsonArray array)
    {
        return array.getValuesAs(JsonString.class).stream()
            .map(JsonString::getString)
            .toList();
    }

    static JsonArrayBuilder exclusiveToJson(
        List<List<String>> exclusive)
    {
        JsonArrayBuilder array = Json.createArrayBuilder();
        exclusive.forEach(group -> array.add(unsupportedToJson(group)));
        return array;
    }

    static List<List<String>> exclusiveFromJson(
        JsonArray array)
    {
        return array.getValuesAs(JsonArray.class).stream()
            .map(LlmMemberLists::unsupportedFromJson)
            .toList();
    }

    private LlmMemberLists()
    {
    }
}
