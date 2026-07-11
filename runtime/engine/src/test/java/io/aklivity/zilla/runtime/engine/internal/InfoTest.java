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
import static org.hamcrest.Matchers.equalTo;

import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class InfoTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shouldReadInfoWrittenByLiveEngine() throws Exception
    {
        // GIVEN
        Path directory = folder.getRoot().toPath();
        try (Info written = new Info.Builder()
            .directory(directory)
            .workers(4)
            .readonly(false)
            .build())
        {
            assertThat(written.workers(), equalTo(4));
        }

        // WHEN
        try (Info read = new Info.Builder()
            .directory(directory)
            .readonly(true)
            .build())
        {
            // THEN
            assertThat(read.workers(), equalTo(4));
        }
    }
}
