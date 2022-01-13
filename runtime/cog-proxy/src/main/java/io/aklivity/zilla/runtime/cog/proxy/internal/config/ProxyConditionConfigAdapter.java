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

import io.aklivity.zilla.runtime.cog.proxy.internal.ProxyBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class ProxyConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String TRANSPORT_NAME = "transport";
    private static final String FAMILY_NAME = "family";
    private static final String SOURCE_NAME = "source";
    private static final String DESTINATION_NAME = "destination";
    private static final String INFO_NAME = "info";

    private final ProxyAddressConfigAdapter address = new ProxyAddressConfigAdapter();
    private final ProxyInfoConfigAdapter info = new ProxyInfoConfigAdapter();

    @Override
    public String type()
    {
        return ProxyBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        ProxyConditionConfig proxy = (ProxyConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (proxy.transport != null)
        {
            object.add(TRANSPORT_NAME, proxy.transport);
        }

        if (proxy.family != null)
        {
            object.add(FAMILY_NAME, proxy.family);
        }

        if (proxy.source != null)
        {
            object.add(SOURCE_NAME, address.adaptToJson(proxy.source));
        }

        if (proxy.destination != null)
        {
            object.add(DESTINATION_NAME, address.adaptToJson(proxy.destination));
        }

        if (proxy.info != null)
        {
            object.add(INFO_NAME, info.adaptToJson(proxy.info));
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String transport = object.containsKey(TRANSPORT_NAME) ? object.getString(TRANSPORT_NAME) : null;
        String family = object.containsKey(FAMILY_NAME) ? object.getString(FAMILY_NAME) : null;
        ProxyAddressConfig source =
                object.containsKey(SOURCE_NAME) ? address.adaptFromJson(object.getJsonObject(SOURCE_NAME)) : null;
        ProxyAddressConfig destination =
                object.containsKey(DESTINATION_NAME) ? address.adaptFromJson(object.getJsonObject(DESTINATION_NAME)) : null;
        ProxyInfoConfig info =
                object.containsKey(INFO_NAME) ? this.info.adaptFromJson(object.getJsonObject(INFO_NAME)) : null;

        return new ProxyConditionConfig(transport, family, source, destination, info);
    }
}
