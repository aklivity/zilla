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
package io.aklivity.zilla.config.binding.tcp;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class TcpOptionsConfigBuilder<T> extends ConfigBuilder<T, TcpOptionsConfigBuilder<T>>
{
    public static final int BACKLOG_DEFAULT = 0;
    public static final boolean NODELAY_DEFAULT = true;
    public static final boolean KEEPALIVE_DEFAULT = false;

    private final Function<OptionsConfig, T> mapper;

    private String host;
    private int[] ports;
    private int backlog = BACKLOG_DEFAULT;
    private boolean nodelay = NODELAY_DEFAULT;
    private boolean keepalive = KEEPALIVE_DEFAULT;

    TcpOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TcpOptionsConfigBuilder<T>> thisType()
    {
        return (Class<TcpOptionsConfigBuilder<T>>) getClass();
    }

    public TcpOptionsConfigBuilder<T> host(
        String host)
    {
        this.host = host;
        return this;
    }

    public TcpOptionsConfigBuilder<T> ports(
        int[] ports)
    {
        this.ports = ports;
        return this;
    }

    public TcpOptionsConfigBuilder<T> backlog(
        int backlog)
    {
        this.backlog = backlog;
        return this;
    }

    public TcpOptionsConfigBuilder<T> nodelay(
        boolean nodelay)
    {
        this.nodelay = nodelay;
        return this;
    }

    public TcpOptionsConfigBuilder<T> keepalive(
        boolean keepalive)
    {
        this.keepalive = keepalive;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TcpOptionsConfig(host, ports, backlog, nodelay, keepalive));
    }
}
