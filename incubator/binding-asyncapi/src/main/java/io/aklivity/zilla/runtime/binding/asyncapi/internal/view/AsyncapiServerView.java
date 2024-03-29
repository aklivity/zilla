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

import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiServer;

public final class AsyncapiServerView
{
    private final AsyncapiServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public URI url()
    {
        return URI.create(server.host);
    }

    public List<Map<String, List<String>>> security()
    {
        List<Map<String, List<String>>> security = null;
        if (server.security != null)
        {
            try
            {
                security = objectMapper.readValue(server.security.toString(), new TypeReference<>()
                {
                });
            }
            catch (JsonProcessingException e)
            {
                rethrowUnchecked(e);
            }
        }

        return security;
    }

    public String protocol()
    {
        return server.protocol;
    }

    public String scheme()
    {
        return url().getScheme();
    }

    public String authority()
    {
        return String.format("%s:%d", url().getHost(), url().getPort());
    }

    public static AsyncapiServerView of(
        AsyncapiServer asyncapiServer)
    {
        return new AsyncapiServerView(asyncapiServer);
    }

    private AsyncapiServerView(
        AsyncapiServer server)
    {
        this.server = server;
    }
}
