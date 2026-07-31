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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.alter_configs_v2.AlterConfigsRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.alter_configs_v2.AlterConfigsRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.alter_configs_v2.ConfigRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.alter_configs_v2.ResourceRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.alter_configs_v2.ResourceRequestPart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.Strings;

public final class KafkaAlterConfigsRequest
{
    public static final byte RESOURCE_TYPE_TOPIC = 2;
    public static final byte RESOURCE_TYPE_BROKER = 4;

    private static final short ALTER_CONFIGS_API_VERSION_V2 = 2;

    private KafkaAlterConfigsRequest()
    {
    }

    /**
     * A fully-observed AlterConfigs request, ready to drive {@link Generator#generate(Source)} or
     * {@link #sizeof(Source, short)} directly, regardless of the input format that produced it
     * (JSON tool-call arguments today; any format implements the same contract).
     */
    public interface Source
    {
        int resourceCount();

        void forEach(
            ResourceConsumer consumer);

        boolean validateOnly();

        interface ResourceConsumer
        {
            void accept(
                Resource resource);
        }

        interface Resource
        {
            byte type();

            String name();

            int configCount();

            void forEachConfig(
                ConfigConsumer consumer);
        }

        interface ConfigConsumer
        {
            void accept(
                Config config);
        }

        interface Config
        {
            String name();

            String value();
        }
    }

    /**
     * The exact number of bytes {@link Generator#generate(Source)} will write for {@code source} at
     * {@code apiVersion}, computed by arithmetic alone (string byte lengths, item counts, and this
     * version's fixed field widths) with no buffer touched. Only version 2 - the first flexible
     * (compact strings/arrays + tagged fields) AlterConfigs version - is implemented today; a future
     * version's formula would branch alongside it here rather than replacing it.
     */
    public static int sizeof(
        Source source,
        short apiVersion)
    {
        if (apiVersion != ALTER_CONFIGS_API_VERSION_V2)
        {
            throw new UnsupportedOperationException("unsupported AlterConfigs API version: " + apiVersion);
        }

        final int[] size = { 1 + varintWidth(source.resourceCount() + 1) };

        source.forEach(r ->
        {
            size[0] += 1 + stringSizeof(r.name()) + varintWidth(r.configCount() + 1);

            r.forEachConfig(c -> size[0] += stringSizeof(c.name()) + stringSizeof(c.value()) + 1);

            size[0] += 1;
        });

        size[0] += 1 + 1;

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
        private final AlterConfigsRequestFW.Builder alterConfigsRequestRW = new AlterConfigsRequestFW.Builder();
        private final AlterConfigsRequestPart2FW.Builder alterConfigsRequestPart2RW = new AlterConfigsRequestPart2FW.Builder();
        private final Resource resourceRW = new Resource();

        private MutableDirectBufferEx buffer;
        private int limit;
        private int progress;

        private int declaredResources;
        private int actualResources;

        public Generator wrap(
            MutableDirectBufferEx buffer,
            int offset,
            int limit)
        {
            this.buffer = buffer;
            this.limit = limit;
            this.progress = offset;
            this.declaredResources = -1;
            this.actualResources = 0;
            return this;
        }

        public Generator resources(
            int count)
        {
            try
            {
                final AlterConfigsRequestFW alterConfigsRequest = alterConfigsRequestRW.wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .resourceCount(count)
                    .build();

                progress = alterConfigsRequest.limit();
                declaredResources = count;
            }
            catch (IndexOutOfBoundsException ex)
            {
                declaredResources = -1;
            }
            return this;
        }

        public Resource resource()
        {
            actualResources++;
            return resourceRW.wrap(this);
        }

        public boolean build(
            boolean validateOnly)
        {
            boolean built = declaredResources >= 0 && declaredResources == actualResources;
            if (built)
            {
                try
                {
                    final AlterConfigsRequestPart2FW alterConfigsRequestPart2 = alterConfigsRequestPart2RW
                        .wrap(buffer, progress, limit)
                        .validate_only(validateOnly ? 1 : 0)
                        .taggedFields(0)
                        .build();

                    progress = alterConfigsRequestPart2.limit();
                }
                catch (IndexOutOfBoundsException ex)
                {
                    built = false;
                }
            }
            return built;
        }

        /**
         * Drives this generator from {@code source}, writing every resource/config it yields, then
         * finishing with {@code source}'s own validateOnly. Returns {@code false} if any struct
         * failed to fit the buffer.
         */
        public boolean generate(
            Source source)
        {
            resources(source.resourceCount());

            final boolean[] ok = { true };
            source.forEach(r ->
            {
                Resource resourceBuilder = resource()
                    .type(r.type())
                    .name(r.name())
                    .configs(r.configCount());

                r.forEachConfig(c -> resourceBuilder.config().name(c.name()).value(c.value()).build());

                if (!resourceBuilder.build())
                {
                    ok[0] = false;
                }
            });

            return ok[0] && build(source.validateOnly());
        }

        public int limit()
        {
            return progress;
        }
    }

