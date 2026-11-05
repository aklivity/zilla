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
        byte[] value)
    {
        final int length = codePoints(value);
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

        return bytes(resolved, value);
    }

    private static List<PresidioFinding> bytes(
        List<PresidioFinding> findings,
        byte[] value)
    {
        List<PresidioFinding> converted = new ArrayList<>(findings.size());
        int point = 0;
        int position = 0;

        for (PresidioFinding finding : findings)
        {
            final int start = skip(value, position, finding.start() - point);
            final int end = skip(value, start, finding.end() - finding.start());

            converted.add(new PresidioFinding(finding.entity(), start, end, finding.score()));
            point = finding.end();
            position = end;
        }

        return converted;
    }

    private static int codePoints(
        byte[] value)
    {
        int count = 0;

        for (byte octet : value)
        {
            if ((octet & 0xC0) != 0x80)
            {
                count++;
            }
        }

        return count;
    }

    private static int skip(
        byte[] value,
        int from,
        int points)
    {
        int position = from;

        for (int i = 0; i < points; i++)
        {
            position++;

            while (position < value.length && (value[position] & 0xC0) == 0x80)
            {
                position++;
            }
        }

        return position;
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
