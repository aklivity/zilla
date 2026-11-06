/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.a2a.internal.card;

import java.net.URI;

public final class A2aAgentInterface
{
    public static final String BINDING_JSONRPC = "JSONRPC";

    public final String url;
    public final String binding;
    public final String version;
    public final String tenant;

    public A2aAgentInterface(
        String url,
        String binding,
        String version,
        String tenant)
    {
        this.url = url;
        this.binding = binding;
        this.version = version;
        this.tenant = tenant;
    }

    public boolean jsonRpc()
    {
        return BINDING_JSONRPC.equalsIgnoreCase(binding);
    }

    public String scheme()
    {
        return URI.create(url).getScheme();
    }

    public String authority()
    {
        final URI uri = URI.create(url);
        final int port = uri.getPort() != -1 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80;
        return String.format("%s:%d", uri.getHost(), port);
    }

    public String path()
    {
        final String path = URI.create(url).getRawPath();
        return path == null || path.isEmpty() ? "/" : path;
    }
}
