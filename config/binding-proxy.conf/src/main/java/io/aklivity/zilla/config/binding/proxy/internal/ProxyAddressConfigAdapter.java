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
package io.aklivity.zilla.config.binding.proxy.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.proxy.ProxyAddressConfig;
import io.aklivity.zilla.config.binding.proxy.ProxyAddressConfigBuilder;

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
        ProxyAddressConfigBuilder<ProxyAddressConfig> address = ProxyAddressConfig.builder();

        if (object.containsKey(HOST_NAME))
        {
            address.host(object.getString(HOST_NAME));
        }

        if (object.containsKey(PORT_NAME))
        {
            address.port(object.getInt(PORT_NAME));
        }

        return address.build();
    }
}
