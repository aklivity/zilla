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
package io.aklivity.zilla.runtime.classifier.presidio.internal;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PresidioSpans
{
    private static final Comparator<PresidioFinding> ORDER = Comparator
        .comparingInt(PresidioFinding::start)
        .thenComparing(Comparator.comparingInt((PresidioFinding finding) -> finding.end() - finding.start()).reversed())
        .thenComparing(Comparator.comparingDouble(PresidioFinding::score).reversed());

    static List<PresidioFinding> resolve(
        List<PresidioFinding> findings,
        int length)
    {
        findings.forEach(finding -> validate(finding, length));

        List<PresidioFinding> ordered = findings.stream()
            .sorted(ORDER)
            .toList();

        List<PresidioFinding> resolved = new ArrayList<>(ordered.size());
        int limit = 0;

        for (PresidioFinding finding : ordered)
        {
            if (finding.start() >= limit)
            {
                resolved.add(finding);
                limit = finding.end();
            }
        }

        return resolved;
    }

    private static void validate(
        PresidioFinding finding,
        int length)
    {
        if (finding.start() < 0 || finding.end() > length || finding.start() >= finding.end())
        {
            throw new IllegalArgumentException("Finding outside of text: " + finding);
        }
    }

    private PresidioSpans()
    {
    }
}
