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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

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

import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaCorrelationConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaIdempotencyConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchFilterConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchFilterHeaderConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceOverrideConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaConditionFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;

public class HttpKafkaWithResolverTest
{
    private static final long AUTHORIZATION = 0xFEDCBA9876543210L;
    private static final long COMPOSITE_ID = 0x1234567890ABCDEFL;

    private HttpBeginExFW httpBeginEx;
    private HttpKafkaOptionsConfig options;
    private HttpKafkaWithConfig withConfig;
    private LongObjectBiFunction<MatchResult, String> identityReplacer;
    private Array32FW<HttpHeaderFW> headers;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp()
    {
        httpBeginEx = mock(HttpBeginExFW.class);

        // Create config objects with proper constructors
        String8FW idempotencyHeader = new String8FW("Idempotency-Key");
        HttpKafkaIdempotencyConfig idempotencyConfig = new HttpKafkaIdempotencyConfig(idempotencyHeader);
        String16FW correlationReplyTo = null; // Not needed for tests
        String16FW correlationId = new String16FW("correlation");
        HttpKafkaCorrelationConfig correlationConfig = new HttpKafkaCorrelationConfig(correlationReplyTo, correlationId);
        options = new HttpKafkaOptionsConfig(idempotencyConfig, correlationConfig);

        // Create HttpKafkaWithConfig using constructor through reflection
        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .build();

        identityReplacer = mock(LongObjectBiFunction.class);
        headers = mock(Array32FW.class);
        when(httpBeginEx.headers()).thenReturn(headers);
    }

    @Test
    public void shouldResolveFetchWithBasicTopic()
    {
        // Given
        HttpKafkaWithFetchConfig fetchConfig = new HttpKafkaWithFetchConfig("test-topic", null, null);
        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .fetch(fetchConfig)
            .build();

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);

        // When
        HttpKafkaWithFetchResult result = resolver.resolveFetch(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        assertEquals(COMPOSITE_ID, result.compositeId());
        assertEquals("test-topic", result.topic().asString());
        assertEquals(0L, result.timeout());
    }

    @Test
    public void shouldResolveFetchWithParamReplacement()
    {
        // Given
        HttpKafkaWithFetchConfig fetchConfig = new HttpKafkaWithFetchConfig("topic-${params.id}", null, null);
        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .fetch(fetchConfig)
            .build();

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);
        resolver.onConditionMatched(createConditionMatcherWithParam("id", "123"));

        // When
        HttpKafkaWithFetchResult result = resolver.resolveFetch(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        assertEquals("topic-123", result.topic().asString());
    }

    @Test
    public void shouldResolveFetchWithHeaderReplacement()
    {
        // Given
        HttpKafkaWithFetchConfig fetchConfig = new HttpKafkaWithFetchConfig("topic-${headers.x-custom}", null, null);
        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .fetch(fetchConfig)
            .build();

        mockHeader("x-custom", "custom-value");

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);

        // When
        HttpKafkaWithFetchResult result = resolver.resolveFetch(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        assertEquals("topic-custom-value", result.topic().asString());
    }

    @Test
    public void shouldResolveFetchWithIdentityReplacement()
    {
        // Given
        HttpKafkaWithFetchConfig fetchConfig = new HttpKafkaWithFetchConfig("topic-${guarded['user'].identity}", null, null);
        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .fetch(fetchConfig)
            .build();

        when(identityReplacer.apply(Mockito.eq(AUTHORIZATION), any())).thenReturn("test-user");

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);

        // When
        HttpKafkaWithFetchResult result = resolver.resolveFetch(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        assertEquals("topic-test-user", result.topic().asString());
    }

    @Test
    public void shouldResolveFetchWithFilters()
    {
        // Given
        HttpKafkaWithFetchFilterHeaderConfig headerConfig =
            new HttpKafkaWithFetchFilterHeaderConfig("header1", "value-${params.id}");

        List<HttpKafkaWithFetchFilterHeaderConfig> headerConfigs = new ArrayList<>();
        headerConfigs.add(headerConfig);
        HttpKafkaWithFetchFilterConfig filter =
            new HttpKafkaWithFetchFilterConfig("key-${params.id}", headerConfigs);

        List<HttpKafkaWithFetchFilterConfig> filters = new ArrayList<>();
        filters.add(filter);

        HttpKafkaWithFetchConfig fetchConfig = new HttpKafkaWithFetchConfig("test-topic", filters, null);

        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .fetch(fetchConfig)
            .build();

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);
        resolver.onConditionMatched(createConditionMatcherWithParam("id", "123"));

