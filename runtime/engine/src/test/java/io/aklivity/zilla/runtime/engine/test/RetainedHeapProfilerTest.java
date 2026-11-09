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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Path;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.junit.Test;

public class RetainedHeapProfilerTest
{
    private static final String RETAINED_HEAP = "retained.heap.norm";

    @Test
    public void shouldReportRetainedHeapPerRetainingOperation() throws Exception
    {
        final double retained = run("retain");

        assertThat(retained, greaterThanOrEqualTo((double) RetainedHeapBenchmark.RETAINED_LENGTH));
        assertThat(retained, lessThan(RetainedHeapBenchmark.RETAINED_LENGTH * 1.25));
    }

    @Test
    public void shouldReportNothingForNonRetainingOperation() throws Exception
    {
        final double retained = run("release");

        assertThat(retained, lessThan(RetainedHeapBenchmark.RETAINED_LENGTH * 0.1));
    }

    private static double run(
        String method) throws Exception
    {
        final File results = File.createTempFile("retained-heap", ".json");
        results.deleteOnExit();

        final Process process = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
            "-cp", System.getProperty("java.class.path"),
            "org.openjdk.jmh.Main",
            String.format("%s.%s", RetainedHeapBenchmark.class.getSimpleName(), method),
            "-prof", RetainedHeapProfiler.class.getName(),
            "-rf", "json",
            "-rff", results.getPath())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();

        assertThat(process.waitFor(), equalTo(0));

        try (Reader reader = new FileReader(results);
             JsonReader json = Json.createReader(reader))
        {
            final JsonArray benchmarks = json.readArray();
            final JsonObject secondary = benchmarks.getJsonObject(0).getJsonObject("secondaryMetrics");

            return secondary.getJsonObject(RETAINED_HEAP).getJsonNumber("score").doubleValue();
        }
    }
}
