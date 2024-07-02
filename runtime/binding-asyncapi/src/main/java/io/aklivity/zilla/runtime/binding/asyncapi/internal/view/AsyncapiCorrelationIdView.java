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

import java.util.Map;

import jakarta.json.bind.annotation.JsonbPropertyOrder;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiCorrelationId;

@JsonbPropertyOrder({
    "type",
    "items",
    "properties",
    "required"
})
public final class AsyncapiCorrelationIdView extends AsyncapiResolvable<AsyncapiCorrelationId>
{
    private final AsyncapiCorrelationId correlationId;
    private final Map<String, AsyncapiCorrelationId> correlationIds;

    public String refKey()
    {
        return key;
    }
    public AsyncapiCorrelationId correlationId()
    {
        return correlationId;
    }

    public String location()
    {
        return correlationId.location;
    }

    public static AsyncapiCorrelationIdView of(
        Map<String, AsyncapiCorrelationId> correlationIds,
        AsyncapiCorrelationId correlationId)
    {
        return new AsyncapiCorrelationIdView(correlationIds, correlationId);
    }

    private AsyncapiCorrelationIdView(
        Map<String, AsyncapiCorrelationId> correlationIds,
        AsyncapiCorrelationId correlationId)
    {
        super(correlationIds, "#/components/correlationIds/(.+)");
        if (correlationId.ref != null)
        {
            correlationId = resolveRef(correlationId.ref);
        }

        this.correlationIds = correlationIds;
        this.correlationId = correlationId;
    }
}
