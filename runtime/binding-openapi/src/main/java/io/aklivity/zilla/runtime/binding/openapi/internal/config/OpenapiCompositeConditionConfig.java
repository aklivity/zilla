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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;

public class OpenapiCompositeConditionConfig extends ConditionConfig
{
    public final long apiId;
    public final int operationTypeId;

    public OpenapiCompositeConditionConfig(
        long apiId,
        int operationTypeId)
    {
        this.apiId = apiId;
        this.operationTypeId = operationTypeId;
    }

    public boolean matches(
        long apiId,
        int operationTypeId)
    {
        return matchesApiId(apiId) &&
            matchesOperationTypeId(operationTypeId);
    }

    private boolean matchesApiId(
        long apiId)
    {
        return this.apiId == apiId;
    }

    private boolean matchesOperationTypeId(
        int operationTypeId)
    {
        return this.operationTypeId == operationTypeId;
    }
}
