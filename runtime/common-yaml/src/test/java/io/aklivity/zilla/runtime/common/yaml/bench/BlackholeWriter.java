/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.yaml.bench;

import java.io.Writer;

import org.openjdk.jmh.infra.Blackhole;

/**
 * A {@link Writer} sink for the generate benchmarks that adds no allocation of its own and cannot be
 * eliminated by the JIT. Every write is forwarded to a JMH {@link Blackhole} rather than buffered, so
 * {@code gc.alloc.rate.norm} reflects the generator's own allocation instead of an output buffer growing.
 * The {@code String}-accepting overrides are provided deliberately: the default {@link Writer#write(String)}
 * copies into a freshly allocated {@code char[]}, which would reintroduce the very overhead being avoided.
 */
final class BlackholeWriter extends Writer
{
    private final Blackhole blackhole;

    BlackholeWriter(
        Blackhole blackhole)
    {
        this.blackhole = blackhole;
    }

    @Override
    public void write(
        int c)
    {
        blackhole.consume(c);
    }

    @Override
    public void write(
        String str)
    {
        blackhole.consume(str);
    }

    @Override
    public void write(
        String str,
        int off,
        int len)
    {
        blackhole.consume(str);
        blackhole.consume(off);
        blackhole.consume(len);
    }

    @Override
    public void write(
        char[] cbuf,
        int off,
        int len)
    {
        blackhole.consume(cbuf);
        blackhole.consume(off);
        blackhole.consume(len);
    }

    @Override
    public void flush()
    {
    }

    @Override
    public void close()
    {
    }
}
