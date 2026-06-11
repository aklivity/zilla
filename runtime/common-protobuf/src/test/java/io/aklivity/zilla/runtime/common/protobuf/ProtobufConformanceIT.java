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
package io.aklivity.zilla.runtime.common.protobuf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

import io.aklivity.zilla.runtime.common.protobuf.internal.ConformanceTestee;

/**
 * Runs the native protobuf {@code conformance_test_runner} against the {@code common-protobuf}
 * binary testee, with the runner as judge.
 * <p>
 * The pinned runner image (built from {@code src/test/conformance/Dockerfile}) bundles the runner,
 * {@code protoc}, and the conformance {@code FileDescriptorSet}; it changes only with the protobuf
 * version. The testee classpath is copied in at runtime, so iterating on Java never rebuilds it.
 * The runner exercises its full generated case set; JSON/text cases are reported skipped (owned by
 * {@code model-protobuf}), and binary gaps are tracked in {@code runner-failure-list.txt}. Skipped
 * where Docker is unavailable.
 */
public class ProtobufConformanceIT
{
    private static final Path CONFORMANCE = Path.of("src", "test", "conformance");
    private static final String TESTEE_CLASSPATH = "/app/classes:/app/test-classes:/app/libs/*";

    @Test
    public void shouldPassBinaryConformanceSuite() throws Exception
    {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required");

        ImageFromDockerfile image = new ImageFromDockerfile("zilla/common-protobuf-conformance", false)
            .withFileFromPath("Dockerfile", CONFORMANCE.resolve("Dockerfile"));

        try (GenericContainer<?> container = new GenericContainer<>(image)
            .withCommand("tail", "-f", "/dev/null"))
        {
            container.start();

            container.copyFileToContainer(MountableFile.forHostPath("target/classes"), "/app/classes");
            container.copyFileToContainer(MountableFile.forHostPath("target/test-classes"), "/app/test-classes");
            for (Path jar : libraryJars())
            {
                container.copyFileToContainer(MountableFile.forHostPath(jar.toString()),
                    "/app/libs/" + jar.getFileName());
            }
            container.copyFileToContainer(MountableFile.forHostPath(CONFORMANCE.resolve("runner-failure-list.txt").toString()),
                "/conformance/failure_list.txt");

            String command = "conformance_test_runner --enforce_recommended " +
                "--failure_list /conformance/failure_list.txt -- " +
                "java -cp '" + TESTEE_CLASSPATH + "' " + ConformanceTestee.class.getName() +
                " /conformance/descriptors.bin";
            Container.ExecResult result = container.execInContainer("bash", "-lc", command);

            System.out.println(result.getStdout());
            System.err.println(result.getStderr());
            assertEquals(0, result.getExitCode(), "conformance runner reported failures:\n" + result.getStdout());
        }
    }

    private static List<Path> libraryJars()
    {
        List<Path> jars = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator))
        {
            if (entry.endsWith(".jar") && entry.contains("agrona"))
            {
                jars.add(Path.of(entry));
            }
        }
        return jars;
    }
}