        // When
        HttpKafkaWithFetchResult result = resolver.resolveFetch(AUTHORIZATION, httpBeginEx);

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
    public void shouldResolveProduceWithBasicTopic()
    {
        // Given
        HttpKafkaWithProduceConfig produceConfig = HttpKafkaWithProduceConfig.builder()
            .topic("test-topic")
            .build();

        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .produce(produceConfig)
            .build();

        // Mock :method header to not be GET (avoiding correlation ID logic for this test)
        mockHeader(":method", "POST");

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);

        // When
        HttpKafkaWithProduceResult result = resolver.resolveProduce(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        assertEquals(COMPOSITE_ID, result.compositeId());
        assertEquals("test-topic", result.topic().asString());
    }

    @Test
    public void shouldResolveProduceWithKeyReplacement()
    {
        // Given
        HttpKafkaWithProduceConfig produceConfig = HttpKafkaWithProduceConfig.builder()
            .topic("test-topic")
            .key("key-${params.id}")
            .build();

        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .produce(produceConfig)
            .build();

        mockHeader(":method", "POST");

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);
        resolver.onConditionMatched(createConditionMatcherWithParam("id", "123"));

        // When
        HttpKafkaWithProduceResult result = resolver.resolveProduce(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        KafkaKeyFW.Builder builder = new KafkaKeyFW.Builder();
        builder = wrap(builder);
        result.key(builder);
        assertEquals("key-123", octetToString(builder.build().value()));
    }

    @Test
    public void shouldResolveProduceWithOverrides()
    {
        // Given
        HttpKafkaWithProduceOverrideConfig override =
            new HttpKafkaWithProduceOverrideConfig("header1", "value-${params.id}");

        List<HttpKafkaWithProduceOverrideConfig> overrides = new ArrayList<>();
        overrides.add(override);

        HttpKafkaWithProduceConfig produceConfig = HttpKafkaWithProduceConfig.builder()
            .topic("test-topic")
            .overrides(overrides)
            .build();

        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .produce(produceConfig)
            .build();

        mockHeader(":method", "POST");

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);
        resolver.onConditionMatched(createConditionMatcherWithParam("id", "123"));

