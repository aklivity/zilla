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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_acls_v2.AclCreationRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_acls_v2.CreateAclsRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_acls_v2.CreateAclsRequestPart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.Strings;

public final class KafkaCreateAclsRequest
{
    private static final short CREATE_ACLS_API_VERSION_V2 = 2;

    private KafkaCreateAclsRequest()
    {
    }

    /**
     * A fully-observed CreateAcls request, ready to drive {@link Generator#generate(Source)} or
     * {@link #sizeof(Source, short)} directly, regardless of the input format that produced it
     * (JSON tool-call arguments today; any format implements the same contract).
     */
    public interface Source
    {
        int creationCount();

        void forEach(
            CreationConsumer consumer);

        interface CreationConsumer
        {
            void accept(
                Creation creation);
        }

        /**
         * A single ACL binding to create; unlike {@link KafkaAlterConfigsRequest.Source.Resource},
         * a creation entry has no nested repeated fields, so it is a flat set of scalars.
         */
        interface Creation
        {
            byte resourceType();

            String resourceName();

            byte resourcePatternType();

            String principal();

            String host();

            byte operation();

            byte permissionType();
        }
    }

    /**
     * The exact number of bytes {@link Generator#generate(Source)} will write for {@code source} at
     * {@code apiVersion}, computed by arithmetic alone (string byte lengths, item counts, and this
     * version's fixed field widths) with no buffer touched. Only version 2 - the first flexible
     * (compact strings/arrays + tagged fields) CreateAcls version - is implemented today; a future
     * version's formula would branch alongside it here rather than replacing it.
     */
    public static int sizeof(
        Source source,
        short apiVersion)
    {
        if (apiVersion != CREATE_ACLS_API_VERSION_V2)
        {
            throw new UnsupportedOperationException("unsupported CreateAcls API version: " + apiVersion);
        }

        final int[] size = { 1 + varintWidth(source.creationCount() + 1) };

        source.forEach(c ->
        {
            size[0] += 1 +
                stringSizeof(c.resourceName()) +
                1 +
                stringSizeof(c.principal()) +
                stringSizeof(c.host()) +
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
        private final CreateAclsRequestFW.Builder createAclsRequestRW = new CreateAclsRequestFW.Builder();
        private final AclCreationRequestFW.Builder aclCreationRequestRW = new AclCreationRequestFW.Builder();
        private final CreateAclsRequestPart2FW.Builder createAclsRequestPart2RW = new CreateAclsRequestPart2FW.Builder();

        private MutableDirectBufferEx buffer;
        private int limit;
        private int progress;

        private int declaredCreations;
        private int actualCreations;

        public Generator wrap(
            MutableDirectBufferEx buffer,
            int offset,
            int limit)
        {
            this.buffer = buffer;
            this.limit = limit;
            this.progress = offset;
            this.declaredCreations = -1;
            this.actualCreations = 0;
            return this;
        }

        public Generator creations(
            int count)
        {
            try
            {
                final CreateAclsRequestFW createAclsRequest = createAclsRequestRW
                    .wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .creationCount(count)
                    .build();

                progress = createAclsRequest.limit();
                declaredCreations = count;
            }
            catch (IndexOutOfBoundsException ex)
            {
                declaredCreations = -1;
            }
            return this;
        }

        public boolean creation(
            Source.Creation creation)
        {
            actualCreations++;
            boolean built;
            try
            {
                final AclCreationRequestFW aclCreationRequest = aclCreationRequestRW
                    .wrap(buffer, progress, limit)
                    .resourceType(creation.resourceType())
                    .resourceName(creation.resourceName())
                    .resourcePatternType(creation.resourcePatternType())
                    .principal(creation.principal())
                    .host(creation.host())
                    .operation(creation.operation())
                    .permissionType(creation.permissionType())
                    .taggedFields(0)
                    .build();

                progress = aclCreationRequest.limit();
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
            boolean built = declaredCreations >= 0 && declaredCreations == actualCreations;
            if (built)
            {
                try
                {
                    final CreateAclsRequestPart2FW createAclsRequestPart2 = createAclsRequestPart2RW
                        .wrap(buffer, progress, limit)
                        .taggedFields(0)
                        .build();

                    progress = createAclsRequestPart2.limit();
                }
                catch (IndexOutOfBoundsException ex)
                {
                    built = false;
                }
            }
            return built;
        }

        /**
         * Drives this generator from {@code source}, writing every ACL creation it yields. Returns
         * {@code false} if any struct failed to fit the buffer.
         */
        public boolean generate(
            Source source)
        {
            creations(source.creationCount());

            final boolean[] ok = { true };
            source.forEach(c ->
            {
                if (!creation(c))
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
