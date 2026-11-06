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

import java.time.Duration;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.binding.a2a.A2aCacheConfig;
import io.aklivity.zilla.config.binding.a2a.A2aCacheConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;

public final class A2aCacheConfigAdapter extends ConfigAdapter<A2aCacheConfig, JsonObject>
{
    private static final String STORE_NAME = "store";
    private static final String TTL_NAME = "ttl";
    private static final String TTL_DEFAULT = "PT5M";
    private static final String AUTHORIZATION_NAME = "authorization";

    private final A2aAuthorizationConfigAdapter authorization = new A2aAuthorizationConfigAdapter(null);

    @Override
    public JsonObject adaptToJson(
        A2aCacheConfig cache)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(STORE_NAME, cache.store);

        if (cache.ttl != null)
        {
            object.add(TTL_NAME, cache.ttl.toString());
        }

        if (cache.authorization != null)
        {
            object.add(AUTHORIZATION_NAME, authorization.adaptToJson(cache.authorization));
        }

        return object.build();
    }

    @Override
    public A2aCacheConfig adaptFromJson(
        JsonObject object)
    {
        A2aCacheConfigBuilder<A2aCacheConfig> builder = A2aCacheConfig.builder()
            .store(object.getString(STORE_NAME))
            .ttl(Duration.parse(object.getString(TTL_NAME, TTL_DEFAULT)));

        if (object.containsKey(AUTHORIZATION_NAME))
        {
            builder.authorization(authorization.adaptFromJson(object.getJsonObject(AUTHORIZATION_NAME)));
        }

        return builder.build();
    }
}
