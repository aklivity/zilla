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

import static org.agrona.BitUtil.fromHex;
import static org.agrona.BitUtil.toHex;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

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
        String alpn = object.containsKey(ALPN_NAME) ? object.getString(ALPN_NAME) : null;
        String authority = object.containsKey(AUTHORITY_NAME) ? object.getString(AUTHORITY_NAME) : null;
        byte[] identity = object.containsKey(IDENTITY_NAME) ? fromHex(object.getString(IDENTITY_NAME)) : null;
        String namespace = object.containsKey(NAMESPACE_NAME) ? object.getString(NAMESPACE_NAME) : null;
        ProxySecureInfoConfig secure =
                object.containsKey(SECURE_NAME) ? secureInfo.adaptFromJson(object.getJsonObject(SECURE_NAME)) : null;

        return new ProxyInfoConfig(alpn, authority, identity, namespace, secure);
    }
}
