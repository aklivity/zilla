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
package io.aklivity.zilla.cloud.docker.bench;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@Fork(10)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@OutputTimeUnit(MILLISECONDS)
public class ZillaStartupBM
{
    private static final String CONFIG =
        """
        name: startup
        bindings:
          tcp_server0:
            type: tcp
            kind: server
            options:
              host: 0.0.0.0
              port: 8080
            exit: echo_server0
          echo_server0:
            type: echo
            kind: server
        """;

    private static final String CONTAINER_CONFIG = "/tmp/zilla.yaml";
    private static final String READY_PATH = "/var/run/zilla/ready";
    private static final Duration HEALTHY_TIMEOUT = Duration.ofMinutes(2);
    private static final DateTimeFormatter STAT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS xx");

    @Param("")
    public String image;

    private String imageName;
    private Path config;
    private String container;

    @Setup(Level.Trial)
    public void writeConfig() throws IOException
    {
        imageName = image.isEmpty() ? defaultImage() : image;
        config = Files.createTempDirectory("zilla-startup").resolve("zilla.yaml");
        Files.writeString(config, CONFIG);
    }

    @TearDown(Level.Trial)
    public void deleteConfig() throws IOException
    {
        Files.deleteIfExists(config);
        Files.deleteIfExists(config.getParent());
    }

    @Setup(Level.Invocation)
    public void createContainer() throws IOException, InterruptedException
    {
        container = docker(
            "create",
            "--health-cmd", "test -f " + READY_PATH,
            "--health-interval", "50ms",
            "--health-start-interval", "50ms",
            "--health-start-period", "2m",
            imageName, "start", "-c", CONTAINER_CONFIG);
        docker("cp", config.toString(), container + ":" + CONTAINER_CONFIG);
    }

    @TearDown(Level.Invocation)
    public void removeContainer() throws IOException, InterruptedException
    {
        docker("rm", "-f", container);
    }

    @Benchmark
    public void startup(
        StartupCounters counters) throws IOException, InterruptedException
    {
        docker("start", container);
        awaitHealthy();

        final Instant started = Instant.parse(docker("inspect", "-f", "{{.State.StartedAt}}", container));
        final Instant ready = OffsetDateTime.parse(docker("exec", container, "stat", "-c", "%y", READY_PATH), STAT_TIME)
            .toInstant();

        counters.startupMs = Duration.between(started, ready).toNanos() / 1_000_000.0;
    }

    private void awaitHealthy() throws IOException, InterruptedException
    {
        final Instant deadline = Instant.now().plus(HEALTHY_TIMEOUT);

        String state = docker("inspect", "-f", "{{.State.Status}} {{.State.Health.Status}}", container);
        while (!"running healthy".equals(state))
        {
            if (!state.startsWith("running") || Instant.now().isAfter(deadline))
            {
                throw new IllegalStateException(
                    "zilla not healthy: %s%n%s".formatted(state, docker("logs", container)));
            }

            Thread.sleep(25);
            state = docker("inspect", "-f", "{{.State.Status}} {{.State.Health.Status}}", container);
        }
    }

    private static String defaultImage() throws IOException
    {
        final Properties properties = new Properties();
        try (InputStream input = ZillaStartupBM.class.getResourceAsStream("ZillaStartupBM.properties"))
        {
            properties.load(input);
        }

        return properties.getProperty("image");
    }

    private static String docker(
        String... arguments) throws IOException, InterruptedException
    {
        final List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));

        final Process process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .start();

        final String output = new String(process.getInputStream().readAllBytes(), UTF_8).trim();

        if (process.waitFor() != 0)
        {
            throw new IllegalStateException("%s failed:%n%s".formatted(String.join(" ", command), output));
        }

        return output;
    }

    public static void main(
        String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
            .include(ZillaStartupBM.class.getSimpleName())
            .build();

        new Runner(opt).run();
    }
}
