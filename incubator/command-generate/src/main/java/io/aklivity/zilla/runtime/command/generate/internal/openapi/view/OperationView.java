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
package io.aklivity.zilla.runtime.command.generate.internal.openapi.view;

import java.util.Map;

import io.aklivity.zilla.runtime.command.generate.internal.openapi.model.Operation;
import io.aklivity.zilla.runtime.command.generate.internal.openapi.model.ResponseByContentType;

public class OperationView
{
    public static final String DEFAULT = "default";

    private final Operation operation;
    private final boolean hasResponses;

    public OperationView(
        Operation operation)
    {
        this.operation = operation;
        this.hasResponses = initHasResponses();
    }

    public Map<String, ResponseByContentType> responsesByStatus()
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
            for (Map.Entry<String, ResponseByContentType> response0 : operation.responses.entrySet())
            {
                String status = response0.getKey();
                ResponseByContentType response1 = response0.getValue();
                if (!(DEFAULT.equals(status)) && response1.content != null)
                {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    public static OperationView of(
        Operation operation)
    {
        return new OperationView(operation);
    }
}
