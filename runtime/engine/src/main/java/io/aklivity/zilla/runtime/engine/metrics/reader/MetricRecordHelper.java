/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.metrics.reader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongFunction;

final class MetricRecordHelper
{
    private MetricRecordHelper()
    {
    }

    static Map<String, String> parseAttributes(
        int attributesId,
        LongFunction<String> labelResolver)
    {
        if (attributesId == 0)
        {
            return Map.of();
        }
        String label = labelResolver.apply(attributesId);
        if (label == null || label.isEmpty())
        {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : label.split(","))
        {
            int eq = pair.indexOf('=');
            if (eq > 0)
            {
                result.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return result;
    }
}
