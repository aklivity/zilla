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
package io.aklivity.zilla.runtime.binding.openapi.internal.view;

import static java.util.Objects.requireNonNull;

import java.net.URI;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiServer;

public final class OpenapiView
{
    private final Openapi openapi;

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
        for (OpenapiServer server : openapi.servers)
        {
            OpenapiServerView view = OpenapiServerView.of(server);
            if (scheme.equals(view.url().getScheme()))
            {
                result = view.url();
                break;
            }
        }
        return result;
    }

    public static OpenapiView of(
        Openapi openapi)
    {
        return new OpenapiView(openapi);
    }

    private OpenapiView(
        Openapi openapi)
    {
        this.openapi = openapi;
    }
}
