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
package io.aklivity.zilla.runtime.binding.tcp.internal.bench;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CONFIG_URL;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class TcpServerBM
{
    private final Engine engine;
    //private long routedId;

    {
        Properties props = new Properties();
        props.setProperty(ENGINE_DIRECTORY.name(), "target/zilla-benchmarks");
        props.setProperty(ENGINE_STREAMS_BUFFER_CAPACITY.name(), Long.toString(1024L * 1024L * 16L));

        final EngineConfiguration config = new EngineConfiguration(props);
        URL configURL = config.configURL();
        if ("file".equals(configURL.getProtocol()))
        {
            Path configPath = Paths.get(configURL.getPath());
            if (configPath.endsWith("zilla.yaml") && Files.notExists(configPath))
            {
                String configJSON = String.format("file:%s", configPath.resolveSibling("zilla.json"));
                props.setProperty(ENGINE_CONFIG_URL.name(), configJSON);
                configPath = Paths.get(config.configURL().getPath());
                System.out.println("zilla.yaml file not found, loading zilla.json instead");
            }
            if (configPath.getFileName().toString().endsWith(".json"))
            {
                System.out.println("warning: json syntax is deprecated, migrate to yaml");
            }
        }

        this.engine = Engine.builder()
                    .config(config)
                    .errorHandler(ex -> ex.printStackTrace(System.err))
                    .build();
    }

    @Setup(Level.Trial)
    public void reinit() throws Exception
    {
        engine.start();
        //routedId = controller.route(RouteKind.SERVER, "127.0.0.1:8080", "tcp#0").get();
    }

    @TearDown(Level.Trial)
    public void reset() throws Exception
    {
        //controller.unroute(routedId).get();
        engine.close();
    }

    @State(Scope.Group)
    public static class GroupState
    {
        private final ByteBuffer sendByteBuffer;
        private final ByteBuffer receiveByteBuffer;

        private SocketChannel channel;

        public GroupState()
        {
            final byte[] sendByteArray = new byte[512];
            final Random random = new Random();
            for (int i = 0; i < sendByteArray.length; i++)
            {
                sendByteArray[i] = (byte) random.nextInt();
            }

            this.sendByteBuffer = allocateDirect(sendByteArray.length).order(nativeOrder()).put(sendByteArray);
            this.receiveByteBuffer = allocateDirect(8192).order(nativeOrder());
        }

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            channel = SocketChannel.open();
            channel.connect(new InetSocketAddress("127.0.0.1", 8080));
            channel.configureBlocking(false);
        }

        @TearDown(Level.Trial)
        public void reset() throws Exception
        {
            channel.close();
        }
    }

    @Benchmark
    @Group("echo")
    @GroupThreads(1)
    public void reader(
        final GroupState state) throws Exception
    {
        final SocketChannel channel = state.channel;
        final ByteBuffer receiveByteBuffer = state.receiveByteBuffer;

        receiveByteBuffer.position(0);
        if (channel.read(receiveByteBuffer) == 0)
        {
            Thread.yield();
        }
    }

    @Benchmark
    @Group("echo")
    @GroupThreads(1)
    public void writer(
        final GroupState state) throws Exception
    {
        final SocketChannel channel = state.channel;
        final ByteBuffer sendByteBuffer = state.sendByteBuffer;

        sendByteBuffer.position(0);
        while (sendByteBuffer.hasRemaining())
        {
            if (channel.write(sendByteBuffer) == 0)
            {
                Thread.yield();
            }
        }
    }

    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(TcpServerBM.class.getSimpleName())
                .forks(0)
                .threads(1)
                .build();

        new Runner(opt).run();
    }
}
