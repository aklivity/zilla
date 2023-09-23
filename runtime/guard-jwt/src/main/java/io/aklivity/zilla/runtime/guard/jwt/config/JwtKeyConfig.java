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

import static java.util.function.Function.identity;

public class JwtKeyConfig
{
    public final String alg;
    public final String kty;
    public final String kid;
    public final String use;
    public final String n;
    public final String e;
    public final String crv;
    public final String x;
    public final String y;

    public static JwtKeyConfigBuilder<JwtKeyConfig> builder()
    {
        return new JwtKeyConfigBuilder<>(identity());
    }

    JwtKeyConfig(
        String kty,
        String kid,
        String use,
        String n,
        String e,
        String alg,
        String crv,
        String x,
        String y)
    {
        this.kty = kty;
        this.kid = kid;
        this.use = use;
        this.n = n;
        this.e = e;
        this.alg = alg;
        this.crv = crv;
        this.x = x;
        this.y = y;
    }
}
