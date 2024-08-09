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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiOperation;

public final class AsyncapiOperationResolver extends AbstractAsyncapiResolver<AsyncapiOperation>
{
    private AsyncapiChannelResolver channels;
    private AsyncapiMessageResolver messages;
    private final Matcher matcher;

    public AsyncapiOperationResolver(
        Asyncapi model)
    {
        super(model.operations, Pattern.compile("#/operations/(.+)"));
        this.channels = new AsyncapiChannelResolver(model);
        this.messages = new AsyncapiMessageResolver(model);
        this.matcher = Pattern.compile("#/channels/(.+)/messages/(.+)").matcher("");
    }

    public AsyncapiMessage resolve(
        AsyncapiMessage model)
    {
        AsyncapiMessage resolvable = Stream.of(model)
                .filter(m -> m.ref != null && matcher.reset(m.ref).matches())
                .map(m -> channels.resolve(matcher.group(1)))
                .filter(Objects::nonNull)
                .map(c -> c.messages.get(matcher.group(2)))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(model);

        return messages.resolve(resolvable);
    }
}
