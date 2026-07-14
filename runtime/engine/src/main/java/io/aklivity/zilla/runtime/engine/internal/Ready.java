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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.agrona.LangUtil;

public final class Ready
{
    private static final String FILE_NAME = "ready";

    private Ready()
    {
    }

    public static boolean initialized(
        Path directory,
        Instant startTime)
    {
        boolean initialized = false;
        Path path = directory.resolve(FILE_NAME);
        if (Files.exists(path))
        {
            try
            {
                initialized = !Files.getLastModifiedTime(path).toInstant().isBefore(startTime);
            }
            catch (IOException ex)
            {
                initialized = false;
            }
        }
        return initialized;
    }

    public static void markReady(
        Path directory)
    {
        try
        {
            Path path = directory.resolve(FILE_NAME);
            Files.deleteIfExists(path);
            Files.createFile(path);
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }
}
