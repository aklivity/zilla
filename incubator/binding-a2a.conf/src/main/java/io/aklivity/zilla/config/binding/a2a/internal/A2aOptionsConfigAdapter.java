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
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

import io.aklivity.zilla.config.binding.a2a.A2aOptionsConfig;
import io.aklivity.zilla.config.binding.a2a.A2aOptionsConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class A2aOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String URL_NAME = "url";
    private static final String SERVER_NAME = "server";
    private static final String VERSIONS_NAME = "versions";
    private static final String PUSH_NAME = "push";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_CREDENTIALS_DEFAULT = "Bearer {credentials}";
    private static final String CACHE_NAME = "cache";
    private static final String DISCOVERY_NAME = "discovery";

    private final A2aServerPushConfigAdapter serverPush = new A2aServerPushConfigAdapter();
    private final A2aAuthorizationConfigAdapter authorization =
        new A2aAuthorizationConfigAdapter(AUTHORIZATION_CREDENTIALS_DEFAULT);
    private final A2aCacheConfigAdapter cache = new A2aCacheConfigAdapter();
    private final A2aDiscoveryConfigAdapter discovery = new A2aDiscoveryConfigAdapter();
    private final A2aProxyPushConfigAdapter proxyPush = new A2aProxyPushConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        A2aOptionsConfig a2aOptions = (A2aOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (a2aOptions.url != null)
        {
            object.add(URL_NAME, a2aOptions.url);
        }

        if (a2aOptions.server != null)
        {
            object.add(SERVER_NAME, a2aOptions.server);
        }

        if (a2aOptions.versions != null)
        {
            JsonArrayBuilder versions = Json.createArrayBuilder();
            a2aOptions.versions.forEach(versions::add);
            object.add(VERSIONS_NAME, versions);
        }

        if (a2aOptions.serverPush != null)
        {
            object.add(PUSH_NAME, serverPush.adaptToJson(a2aOptions.serverPush));
        }

        if (a2aOptions.authorization != null)
        {
            object.add(AUTHORIZATION_NAME, authorization.adaptToJson(a2aOptions.authorization));
        }

        if (a2aOptions.cache != null)
        {
            object.add(CACHE_NAME, cache.adaptToJson(a2aOptions.cache));
        }

        if (a2aOptions.discovery != null)
        {
            object.add(DISCOVERY_NAME, discovery.adaptToJson(a2aOptions.discovery));
        }

        if (a2aOptions.proxyPush != null)
        {
            object.add(PUSH_NAME, proxyPush.adaptToJson(a2aOptions.proxyPush));
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        A2aOptionsConfigBuilder<A2aOptionsConfig> builder = A2aOptionsConfig.builder();

        if (object.containsKey(URL_NAME))
        {
            builder.url(object.getString(URL_NAME));
        }

        if (object.containsKey(SERVER_NAME))
        {
            builder.server(object.getString(SERVER_NAME));
        }

        if (object.containsKey(VERSIONS_NAME))
        {
            builder.versions(object.getJsonArray(VERSIONS_NAME).getValuesAs(JsonString.class).stream()
                .map(JsonString::getString)
                .toList());
        }

        if (object.containsKey(PUSH_NAME))
        {
            JsonObject push = object.getJsonObject(PUSH_NAME);
            if (push.containsKey(A2aProxyPushConfigAdapter.VAULT_NAME))
            {
                builder.proxyPush(proxyPush.adaptFromJson(push));
            }
            else
            {
                builder.serverPush(serverPush.adaptFromJson(push));
            }
        }

        if (object.containsKey(AUTHORIZATION_NAME))
        {
            builder.authorization(authorization.adaptFromJson(object.getJsonObject(AUTHORIZATION_NAME)));
        }

        if (object.containsKey(CACHE_NAME))
        {
            builder.cache(cache.adaptFromJson(object.getJsonObject(CACHE_NAME)));
        }

        if (object.containsKey(DISCOVERY_NAME))
        {
            builder.discovery(discovery.adaptFromJson(object.getJsonObject(DISCOVERY_NAME)));
        }

        return builder.build();
    }
}
