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
package io.aklivity.zilla.runtime.engine.budget;

public interface BudgetCreditor
{
    long NO_CREDITOR_INDEX = -1L;
    long NO_BUDGET_ID = 0;

    long acquire(
        long budgetId);

    long credit(
        long traceId,
        long budgetIndex,
        long credit);

    void release(
        long budgetIndex);

    long supplyChild(
        long budgetId);

    void cleanupChild(
        long budgetId);
}
