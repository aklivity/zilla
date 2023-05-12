/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.layouts;

import static java.lang.Integer.numberOfTrailingZeros;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.align;
import static org.agrona.BitUtil.findNextPositivePowerOfTwo;
import static org.agrona.BitUtil.isPowerOfTwo;
import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;

import org.agrona.CloseHelper;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class BudgetsLayout extends Layout
{
    public static final int OFFSET_BUDGET_ID = 0;
    public static final int SIZEOF_BUDGET_ID = Long.BYTES;
    public static final int LIMIT_BUDGET_ID = OFFSET_BUDGET_ID + SIZEOF_BUDGET_ID;
    public static final int OFFSET_BUDGET_REMAINING = LIMIT_BUDGET_ID;
    public static final int SIZEOF_BUDGET_REMAINING = Long.BYTES;
    public static final int LIMIT_BUDGET_REMAINING = OFFSET_BUDGET_REMAINING + SIZEOF_BUDGET_REMAINING;
    public static final int OFFSET_BUDGET_WATCHERS = LIMIT_BUDGET_REMAINING;
    public static final int SIZEOF_BUDGET_WATCHERS = Long.BYTES;
    public static final int LIMIT_BUDGET_WATCHERS = OFFSET_BUDGET_WATCHERS + SIZEOF_BUDGET_WATCHERS;

    public static final int SIZEOF_BUDGET_ENTRY =
            findNextPositivePowerOfTwo(align(LIMIT_BUDGET_WATCHERS - OFFSET_BUDGET_ID, CACHE_LINE_LENGTH));

    public static final int SIZEOF_BUDGET_ENTRY_SHIFT = numberOfTrailingZeros(SIZEOF_BUDGET_ENTRY);

    private final AtomicBuffer buffer;

    private BudgetsLayout(
        AtomicBuffer buffer)
    {
        if (!isPowerOfTwo(buffer.capacity()))
        {
            throw new IllegalArgumentException("budgets buffer capacity is not a power of 2");
        }
        this.buffer = buffer;
    }

    @Override
    public void close()
    {
        unmap(buffer().byteBuffer());
    }

    public AtomicBuffer buffer()
    {
        return buffer;
    }

    public int entries()
    {
        return buffer.capacity() / SIZEOF_BUDGET_ENTRY;
    }

    public static int budgetIdOffset(
        int index)
    {
        return budgetEntryOffset(index) + OFFSET_BUDGET_ID;
    }

    public static int budgetRemainingOffset(
        int index)
    {
        return budgetEntryOffset(index) + OFFSET_BUDGET_REMAINING;
    }

    public static int budgetWatchersOffset(
        int index)
    {
        return budgetEntryOffset(index) + OFFSET_BUDGET_WATCHERS;
    }

    private static int budgetEntryOffset(
        int index)
    {
        return (index & 0x7FFF_FFFF) << SIZEOF_BUDGET_ENTRY_SHIFT;
    }

    public static final class Builder extends Layout.Builder<BudgetsLayout>
    {
        private Path path;
        private int capacity;
        private boolean owner;

        public Builder path(
            Path path)
        {
            this.path = path;
            return this;
        }

        public Builder capacity(
            int capacity)
        {
            this.capacity = capacity;
            return this;
        }

        public Builder owner(
            boolean owner)
        {
            this.owner = owner;
            return this;
        }

        @Override
        public BudgetsLayout build()
        {
            final File budgets = path.toFile();

            if (owner)
            {
                CloseHelper.close(createEmptyFile(budgets, capacity));
            }
            else
            {
                capacity = (int) budgets.length();
            }

            final MappedByteBuffer mapped = mapExistingFile(budgets, "budgets");

            final AtomicBuffer buffer = new UnsafeBuffer(mapped);

            return new BudgetsLayout(buffer);
        }
    }
}
