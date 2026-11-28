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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import java.util.ArrayList;
import java.util.List;

final class RecordingProduceClaims implements KafkaCacheProduceClaims
{
    private final List<Integer> requests = new ArrayList<>();
    private final List<KafkaCacheSegment> removedSegments = new ArrayList<>();
    private final List<Integer> removedBytes = new ArrayList<>();

    private long limit = Long.MAX_VALUE;
    private long claimed;

    void limit(
        long limit)
    {
        this.limit = limit;
    }

    List<Integer> requests()
    {
        return requests;
    }

    long outstanding()
    {
        long outstanding = claimed;
        for (int i = 0; i < removedSegments.size(); i++)
        {
            outstanding -= removedSegments.get(i).closed() ? removedBytes.get(i) : 0L;
        }
        return outstanding;
    }

    @Override
    public boolean claim(
        int bytes)
    {
        requests.add(bytes);
        final boolean claimable = bytes <= limit - claimed;
        claimed += claimable ? bytes : 0L;
        return claimable;
    }

    @Override
    public void unclaim(
        int bytes)
    {
        claimed -= bytes;
    }

    @Override
    public void remove(
        KafkaCacheSegment segment,
        int bytes)
    {
        removedSegments.add(segment);
        removedBytes.add(bytes);
    }
}
