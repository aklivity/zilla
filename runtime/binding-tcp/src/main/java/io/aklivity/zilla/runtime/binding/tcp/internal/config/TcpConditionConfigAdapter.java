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
package io.aklivity.zilla.runtime.binding.tcp.internal.config;

import static io.aklivity.zilla.runtime.binding.tcp.internal.config.TcpOptionsConfigAdapter.adaptPortsValueFromJson;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import org.agrona.collections.IntHashSet;
import org.agrona.collections.MutableInteger;

import io.aklivity.zilla.runtime.binding.tcp.config.TcpConditionConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpConditionConfigBuilder;
import io.aklivity.zilla.runtime.binding.tcp.internal.TcpBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class TcpConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String CIDR_NAME = "cidr";
    private static final String AUTHORITY_NAME = "authority";
    private static final String PORT_NAME = "port";

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

        if (tcpCondition.ports != null)
        {
            if (tcpCondition.ports.length == 1)
            {
                object.add(PORT_NAME, tcpCondition.ports[0]);
            }
            else
            {
                JsonArrayBuilder ports = Json.createArrayBuilder();
                for (int port : tcpCondition.ports)
                {
                    ports.add(port);
                }

                object.add(PORT_NAME, ports);
            }
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        TcpConditionConfigBuilder<TcpConditionConfig> tcpCondition = TcpConditionConfig.builder();

        if (object.containsKey(CIDR_NAME))
        {
            tcpCondition.cidr(object.getString(CIDR_NAME));
        }

        if (object.containsKey(AUTHORITY_NAME))
        {
            tcpCondition.authority(object.getString(AUTHORITY_NAME));
        }

        if (object.containsKey(PORT_NAME))
        {
            JsonValue portsValue = object.get(PORT_NAME);

            IntHashSet portsSet = new IntHashSet();
            switch (portsValue.getValueType())
            {
            case ARRAY:
                JsonArray portsArray = portsValue.asJsonArray();
                portsArray.forEach(value -> adaptPortsValueFromJson(value, portsSet));
                break;
            default:
                adaptPortsValueFromJson(portsValue, portsSet);
                break;
            }

            int[] ports = new int[portsSet.size()];
            MutableInteger index = new MutableInteger();
            portsSet.forEach(i -> ports[index.value++] = i);

            tcpCondition.ports(ports);
        }

        return tcpCondition.build();
    }
}
