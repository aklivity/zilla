/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class AsyncapiConfiguration extends Configuration
{
    public static final LongPropertyDef ASYNCAPI_TARGET_ROUTE_ID;
    private static final ConfigurationDef ASYNCAPI_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.asyncapi");
        ASYNCAPI_TARGET_ROUTE_ID = config.property("target.route.id", -1L);
        ASYNCAPI_CONFIG = config;
    }

    public AsyncapiConfiguration(
        Configuration config)
    {
        super(ASYNCAPI_CONFIG, config);
    }

    public long targetRouteId()
    {
        return ASYNCAPI_TARGET_ROUTE_ID.getAsLong(this);
    }

}
