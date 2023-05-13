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
package io.aklivity.zilla.runtime.binding.tls.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.tls.internal.TlsBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class TlsConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String AUTHORITY_NAME = "authority";
    private static final String ALPN_NAME = "alpn";

    @Override
    public String type()
    {
        return TlsBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        TlsConditionConfig tlsCondition = (TlsConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (tlsCondition.authority != null)
        {
            object.add(AUTHORITY_NAME, tlsCondition.authority);
        }

        if (tlsCondition.alpn != null)
        {
            object.add(ALPN_NAME, tlsCondition.alpn);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String authority = object.containsKey(AUTHORITY_NAME)
                ? object.getString(AUTHORITY_NAME)
                : null;
        String alpn = object.containsKey(ALPN_NAME)
                ? object.getString(ALPN_NAME)
                : null;

        return new TlsConditionConfig(authority, alpn);
    }
}
