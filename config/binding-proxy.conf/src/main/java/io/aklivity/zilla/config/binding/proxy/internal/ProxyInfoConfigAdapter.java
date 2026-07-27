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

import static org.agrona.BitUtil.fromHex;
import static org.agrona.BitUtil.toHex;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.proxy.ProxyInfoConfig;
import io.aklivity.zilla.config.binding.proxy.ProxyInfoConfigBuilder;

public final class ProxyInfoConfigAdapter implements JsonbAdapter<ProxyInfoConfig, JsonObject>
{
    private static final String ALPN_NAME = "alpn";
    private static final String AUTHORITY_NAME = "authority";
    private static final String IDENTITY_NAME = "identity";
    private static final String NAMESPACE_NAME = "namespace";
    private static final String SECURE_NAME = "secure";

    private final ProxySecureInfoConfigAdapter secureInfo = new ProxySecureInfoConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        ProxyInfoConfig address)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (address.alpn != null)
        {
            object.add(ALPN_NAME, address.alpn);
        }

        if (address.authority != null)
        {
            object.add(AUTHORITY_NAME, address.authority);
        }

        if (address.identity != null)
        {
            object.add(IDENTITY_NAME, toHex(address.identity));
        }

        if (address.namespace != null)
        {
            object.add(NAMESPACE_NAME, address.namespace);
        }

        if (address.secure != null)
        {
            object.add(SECURE_NAME, secureInfo.adaptToJson(address.secure));
        }

        return object.build();
    }

    @Override
    public ProxyInfoConfig adaptFromJson(
        JsonObject object)
    {
        ProxyInfoConfigBuilder<ProxyInfoConfig> info = ProxyInfoConfig.builder();

        if (object.containsKey(ALPN_NAME))
        {
            info.alpn(object.getString(ALPN_NAME));
        }

        if (object.containsKey(AUTHORITY_NAME))
        {
            info.authority(object.getString(AUTHORITY_NAME));
        }

        if (object.containsKey(IDENTITY_NAME))
        {
            info.identity(fromHex(object.getString(IDENTITY_NAME)));
        }

        if (object.containsKey(NAMESPACE_NAME))
        {
            info.namespace(object.getString(NAMESPACE_NAME));
        }

        if (object.containsKey(SECURE_NAME))
        {
            info.secure(secureInfo.adaptFromJson(object.getJsonObject(SECURE_NAME)));
        }

        return info.build();
    }
}
