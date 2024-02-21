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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.view;

import static java.util.Objects.requireNonNull;

import java.net.URI;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;

public final class AsyncapiView
{
    private final Asyncapi asyncapi;

    public int[] resolveAllPorts()
    {
        int[] ports = new int[asyncapi.servers.size()];
        String[] keys = asyncapi.servers.keySet().toArray(String[]::new);
        for (int i = 0; i < asyncapi.servers.size(); i++)
        {
            AsyncapiServerView server = AsyncapiServerView.of(asyncapi.servers.get(keys[i]));
            URI url = server.url();
            ports[i] = url.getPort();
        }
        return ports;
    }

    public int[] resolvePortsForScheme(
        String scheme)
    {
        requireNonNull(scheme);
        int[] ports = null;
        URI url = findFirstServerUrlWithScheme(scheme);
        if (url != null)
        {
            ports = new int[] {url.getPort()};
        }
        return ports;
    }

    public URI findFirstServerUrlWithScheme(
        String scheme)
    {
        requireNonNull(scheme);
        URI result = null;
        for (String key : asyncapi.servers.keySet())
        {
            AsyncapiServerView server = AsyncapiServerView.of(asyncapi.servers.get(key));
            if (scheme.equals(server.url().getScheme()))
            {
                result = server.url();
                break;
            }
        }
        return result;
    }

    public static AsyncapiView of(
        Asyncapi asyncapi)
    {
        return new AsyncapiView(asyncapi);
    }

    private AsyncapiView(
        Asyncapi asyncapi)
    {
        this.asyncapi = asyncapi;
    }
}
