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
package io.aklivity.zilla.runtime.binding.mcp.internal.eager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolEagerDocument;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEager;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEagerRecorder;

/**
 * Only candidates matching one of the configured glob patterns are admitted -- a hard
 * membership ceiling, preserving the input's relative order among admitted names.
 */
final class McpExplicitToolsEager implements McpToolsEager
{
    private final Consumer<Runnable> dispatch;
    private final List<Pattern> match;

    McpExplicitToolsEager(
        Consumer<Runnable> dispatch,
        List<String> match)
    {
        this.dispatch = dispatch;
        this.match = compileMatch(match);
    }

    @Override
    public void index(
        Collection<McpToolEagerDocument> documents,
        CompletionCallback<Void> completed)
    {
        dispatch.accept(() -> completed.completed(null));
    }

    @Override
    public List<CharSequence> select(
        long authorization,
        List<CharSequence> names)
    {
        List<CharSequence> selected = new ArrayList<>();
        for (CharSequence name : names)
        {
            if (admits(name))
            {
                selected.add(name);
            }
        }
        return selected;
    }

    @Override
    public McpToolsEagerRecorder recorder()
    {
        return McpToolsEagerRecorder.NONE;
    }

    private boolean admits(
        CharSequence name)
    {
        boolean admitted = false;
        for (Pattern pattern : match)
        {
            if (pattern.matcher(name).matches())
            {
                admitted = true;
                break;
            }
        }
        return admitted;
    }

    private static List<Pattern> compileMatch(
        List<String> globs)
    {
        return globs.stream()
            .map(McpExplicitToolsEager::compileGlob)
            .collect(Collectors.toList());
    }

    private static Pattern compileGlob(
        String glob)
    {
        final StringBuilder regex = new StringBuilder();
        final String[] literals = glob.split("\\*", -1);
        for (int index = 0; index < literals.length; index++)
        {
            if (index > 0)
            {
                regex.append(".*");
            }
            if (!literals[index].isEmpty())
            {
                regex.append(Pattern.quote(literals[index]));
            }
        }
        return Pattern.compile(regex.toString());
    }
}
