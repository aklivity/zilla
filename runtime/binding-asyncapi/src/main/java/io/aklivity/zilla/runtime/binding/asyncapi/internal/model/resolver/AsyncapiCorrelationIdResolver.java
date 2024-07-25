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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.model.resolver;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiCorrelationId;

public final class AsyncapiCorrelationIdResolver
{
    private final Map<String, AsyncapiCorrelationId> correlationIds;
    private final Matcher matcher;

    public AsyncapiCorrelationIdResolver(
        Asyncapi model)
    {
        this.correlationIds = model.components.correlationIds;
        this.matcher = Pattern.compile("#/components/correlationIds/(.+)").matcher("");
    }

    public AsyncapiCorrelationId resolve(
        AsyncapiCorrelationId correlationId)
    {
        AsyncapiCorrelationId resolved = correlationId;

        if (correlationId.ref != null && matcher.reset(correlationId.ref).matches())
        {
            String key = matcher.group(1);
            resolved = correlationIds.get(key);
        }

        return resolved;
    }
}
