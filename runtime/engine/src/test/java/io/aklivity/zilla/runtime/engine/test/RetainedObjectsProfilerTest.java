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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class RetainedObjectsProfilerTest
{
    private static final Pattern BYTE_ARRAYS = Pattern.compile("^\\s*([0-9.]+) B/op\\s+([0-9.]+) obj/op\\s+\\[B\\s*$",
        Pattern.MULTILINE);

    @Test
    public void shouldReportObjectsRetainedPerRetainingOperation() throws Exception
    {
        final String output = run("retain");

        final Matcher row = BYTE_ARRAYS.matcher(output);
        assertThat(output, row.find(), equalTo(true));
        assertThat(Double.parseDouble(row.group(2)), greaterThan(0.95));
        assertThat(Double.parseDouble(row.group(2)), lessThan(1.05));
        assertThat(Double.parseDouble(row.group(1)), greaterThan((double) RetainedHeapBenchmark.RETAINED_LENGTH));
    }

    @Test
    public void shouldReportNoRetainedByteArraysForNonRetainingOperation() throws Exception
    {
        final String output = run("release");

        final Matcher row = BYTE_ARRAYS.matcher(output);
        while (row.find())
        {
            assertThat(output, Double.parseDouble(row.group(2)), lessThan(0.1));
        }
    }

    private static String run(
        String method) throws Exception
    {
        final File output = File.createTempFile("retained-objects", ".txt");
        output.deleteOnExit();

        final Process process = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
            "-cp", System.getProperty("java.class.path"),
            "org.openjdk.jmh.Main",
            String.format("%s.%s", RetainedHeapBenchmark.class.getSimpleName(), method),
            "-prof", RetainedObjectsProfiler.class.getName())
            .redirectErrorStream(true)
            .redirectOutput(output)
            .start();

        assertThat(process.waitFor(), equalTo(0));

        return Files.readString(output.toPath());
    }
}
