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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.VarStringFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_configs_v4.DescribeConfigsRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_configs_v4.DescribeConfigsRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_configs_v4.ResourceRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_configs_v4.ResourceRequestPart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.Strings;

public final class KafkaDescribeConfigsRequest
{
    public static final byte RESOURCE_TYPE_TOPIC = 2;
    public static final byte RESOURCE_TYPE_BROKER = 4;

    /**
     * A resource's {@link Source.Resource#configCount()} value requesting every config for the
     * resource, encoded on the wire as a null (rather than empty) configuration_keys array.
     */
    public static final int ALL_CONFIGS = -1;

    private static final short DESCRIBE_CONFIGS_API_VERSION_V4 = 4;

    private KafkaDescribeConfigsRequest()
    {
    }

    /**
     * A fully-observed DescribeConfigs request, ready to drive {@link Generator#generate(Source)} or
     * {@link #sizeof(Source, short)} directly, regardless of the input format that produced it
     * (JSON tool-call arguments today; any format implements the same contract).
     */
    public interface Source
    {
        int resourceCount();

        void forEach(
            ResourceConsumer consumer);

        interface ResourceConsumer
        {
            void accept(
                Resource resource);
        }

        interface Resource
        {
            byte type();

            String name();

            /**
             * @return {@link #ALL_CONFIGS} to request every config, otherwise the number of config
             *         names {@link #forEachConfigName(Consumer)} yields
             */
            int configCount();

            void forEachConfigName(
                Consumer<String> consumer);
        }
    }

    /**
     * The exact number of bytes {@link Generator#generate(Source)} will write for {@code source} at
     * {@code apiVersion}, computed by arithmetic alone (string byte lengths, item counts, and this
     * version's fixed field widths) with no buffer touched. Only version 4 - the first flexible
     * (compact strings/arrays + tagged fields) DescribeConfigs version - is implemented today; a
     * future version's formula would branch alongside it here rather than replacing it.
     */
    public static int sizeof(
        Source source,
        short apiVersion)
    {
        if (apiVersion != DESCRIBE_CONFIGS_API_VERSION_V4)
        {
            throw new UnsupportedOperationException("unsupported DescribeConfigs API version: " + apiVersion);
        }

        final int[] size = { 1 + varintWidth(source.resourceCount() + 1) };

        source.forEach(r ->
        {
            size[0] += 1 + stringSizeof(r.name()) + varintWidth(r.configCount() + 1);

            r.forEachConfigName(name -> size[0] += stringSizeof(name));

            size[0] += 1;
        });

        size[0] += 1 + 1 + 1;

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
        private final DescribeConfigsRequestFW.Builder describeConfigsRequestRW = new DescribeConfigsRequestFW.Builder();
        private final DescribeConfigsRequestPart2FW.Builder describeConfigsRequestPart2RW =
            new DescribeConfigsRequestPart2FW.Builder();
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
                final DescribeConfigsRequestFW describeConfigsRequest = describeConfigsRequestRW
                    .wrap(buffer, progress, limit)
                    .taggedFields(0)
                    .resourceCount(count)
                    .build();

                progress = describeConfigsRequest.limit();
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
            boolean includeSynonyms,
            boolean includeDocumentation)
        {
            boolean built = declaredResources >= 0 && declaredResources == actualResources;
            if (built)
            {
                try
                {
                    final DescribeConfigsRequestPart2FW describeConfigsRequestPart2 = describeConfigsRequestPart2RW
                        .wrap(buffer, progress, limit)
                        .includeSynonyms(includeSynonyms ? 1 : 0)
                        .includeDocumentation(includeDocumentation ? 1 : 0)
                        .taggedFields(0)
                        .build();

                    progress = describeConfigsRequestPart2.limit();
                }
                catch (IndexOutOfBoundsException ex)
                {
                    built = false;
                }
            }
            return built;
        }

        /**
         * Drives this generator from {@code source}, writing every resource/config name it yields,
         * then finishing without synonyms or documentation (neither is surfaced by any current
         * caller). Returns {@code false} if any struct failed to fit the buffer.
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
                    .configNames(r.configCount());

                r.forEachConfigName(resourceBuilder::configName);

                if (!resourceBuilder.build())
                {
                    ok[0] = false;
                }
            });

            return ok[0] && build(false, false);
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
        private final VarStringFW.Builder configNameRW = new VarStringFW.Builder();

        private Generator generator;
        private byte type;
        private String name;

        private boolean headerWritten;
        private boolean overflowed;

        private int declaredConfigNames;
        private int actualConfigNames;

        private Resource wrap(
            Generator generator)
        {
            this.generator = generator;
            this.type = 0;
            this.name = null;
            this.headerWritten = false;
            this.overflowed = false;
            this.declaredConfigNames = 0;
            this.actualConfigNames = 0;
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

        /**
         * @param count {@link #ALL_CONFIGS} to request every config, otherwise the number of
         *              {@link #configName(String)} calls that will follow
         */
        public Resource configNames(
            int count)
        {
            try
            {
                final ResourceRequestFW resourceRequest = resourceRequestRW
                    .wrap(generator.buffer, generator.progress, generator.limit)
                    .type(type)
                    .name(name)
                    .configNamesCount(count)
                    .build();

                generator.progress = resourceRequest.limit();
                declaredConfigNames = Math.max(count, 0);
            }
            catch (IndexOutOfBoundsException ex)
            {
                overflowed = true;
                declaredConfigNames = -1;
            }
            headerWritten = true;
            actualConfigNames = 0;
            return this;
        }

        public Resource configName(
            String name)
        {
            if (!headerWritten)
            {
                configNames(ALL_CONFIGS);
            }
            actualConfigNames++;
            try
            {
                final VarStringFW configName = configNameRW.wrap(generator.buffer, generator.progress, generator.limit)
                    .set(name, UTF_8)
                    .build();

                generator.progress = configName.limit();
            }
            catch (IndexOutOfBoundsException ex)
            {
                overflowed = true;
            }
            return this;
        }

        public boolean build()
        {
            if (!headerWritten)
            {
                configNames(ALL_CONFIGS);
            }

            boolean built = !overflowed && declaredConfigNames >= 0 && declaredConfigNames == actualConfigNames;
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
}
