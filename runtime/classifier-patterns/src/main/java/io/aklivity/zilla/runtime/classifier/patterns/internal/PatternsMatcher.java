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
package io.aklivity.zilla.runtime.classifier.patterns.internal;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PatternsMatcher
{
    private final double entropy;
    private final String[] labels;
    private final Matcher[] matchers;
    private final PatternsInput input;
    private final PatternsEntropy shannon;

    private final int[] candidateStarts;
    private final int[] candidateEnds;

    private int found;
    private int start;
    private int end;

    private int[] spanIndexes;
    private int[] spanStarts;
    private int[] spanEnds;
    private int spanCount;

    PatternsMatcher(
        double entropy,
        List<String> labels,
        List<Pattern> patterns)
    {
        this.entropy = entropy;
        this.labels = labels.toArray(String[]::new);
        this.input = new PatternsInput();
        this.shannon = new PatternsEntropy();
        this.matchers = new Matcher[patterns.size()];
        this.candidateStarts = new int[matchers.length];
        this.candidateEnds = new int[matchers.length];
        this.spanIndexes = new int[16];
        this.spanStarts = new int[16];
        this.spanEnds = new int[16];
        for (int i = 0; i < matchers.length; i++)
        {
            matchers[i] = patterns.get(i).matcher(input).useTransparentBounds(true).useAnchoringBounds(false);
        }
    }

    boolean find(
        String value,
        long steps)
    {
        input.reset(value, steps);

        found = -1;
        for (int i = 0; found == -1 && i < matchers.length; i++)
        {
            final Matcher matcher = matchers[i].reset(input);

            while (found == -1 && matcher.find())
            {
                if (matcher.end() > matcher.start() &&
                    (entropy <= 0.0 || shannon.measure(input, matcher.start(), matcher.end()) >= entropy))
                {
                    found = i;
                    start = matcher.start();
                    end = matcher.end();
                }
            }
        }

        return found != -1;
    }

    int locate(
        String value,
        long steps)
    {
        input.reset(value, steps);

        spanCount = 0;
        for (int i = 0; i < matchers.length; i++)
        {
            matchers[i].reset(input);
            advance(i, 0);
        }

        for (int best = best(); best != -1; best = best())
        {
            final int limit = candidateEnds[best];

            append(best, candidateStarts[best], limit);

            for (int i = 0; i < matchers.length; i++)
            {
                if (candidateStarts[i] != -1 && candidateStarts[i] < limit)
                {
                    advance(i, limit);
                }
            }
        }

        return spanCount;
    }

    int retained()
    {
        final int length = input.length();
        int earliest = length;

        for (Matcher matcher : matchers)
        {
            for (int from = 0; from < earliest; from++)
            {
                matcher.region(from, length).lookingAt();

                if (matcher.hitEnd())
                {
                    earliest = from;
                }
            }
        }

        return length - earliest;
    }

    String label()
    {
        return labels[found];
    }

    int start()
    {
        return start;
    }

    int end()
    {
        return end;
    }

    String label(
        int span)
    {
        return labels[spanIndexes[span]];
    }

    int start(
        int span)
    {
        return spanStarts[span];
    }

    int end(
        int span)
    {
        return spanEnds[span];
    }

    private void advance(
        int index,
        int limit)
    {
        final Matcher matcher = matchers[index];

        candidateStarts[index] = -1;
        while (candidateStarts[index] == -1 && matcher.find())
        {
            if (matcher.end() > matcher.start() &&
                matcher.start() >= limit &&
                (entropy <= 0.0 || shannon.measure(input, matcher.start(), matcher.end()) >= entropy))
            {
                candidateStarts[index] = matcher.start();
                candidateEnds[index] = matcher.end();
            }
        }
    }

    private int best()
    {
        int best = -1;

        for (int i = 0; i < matchers.length; i++)
        {
            if (candidateStarts[i] != -1 &&
                (best == -1 ||
                 candidateStarts[i] < candidateStarts[best] ||
                 candidateStarts[i] == candidateStarts[best] && candidateEnds[i] > candidateEnds[best]))
            {
                best = i;
            }
        }

        return best;
    }

    private void append(
        int index,
        int start,
        int end)
    {
        if (spanCount == spanIndexes.length)
        {
            final int capacity = spanCount << 1;
            spanIndexes = Arrays.copyOf(spanIndexes, capacity);
            spanStarts = Arrays.copyOf(spanStarts, capacity);
            spanEnds = Arrays.copyOf(spanEnds, capacity);
        }

        spanIndexes[spanCount] = index;
        spanStarts[spanCount] = start;
        spanEnds[spanCount] = end;
        spanCount++;
    }
}
