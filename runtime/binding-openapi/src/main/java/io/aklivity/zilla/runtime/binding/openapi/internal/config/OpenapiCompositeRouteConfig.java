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

import java.util.List;

public final class OpenapiCompositeRouteConfig
{
    public final long id;
    public final OpenapiCompositeWithConfig with;
    public final List<OpenapiCompositeConditionConfig> when;

    // TODO: builder instead of overloaded constructors
    public OpenapiCompositeRouteConfig(
        long id,
        OpenapiCompositeConditionConfig when)
    {
        this(id, List.of(when), null);
    }

    public OpenapiCompositeRouteConfig(
        long id,
        List<OpenapiCompositeConditionConfig> when,
        OpenapiCompositeWithConfig with)
    {
        this.id = id;
        this.when = when;
        this.with = with;
    }

    boolean matches(
        long apiId,
        int operationTypeId)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matches(apiId, operationTypeId));
    }
}
