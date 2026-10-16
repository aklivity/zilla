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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

// Creating a parser must not touch agrona's unsafe access, so a caller that has not opened jdk.internal.misc
// can still parse; the buffers that need it are acquired only by a stage that asks for them.
class JsonParserConstructionTest
{
    @Test
    void shouldCreateParserWithoutOpeningInternalMisc() throws Exception
    {
        Process process = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("java.class.path"),
            Launch.class.getName())
            .redirectErrorStream(true)
            .start();

        String output = new String(process.getInputStream().readAllBytes(), UTF_8);

        assertEquals(0, process.waitFor(), output);
    }

    static final class Launch
    {
        public static void main(
            String[] args) throws IOException
        {
            JsonEx.createParser();
        }
    }
}
