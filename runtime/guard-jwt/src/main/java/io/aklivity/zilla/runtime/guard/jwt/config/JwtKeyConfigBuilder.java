/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.guard.jwt.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class JwtKeyConfigBuilder<T> implements ConfigBuilder<T>
{
    private final Function<JwtKeyConfig, T> mapper;

    private String alg;
    private String kty;
    private String kid;
    private String use;
    private String n;
    private String e;
    private String crv;
    private String x;
    private String y;

    JwtKeyConfigBuilder(
        Function<JwtKeyConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public JwtKeyConfigBuilder<T> kty(
        String kty)
    {
        this.kty = kty;
        return this;
    }

    public JwtKeyConfigBuilder<T> kid(
        String kid)
    {
        this.kid = kid;
        return this;
    }

    public JwtKeyConfigBuilder<T> use(
        String use)
    {
        this.use = use;
        return this;
    }

    public JwtKeyConfigBuilder<T> n(
        String n)
    {
        this.n = n;
        return this;
    }

    public JwtKeyConfigBuilder<T> e(
        String e)
    {
        this.e = e;
        return this;
    }

    public JwtKeyConfigBuilder<T> alg(
        String alg)
    {
        this.alg = alg;
        return this;
    }

    public JwtKeyConfigBuilder<T> crv(
        String crv)
    {
        this.crv = crv;
        return this;
    }

    public JwtKeyConfigBuilder<T> x(
        String x)
    {
        this.x = x;
        return this;
    }

    public JwtKeyConfigBuilder<T> y(
        String y)
    {
        this.y = y;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new JwtKeyConfig(kty, kid, use, n, e, alg, crv, x, y));
    }
}
