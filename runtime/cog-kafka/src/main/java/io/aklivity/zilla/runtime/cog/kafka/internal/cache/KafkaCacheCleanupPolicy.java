/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.kafka.internal.cache;

public enum KafkaCacheCleanupPolicy
{
    COMPACT
    {
        public boolean compact()
        {
            return true;
        }

        public boolean delete()
        {
            return false;
        }
    },
    DELETE
    {
        public boolean compact()
        {
            return false;
        }

        public boolean delete()
        {
            return true;
        }
    },
    COMPACT_AND_DELETE
    {
        public boolean compact()
        {
            return true;
        }

        public boolean delete()
        {
            return true;
        }
    },
    UNKNOWN
    {
        public boolean compact()
        {
            return false;
        }

        public boolean delete()
        {
            return false;
        }
    };

    public abstract boolean compact();

    public abstract boolean delete();
}
