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
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

import io.aklivity.zilla.config.binding.a2a.A2aProxyPushConfig;
import io.aklivity.zilla.config.binding.a2a.A2aProxyPushConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;

public final class A2aProxyPushConfigAdapter extends ConfigAdapter<A2aProxyPushConfig, JsonObject>
{
    static final String VAULT_NAME = "vault";

    private static final String URL_NAME = "url";

    private static final String KEY_NAME = "key";
    private static final String TTL_NAME = "ttl";
    private static final String TTL_DEFAULT = "PT24H";
    private static final String MAX_LENGTH_NAME = "maxLength";
    private static final int MAX_LENGTH_DEFAULT = 2048;
    private static final String ALLOW_NAME = "allow";
    private static final String EXIT_NAME = "exit";
    private static final String AUTHORIZATION_NAME = "authorization";

    private final A2aAuthorizationConfigAdapter authorization = new A2aAuthorizationConfigAdapter(null);

    @Override
    public JsonObject adaptToJson(
        A2aProxyPushConfig push)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (push.url != null)
        {
            object.add(URL_NAME, push.url);
        }

        object.add(VAULT_NAME, push.vault);
        object.add(KEY_NAME, push.key);

        if (push.ttl != null)
        {
            object.add(TTL_NAME, push.ttl.toString());
        }

        if (push.maxLength > 0)
        {
            object.add(MAX_LENGTH_NAME, push.maxLength);
        }

        if (push.allow != null)
        {
            JsonArrayBuilder allow = Json.createArrayBuilder();
            push.allow.forEach(allow::add);
            object.add(ALLOW_NAME, allow);
        }

        object.add(EXIT_NAME, push.exit);

        if (push.authorization != null)
        {
            object.add(AUTHORIZATION_NAME, authorization.adaptToJson(push.authorization));
        }

        return object.build();
    }

    @Override
    public A2aProxyPushConfig adaptFromJson(
        JsonObject object)
    {
        A2aProxyPushConfigBuilder<A2aProxyPushConfig> builder = A2aProxyPushConfig.builder()
            .vault(object.getString(VAULT_NAME))
            .key(object.getString(KEY_NAME))
            .ttl(Duration.parse(object.getString(TTL_NAME, TTL_DEFAULT)))
            .maxLength(object.getInt(MAX_LENGTH_NAME, MAX_LENGTH_DEFAULT))
            .exit(object.getString(EXIT_NAME));

        if (object.containsKey(URL_NAME))
        {
            builder.url(object.getString(URL_NAME));
        }

        if (object.containsKey(ALLOW_NAME))
        {
            List<String> allow = object.getJsonArray(ALLOW_NAME).getValuesAs(JsonString.class).stream()
                .map(JsonString::getString)
                .toList();
            builder.allow(allow);
        }

        if (object.containsKey(AUTHORIZATION_NAME))
        {
            builder.authorization(authorization.adaptFromJson(object.getJsonObject(AUTHORIZATION_NAME)));
        }

        return builder.build();
    }
}
