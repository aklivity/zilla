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
package io.aklivity.zilla.runtime.command.metrics.internal.layout;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

public interface FileReader
{
    int BINDING_ID_INDEX = 0;
    int METRIC_ID_INDEX = 1;
    int VALUES_INDEX = 2;
    int HISTOGRAM_BUCKETS = 63;
    Map<Kind, Integer> NUMBER_OF_VALUES = Map.of(
            Kind.COUNTER, 1,
            Kind.GAUGE, 1,
            Kind.HISTOGRAM, HISTOGRAM_BUCKETS);
    Map<Integer, Long> HISTOGRAM_BUCKET_LIMITS = histogramBucketLimits();

    // exclusive upper limits of each bucket
    static Map<Integer, Long> histogramBucketLimits()
    {
        Map<Integer, Long> limits = new HashMap<>();
        for (int i = 0; i < 63; i++)
        {
            limits.put(i, 1L << (i + 1));
        }
        return limits;
    }

    enum Kind
    {
        COUNTER,
        GAUGE,
        HISTOGRAM
    }

    Kind kind();

    LongSupplier[][] recordReaders();

    void close();
}
