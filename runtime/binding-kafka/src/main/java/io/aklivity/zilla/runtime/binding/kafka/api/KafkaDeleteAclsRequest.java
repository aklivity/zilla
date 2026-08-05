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
package io.aklivity.zilla.runtime.binding.kafka.api;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.delete_acls_v2.DeleteAclsFilterRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.delete_acls_v2.DeleteAclsRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.delete_acls_v2.DeleteAclsRequestPart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.Strings;

public final class KafkaDeleteAclsRequest
{
    private static final short DELETE_ACLS_API_VERSION_V2 = 2;

    private KafkaDeleteAclsRequest()
    {
    }

    /**
     * A fully-observed DeleteAcls request, ready to drive {@link Generator#generate(Source)} or
     * {@link #sizeof(Source, short)} directly, regardless of the input format that produced it
     * (JSON tool-call arguments today; any format implements the same contract).
     */
    public interface Source
    {
        int filterCount();

        void forEach(
            FilterConsumer consumer);

        interface FilterConsumer
        {
            void accept(
                Filter filter);
        }

        /**
         * A single deletion filter; matches every ACL binding satisfying every non-null field. Like
         * {@link KafkaCreateAclsRequest.Source.Creation}, a filter has no nested repeated fields, so
         * it is a flat set of scalars.
         */
        interface Filter
        {
            byte resourceType();

            /**
             * @return the resource name to match, or {@code null} to match any resource name
             */
            String resourceName();

            byte patternType();

            /**
             * @return the principal to match, or {@code null} to match any principal
             */
            String principal();

            /**
             * @return the host to match, or {@code null} to match any host
             */
            String host();

            byte operation();

            byte permissionType();
        }
    }

    /**
     * The exact number of bytes {@link Generator#generate(Source)} will write for {@code source} at
     * {@code apiVersion}, computed by arithmetic alone (string byte lengths, item counts, and this
     * version's fixed field widths) with no buffer touched. Only version 2 - the first flexible
     * (compact strings/arrays + tagged fields) DeleteAcls version - is implemented today; a future
     * version's formula would branch alongside it here rather than replacing it.
     */
    public static int sizeof(
        Source source,
        short apiVersion)
    {
        if (apiVersion != DELETE_ACLS_API_VERSION_V2)
        {
            throw new UnsupportedOperationException("unsupported DeleteAcls API version: " + apiVersion);
        }

        final int[] size = { 1 + varintWidth(source.filterCount() + 1) };

        source.forEach(f ->
        {
            size[0] += 1 +
                stringSizeof(f.resourceName()) +
                1 +
                stringSizeof(f.principal()) +
                stringSizeof(f.host()) +
                1 +
                1 +
                1;
        });

        size[0] += 1;

        return size[0];
    }

    private static int stringSizeof(
        String value)
    {
        int sizeof;
        if (value == null)
        {
            sizeof = 1;
        }
        else
        {
            final int length = Strings.utf8Length(value);
            sizeof = varintWidth(length + 1) + length;
        }
        return sizeof;
    }

    private static int varintWidth(
        int value)
    {
        int width = 1;
        int remaining = value >>> 7;
        while (remaining != 0)
        {
            width++;
            remaining >>>= 7;
        }
        return width;
    }

    public static final class Generator
    {
        private final DeleteAclsRequestFW.Builder deleteAclsRequestRW = new DeleteAclsRequestFW.Builder();
        private final DeleteAclsFilterRequestFW.Builder deleteAclsFilterRequestRW = new DeleteAclsFilterRequestFW.Builder();
        private final DeleteAclsRequestPart2FW.Builder deleteAclsRequestPart2RW = new DeleteAclsRequestPart2FW.Builder();

        private MutableDirectBufferEx buffer;
        private int limit;
        private int progress;

        private int declaredFilters;
        private int actualFilters;

        public Generator wrap(
            MutableDirectBufferEx buffer,
            int offset,
            int limit)
        {
            this.buffer = buffer;
            this.limit = limit;
            this.progress = offset;
            this.declaredFilters = -1;
            this.actualFilters = 0;
            return this;
        }

        public Generator filters(
            int count)
        {
            try
            {
                final DeleteAclsRequestFW deleteAclsRequest = deleteAclsRequestRW
                    .wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .filterCount(count)
                    .build();

                progress = deleteAclsRequest.limit();
                declaredFilters = count;
            }
            catch (IndexOutOfBoundsException ex)
            {
                declaredFilters = -1;
            }
            return this;
        }

        public boolean filter(
            Source.Filter filter)
        {
            actualFilters++;
            boolean built;
            try
            {
                final DeleteAclsFilterRequestFW deleteAclsFilterRequest = deleteAclsFilterRequestRW
                    .wrap(buffer, progress, limit)
                    .resourceTypeFilter(filter.resourceType())
                    .resourceNameFilter(filter.resourceName())
                    .patternTypeFilter(filter.patternType())
                    .principalFilter(filter.principal())
                    .hostFilter(filter.host())
                    .operation(filter.operation())
                    .permissionType(filter.permissionType())
                    .taggedFields(0)
                    .build();

                progress = deleteAclsFilterRequest.limit();
                built = true;
            }
            catch (IndexOutOfBoundsException ex)
            {
                built = false;
            }
            return built;
        }

        public boolean build()
        {
            boolean built = declaredFilters >= 0 && declaredFilters == actualFilters;
            if (built)
            {
                try
                {
                    final DeleteAclsRequestPart2FW deleteAclsRequestPart2 = deleteAclsRequestPart2RW
                        .wrap(buffer, progress, limit)
                        .taggedFields(0)
                        .build();

                    progress = deleteAclsRequestPart2.limit();
                }
                catch (IndexOutOfBoundsException ex)
                {
                    built = false;
                }
            }
            return built;
        }

        /**
         * Drives this generator from {@code source}, writing every filter it yields. Returns
         * {@code false} if any struct failed to fit the buffer.
         */
        public boolean generate(
            Source source)
        {
            filters(source.filterCount());

            final boolean[] ok = { true };
            source.forEach(f ->
            {
                if (!filter(f))
                {
                    ok[0] = false;
                }
            });

            return ok[0] && build();
        }

        public int limit()
        {
            return progress;
        }
    }
}
