/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.cog.budget;

import java.util.function.LongConsumer;

public interface BudgetDebitor
{
    long NO_DEBITOR_INDEX = -1L;

    long acquire(
        long budgetId,
        long watcherId,
        LongConsumer flusher);

    @Deprecated
    int claim(
        long budgetIndex,
        long watcherId,
        int minimum,
        int maximum);

    @Deprecated
    int claim(
        long budgetIndex,
        long watcherId,
        int minimum,
        int maximum,
        int deferred);

    int claim(
        long traceId,
        long budgetIndex,
        long watcherId,
        int minimum,
        int maximum,
        int deferred);

    void release(
        long budgetIndex,
        long watcherId);
}
