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

/**
 * Options of the {@code test} exporter, which asserts exported events in the order configured by
 * {@code events} and exported metrics and attributes as configured.
 * <p>
 * The optional {@code filter} selects which exported events take part in that assertion. An event
 * matching any {@code filter.exclude} matcher is skipped: it is not compared to {@code events}, does
 * not advance the expected sequence and is never reported as unexpected. Without a {@code filter} every
 * event is asserted in sequence.
 * <p>
 * An {@code include} list is reserved for later and is rejected until supported. Once supported, an event
 * will be asserted only if it matches {@code include} (or {@code include} is absent) and does not match
 * {@code exclude}, so {@code exclude} takes precedence over {@code include}.
 */
public final class TestExporterOptionsConfig extends OptionsConfig
{
    public final String mode;
    public final Filter filter;
    public final List<Event> events;
    public final List<Metric> metrics;
    public final Map<String, String> attributes;

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
        Filter filter,
        List<Event> events,
        List<Metric> metrics,
        Map<String, String> attributes)
    {
        super(null, null);
        this.mode = mode;
        this.filter = filter;
        this.events = events;
        this.metrics = metrics;
        this.attributes = attributes;
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

    public static final class Filter
    {
        public final List<EventMatcher> exclude;

        public Filter(
            List<EventMatcher> exclude)
        {
            this.exclude = exclude;
        }
    }

    public static final class EventMatcher
    {
        public final String qName;
        public final String id;
        public final String name;
        public final String message;

        public EventMatcher(
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