    public static final class Resource
    {
        private final ResourceRequestFW.Builder resourceRequestRW = new ResourceRequestFW.Builder();
        private final ResourceRequestPart2FW.Builder resourceRequestPart2RW = new ResourceRequestPart2FW.Builder();
        private final Config configRW = new Config();

        private Generator generator;
        private byte type;
        private String name;

        private boolean headerWritten;
        private boolean overflowed;

        private int declaredConfigs;
        private int actualConfigs;

        private Resource wrap(
            Generator generator)
        {
            this.generator = generator;
            this.type = 0;
            this.name = null;
            this.headerWritten = false;
            this.overflowed = false;
            this.declaredConfigs = 0;
            this.actualConfigs = 0;
            return this;
        }

        public Resource type(
            byte type)
        {
            this.type = type;
            return this;
        }

        public Resource name(
            String name)
        {
            this.name = name;
            return this;
        }

        public Resource configs(
            int count)
        {
            try
            {
                final ResourceRequestFW resourceRequest = resourceRequestRW
                    .wrap(generator.buffer, generator.progress, generator.limit)
                    .type(type)
                    .name(name)
                    .configCount(count)
                    .build();

                generator.progress = resourceRequest.limit();
                declaredConfigs = count;
            }
            catch (IndexOutOfBoundsException ex)
            {
                overflowed = true;
                declaredConfigs = -1;
            }
            headerWritten = true;
            actualConfigs = 0;
            return this;
        }

        public Config config()
        {
            if (!headerWritten)
            {
                configs(0);
            }
            actualConfigs++;
            return configRW.wrap(generator, this);
        }

        public boolean build()
        {
            if (!headerWritten)
            {
                configs(0);
            }

            boolean built = !overflowed && declaredConfigs == actualConfigs;
            if (built)
            {
                try
                {
                    final ResourceRequestPart2FW resourceRequestPart2 = resourceRequestPart2RW
                        .wrap(generator.buffer, generator.progress, generator.limit)
                        .taggedFields(0)
                        .build();

                    generator.progress = resourceRequestPart2.limit();
                }
                catch (IndexOutOfBoundsException ex)
                {
                    built = false;
                }
            }
            return built;
        }
    }

    public static final class Config
    {
        private final ConfigRequestFW.Builder configRequestRW = new ConfigRequestFW.Builder();

        private Generator generator;
        private Resource resource;
        private String name;
        private String value;

        private Config wrap(
            Generator generator,
            Resource resource)
        {
            this.generator = generator;
            this.resource = resource;
            this.name = null;
            this.value = null;
            return this;
        }

        public Config name(
            String name)
        {
            this.name = name;
            return this;
        }

        public Config value(
            String value)
        {
            this.value = value;
            return this;
        }

        public Resource build()
        {
            try
            {
                final ConfigRequestFW configRequest = configRequestRW.wrap(generator.buffer, generator.progress, generator.limit)
                    .name(name)
                    .value(value)
                    .taggedFields(0)
                    .build();

                generator.progress = configRequest.limit();
            }
            catch (IndexOutOfBoundsException ex)
            {
                resource.overflowed = true;
            }
            return resource;
        }
    }
}
