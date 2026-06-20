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

import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

/**
 * Exercises the {@code YamlJson} adapter that bridges YAML source to the {@code jakarta.json} streaming API:
 * {@code YamlJsonParser} projects YAML (block mappings, JSON-syntax documents, anchors and merge keys) into
 * {@code JsonParser} events, while {@code YamlJsonGenerator} renders YAML from {@code JsonGenerator} calls.
 * The parse benchmarks materialize key and scalar text via {@code getString()} so the projection cost — not
 * just event dispatch — is measured; with {@code -prof gc} the allocation per document is reported directly.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class YamlJsonBM
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
        """;

    private static final String JSON_DOCUMENT = """
        {"name":"test","enabled":true,"items":[{"id":1,"name":"a"},{"id":2,"name":"b"}],
         "nested":{"x":1,"y":2},"missing":null}
        """;

    private static final String ANCHORS_MERGE = """
        base: &base
          host: localhost
          port: 7114
        tls: &tls
          port: 443
          secure: true
        route:
          <<: [*base, *tls]
          host: example.com
        """;

    private static final String QUOTED_CONFIG = """
        name: "example service"
        bindings:
          tcp0:
            type: tcp
            kind: server
            options:
              host: "0.0.0.0"
              port: 7114
            routes:
            - exit: 'http0'
              when:
              - port: 7114
        """;

    private static final String QUOTED_VALUE = "value: with #special \"chars\" and \\backslash";

    private static final String[] SCALAR_ITEMS = scalarItems();
    private static final String[] OBJECT_KEYS = objectKeys();

    @Benchmark
    public int parseBlockConfig()
    {
        return parse(BLOCK_CONFIG);
    }

    @Benchmark
    public int parseJsonDocument()
    {
        return parse(JSON_DOCUMENT);
    }

    @Benchmark
    public int parseAnchorsMerge()
    {
        return parse(ANCHORS_MERGE);
    }

    @Benchmark
    public int parseQuotedConfig()
    {
        return parse(QUOTED_CONFIG);
    }

    @Benchmark
    public void generateBlockConfig(
        Blackhole blackhole)
    {
        YamlJson.createGenerator(new BlackholeWriter(blackhole))
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
    }

    @Benchmark
    public void generateScalarArray(
        Blackhole blackhole)
    {
        JsonGenerator generator = YamlJson.createGenerator(new BlackholeWriter(blackhole));
        generator.writeStartObject()
            .writeStartArray("values");
        for (int index = 0; index < 64; index++)
        {
            if ((index & 1) == 0)
            {
                generator.write(SCALAR_ITEMS[index >> 1]);
            }
            else
            {
                generator.write(index);
            }
        }
        generator.writeEnd()
            .writeEnd()
            .close();
    }

    @Benchmark
    public void generateQuotedScalars(
        Blackhole blackhole)
    {
        JsonGenerator generator = YamlJson.createGenerator(new BlackholeWriter(blackhole));
        generator.writeStartObject();
        for (int index = 0; index < OBJECT_KEYS.length; index++)
        {
            generator.write(OBJECT_KEYS[index], QUOTED_VALUE);
        }
        generator.writeEnd()
            .close();
    }

    private static String[] scalarItems()
    {
        String[] items = new String[32];
        for (int index = 0; index < items.length; index++)
        {
            items[index] = "item" + (index << 1);
        }
        return items;
    }

    private static String[] objectKeys()
    {
        String[] keys = new String[32];
        for (int index = 0; index < keys.length; index++)
        {
            keys[index] = "key" + index;
        }
        return keys;
    }

    private int parse(
        String text)
    {
        JsonParser parser = YamlJson.createParser(new StringReader(text));
        int hash = 0;
        while (parser.hasNext())
        {
            JsonParser.Event event = parser.next();
            switch (event)
            {
            case KEY_NAME, VALUE_STRING, VALUE_NUMBER -> hash += parser.getString().hashCode();
            default -> hash += event.ordinal();
            }
        }
        return hash;
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(YamlJsonBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
