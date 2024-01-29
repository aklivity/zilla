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

import java.util.stream.IntStream;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import org.agrona.collections.IntHashSet;
import org.agrona.collections.MutableInteger;

import io.aklivity.zilla.runtime.binding.tls.config.TlsConditionConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsConditionConfigBuilder;
import io.aklivity.zilla.runtime.binding.tls.internal.TlsBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class TlsConditionConfigAdapter implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String AUTHORITY_NAME = "authority";
    private static final String ALPN_NAME = "alpn";
    private static final String PORT_NAME = "port";

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

        if (tlsCondition.ports != null)
        {
            if (tlsCondition.ports.length == 1)
            {
                object.add(PORT_NAME, tlsCondition.ports[0]);
            }
            else
            {
                JsonArrayBuilder ports = Json.createArrayBuilder();
                for (int port : tlsCondition.ports)
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
        TlsConditionConfigBuilder<TlsConditionConfig> tlsCondition = TlsConditionConfig.builder();

        if (object.containsKey(AUTHORITY_NAME))
        {
            tlsCondition.authority(object.getString(AUTHORITY_NAME));
        }

        if (object.containsKey(ALPN_NAME))
        {
            tlsCondition.alpn(object.getString(ALPN_NAME));
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

            tlsCondition.ports(ports);
        }

        return tlsCondition.build();
    }

    private static void adaptPortsValueFromJson(
        JsonValue value,
        IntHashSet ports)
    {
        switch (value.getValueType())
        {
        case STRING:
        {
            String port = ((JsonString) value).getString();
            int dashAt = port.indexOf('-');
            if (dashAt != -1)
            {
                int portRangeLow = Integer.parseInt(port.substring(0, dashAt));
                int portRangeHigh = Integer.parseInt(port.substring(dashAt + 1));
                IntStream.range(portRangeLow, portRangeHigh + 1).forEach(ports::add);
            }
            else
            {
                ports.add(Integer.parseInt(port));
            }
            break;
        }
        case NUMBER:
        default:
        {
            int port = ((JsonNumber) value).intValue();
            ports.add(port);
            break;
        }
        }
    }
}
