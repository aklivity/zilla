/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.config.engine.test.internal.exporter.config;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class TestExporterOptionsConfig extends OptionsConfig
{
    public final String mode;
    public final List<Event> events;
    public final List<Metric> metrics;

    public static TestExporterOptionsConfigBuilder<TestExporterOptionsConfig> builder()
    {
        return new TestExporterOptionsConfigBuilder<>(TestExporterOptionsConfig.class::cast);
    }

    public static <T> TestExporterOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new TestExporterOptionsConfigBuilder<>(mapper);
    }

    TestExporterOptionsConfig(
        String mode,
        List<Event> events,
        List<Metric> metrics)
    {
        super(null, null);
        this.mode = mode;
        this.events = events;
        this.metrics = metrics;
    }

    public static final class Event
    {
        public final String qName;
        public final String id;
        public final String name;
        public final String message;

        public Event(
                String qName,
                String id,
                String name,
                String message)
        {
            this.qName = qName;
            this.id = id;
            this.name = name;
            this.message = message;
        }
    }

    public static final class Metric
    {
        public final String name;
        public final String binding;
        public final String kind;
        public final Map<String, String> attributes;
        public final Long value;
        public final Long count;
        public final List<Bucket> buckets;

        public Metric(
            String name,
            String binding,
            String kind,
            Map<String, String> attributes,
            Long value,
            Long count,
            List<Bucket> buckets)
        {
            this.name = name;
            this.binding = binding;
            this.kind = kind;
            this.attributes = attributes;
            this.value = value;
            this.count = count;
            this.buckets = buckets;
        }
    }

    public static final class Bucket
    {
        public final long limit;
        public final long count;

        public Bucket(
            long limit,
            long count)
        {
            this.limit = limit;
            this.count = count;
        }
    }
}
