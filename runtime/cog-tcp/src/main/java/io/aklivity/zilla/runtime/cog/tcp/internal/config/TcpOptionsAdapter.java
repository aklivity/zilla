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
package io.aklivity.zilla.runtime.cog.tcp.internal.config;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.bind.adapter.JsonbAdapter;

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
        object.add(PORT_NAME, tcpOptions.port);

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
        int port = object.getJsonNumber(PORT_NAME).intValue();
        int backlog = object.containsKey(BACKLOG_NAME) ? object.getJsonNumber(BACKLOG_NAME).intValue() : BACKLOG_DEFAULT;
        boolean nodelay = NODELAY_DEFAULT;
        boolean keepalive = KEEPALIVE_DEFAULT;

        return new TcpOptions(host, port, backlog, nodelay, keepalive);
    }
}
