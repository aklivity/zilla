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
package io.aklivity.zilla.runtime.engine.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReadyTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shouldNotBeInitializedWhenMarkerAbsent() throws Exception
    {
        Path directory = folder.getRoot().toPath();

        assertThat(Ready.initialized(directory, Instant.now()), is(false));
    }

    @Test
    public void shouldBeInitializedWhenMarkerCreatedAfterStartTime() throws Exception
    {
        Path directory = folder.getRoot().toPath();
        Instant startTime = Instant.now().minus(1, ChronoUnit.SECONDS);

        Ready.markReady(directory);

        assertThat(Ready.initialized(directory, startTime), is(true));
    }

    @Test
    public void shouldNotBeInitializedWhenMarkerStaleFromPriorRun() throws Exception
    {
        Path directory = folder.getRoot().toPath();

        Ready.markReady(directory);
        Instant startTime = Instant.now().plus(1, ChronoUnit.SECONDS);

        assertThat(Ready.initialized(directory, startTime), is(false));
    }

    @Test
    public void shouldRecreateMarkerWhenAlreadyExists() throws Exception
    {
        Path directory = folder.getRoot().toPath();

        Ready.markReady(directory);
        Ready.markReady(directory);

        assertThat(Files.exists(directory.resolve("ready")), is(true));
    }
}
