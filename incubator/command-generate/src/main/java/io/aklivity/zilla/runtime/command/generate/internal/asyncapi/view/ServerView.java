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
package io.aklivity.zilla.runtime.command.generate.internal.asyncapi.view;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Server;

public final class ServerView
{
    private final Server server;

    private ServerView(
        Server server)
    {
        this.server = server;
    }

    public URI url()
    {
        return URI.create(server.host);
    }

    public List<Map<String, List<String>>> security()
    {
        return server.security;
    }

    public String scheme()
    {
        return url().getScheme();
    }

    public String authority()
    {
        return String.format("%s:%d", url().getHost(), url().getPort());
    }

    public static ServerView of(
        Server server)
    {
        return new ServerView(server);
    }
}
