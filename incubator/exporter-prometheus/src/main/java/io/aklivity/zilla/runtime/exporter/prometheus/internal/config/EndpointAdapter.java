/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.prometheus.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

public class EndpointAdapter implements JsonbAdapter<EndpointConfig, JsonObject>
{
    private static final String SCHEME_NAME = "scheme";
    private static final String PORT_NAME = "port";
    private static final String PATH_NAME = "path";

    @Override
    public JsonObject adaptToJson(
        EndpointConfig endpoint)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        if (endpoint.scheme != null)
        {
            object.add(SCHEME_NAME, endpoint.scheme);
        }
        object.add(PORT_NAME, endpoint.port);
        if (endpoint.path != null)
        {
            object.add(PATH_NAME, endpoint.path);
        }
        return object.build();
    }

    @Override
    public EndpointConfig adaptFromJson(
        JsonObject object)
    {
        String scheme = object.containsKey(SCHEME_NAME)
            ? object.getString(SCHEME_NAME)
            : null;
        int port = object.containsKey(PORT_NAME)
            ? object.getInt(PORT_NAME)
            : 0;
        String path = object.containsKey(PATH_NAME)
            ? object.getString(PATH_NAME)
            : null;
        return new EndpointConfig(scheme, port, path);
    }
}
