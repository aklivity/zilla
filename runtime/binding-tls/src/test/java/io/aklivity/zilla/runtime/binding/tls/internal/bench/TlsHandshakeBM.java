/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tls.internal.bench;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CONFIG_URL;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.net.URL;
import java.util.List;
import java.util.Properties;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Control;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class TlsHandshakeBM
{
    private static final int BUFFER_SIZE = 1024 * 8;

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[BUFFER_SIZE]);

    private BindingHandler streamFactory;
    private TlsWorker worker;

    @Setup(Level.Trial)
    public void init()
    {
        final Properties properties = new Properties();
        URL configURL = TlsHandshakeBM.class.getClassLoader().getResource("io/aklivity/zilla/specs/binding/tls/config");
        properties.setProperty(ENGINE_CONFIG_URL.name(), String.format("%s/zilla.yaml", configURL.toString()));
        final EngineConfiguration config = new EngineConfiguration(properties);
        this.worker = new TlsWorker(config);

        NamespaceConfig namespace = NamespaceConfig.builder()
            .name("tls")
            .vault()
                .name("server")
                .type("filesystem")
                .options(FileSystemOptionsConfig.builder()
                    .keys()
                        .store("stores/server/keys")
                        .type("pkcs12")
                        .password("generated")
                        .build()
                    .trust()
                        .store("stores/client/trust")
                        .type("pkcs12")
                        .password("generated")
                        .build()
                    .build())
                .build()
            .binding()
                .name("tls_client0")
                .type("tls")
                .kind(CLIENT)
                .vault("server")
                .options(TlsOptionsConfig.builder()
                    .trust(List.of("serverca"))
                    .sni(List.of("localhost"))
                    .build())
                .exit("tls_server0")
                .build()
            .binding()
                .name("tls_server0")
                .type("tls")
                .kind(SERVER)
                .vault("server")
                .options(TlsOptionsConfig.builder()
                    .keys(List.of("localhost"))
                    .version("tls")
                    .build())
                .exit("echo_server0")
                .build()
            .binding()
                .name("echo_server0")
                .type("echo")
                .kind(SERVER)
                .build()
            .build();

        worker.attach(namespace);

        streamFactory = worker.streamFactory();
    }

    @TearDown(Level.Trial)
    public void destroy()
    {
    }

    @Setup(Level.Iteration)
    public void reset()
    {
    }

    @Benchmark
    public void handshake(
        final Control control) throws Exception
    {
        final long initialId = worker.supplyInitialId(0L);
        final long replyId = worker.supplyReplyId(initialId);

        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(0L)
            .routedId(4261135416L)
            .streamId(initialId)
            .sequence(0L)
            .acknowledge(0L)
            .maximum(BUFFER_SIZE)
            .traceId(0L)
            .authorization(0L)
            .affinity(0L)
            .build();

        MessageConsumer sender = MessageConsumer.NOOP;
        MessageConsumer receiver = streamFactory.newStream(
            begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(0L)
                .routedId(4261135416L)
                .streamId(replyId)
                .sequence(0L)
                .acknowledge(0L)
                .maximum(BUFFER_SIZE)
                .traceId(0L)
                .budgetId(0L)
                .padding(0)
                .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());

        worker.doWork();
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(TlsHandshakeBM.class.getSimpleName())
                .forks(0)
                .build();

        new Runner(opt).run();
    }
}
