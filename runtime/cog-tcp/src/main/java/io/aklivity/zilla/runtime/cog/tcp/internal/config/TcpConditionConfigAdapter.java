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
package io.aklivity.zilla.runtime.cog.tcp.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.cog.tcp.internal.TcpBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class TcpConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String CIDR_NAME = "cidr";
    private static final String AUTHORITY_NAME = "authority";

    @Override
    public String type()
    {
        return TcpBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        TcpConditionConfig tcpCondition = (TcpConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (tcpCondition.cidr != null)
        {
            object.add(CIDR_NAME, tcpCondition.cidr);
        }

        if (tcpCondition.authority != null)
        {
            object.add(AUTHORITY_NAME, tcpCondition.authority);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String cidr = object.containsKey(CIDR_NAME) ? object.getString(CIDR_NAME) : null;
        String authority = object.containsKey(AUTHORITY_NAME) ? object.getString(AUTHORITY_NAME) : null;

        return new TcpConditionConfig(cidr, authority);
    }
}
