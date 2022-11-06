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
package io.aklivity.zilla.runtime.command.log.internal;

import static io.aklivity.zilla.runtime.command.log.internal.layouts.BudgetsLayout.budgetIdOffset;
import static io.aklivity.zilla.runtime.command.log.internal.layouts.BudgetsLayout.budgetRemainingOffset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.command.log.internal.layouts.BudgetsLayout;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public final class LogBudgetsCommand implements Runnable
{
    private static final Pattern BUDGETS_PATTERN = Pattern.compile("budgets(\\d+)");

    private final Path directory;
    private final boolean verbose;
    private final Logger out;
    private final long affinity;

    private final Map<Path, BudgetsLayout> layoutsByPath;

    public LogBudgetsCommand(
        EngineConfiguration config,
        Logger out,
        boolean verbose,
        long affinity)
    {
        this.directory = config.directory();
        this.out = out;
        this.verbose = verbose;
        this.affinity = affinity;
        this.layoutsByPath = new LinkedHashMap<>();
    }

    private boolean isBuffersFile(
        Path path)
    {
        boolean accept = path.getNameCount() - directory.getNameCount() == 1 && Files.isRegularFile(path);

        if (accept)
        {
            final String filename = path.getName(path.getNameCount() - 1).toString();
            final Matcher matcher = BUDGETS_PATTERN.matcher(filename);
            accept = accept && matcher.matches();
            accept = accept && matcher.groupCount() == 1;
            accept = accept && ((1 << Integer.parseInt(matcher.group(1))) & affinity) != 0;
        }

        return accept;
    }

    private void onDiscovered(
        Path path)
    {
        if (verbose)
        {
            out.printf("Discovered: %s\n", path);
        }
    }

    private void displayBudgets(
        Path path)
    {
        BudgetsLayout layout = layoutsByPath.computeIfAbsent(path, this::newBudgetsLayout);
        final String name = path.getFileName().toString();
        displayBudgets(name, layout);
    }

    private BudgetsLayout newBudgetsLayout(
        Path path)
    {
        return new BudgetsLayout.Builder()
                .path(path)
                .owner(false)
                .build();
    }

    private void displayBudgets(
        String name,
        BudgetsLayout budgets)
    {
        final DirectBuffer buffer = budgets.buffer();
        final int entries = budgets.entries();

        for (int index = 0; index < entries; index++)
        {
            final long budgetId = buffer.getLong(budgetIdOffset(index));
            if (budgetId != 0L)
            {
                final long remaining = buffer.getLong(budgetRemainingOffset(index));
                out.printf("%s [0x%016x] [0x%08x]\n", name, budgetId, remaining);
            }
        }
    }

    @Override
    public void run()
    {
        try (Stream<Path> files = Files.walk(directory, 3))
        {
            files.filter(this::isBuffersFile)
                 .peek(this::onDiscovered)
                 .forEach(this::displayBudgets);
            out.printf("\n");
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }
}
