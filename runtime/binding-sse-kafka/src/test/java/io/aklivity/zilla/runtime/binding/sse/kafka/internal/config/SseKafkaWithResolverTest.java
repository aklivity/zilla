/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithFilterConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithFilterHeaderConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.stream.SseKafkaIdHelper;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaConditionFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.HeaderFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.SseBeginExFW;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;

public class SseKafkaWithResolverTest
{
    private static final long AUTHORIZATION = 0xFEDCBA9876543210L;
    private static final long COMPOSITE_ID = 0x1234567890ABCDEFL;
    private static final String EVENT_ID = "test-event-id";

    private SseBeginExFW sseBeginEx;
    private SseKafkaWithConfig withConfig;
    private LongObjectBiFunction<MatchResult, String> identityReplacer;
    private Array32FW<HeaderFW> headers;
    private SseKafkaIdHelper sseEventId;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp()
    {
        sseBeginEx = mock(SseBeginExFW.class);
        sseEventId = mock(SseKafkaIdHelper.class);

        // Create SseKafkaWithConfig using builder
        withConfig = SseKafkaWithConfig.builder()
                .compositeId(COMPOSITE_ID)
                .eventId(EVENT_ID)
                .topic("test-topic")
                .build();

        identityReplacer = mock(LongObjectBiFunction.class);
        headers = mock(Array32FW.class);
        when(sseBeginEx.headers()).thenReturn(headers);

        // Mock necessary behaviors for sseEventId
        DirectBuffer progressBuffer = new ExpandableArrayBuffer(0);
        when(sseEventId.findProgress(any(String8FW.class))).thenReturn(progressBuffer);
        Array32FW<KafkaOffsetFW> partitions = mock(Array32FW.class);
        when(sseEventId.decode(any(DirectBuffer.class))).thenReturn(partitions);
    }

    @Test
    public void shouldResolveWithBasicTopic()
    {

        SseKafkaWithResolver resolver = new SseKafkaWithResolver(identityReplacer, withConfig);

        // When
        SseKafkaWithResult result = resolver.resolve(AUTHORIZATION, sseBeginEx, sseEventId);

        // Then
        assertNotNull(result);
        assertEquals(COMPOSITE_ID, result.compositeId());
        assertEquals("test-topic", result.topic().asString());
    }

    @Test
    public void shouldResolveWithParamReplacement()
    {
        // Given
        SseKafkaWithConfig withConfig = SseKafkaWithConfig.builder()
                .compositeId(COMPOSITE_ID)
                .eventId(EVENT_ID)
                .topic("topic-${params.id}")
                .eventId(EVENT_ID)
                .build();

        when(identityReplacer.apply(Mockito.eq(AUTHORIZATION), any())).thenReturn("test-user");

        SseKafkaWithResolver resolver = new SseKafkaWithResolver(identityReplacer, withConfig);
        resolver.onConditionMatched(createConditionMatcherWithParam("id", "123"));

        // When
        SseKafkaWithResult result = resolver.resolve(AUTHORIZATION, sseBeginEx, sseEventId);

        // Then
        assertNotNull(result);
        assertEquals("topic-123", result.topic().asString());
    }

    @Test
    public void shouldResolveWithIdentityReplacement()
    {
        // Given
        SseKafkaWithConfig withConfig = SseKafkaWithConfig.builder()
                .compositeId(COMPOSITE_ID)
                .eventId(EVENT_ID)
                .topic("topic-${guarded['user'].identity}")
                .eventId(EVENT_ID)
                .build();

        when(identityReplacer.apply(Mockito.eq(AUTHORIZATION), any())).thenReturn("test-user");

        SseKafkaWithResolver resolver = new SseKafkaWithResolver(identityReplacer, withConfig);

        // When
        SseKafkaWithResult result = resolver.resolve(AUTHORIZATION, sseBeginEx, sseEventId);

        // Then
        assertNotNull(result);
        assertEquals("topic-test-user", result.topic().asString());
    }

