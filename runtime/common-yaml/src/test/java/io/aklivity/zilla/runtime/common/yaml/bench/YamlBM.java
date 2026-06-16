/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.yaml.bench;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.StringReader;
import java.io.StringWriter;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.common.yaml.Yaml;
import io.aklivity.zilla.runtime.common.yaml.YamlEvent;
import io.aklivity.zilla.runtime.common.yaml.YamlParser;
import io.aklivity.zilla.runtime.common.yaml.YamlValue;

/**
 * Exercises the native {@code Yaml} facade over inputs representative of Zilla config: block mappings with
 * indentless sequences (the common case), flow collections with comments, and anchors with merge keys. Each
 * input is driven through both the streaming event parser ({@code Yaml.createParser}) and the source-preserving
 * tree reader ({@code Yaml.createReader().readValue()}) so event throughput can be compared against tree
 * materialization, and (under {@code -prof gc}) allocation per document can be read directly. A streaming
 * generate benchmark provides the inverse: emitting a config-shaped document through the builder API.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class YamlBM
{
    private static final String BLOCK_CONFIG = """
        name: example
        bindings:
          tcp0:
            type: tcp
            kind: server
            options:
              host: 0.0.0.0
              port: 7114
            routes:
            - exit: http0
              when:
              - port: 7114
          http0:
            type: http
            kind: server
            routes:
            - exit: echo0
              when:
              - headers:
                  ":path": /
        """;

    private static final String FLOW = """
        name: test # trailing comment
        values: [1, true, false, null, "a # value", {path: "/a#b"}]
        matrix: [[1, 2], [3, 4], [5, 6]]
        """;

    private static final String ANCHORS_MERGE = """
        defaults: &defaults
          type: test
          enabled: true
        base: &base
          host: localhost
          port: 7114
        tls: &tls
          port: 443
          secure: true
        route:
          <<: [*base, *tls]
          host: example.com
        binding:
          <<: *defaults
          enabled: false
        """;

    @Benchmark
    public int parseBlockConfig()
    {
        return parse(BLOCK_CONFIG);
    }

    @Benchmark
    public int parseFlow()
    {
        return parse(FLOW);
    }

    @Benchmark
    public int parseAnchorsMerge()
    {
        return parse(ANCHORS_MERGE);
    }

    @Benchmark
    public int readValueBlockConfig()
    {
        return readValue(BLOCK_CONFIG);
    }

    @Benchmark
    public int readValueAnchorsMerge()
    {
        return readValue(ANCHORS_MERGE);
    }

    @Benchmark
    public int generateBlockConfig()
    {
        StringWriter out = new StringWriter();
        Yaml.createGenerator(out)
            .writeStartObject()
                .write("name", "example")
                .writeStartObject("bindings")
                    .writeStartObject("tcp0")
                        .write("type", "tcp")
                        .write("kind", "server")
                        .writeStartObject("options")
                            .write("host", "0.0.0.0")
                            .write("port", 7114)
                        .writeEnd()
                        .writeStartArray("routes")
                            .writeStartObject()
                                .write("exit", "http0")
                                .writeStartArray("when")
                                    .writeStartObject()
                                        .write("port", 7114)
                                    .writeEnd()
                                .writeEnd()
                            .writeEnd()
                        .writeEnd()
                    .writeEnd()
                .writeEnd()
            .writeEnd()
            .close();
        return out.getBuffer().length();
    }

    private int parse(
        String text)
    {
        YamlParser parser = Yaml.createParser(new StringReader(text));
        int hash = 0;
        while (parser.hasNext())
        {
            YamlEvent event = parser.next();
            String value = event.getString();
            if (value != null)
            {
                hash += value.hashCode();
            }
        }
        return hash;
    }

    private int readValue(
        String text)
    {
        YamlValue value = Yaml.createReader(new StringReader(text)).readValue();
        return System.identityHashCode(value);
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(YamlBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
