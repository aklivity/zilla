/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.config;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum KafkaScramMechanism
{
    SCRAM_SHA_256("SHA-256", "HmacSHA256", 4096),
    SCRAM_SHA_512("SHA-512", "HmacSHA512", 4096),
    SCRAM_SHA_1("SHA-1", "HmacSHA1", 4096);

    private final String mechanismName;
    private final String hashAlgorithm;
    private final String macAlgorithm;
    private final int minIterations;

    private static final Map<String, KafkaScramMechanism> MECHANISMS_MAP;

    static
    {
        Map<String, KafkaScramMechanism> map = new HashMap<>();
        for (KafkaScramMechanism mech : values())
        {
            map.put(mech.mechanismName, mech);
        }
        MECHANISMS_MAP = Collections.unmodifiableMap(map);
    }

    KafkaScramMechanism(String hashAlgorithm, String macAlgorithm, int minIterations)
    {
        this.mechanismName = "SCRAM-" + hashAlgorithm;
        this.hashAlgorithm = hashAlgorithm;
        this.macAlgorithm = macAlgorithm;
        this.minIterations = minIterations;
    }

    public final String mechanismName()
    {
        return mechanismName;
    }

    public String hashAlgorithm()
    {
        return hashAlgorithm;
    }

    public String macAlgorithm()
    {
        return macAlgorithm;
    }

    public int minIterations()
    {
        return minIterations;
    }

    public static KafkaScramMechanism forMechanismName(String mechanismName)
    {
        return MECHANISMS_MAP.get(mechanismName);
    }

    public static Collection<String> mechanismNames()
    {
        return MECHANISMS_MAP.keySet();
    }

    public static boolean isScram(String mechanismName)
    {
        return MECHANISMS_MAP.containsKey(mechanismName);
    }
}