    @Test
    public void shouldResolveWithFilters()
    {
        // Given
        SseKafkaWithFilterHeaderConfig headerConfig =
                new SseKafkaWithFilterHeaderConfig("header1", "value-${params.id}");

        List<SseKafkaWithFilterHeaderConfig> headerConfigs = new ArrayList<>();
        headerConfigs.add(headerConfig);

        SseKafkaWithFilterConfig filter = SseKafkaWithFilterConfig.builder()
                .key("key-${params.id}")
                .headers(headerConfigs)
                .build();

        List<SseKafkaWithFilterConfig> filters = new ArrayList<>();
        filters.add(filter);

        withConfig = SseKafkaWithConfig.builder()
                .compositeId(COMPOSITE_ID)
                .topic("test-topic")
                .eventId(EVENT_ID)
                .filters(filters)
                .build();

        SseKafkaWithResolver resolver = new SseKafkaWithResolver(identityReplacer, withConfig);
        resolver.onConditionMatched(createConditionMatcherWithParam("id", "123"));

        // When
        SseKafkaWithResult result = resolver.resolve(AUTHORIZATION, sseBeginEx, sseEventId);

        // Then
        assertNotNull(result);
        var filtersBuilder = new Array32FW.Builder<>(new KafkaFilterFW.Builder(), new KafkaFilterFW());
        filtersBuilder = wrap(filtersBuilder);
        result.filters(filtersBuilder);
        Array32FW<KafkaFilterFW> filtersResult = filtersBuilder.build();
        assertEquals(1, filtersResult.fieldCount());
        KafkaFilterFW kafkaFilterFW = filtersResult.matchFirst(x -> true);
        Array32FW<KafkaConditionFW> conditions = kafkaFilterFW.conditions();
        assertEquals(2, conditions.fieldCount());
        var keyCondition = conditions.matchFirst(c -> c.key() != null && !octetToString(c.key().value()).isEmpty());
        assertEquals("key-123", octetToString(keyCondition.key().value()));
        var headerCondition = conditions.matchFirst(c -> c.header() != null && !octetToString(c.header().name()).isEmpty());
        assertEquals("header1", octetToString(headerCondition.header().name()));
        assertEquals("value-123", octetToString(headerCondition.header().value()));
    }

    @Test
    public void shouldResolveWithIdentityReplacementInFilter()
    {
        // Given
        SseKafkaWithFilterHeaderConfig headerConfig =
                new SseKafkaWithFilterHeaderConfig("header1", "value-${guarded['user'].identity}");

        List<SseKafkaWithFilterHeaderConfig> headerConfigs = new ArrayList<>();
        headerConfigs.add(headerConfig);

        SseKafkaWithFilterConfig filter = SseKafkaWithFilterConfig.builder()
                .key("key-${guarded['user'].identity}")
                .headers(headerConfigs)
                .build();

        List<SseKafkaWithFilterConfig> filters = new ArrayList<>();
        filters.add(filter);

        withConfig = SseKafkaWithConfig.builder()
                .compositeId(COMPOSITE_ID)
                .eventId(EVENT_ID)
                .topic("test-topic")
                .filters(filters)
                .build();

        when(identityReplacer.apply(Mockito.eq(AUTHORIZATION), any())).thenReturn("test-user");
        SseKafkaWithResolver resolver = new SseKafkaWithResolver(identityReplacer, withConfig);

        // When
        SseKafkaWithResult result = resolver.resolve(AUTHORIZATION, sseBeginEx, sseEventId);

        // Then
        assertNotNull(result);
        var filtersBuilder = new Array32FW.Builder<>(new KafkaFilterFW.Builder(), new KafkaFilterFW());
        filtersBuilder = wrap(filtersBuilder);
        result.filters(filtersBuilder);
        Array32FW<KafkaFilterFW> filtersResult = filtersBuilder.build();
        assertEquals(1, filtersResult.fieldCount());
        KafkaFilterFW kafkaFilterFW = filtersResult.matchFirst(x -> true);
        Array32FW<KafkaConditionFW> conditions = kafkaFilterFW.conditions();
        assertEquals(2, conditions.fieldCount());
        var keyCondition = conditions.matchFirst(c -> c.key() != null && !octetToString(c.key().value()).isEmpty());
        assertEquals("key-test-user", octetToString(keyCondition.key().value()));
        var headerCondition = conditions.matchFirst(c -> c.header() != null && !octetToString(c.header().name()).isEmpty());
        assertEquals("header1", octetToString(headerCondition.header().name()));
        assertEquals("value-test-user", octetToString(headerCondition.header().value()));
    }

