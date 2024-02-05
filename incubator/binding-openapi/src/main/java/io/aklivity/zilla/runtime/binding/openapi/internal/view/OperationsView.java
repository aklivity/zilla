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

import java.util.LinkedHashMap;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.PathItem;

public final class OperationsView
{
    private final Map<String, Map<String, OperationView>> operationsPerPath;
    private final boolean hasResponses;

    private OperationsView(
        LinkedHashMap<String, PathItem> paths)
    {
        this.operationsPerPath = new LinkedHashMap<>();
        boolean hasResponses = false;
        for (String pathName : paths.keySet())
        {
            PathView path = PathView.of(paths.get(pathName));
            for (String methodName : path.methods().keySet())
            {
                OperationView operation = OperationView.of(path.methods().get(methodName));
                hasResponses |= operation.hasResponses();
                if (operationsPerPath.containsKey(pathName))
                {
                    operationsPerPath.get(pathName).put(methodName, operation);
                }
                else
                {
                    Map<String, OperationView> operationsPerMethod = new LinkedHashMap<>();
                    operationsPerMethod.put(methodName, operation);
                    operationsPerPath.put(pathName, operationsPerMethod);
                }
            }
        }
        this.hasResponses = hasResponses;
    }

    public boolean hasResponses()
    {
        return this.hasResponses;
    }

    public OperationView operation(
        String pathName,
        String methodName)
    {
        return operationsPerPath.get(pathName).get(methodName);
    }

    public static OperationsView of(
        LinkedHashMap<String, PathItem> paths)
    {
        return new OperationsView(paths);
    }
}
