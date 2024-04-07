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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import java.util.function.ToLongFunction;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;

public class AsyncapiConditionConfig extends ConditionConfig
{
    public final String subject;
    public final String operationId;

    public AsyncapiConditionConfig(
        String subject,
        String operationId)
    {
        this.subject = subject;
        this.operationId = operationId;
    }

    public boolean matches(
        long schemaId,
        ToLongFunction<String> supplySchemaId)
    {
        return matchesApiId(schemaId, supplySchemaId);
    }

    private boolean matchesApiId(
        long apiId,
        ToLongFunction<String> supplyApiId)
    {
        return supplyApiId.applyAsLong(this.subject) == apiId;
    }
}
