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
package io.aklivity.zilla.runtime.common.asyncapi.config;

import java.io.StringReader;
import java.io.StringWriter;

import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonWriter;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import io.aklivity.zilla.runtime.common.asyncapi.model.Asyncapi;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class AsyncapiWriter
{
    private final Jsonb jsonb;

    public AsyncapiWriter()
    {
        this.jsonb = JsonbBuilder.newBuilder()
            .withConfig(new JsonbConfig().withNullValues(false))
            .build();
    }

    public String write(
        Asyncapi asyncapi)
    {
        String json = jsonb.toJson(asyncapi);

        JsonObject object;
        try (JsonReader reader = YamlJson.createReader(new StringReader(json)))
        {
            object = reader.readObject();
        }

        StringWriter writer = new StringWriter();
        try (JsonWriter yaml = YamlJson.createWriter(writer))
        {
            yaml.writeObject(object);
        }

        return writer.toString();
    }
}
