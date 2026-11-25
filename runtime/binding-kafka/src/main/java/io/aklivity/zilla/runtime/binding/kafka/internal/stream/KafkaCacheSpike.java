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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import java.util.concurrent.atomic.AtomicLong;

final class KafkaCacheSpike
{
    static final boolean INITIAL_DOORBELL = Boolean.parseBoolean(System.getProperty("zilla.spike.initial.doorbell", "true"));
    static final boolean REPLY_DOORBELL = Boolean.getBoolean("zilla.spike.reply.doorbell");
    static final boolean REPLY_WINDOW_DROP = Boolean.getBoolean("zilla.spike.reply.window.drop");

    static final AtomicLong INITIAL_ATTEMPTS = new AtomicLong();
    static final AtomicLong INITIAL_SENT = new AtomicLong();
    static final AtomicLong REPLY_ATTEMPTS = new AtomicLong();
    static final AtomicLong REPLY_SENT = new AtomicLong();
    static final AtomicLong WINDOW_ATTEMPTS = new AtomicLong();
    static final AtomicLong WINDOW_SENT = new AtomicLong();

    static
    {
        if (Boolean.getBoolean("zilla.spike.counters"))
        {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.err.format(
                "SPIKE initial.doorbell=%b reply.doorbell=%b reply.window.drop=%b " +
                "initial attempts=%d sent=%d reply attempts=%d sent=%d window attempts=%d sent=%d%n",
                INITIAL_DOORBELL, REPLY_DOORBELL, REPLY_WINDOW_DROP,
                INITIAL_ATTEMPTS.get(), INITIAL_SENT.get(), REPLY_ATTEMPTS.get(), REPLY_SENT.get(),
                WINDOW_ATTEMPTS.get(), WINDOW_SENT.get())));
        }
    }

    private KafkaCacheSpike()
    {
    }
}
