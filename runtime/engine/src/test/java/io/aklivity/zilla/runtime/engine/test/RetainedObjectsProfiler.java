/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test;

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.JMException;
import javax.management.ObjectName;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.TextResult;

/**
 * Names the objects a benchmark operation leaves live, so the heap reported by {@link RetainedHeapProfiler}
 * can be attributed to classes.
 * <p>
 * A class histogram of live objects (which forces a full collection) is taken before and after every
 * iteration, and the growth of each class is divided by the operations executed in that iteration. The
 * classes that grew most are listed, largest first, as bytes and objects per operation. The same lifecycle
 * rules as {@link RetainedHeapProfiler} apply: state must be released only at trial teardown, and the last
 * iteration is profiled after teardown so it lists nothing.
 * <p>
 * Use with {@code -prof io.aklivity.zilla.runtime.engine.test.RetainedObjectsProfiler}.
 */
public final class RetainedObjectsProfiler implements InternalProfiler
{
    private static final String LABEL = "retained.objects";
    private static final int TOP = 15;
    private static final Pattern ROW = Pattern.compile("^\\s*\\d+:\\s+(\\d+)\\s+(\\d+)\\s+(\\S+)");

    private Map<String, long[]> before;

    @Override
    public void beforeIteration(
        BenchmarkParams benchmarkParams,
        IterationParams iterationParams)
    {
        before = histogram();
    }

    @Override
    public Collection<? extends Result> afterIteration(
        BenchmarkParams benchmarkParams,
        IterationParams iterationParams,
        IterationResult result)
    {
        final Map<String, long[]> after = histogram();
        final long operations = Math.max(result.getMetadata().getAllOps(), 1L);

        final StringBuilder text = new StringBuilder("retained objects per operation:\n");
        after.entrySet().stream()
            .map(entry -> new Growth(entry.getKey(), entry.getValue(), before.get(entry.getKey())))
            .filter(growth -> growth.bytes > 0L)
            .sorted((a, b) -> Long.compare(b.bytes, a.bytes))
            .limit(TOP)
            .forEach(growth -> text.append(String.format("%12.1f B/op %9.2f obj/op  %s%n",
                (double) growth.bytes / operations, (double) growth.objects / operations, growth.name)));

        return List.of(new TextResult(text.toString(), LABEL));
    }

    @Override
    public String getDescription()
    {
        return "Classes whose live objects grow per operation";
    }

    private static Map<String, long[]> histogram()
    {
        final Map<String, long[]> counts = new HashMap<>();

        try
        {
            final String text = (String) ManagementFactory.getPlatformMBeanServer().invoke(
                new ObjectName("com.sun.management:type=DiagnosticCommand"),
                "gcClassHistogram",
                new Object[] { new String[0] },
                new String[] { String[].class.getName() });

            for (String line : text.split("\n"))
            {
                final Matcher row = ROW.matcher(line);
                if (row.find())
                {
                    counts.put(row.group(3), new long[] { Long.parseLong(row.group(1)), Long.parseLong(row.group(2)) });
                }
            }
        }
        catch (JMException ex)
        {
            throw new IllegalStateException("class histogram unavailable", ex);
        }

        return counts;
    }

    private static final class Growth
    {
        private final String name;
        private final long objects;
        private final long bytes;

        private Growth(
            String name,
            long[] after,
            long[] before)
        {
            this.name = name;
            this.objects = after[0] - (before != null ? before[0] : 0L);
            this.bytes = after[1] - (before != null ? before[1] : 0L);
        }
    }
}
