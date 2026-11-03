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
package io.aklivity.zilla.runtime.binding.llm.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.binding.llm.LlmModelConfig;
import io.aklivity.zilla.runtime.common.lang.Matchers;

final class LlmModelCatalog
{
    private static final int[][] NO_GROUPS = new int[0][];

    private final List<Entry> entries;

    LlmModelCatalog(
        List<LlmModelConfig> configured,
        List<LlmModelConfig> defaults)
    {
        this(configured, null, defaults);
    }

    LlmModelCatalog(
        List<LlmModelConfig> configured,
        LlmModelConfig dialect,
        List<LlmModelConfig> defaults)
    {
        this.entries = new ArrayList<>(configured.size() + defaults.size() + 1);
        addAll(configured);

        if (dialect != null)
        {
            add(dialect);
        }

        addAll(defaults);
    }

    LlmModelCapability resolve(
        String model)
    {
        LlmModelCapability capability = LlmModelCapability.UNSPECIFIED;

        if (model != null)
        {
            Boolean sampling = null;
            String thinking = null;
            Integer budgetMinimum = null;
            Integer budgetMaximum = null;
            Map<String, Integer> ladder = null;
            List<String> tiers = null;
            List<String> unsupported = null;
            List<List<String>> exclusive = null;

            for (Entry entry : entries)
            {
                if (entry.pattern.matcher(model).matches())
                {
                    final LlmModelConfig config = entry.config;

                    sampling = sampling != null ? sampling : config.sampling;
                    thinking = thinking != null ? thinking : config.thinking;
                    budgetMinimum = budgetMinimum != null ? budgetMinimum : config.budgetMinimum;
                    budgetMaximum = budgetMaximum != null ? budgetMaximum : config.budgetMaximum;
                    ladder = ladder != null ? ladder : config.ladder.isEmpty() ? null : config.ladder;
                    tiers = tiers != null ? tiers : config.tiers;
                    unsupported = unsupported != null ? unsupported : config.unsupported;
                    exclusive = exclusive != null ? exclusive : config.exclusive;
                }
            }

            capability = sampling == null && thinking == null && budgetMinimum == null && budgetMaximum == null &&
                ladder == null && tiers == null && unsupported == null && exclusive == null
                ? LlmModelCapability.UNSPECIFIED
                : new LlmModelCapability(
                    sampling == null || sampling,
                    LlmThinking.of(thinking),
                    budgetMinimum != null ? budgetMinimum : LlmModelCapability.UNSET,
                    budgetMaximum != null ? budgetMaximum : LlmModelCapability.UNSET,
                    ladder != null ? ladderOf(ladder) : null,
                    tiers != null ? LlmServiceTier.maskOf(tiers) : LlmServiceTier.ALL,
                    unsupported != null ? LlmMember.maskOf(unsupported) : 0,
                    exclusive != null ? groupsOf(exclusive) : NO_GROUPS);
        }

        return capability;
    }

    private void addAll(
        List<LlmModelConfig> models)
    {
        models.stream()
            .filter(model -> model.name.indexOf('*') < 0)
            .forEach(this::add);
        models.stream()
            .filter(model -> model.name.indexOf('*') >= 0)
            .forEach(this::add);
    }

    private void add(
        LlmModelConfig model)
    {
        entries.add(new Entry(Matchers.glob(model.name), model));
    }

    private static int[][] groupsOf(
        List<List<String>> exclusive)
    {
        return exclusive.stream()
            .map(LlmMember::groupOf)
            .toArray(int[][]::new);
    }

    private static int[] ladderOf(
        Map<String, Integer> ladder)
    {
        final int[] budgets = new int[LlmEffort.COUNT];

        Arrays.fill(budgets, LlmModelCapability.UNSET);

        ladder.forEach((name, budget) ->
        {
            final int effort = LlmEffort.of(name);

            if (effort > LlmEffort.NONE)
            {
                budgets[effort] = budget;
            }
        });

        return budgets;
    }

    private record Entry(
        Pattern pattern,
        LlmModelConfig config)
    {
    }
}
