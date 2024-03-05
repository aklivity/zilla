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
package io.aklivity.zilla.runtime.binding.openapi.internal.view;

import java.util.Map;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiOperation;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiResponseByContentType;

public final class OpenapiOperationView
{
    public static final String DEFAULT = "default";

    private final OpenapiOperation operation;
    private final boolean hasResponses;

    private OpenapiOperationView(
        OpenapiOperation operation)
    {
        this.operation = operation;
        this.hasResponses = initHasResponses();
    }

    public Map<String, OpenapiResponseByContentType> responsesByStatus()
    {
        return operation.responses;
    }

    public boolean hasResponses()
    {
        return hasResponses;
    }

    private boolean initHasResponses()
    {
        boolean result = false;
        if (operation != null && operation.responses != null)
        {
            for (Map.Entry<String, OpenapiResponseByContentType> response0 : operation.responses.entrySet())
            {
                String status = response0.getKey();
                OpenapiResponseByContentType response1 = response0.getValue();
                if (!(DEFAULT.equals(status)) && response1.content != null)
                {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    public static OpenapiOperationView of(
        OpenapiOperation operation)
    {
        return new OpenapiOperationView(operation);
    }
}