    @Test
    public void shouldResolveWithMultipleReplacementsInAllFields()
    {
        // Given
        SseKafkaWithFilterHeaderConfig headerConfig1 =
                new SseKafkaWithFilterHeaderConfig("user-id", "${guarded['user'].identity}");
        SseKafkaWithFilterHeaderConfig headerConfig2 =
                new SseKafkaWithFilterHeaderConfig("region", "${params.region}");

        List<SseKafkaWithFilterHeaderConfig> headerConfigs = new ArrayList<>();
        headerConfigs.add(headerConfig1);
        headerConfigs.add(headerConfig2);

        SseKafkaWithFilterConfig filter = SseKafkaWithFilterConfig.builder()
                .key("${guarded['user'].identity}-${params.region}")
                .headers(headerConfigs)
                .build();

        List<SseKafkaWithFilterConfig> filters = new ArrayList<>();
        filters.add(filter);

        withConfig = SseKafkaWithConfig.builder()
                .compositeId(COMPOSITE_ID)
                .eventId(EVENT_ID)
                .topic("${params.region}-topic")
                .filters(filters)
                .build();

        when(identityReplacer.apply(Mockito.eq(AUTHORIZATION), any())).thenReturn("test-user");

        SseKafkaWithResolver resolver = new SseKafkaWithResolver(identityReplacer, withConfig);
        resolver.onConditionMatched(createConditionMatcherWithParam("region", "us-west"));

        // When
        SseKafkaWithResult result = resolver.resolve(AUTHORIZATION, sseBeginEx, sseEventId);

        // Then
        assertNotNull(result);

        // Check topic
        assertEquals("us-west-topic", result.topic().asString());

        // Check filters
        var filtersBuilder = new Array32FW.Builder<>(new KafkaFilterFW.Builder(), new KafkaFilterFW());
        filtersBuilder = wrap(filtersBuilder);
        result.filters(filtersBuilder);
        Array32FW<KafkaFilterFW> filtersResult = filtersBuilder.build();
        assertEquals(1, filtersResult.fieldCount());

        KafkaFilterFW kafkaFilterFW = filtersResult.matchFirst(x -> true);
        Array32FW<KafkaConditionFW> conditions = kafkaFilterFW.conditions();
        assertEquals(3, conditions.fieldCount());

        // Check key
        var keyCondition = conditions.matchFirst(c -> c.key() != null && !octetToString(c.key().value()).isEmpty());
        assertEquals("test-user-us-west", octetToString(keyCondition.key().value()));

        // Find and verify each header

        KafkaConditionFW userId = conditions.matchFirst(c -> c.header() != null &&
                "user-id".equals(octetToString(c.header().name())));
        assertNotNull(userId);
        assertEquals("test-user", octetToString(userId.header().value()));

        KafkaConditionFW region = conditions.matchFirst(c -> c.header() != null &&
                "region".equals(octetToString(c.header().name())));
        assertNotNull(region);
        assertEquals("us-west", octetToString(region.header().value()));
    }

    private SseKafkaConditionMatcher createConditionMatcherWithParam(
            String name,
            String value)
    {
        SseKafkaConditionMatcher matcher = mock(SseKafkaConditionMatcher.class);
        when(matcher.parameter(name)).thenReturn(value);
        return matcher;
    }

    private String octetToString(OctetsFW octetsFW)
    {
        if (octetsFW == null)
        {
            return null;
        }
        DirectBuffer buffer = octetsFW.value();
        return buffer.getStringWithoutLengthUtf8(0, buffer.capacity());
    }

    private <T extends Flyweight, B extends Flyweight.Builder<T>> B wrap(
            B builder)
    {
        return (B) builder.wrap(new ExpandableArrayBuffer(0), 0, Integer.MAX_VALUE);
    }
}

