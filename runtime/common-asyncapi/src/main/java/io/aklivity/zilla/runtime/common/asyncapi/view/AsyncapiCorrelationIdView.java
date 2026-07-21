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
package io.aklivity.zilla.runtime.common.asyncapi.view;

import java.util.Map;
import java.util.Optional;

import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiCorrelationId;
import io.aklivity.zilla.runtime.common.asyncapi.model.resolver.AsyncapiResolver;

public final class AsyncapiCorrelationIdView
{
    public final String location;

    private final Map<String, Object> extensions;

    public boolean hasExtension(
        String name)
    {
        return extensions != null && extensions.containsKey(name);
    }

    public <T> Optional<T> extension(
        String name,
        Class<T> type)
    {
        return Optional.ofNullable(extensions != null ? type.cast(extensions.get(name)) : null);
    }

    AsyncapiCorrelationIdView(
        AsyncapiResolver resolver,
        AsyncapiCorrelationId model)
    {
        final AsyncapiCorrelationId resolved = resolver.correlationIds.resolve(model);

        this.location = resolved.location;
        this.extensions = resolved.extensions;
    }
}
