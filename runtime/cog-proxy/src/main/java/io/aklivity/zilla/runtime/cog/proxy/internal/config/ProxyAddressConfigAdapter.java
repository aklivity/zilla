/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.proxy.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

public final class ProxyAddressConfigAdapter implements JsonbAdapter<ProxyAddressConfig, JsonObject>
{
    private static final String HOST_NAME = "host";
    private static final String PORT_NAME = "port";

    @Override
    public JsonObject adaptToJson(
        ProxyAddressConfig address)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (address.host != null)
        {
            object.add(HOST_NAME, address.host);
        }

        if (address.port != null)
        {
            object.add(PORT_NAME, address.port);
        }

        return object.build();
    }

    @Override
    public ProxyAddressConfig adaptFromJson(
        JsonObject object)
    {
        String host = object.containsKey(HOST_NAME) ? object.getString(HOST_NAME) : null;
        Integer port = object.containsKey(PORT_NAME) ? object.getInt(PORT_NAME) : null;

        return new ProxyAddressConfig(host, port);
    }
}
