/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tcp.internal.util;

public final class OperationSystem
{
    public enum OS
    {
        MACOS, LINUX, UNKNOWN
    }

    private OperationSystem()
    {
        // no instances
    }

    public static OS detect()
    {
        OS os = OS.UNKNOWN;
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("mac"))
        {
            os = OS.MACOS;
        }
        else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix"))
        {
            os = OS.LINUX;
        }

        return os;
    }
}
