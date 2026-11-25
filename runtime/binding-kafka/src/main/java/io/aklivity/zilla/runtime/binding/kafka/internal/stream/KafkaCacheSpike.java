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
import java.util.concurrent.atomic.AtomicLongArray;


final class KafkaCacheSpike
{
    static final boolean INITIAL_DOORBELL = Boolean.parseBoolean(System.getProperty("zilla.spike.initial.doorbell", "true"));
    static final boolean REPLY_DOORBELL = Boolean.getBoolean("zilla.spike.reply.doorbell");
    static final boolean REPLY_WINDOW_DROP = Boolean.getBoolean("zilla.spike.reply.window.drop");

    static final boolean REPLY_DEFER = Boolean.getBoolean("zilla.spike.reply.defer");
    static final boolean COUNTERS = Boolean.getBoolean("zilla.spike.counters");

    static final int TRIGGER_ACK = 0;
    static final int TRIGGER_WINDOW = 1;
    static final int TRIGGER_DEFERRED = 2;
    static final int TRIGGER_OTHER = 3;

    static final AtomicLongArray MEMBERS_HISTOGRAM = new AtomicLongArray(9);
    static final AtomicLongArray ACKS_PER_CYCLE_HISTOGRAM = new AtomicLongArray(9);
    static final AtomicLongArray WINDOWS_PER_CYCLE_HISTOGRAM = new AtomicLongArray(9);
    static final AtomicLongArray FANOUTS = new AtomicLongArray(4);
    static final AtomicLongArray FRAMES = new AtomicLongArray(4);
    static final AtomicLong FANOUT_ARMED = new AtomicLong();

    static final AtomicLong INITIAL_ATTEMPTS = new AtomicLong();
    static final AtomicLong INITIAL_SENT = new AtomicLong();
    static final AtomicLong REPLY_ATTEMPTS = new AtomicLong();
    static final AtomicLong REPLY_SENT = new AtomicLong();
    static final AtomicLong WINDOW_ATTEMPTS = new AtomicLong();
    static final AtomicLong WINDOW_SENT = new AtomicLong();

    static
    {
        if (COUNTERS)
        {
            Runtime.getRuntime().addShutdownHook(new Thread(KafkaCacheSpike::report));
        }
    }

    private static void report()
    {
        System.err.format("SPIKE initial.doorbell=%b reply.doorbell=%b reply.window.drop=%b " +
            "initial attempts=%d sent=%d reply attempts=%d sent=%d window attempts=%d sent=%d%n",
            INITIAL_DOORBELL, REPLY_DOORBELL, REPLY_WINDOW_DROP,
            INITIAL_ATTEMPTS.get(), INITIAL_SENT.get(), REPLY_ATTEMPTS.get(), REPLY_SENT.get(),
            WINDOW_ATTEMPTS.get(), WINDOW_SENT.get());
        System.err.format("SPIKE2 reply.defer=%b members.hist[0,1,2,3-4,5-8,9-16,17-32,33-64,65+]=%s " +
            "fanouts[ack,window,deferred,other]=%s frames[ack,window,deferred,other]=%s " +
            "acks.per.cycle.hist=%s windows.per.cycle.hist=%s fanout.armed=%d%n",
            REPLY_DEFER, format(MEMBERS_HISTOGRAM), format(FANOUTS), format(FRAMES),
            format(ACKS_PER_CYCLE_HISTOGRAM), format(WINDOWS_PER_CYCLE_HISTOGRAM), FANOUT_ARMED.get());
    }

    static int bucket(
        long n)
    {
        return n <= 2 ? (int) n : n <= 4 ? 3 : n <= 8 ? 4 : n <= 16 ? 5 : n <= 32 ? 6 : n <= 64 ? 7 : 8;
    }

    static String format(
        AtomicLongArray array)
    {
        final StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < array.length(); i++)
        {
            out.append(i == 0 ? "" : ",").append(array.get(i));
        }
        return out.append("]").toString();
    }

    private KafkaCacheSpike()
    {
    }
}
