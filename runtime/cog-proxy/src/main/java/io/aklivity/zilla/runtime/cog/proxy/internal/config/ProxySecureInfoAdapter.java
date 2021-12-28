/*
 * Copyright 2021-2021 Aklivity Inc.
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

public final class ProxySecureInfoAdapter implements JsonbAdapter<ProxySecureInfo, JsonObject>
{
    private static final String VERSION_NAME = "version";
    private static final String CIPHER_NAME = "cipher";
    private static final String KEY_NAME = "key";
    private static final String NAME_NAME = "name";
    private static final String SIGNATURE_NAME = "signature";

    @Override
    public JsonObject adaptToJson(
        ProxySecureInfo secureInfo)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (secureInfo.version != null)
        {
            object.add(VERSION_NAME, secureInfo.version);
        }

        if (secureInfo.cipher != null)
        {
            object.add(CIPHER_NAME, secureInfo.cipher);
        }

        if (secureInfo.key != null)
        {
            object.add(KEY_NAME, secureInfo.key);
        }

        if (secureInfo.name != null)
        {
            object.add(NAME_NAME, secureInfo.name);
        }

        if (secureInfo.signature != null)
        {
            object.add(SIGNATURE_NAME, secureInfo.signature);
        }

        return object.build();
    }

    @Override
    public ProxySecureInfo adaptFromJson(
        JsonObject object)
    {
        String version = object.containsKey(VERSION_NAME) ? object.getString(VERSION_NAME) : null;
        String cipher = object.containsKey(CIPHER_NAME) ? object.getString(CIPHER_NAME) : null;
        String key = object.containsKey(KEY_NAME) ? object.getString(KEY_NAME) : null;
        String name = object.containsKey(NAME_NAME) ? object.getString(NAME_NAME) : null;
        String signature = object.containsKey(SIGNATURE_NAME) ? object.getString(SIGNATURE_NAME) : null;

        return new ProxySecureInfo(version, cipher, key, name, signature);
    }
}
