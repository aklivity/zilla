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
package io.aklivity.zilla.runtime.common.asyncapi.model.resolver;

import java.util.Set;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.common.asyncapi.model.Asyncapi;
import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiChannel;

public final class AsyncapiChannelResolver extends AbstractAsyncapiResolver<AsyncapiChannel>
{
    public AsyncapiChannelResolver(
        Asyncapi model,
        Set<String> unresolved)
    {
        super(model.channels, Pattern.compile("#/channels/(.+)"), unresolved);
    }
}
