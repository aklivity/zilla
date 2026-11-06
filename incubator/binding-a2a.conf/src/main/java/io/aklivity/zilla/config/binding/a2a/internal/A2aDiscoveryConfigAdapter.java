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
package io.aklivity.zilla.config.binding.a2a.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.binding.a2a.A2aDiscoveryConfig;
import io.aklivity.zilla.config.binding.a2a.A2aDiscoveryConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;

public final class A2aDiscoveryConfigAdapter extends ConfigAdapter<A2aDiscoveryConfig, JsonObject>
{
    private static final String AGENT_NAME = "agent";
    private static final String AGENT_DEFAULT = "discovery";
    private static final String SEARCH_NAME = "search";

    private final A2aDiscoverySearchConfigAdapter search = new A2aDiscoverySearchConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        A2aDiscoveryConfig discovery)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (discovery.agent != null)
        {
            object.add(AGENT_NAME, discovery.agent);
        }

        if (discovery.search != null)
        {
            object.add(SEARCH_NAME, search.adaptToJson(discovery.search));
        }

        return object.build();
    }

    @Override
    public A2aDiscoveryConfig adaptFromJson(
        JsonObject object)
    {
        A2aDiscoveryConfigBuilder<A2aDiscoveryConfig> builder = A2aDiscoveryConfig.builder()
            .agent(object.getString(AGENT_NAME, AGENT_DEFAULT));

        if (object.containsKey(SEARCH_NAME))
        {
            builder.search(search.adaptFromJson(object.getJsonObject(SEARCH_NAME)));
        }

        return builder.build();
    }
}
