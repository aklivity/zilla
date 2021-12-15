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
package io.aklivity.zilla.runtime.cog.http.internal;

import io.aklivity.zilla.runtime.engine.cog.Configuration;

public class HttpConfiguration extends Configuration
{
    public static final IntPropertyDef HTTP_MAXIMUM_CONNECTIONS;
    public static final IntPropertyDef HTTP_MAXIMUM_QUEUED_REQUESTS;

    private static final ConfigurationDef HTTP_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.cog.http");
        HTTP_MAXIMUM_CONNECTIONS = config.property("maximum.connections", 10);
        HTTP_MAXIMUM_QUEUED_REQUESTS = config.property("maximum.requests.queued", 10000);
        HTTP_CONFIG = config;
    }

    public HttpConfiguration(
        Configuration config)
    {
        super(HTTP_CONFIG, config);
    }

    public int maximumConnectionsPerRoute()
    {
        return HTTP_MAXIMUM_CONNECTIONS.getAsInt(this);
    }

    public int maximumRequestsQueuedPerRoute()
    {
        return HTTP_MAXIMUM_QUEUED_REQUESTS.getAsInt(this);
    }
}