        // When
        HttpKafkaWithProduceResult result = resolver.resolveProduce(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> headersBuilder =
                new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW());
        headersBuilder = wrap(headersBuilder);
        result.headers((HttpHeaderFW) null, headersBuilder);
        Array32FW<KafkaHeaderFW> headersResult = headersBuilder.build();
        assertEquals(1, headersResult.fieldCount());
        var overrideResult = headersResult.matchFirst(x -> true);
        assertEquals("header1", octetToString(overrideResult.name()));
        assertEquals("value-123", octetToString(overrideResult.value()));
    }

    @Test
    public void shouldResolveProduceWithIdentityReplacementInTopic()
    {
        // Given
        HttpKafkaWithProduceConfig produceConfig = HttpKafkaWithProduceConfig.builder()
            .topic("topic-${guarded['user'].identity}")
            .build();

        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .produce(produceConfig)
            .build();

        mockHeader(":method", "POST");
        when(identityReplacer.apply(Mockito.eq(AUTHORIZATION), any())).thenReturn("test-user");

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);

        // When
        HttpKafkaWithProduceResult result = resolver.resolveProduce(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        assertEquals("topic-test-user", result.topic().asString());
    }

    @Test
    public void shouldResolveProduceWithHeaderReplacementInTopic()
    {
        // Given
        HttpKafkaWithProduceConfig produceConfig = HttpKafkaWithProduceConfig.builder()
            .topic("topic-${headers.x-tenant}")
            .build();

        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .produce(produceConfig)
            .build();

        mockHeader(":method", "POST");
        mockHeader("x-tenant", "tenant-a");

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);

        // When
        HttpKafkaWithProduceResult result = resolver.resolveProduce(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        assertEquals("topic-tenant-a", result.topic().asString());
    }

    @Test
    public void shouldResolveProduceWithParamReplacementInTopic()
    {
        // Given
        HttpKafkaWithProduceConfig produceConfig = HttpKafkaWithProduceConfig.builder()
            .topic("topic-${params.tenant}")
            .build();

        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .produce(produceConfig)
            .build();

        mockHeader(":method", "POST");
        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);
        resolver.onConditionMatched(createConditionMatcherWithParam("tenant", "tenant-b"));

        // When
        HttpKafkaWithProduceResult result = resolver.resolveProduce(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        assertEquals("topic-tenant-b", result.topic().asString());
    }

    @Test
    public void shouldResolveProduceWithIdentityReplacementInKey()
    {
        // Given
        HttpKafkaWithProduceConfig produceConfig = HttpKafkaWithProduceConfig.builder()
            .topic("test-topic")
            .key("user-${guarded['user'].identity}")
            .build();

        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .produce(produceConfig)
            .build();

        mockHeader(":method", "POST");
        when(identityReplacer.apply(Mockito.eq(AUTHORIZATION), any())).thenReturn("test-user");

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);

        // When
        HttpKafkaWithProduceResult result = resolver.resolveProduce(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        KafkaKeyFW.Builder builder = new KafkaKeyFW.Builder();
        builder = wrap(builder);
        result.key(builder);
        assertEquals("user-test-user", octetToString(builder.build().value()));
    }

    @Test
    public void shouldResolveProduceWithHeaderReplacementInKey()
    {
        // Given
        HttpKafkaWithProduceConfig produceConfig = HttpKafkaWithProduceConfig.builder()
            .topic("test-topic")
            .key("tenant-${headers.x-tenant}")
            .build();

        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .produce(produceConfig)
            .build();

        mockHeader(":method", "POST");
        mockHeader("x-tenant", "tenant-c");

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);

        // When
        HttpKafkaWithProduceResult result = resolver.resolveProduce(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        KafkaKeyFW.Builder builder = new KafkaKeyFW.Builder();
        builder = wrap(builder);
        result.key(builder);
        assertEquals("tenant-tenant-c", octetToString(builder.build().value()));
    }

    @Test
    public void shouldResolveProduceWithIdentityReplacementInHeader()
    {
        // Given
        HttpKafkaWithProduceOverrideConfig override =
            new HttpKafkaWithProduceOverrideConfig("user-id", "${guarded['user'].identity}");

        List<HttpKafkaWithProduceOverrideConfig> overrides = new ArrayList<>();
        overrides.add(override);

        HttpKafkaWithProduceConfig produceConfig = HttpKafkaWithProduceConfig.builder()
            .topic("test-topic")
            .overrides(overrides)
            .build();

        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .produce(produceConfig)
            .build();

        mockHeader(":method", "POST");
        when(identityReplacer.apply(Mockito.eq(AUTHORIZATION), any())).thenReturn("test-user");

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);

        // When
        HttpKafkaWithProduceResult result = resolver.resolveProduce(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> headersBuilder =
                new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW());
        headersBuilder = wrap(headersBuilder);
        result.headers((HttpHeaderFW) null, headersBuilder);
        Array32FW<KafkaHeaderFW> headersResult = headersBuilder.build();
        assertEquals(1, headersResult.fieldCount());
        var overrideResult = headersResult.matchFirst(x -> true);
        assertEquals("user-id", octetToString(overrideResult.name()));
        assertEquals("test-user", octetToString(overrideResult.value()));
    }

    @Test
    public void shouldResolveProduceWithHeaderReplacementInHeader()
    {
        // Given
        HttpKafkaWithProduceOverrideConfig override =
            new HttpKafkaWithProduceOverrideConfig("forwarded-tenant", "${headers.x-tenant}");

        List<HttpKafkaWithProduceOverrideConfig> overrides = new ArrayList<>();
        overrides.add(override);

        HttpKafkaWithProduceConfig produceConfig = HttpKafkaWithProduceConfig.builder()
            .topic("test-topic")
            .overrides(overrides)
            .build();

        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .produce(produceConfig)
            .build();

        mockHeader(":method", "POST");
        mockHeader("x-tenant", "tenant-d");

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);

        // When
        HttpKafkaWithProduceResult result = resolver.resolveProduce(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> headersBuilder =
                new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW());
        headersBuilder = wrap(headersBuilder);
        result.headers((HttpHeaderFW) null, headersBuilder);
        Array32FW<KafkaHeaderFW> headersResult = headersBuilder.build();
        assertEquals(1, headersResult.fieldCount());
        var overrideResult = headersResult.matchFirst(x -> true);
        assertEquals("forwarded-tenant", octetToString(overrideResult.name()));
        assertEquals("tenant-d", octetToString(overrideResult.value()));
    }

    @Test
    public void shouldResolveProduceWithMultipleReplacementsInAllFields()
    {
        // Given
        HttpKafkaWithProduceOverrideConfig override1 =
            new HttpKafkaWithProduceOverrideConfig("user-id", "${guarded['user'].identity}");
        HttpKafkaWithProduceOverrideConfig override2 =
            new HttpKafkaWithProduceOverrideConfig("tenant", "${headers.x-tenant}");
        HttpKafkaWithProduceOverrideConfig override3 =
            new HttpKafkaWithProduceOverrideConfig("region", "${params.region}");

        List<HttpKafkaWithProduceOverrideConfig> overrides = new ArrayList<>();
        overrides.add(override1);
        overrides.add(override2);
        overrides.add(override3);

        HttpKafkaWithProduceConfig produceConfig = HttpKafkaWithProduceConfig.builder()
            .topic("${params.region}-topic-${headers.x-tenant}")
            .key("${guarded['user'].identity}-${params.region}")
            .overrides(overrides)
            .build();

        withConfig = HttpKafkaWithConfig.builder()
            .compositeId(COMPOSITE_ID)
            .produce(produceConfig)
            .build();

        mockHeader(":method", "POST");
        mockHeader("x-tenant", "tenant-e");
        when(identityReplacer.apply(Mockito.eq(AUTHORIZATION), any())).thenReturn("test-user");

        HttpKafkaWithResolver resolver = new HttpKafkaWithResolver(options, identityReplacer, withConfig);
        resolver.onConditionMatched(createConditionMatcherWithParam("region", "us-west"));

        // When
        HttpKafkaWithProduceResult result = resolver.resolveProduce(AUTHORIZATION, httpBeginEx);

        // Then
        assertNotNull(result);

        // Check topic
        assertEquals("us-west-topic-tenant-e", result.topic().asString());

        // Check key
        KafkaKeyFW.Builder keyBuilder = new KafkaKeyFW.Builder();
        keyBuilder = wrap(keyBuilder);
        result.key(keyBuilder);
        assertEquals("test-user-us-west", octetToString(keyBuilder.build().value()));

        // Check headers
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> headersBuilder =
            new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW());
        headersBuilder = wrap(headersBuilder);
        result.headers((HttpHeaderFW) null, headersBuilder);
        Array32FW<KafkaHeaderFW> headersResult = headersBuilder.build();
        assertEquals(3, headersResult.fieldCount());

        // Find and verify each header
        KafkaHeaderFW userId = headersResult.matchFirst(h -> "user-id".equals(octetToString(h.name())));
        assertNotNull(userId);
        assertEquals("test-user", octetToString(userId.value()));

        KafkaHeaderFW tenant = headersResult.matchFirst(h -> "tenant".equals(octetToString(h.name())));
        assertNotNull(tenant);
        assertEquals("tenant-e", octetToString(tenant.value()));

        KafkaHeaderFW region = headersResult.matchFirst(h -> "region".equals(octetToString(h.name())));
        assertNotNull(region);
        assertEquals("us-west", octetToString(region.value()));
    }

    private HttpKafkaConditionMatcher createConditionMatcherWithParam(
        String name,
        String value)
    {
        HttpKafkaConditionMatcher matcher = mock(HttpKafkaConditionMatcher.class);
        when(matcher.parameter(name)).thenReturn(value);
        return matcher;
    }

    private void mockHeader(
        String name,
        String value)
    {
        var builder = new Array32FW.Builder<>(new HttpHeaderFW.Builder(), new HttpHeaderFW());
        builder = wrap(builder);
        headers = builder.item(h -> h.name(name).value(value)).build();
        when(httpBeginEx.headers()).thenReturn(headers);
    }

    private String octetToString(
        OctetsFW octetsFW)
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
