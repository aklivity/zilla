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
package io.aklivity.zilla.runtime.model.core.config;

public class RangeConfig
{
    public String max;
    public String min;
    public boolean exclusiveMax;
    public boolean exclusiveMin;

    public RangeConfig(
        String max,
        String min,
        boolean exclusiveMax,
        boolean exclusiveMin)
    {
        this.max = max;
        this.min = min;
        this.exclusiveMax = exclusiveMax;
        this.exclusiveMin = exclusiveMin;
    }
}
