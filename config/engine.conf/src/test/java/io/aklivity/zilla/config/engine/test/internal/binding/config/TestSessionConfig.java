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
package io.aklivity.zilla.config.engine.test.internal.binding.config;

import java.util.List;
import java.util.Map;

public final class TestSessionConfig
{
    public static final String SAME = "same";
    public static final String DIFFERENT = "different";
    public static final String REJECTED = "rejected";

    public final Long expiresAt;
    public final Long expiringAt;
    public final List<Challenge> challenges;
    public final List<Reauthorization> reauthorizations;
    public final String routeIdentity;
    public final Map<String, String> routeAttributes;
    public final boolean unknown;

    public TestSessionConfig(
        Long expiresAt,
        Long expiringAt,
        List<Challenge> challenges,
        List<Reauthorization> reauthorizations,
        String routeIdentity,
        Map<String, String> routeAttributes,
        boolean unknown)
    {
        this.expiresAt = expiresAt;
        this.expiringAt = expiringAt;
        this.challenges = challenges;
        this.reauthorizations = reauthorizations;
        this.routeIdentity = routeIdentity;
        this.routeAttributes = routeAttributes;
        this.unknown = unknown;
    }

    public static final class Challenge
    {
        public final long at;
        public final boolean expect;

        public Challenge(
            long at,
            boolean expect)
        {
            this.at = at;
            this.expect = expect;
        }
    }

    public static final class Reauthorization
    {
        public final String credentials;
        public final long context;
        public final String expect;
        public final Long expiresAt;

        public Reauthorization(
            String credentials,
            long context,
            String expect,
            Long expiresAt)
        {
            this.credentials = credentials;
            this.context = context;
            this.expect = expect;
            this.expiresAt = expiresAt;
        }
    }
}
