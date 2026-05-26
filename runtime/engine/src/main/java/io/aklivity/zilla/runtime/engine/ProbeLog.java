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
package io.aklivity.zilla.runtime.engine;

import java.util.concurrent.ConcurrentLinkedDeque;

public final class ProbeLog
{
    private static final long EPOCH = System.nanoTime();
    private static final ConcurrentLinkedDeque<String> BUFFER = new ConcurrentLinkedDeque<>();

    public static void log(
        String fmt,
        Object... args)
    {
        final long t = (System.nanoTime() - EPOCH) / 1000L;
        final String line = String.format("%010d %s", t, String.format(fmt, args));
        BUFFER.add(line);
        System.err.println("[probe] " + line);
    }

    public static void dump(
        String tag)
    {
        System.err.println("====== PROBE " + tag + " (" + BUFFER.size() + " entries) ======");
        for (String s : BUFFER)
        {
            System.err.println(s);
        }
        System.err.println("====== END " + tag + " ======");
        BUFFER.clear();
    }

    private ProbeLog()
    {
    }
}
