/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.amqp.internal.stream;

public final class AmqpTransferFlags
{
    private static final int FLAG_SETTLED = 1;
    private static final int FLAG_RESUME = 2;
    private static final int FLAG_ABORTED = 4;
    private static final int FLAG_BATCHABLE = 8;

    static int settled(
        int transferFlags)
    {
        return transferFlags | FLAG_SETTLED;
    }

    static int resume(
        int transferFlags)
    {
        return transferFlags | FLAG_RESUME;
    }

    static int aborted(
        int transferFlags)
    {
        return transferFlags | FLAG_ABORTED;
    }

    static int batchable(
        int transferFlags)
    {
        return transferFlags | FLAG_BATCHABLE;
    }

    static boolean isSettled(
        int transferFlags)
    {
        return (transferFlags & FLAG_SETTLED) != 0;
    }

    static boolean isResume(
        int transferFlags)
    {
        return (transferFlags & FLAG_RESUME) != 0;
    }

    static boolean isAborted(
        int transferFlags)
    {
        return (transferFlags & FLAG_ABORTED) != 0;
    }

    static boolean isBatchable(
        int transferFlags)
    {
        return (transferFlags & FLAG_BATCHABLE) != 0;
    }

    private AmqpTransferFlags()
    {
        // utility
    }
}
