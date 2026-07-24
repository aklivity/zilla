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

import io.aklivity.zilla.config.binding.proxy.ProxyConditionConfig;
import io.aklivity.zilla.config.binding.proxy.ProxyConditionConfigBuilder;
import io.aklivity.zilla.config.engine.ConditionConfig;

public final class ProxyConditionConfigAdapter implements JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String TRANSPORT_NAME = "transport";
    private static final String FAMILY_NAME = "family";
    private static final String SOURCE_NAME = "source";
    private static final String DESTINATION_NAME = "destination";
    private static final String INFO_NAME = "info";

    private final ProxyAddressConfigAdapter address = new ProxyAddressConfigAdapter();
    private final ProxyInfoConfigAdapter info = new ProxyInfoConfigAdapter();

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
        ProxyConditionConfigBuilder<ProxyConditionConfig> condition = ProxyConditionConfig.builder();

        if (object.containsKey(TRANSPORT_NAME))
        {
            condition.transport(object.getString(TRANSPORT_NAME));
        }

        if (object.containsKey(FAMILY_NAME))
        {
            condition.family(object.getString(FAMILY_NAME));
        }

        if (object.containsKey(SOURCE_NAME))
        {
            condition.source(address.adaptFromJson(object.getJsonObject(SOURCE_NAME)));
        }

        if (object.containsKey(DESTINATION_NAME))
        {
            condition.destination(address.adaptFromJson(object.getJsonObject(DESTINATION_NAME)));
        }

        if (object.containsKey(INFO_NAME))
        {
            condition.info(info.adaptFromJson(object.getJsonObject(INFO_NAME)));
        }

        return condition.build();
    }
}
