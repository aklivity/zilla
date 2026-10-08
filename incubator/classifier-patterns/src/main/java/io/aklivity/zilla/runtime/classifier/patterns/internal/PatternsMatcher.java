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

    private int found;
    private int start;
    private int end;

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
        for (int i = 0; i < matchers.length; i++)
        {
            matchers[i] = patterns.get(i).matcher(input);
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
}
