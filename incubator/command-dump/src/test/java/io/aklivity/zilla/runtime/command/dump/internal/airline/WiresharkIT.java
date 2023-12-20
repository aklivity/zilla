/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.dump.internal.airline;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

@TestInstance(PER_CLASS)
public class WiresharkIT
{
    private static final String TSHARK_DOCKER_IMAGE = "kreinerattila/tshark:4.2.0";
    private static final String COMMAND = "sleep infinity";
    private static final WaitStrategy WAIT_STRATEGY = Wait.forSuccessfulCommand("echo 42");

    private GenericContainer<?> tshark;

    @BeforeAll
    public void setUp() throws IOException
    {
        try
        {
            System.out.printf("Starting the container using image %s...%n", TSHARK_DOCKER_IMAGE);
            DockerImageName image = DockerImageName.parse(TSHARK_DOCKER_IMAGE);
            tshark = new GenericContainer<>(image)
                .withCommand(COMMAND)
                .waitingFor(WAIT_STRATEGY);
            tshark.start();
        }
        catch (ContainerFetchException ex)
        {
            System.out.printf("Image %s was not found, building it now...%n", TSHARK_DOCKER_IMAGE);
            ImageFromDockerfile image = new ImageFromDockerfile().withDockerfile(resourceToPath("Dockerfile"));
            tshark = new GenericContainer<>(image)
                .withCommand(COMMAND)
                .waitingFor(WAIT_STRATEGY);
            tshark.start();
        }
        assert tshark.isRunning();
        System.out.printf("Container %s (%s) is running!%n", tshark.getContainerName(), tshark.getContainerId());
        copyResource("zilla.lua", tshark, "/home/tshark/.local/lib/wireshark/plugins/zilla.lua");
    }

    @AfterAll
    public void close()
    {
        tshark.close();
    }

    @Test
    public void shouldMatchExpectedOutput() throws Exception
    {
        // GIVEN
        String pcapFileName = "expected_dump.pcap";
        String containerPath = String.format("/opt/%s", pcapFileName);
        copyResource(pcapFileName, tshark, containerPath);
        String expectedText = Files.readString(resourceToPath("expected_dump.txt"));

        // WHEN
        Container.ExecResult result = tshark.execInContainer("tshark", "-O", "zilla,http", "-r", containerPath);

        // THEN
        assertThat(result.getExitCode(), equalTo(0));
        assertThat(result.getStdout(), equalTo(expectedText));
    }

    @Test
    public void shouldMatchExpectedFilteredOutput() throws Exception
    {
        // GIVEN
        String pcapFileName = "expected_filtered_dump.pcap";
        String containerPath = String.format("/opt/%s", pcapFileName);
        copyResource(pcapFileName, tshark, containerPath);
        String expectedText = Files.readString(resourceToPath("expected_filtered_dump.txt"));

        // WHEN
        Container.ExecResult result = tshark.execInContainer("tshark", "-O", "zilla", "-r", containerPath);

        // THEN
        assertThat(result.getExitCode(), equalTo(0));
        assertThat(result.getStdout(), equalTo(expectedText));
    }

    private static Path resourceToPath(
        String name)
    {
        URL resource = WiresharkIT.class.getResource(name);
        assert resource != null;
        return Path.of(URI.create(resource.toString()));
    }

    private static void copyResource(
        String resourceName,
        GenericContainer<?> container,
        String containerPath) throws IOException
    {
        assert container.isRunning();
        try (InputStream is = WiresharkIT.class.getResourceAsStream(resourceName))
        {
            assert is != null;
            container.copyFileToContainer(Transferable.of(is.readAllBytes()), containerPath);
        }
    }
}
