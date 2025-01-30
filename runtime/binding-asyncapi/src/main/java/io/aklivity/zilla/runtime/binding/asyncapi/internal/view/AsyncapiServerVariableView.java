/*
 * Copyright 2021-2024 Aklivity Inc
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

import java.util.List;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiServerVariable;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.resolver.AsyncapiResolver;

public final class AsyncapiServerVariableView
{
    public String name;
    public final String defaultValue;
    public final List<String> values;

    AsyncapiServerVariableView(
        AsyncapiResolver resolver,
        String name,
        AsyncapiServerVariable model)
    {
        final AsyncapiServerVariable resolved = resolver.serverVariables.resolve(model);

        this.name = name;
        this.defaultValue = resolved.defaultValue;
        this.values = resolved.values;
    }
}
