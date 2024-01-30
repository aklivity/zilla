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

import java.io.File;
import java.net.URI;

import io.aklivity.zilla.runtime.engine.Configuration;

public class AsyncapiConfiguration extends Configuration
{
    private static final ConfigurationDef ASYNCAPI_CONFIG;
    public static final PropertyDef<URI> ASYNCAPI_ROOT;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.asyncapi");
        ASYNCAPI_ROOT = config.property(URI.class, "root",
            AsyncapiConfiguration::decodeAsyncapiRoot, new File(".").toURI());

        ASYNCAPI_CONFIG = config;
    }

    public AsyncapiConfiguration(
        Configuration config)
    {
        super(ASYNCAPI_CONFIG, config);
    }

    public URI asyncapiRoot()
    {
        return ASYNCAPI_ROOT.get(this);
    }

    private static URI decodeAsyncapiRoot(
        String location)
    {
        return location.indexOf(':') != -1 ? URI.create(location) : new File(location).toURI();
    }
}
