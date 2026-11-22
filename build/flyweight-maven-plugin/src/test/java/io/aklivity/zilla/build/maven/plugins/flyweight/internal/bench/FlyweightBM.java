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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal.bench;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Random;
import java.util.function.Consumer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Control;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.Array32FW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.FlatFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.FlatWithOctetsFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.StructWithMultipleString8DefaultsFW;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.inner.VariantEnumKindOfStringFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 3, time = 3, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class FlyweightBM
{
    private MutableDirectBufferEx buffer;
    private MutableDirectBufferEx values;
    private long iterations;

    private FlatFW.Builder flatRW = new FlatFW.Builder();
    private FlatFW flatRO = new FlatFW();

    private FlatWithOctetsFW.Builder flatWithOctetsRW = new FlatWithOctetsFW.Builder();
    private FlatWithOctetsFW flatWithOctetsRO = new FlatWithOctetsFW();

    private final Array32FW.Builder<StructWithMultipleString8DefaultsFW.Builder, StructWithMultipleString8DefaultsFW>
        structArrayRW = new Array32FW.Builder<>(
            new StructWithMultipleString8DefaultsFW.Builder(), new StructWithMultipleString8DefaultsFW());
    private final Array32FW.Builder<VariantEnumKindOfStringFW.Builder, VariantEnumKindOfStringFW> variantArrayRW =
        new Array32FW.Builder<>(new VariantEnumKindOfStringFW.Builder(), new VariantEnumKindOfStringFW());

    private final Consumer<StructWithMultipleString8DefaultsFW.Builder> structItem0 = f -> f
        .field1(values, 0, 5).field2(values, 100, 5).field3(values, 200, 5);
    private final Consumer<StructWithMultipleString8DefaultsFW.Builder> structItem1 = f -> f
        .field1(values, 0, 20).field2(values, 100, 12).field3(values, 200, 8);
    private final Consumer<StructWithMultipleString8DefaultsFW.Builder> structItem2 = f -> f
        .field1(values, 0, 40).field2(values, 100, 8).field3(values, 200, 20);
    private final Consumer<StructWithMultipleString8DefaultsFW.Builder> structItem3 = f -> f
        .field1(values, 0, 10).field2(values, 100, 30).field3(values, 200, 6);

    private final Consumer<VariantEnumKindOfStringFW.Builder> variantItem0 = v -> v.setAsString8(values, 0, 5);
    private final Consumer<VariantEnumKindOfStringFW.Builder> variantItem1 = v -> v.setAsString8(values, 0, 20);
    private final Consumer<VariantEnumKindOfStringFW.Builder> variantItem2 = v -> v.setAsString8(values, 0, 40);
    private final Consumer<VariantEnumKindOfStringFW.Builder> variantItem3 = v -> v.setAsString8(values, 0, 10);

    @Setup(Level.Trial)
    public void init()
    {
        this.buffer = new UnsafeBufferEx(allocateDirect(1024).order(nativeOrder()));
        this.buffer.setMemory(0, 1024, (byte) new Random().nextInt(256));
        this.values = new UnsafeBufferEx(allocateDirect(1024).order(nativeOrder()));
        this.values.setMemory(0, 1024, (byte) new Random().nextInt(256));
        iterations = 0;
    }

    @Benchmark
    public int arrayOfStructs()
    {
        structArrayRW.wrap(buffer, 0, buffer.capacity())
            .item(structItem0)
            .item(structItem1)
            .item(structItem2)
            .item(structItem3);
        return structArrayRW.build().fieldCount();
    }

    @Benchmark
    public int arrayOfVariantStrings()
    {
        variantArrayRW.wrap(buffer, 0, buffer.capacity())
            .item(variantItem0)
            .item(variantItem1)
            .item(variantItem2)
            .item(variantItem3);
        return variantArrayRW.build().fieldCount();
    }

    @Benchmark
    public long flatFWUsingString(
        final Control control) throws Exception
    {
        flatRW.wrap(buffer, 0, buffer.capacity())
              .fixed1(++iterations)
              .fixed2(20)
              .string1("value1.............................................................................")
              .fixed3(30)
              .string2("value2.............................................................................")
              .build();
        return flatRO.wrap(buffer, 0, buffer.capacity()).fixed1();
    }

    @Benchmark
    public long flatFWUsingBuffer(
        final Control control) throws Exception
    {
        flatRW.wrap(buffer, 0, buffer.capacity())
              .fixed1(++iterations)
              .fixed2(20)
              .string1(values, 0, 70)
              .fixed3(30)
              .string2(values, 500, 70)
              .build();
        return flatRO.wrap(buffer, 0, buffer.capacity()).fixed1();
    }

    @Benchmark
    public long flatWithOctetsFWVisitors(
        final Control control) throws Exception
    {
        flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(++iterations)
            .octets1(b -> b.put(values, 0, 10))
            .string1(values, 0, 10)
            .octets2(b -> b.put(values, 20, 10))
            .lengthOctets3(30)
            .octets3(b -> b.put(values, 30, 30))
            .extension(b -> b.put(values, 100, 100))
            .build();
        return flatWithOctetsRO.wrap(buffer, 0, buffer.capacity()).fixed1();
    }

    @Benchmark
    public long flatWithOctetsFW(
        final Control control) throws Exception
    {
        flatWithOctetsRW.wrap(buffer, 0, buffer.capacity())
            .fixed1(++iterations)
            .octets1(values, 0, 10)
            .string1(values, 0, 10)
            .octets2(values, 20, 10)
            .lengthOctets3(30)
            .octets3(values, 30, 30)
            .extension(values, 100, 100)
            .build();
        return flatWithOctetsRO.wrap(buffer, 0, buffer.capacity()).fixed1();
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(FlyweightBM.class.getSimpleName())
                .forks(0)
                .build();

        new Runner(opt).run();
    }
}
