/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.openapi.view;

public final class OpenapiCompositeId
{
    private static final int SPEC_INDEX_BITS = 16;
    private static final int OPERATION_ID_BITS = 32;
    private static final int SERVER_INDEX_BITS = 16;

    private static final int SERVER_INDEX_SHIFT = 0;
    private static final int OPERATION_ID_SHIFT = SERVER_INDEX_BITS;
    private static final int SPEC_INDEX_SHIFT = SERVER_INDEX_BITS + OPERATION_ID_BITS;

    private static final long SPEC_INDEX_MASK = (1L << SPEC_INDEX_BITS) - 1;
    private static final long OPERATION_ID_MASK = (1L << OPERATION_ID_BITS) - 1;
    private static final long SERVER_INDEX_MASK = (1L << SERVER_INDEX_BITS) - 1;

    public static int specIndex(
        long compositeId)
    {
        return (int)((compositeId >>> SPEC_INDEX_SHIFT) & SPEC_INDEX_MASK);
    }

    public static int operationId(
        long compositeId)
    {
        return (int)((compositeId >>> OPERATION_ID_SHIFT) & OPERATION_ID_MASK);
    }

    public static int serverIndex(
        long compositeId)
    {
        return (int)((compositeId >>> SERVER_INDEX_SHIFT) & SERVER_INDEX_MASK);
    }

    public static long compositeId(
        final int specIndex,
        final int operationId)
    {
        return compositeId(specIndex, operationId, 0);
    }

    public static long compositeId(
        final int specIndex,
        final int operationId,
        final int serverIndex)
    {
        return ((long) specIndex & SPEC_INDEX_MASK) << SPEC_INDEX_SHIFT |
               ((long) operationId & OPERATION_ID_MASK) << OPERATION_ID_SHIFT |
               ((long) serverIndex & SERVER_INDEX_MASK) << SERVER_INDEX_SHIFT;
    }

    private OpenapiCompositeId()
    {
    }
}
