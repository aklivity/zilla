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
package io.aklivity.zilla.runtime.binding.openapi.internal.config.composite;

public final class OpenapiCompositeId
{
    public static int apiId(
        long compositeId)
    {
        return (int)(compositeId >> Integer.SIZE) & 0xffff_ffff;
    }

    public static int operationId(
        long compositeId)
    {
        return (int)(compositeId >> 0) & 0xffff_ffff;
    }

    public static long compositeId(
        final int apiId,
        final int operationId)
    {
        return (long) apiId << Integer.SIZE |
               (long) operationId << 0;
    }

    private OpenapiCompositeId()
    {
    }
}
