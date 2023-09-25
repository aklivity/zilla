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
package io.aklivity.zilla.runtime.command.generate.internal.openapi.view;

import java.net.URI;

import io.aklivity.zilla.runtime.command.generate.internal.openapi.model.Server;

public final class ServerView
{
    private URI url;

    private ServerView(
        Server server)
    {
        this.url = URI.create(server.url);
    }

    public URI url()
    {
        return url;
    }

    public static ServerView of(
        Server server)
    {
        return new ServerView(server);
    }
}
