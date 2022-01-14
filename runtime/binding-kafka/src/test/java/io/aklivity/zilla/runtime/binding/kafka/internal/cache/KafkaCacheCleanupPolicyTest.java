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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCleanupPolicy.COMPACT;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCleanupPolicy.COMPACT_AND_DELETE;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCleanupPolicy.DELETE;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCleanupPolicy.UNKNOWN;

import org.junit.Test;

public class KafkaCacheCleanupPolicyTest
{
    @Test
    public void shouldCompact()
    {
        assert COMPACT.compact();
        assert COMPACT_AND_DELETE.compact();
    }

    @Test
    public void shouldNotCompact()
    {
        assert !DELETE.compact();
        assert !UNKNOWN.compact();
    }

    @Test
    public void shouldDelete()
    {
        assert DELETE.delete();
        assert COMPACT_AND_DELETE.delete();
    }

    @Test
    public void shouldNotDelete()
    {
        assert !COMPACT.delete();
        assert !UNKNOWN.delete();
    }
}
