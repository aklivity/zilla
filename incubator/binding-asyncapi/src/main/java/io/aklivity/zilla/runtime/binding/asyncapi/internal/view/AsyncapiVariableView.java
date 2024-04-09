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

import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiVariable;

public final class AsyncapiVariableView extends AsyncapiResolvable<AsyncapiVariable>
{
    private final AsyncapiVariable variable;

    public String refKey()
    {
        return key;
    }

    public List<String> values()
    {
        return variable.values;
    }
    public String defaultValue()
    {
        return variable.defaultValue;
    }

    public static AsyncapiVariableView of(
        Map<String, AsyncapiVariable> variables,
        AsyncapiVariable variable)
    {
        return new AsyncapiVariableView(variables, variable);
    }

    private AsyncapiVariableView(
        Map<String, AsyncapiVariable> variables,
        AsyncapiVariable variable)
    {
        super(variables, "#/components/serverVariables/(\\w+)");
        this.variable = variable.ref == null ? variable : resolveRef(variable.ref);
    }
}
