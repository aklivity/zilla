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

import io.aklivity.zilla.runtime.cog.tcp.internal.TcpCog;
import io.aklivity.zilla.runtime.engine.config.Options;
import io.aklivity.zilla.runtime.engine.config.OptionsAdapterSpi;

public final class TcpOptionsAdapter implements OptionsAdapterSpi, JsonbAdapter<Options, JsonObject>
{
    private static final String HOST_NAME = "host";
    private static final String PORT_NAME = "port";
    private static final String BACKLOG_NAME = "backlog";

    private static final int BACKLOG_DEFAULT = 0;
    private static final boolean NODELAY_DEFAULT = true;
    private static final boolean KEEPALIVE_DEFAULT = false;

    @Override
    public String type()
    {
        return TcpCog.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        Options options)
    {
        TcpOptions tcpOptions = (TcpOptions) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(HOST_NAME, tcpOptions.host);

        if (tcpOptions.ports != null)
        {
            if (tcpOptions.ports.length == 1)
            {
                object.add(PORT_NAME, tcpOptions.ports[0]);
            }
            else
            {
                JsonArrayBuilder ports = Json.createArrayBuilder();
                for (int port : tcpOptions.ports)
                {
                    ports.add(port);
                }

                object.add(PORT_NAME, ports);
            }
        }

        if (tcpOptions.backlog != BACKLOG_DEFAULT)
        {
            object.add(BACKLOG_NAME, tcpOptions.backlog);
        }

        assert tcpOptions.nodelay == NODELAY_DEFAULT;
        assert tcpOptions.keepalive == KEEPALIVE_DEFAULT;

        return object.build();
    }

    @Override
    public Options adaptFromJson(
        JsonObject object)
    {
        String host = object.getString(HOST_NAME);
        JsonValue portsValue = object.get(PORT_NAME);
        int backlog = object.containsKey(BACKLOG_NAME) ? object.getJsonNumber(BACKLOG_NAME).intValue() : BACKLOG_DEFAULT;
        boolean nodelay = NODELAY_DEFAULT;
        boolean keepalive = KEEPALIVE_DEFAULT;

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

        return new TcpOptions(host, ports, backlog, nodelay, keepalive);
    }

    private void adaptPortsValueFromJson(
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
