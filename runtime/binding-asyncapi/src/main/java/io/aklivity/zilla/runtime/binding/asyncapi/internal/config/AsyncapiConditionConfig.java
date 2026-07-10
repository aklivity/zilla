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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;

public class AsyncapiConditionConfig extends ConditionConfig
{
    public final String spec;
    public final String operation;

    public AsyncapiConditionConfig(
        String spec,
        String operation)
    {
        this.spec = spec;
        this.operation = operation;
    }

    public boolean matches(
        String apiId,
        String operationId)
    {
        return matchesSpec(apiId) &&
            matchesOperation(operationId);
    }

    private boolean matchesSpec(
        String apiId)
    {
        return this.spec == null || this.spec.equals(apiId);
    }

    private boolean matchesOperation(
        String operationId)
    {
        return this.operation == null || this.operation.equals(operationId);
    }
}
