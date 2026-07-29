/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.asyncapi.model;

import java.util.function.Function;

public class AsyncapiInfo
{
    public String title;
    public String version;
    public String description;

    public static AsyncapiInfoBuilder<AsyncapiInfo> builder()
    {
        return new AsyncapiInfoBuilder<>(AsyncapiInfo.class::cast);
    }

    public static <T> AsyncapiInfoBuilder<T> builder(
        Function<AsyncapiInfo, T> mapper)
    {
        return new AsyncapiInfoBuilder<>(mapper);
    }
}
