/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.command.metrics.internal.utils;

import java.util.HashMap;
import java.util.Map;

public final class MetricUtils
{
    public static final int HISTOGRAM_BUCKETS = 63;
    public static final Map<Integer, Long> HISTOGRAM_BUCKET_LIMITS = histogramBucketLimits();

    private MetricUtils()
    {
    }

    // exclusive upper limits of each bucket
    private static Map<Integer, Long> histogramBucketLimits()
    {
        Map<Integer, Long> limits = new HashMap<>();
        for (int i = 0; i < HISTOGRAM_BUCKETS; i++)
        {
            limits.put(i, 1L << (i + 1));
        }
        return limits;
    }

    public static int namespaceId(
        long packedId)
    {
        return (int) (packedId >> Integer.SIZE) & 0xffff_ffff;
    }

    public static int localId(
        long packedId)
    {
        return (int) (packedId >> 0) & 0xffff_ffff;
    }
}
