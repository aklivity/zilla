/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.ws.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.ws.config.WsConditionConfig;
import io.aklivity.zilla.runtime.binding.ws.internal.WsBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class WsConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String PROTOCOL_NAME = "protocol";
    private static final String SCHEME_NAME = "scheme";
    private static final String AUTHORITY_NAME = "authority";
    private static final String PATH_NAME = "path";

    @Override
    public String type()
    {
        return WsBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        WsConditionConfig wsCondition = (WsConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (wsCondition.protocol != null)
        {
            object.add(PROTOCOL_NAME, wsCondition.protocol);
        }

        if (wsCondition.scheme != null)
        {
            object.add(SCHEME_NAME, wsCondition.scheme);
        }

        if (wsCondition.authority != null)
        {
            object.add(AUTHORITY_NAME, wsCondition.authority);
        }

        if (wsCondition.path != null)
        {
            object.add(PATH_NAME, wsCondition.path);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String protocol = object.containsKey(PROTOCOL_NAME)
                ? object.getString(PROTOCOL_NAME)
                : null;

        String scheme = object.containsKey(SCHEME_NAME)
                ? object.getString(SCHEME_NAME)
                : null;

        String authority = object.containsKey(AUTHORITY_NAME)
                ? object.getString(AUTHORITY_NAME)
                : null;

        String path = object.containsKey(PATH_NAME)
                ? object.getString(PATH_NAME)
                : null;

        return new WsConditionConfig(protocol, scheme, authority, path);
    }
}
