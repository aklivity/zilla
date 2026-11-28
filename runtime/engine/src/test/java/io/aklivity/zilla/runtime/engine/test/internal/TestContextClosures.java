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
package io.aklivity.zilla.runtime.engine.test.internal;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestContextClosures
{
    public static final List<String> CONCEPTS =
        List.of("binding", "exporter", "guard", "vault", "catalog", "embedding", "classifier", "store", "model");

    private static final Map<String, AtomicInteger> CLOSURES = new ConcurrentHashMap<>();

    public static void reset()
    {
        CLOSURES.clear();
    }

    public static void record(
        String concept)
    {
        CLOSURES.computeIfAbsent(concept, c -> new AtomicInteger()).incrementAndGet();
    }

    public static int closed(
        String concept)
    {
        return CLOSURES.computeIfAbsent(concept, c -> new AtomicInteger()).get();
    }

    private TestContextClosures()
    {
    }
}
