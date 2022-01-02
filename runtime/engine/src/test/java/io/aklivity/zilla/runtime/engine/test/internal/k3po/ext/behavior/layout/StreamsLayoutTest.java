/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.layout;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public final class StreamsLayoutTest
{
    @Test
    public void shouldUnlockStreamsFile() throws Exception
    {
        String fileName = "target/zilla-itests/data127";
        Path streams = Paths.get(fileName);
        StreamsLayout streamsLayout = new StreamsLayout.Builder()
                .path(streams)
                .streamsCapacity(8192)
                .readonly(false)
                .build();
        streamsLayout.close();
        assertTrue(Files.exists(streams));
        Files.delete(streams);
    }
}
