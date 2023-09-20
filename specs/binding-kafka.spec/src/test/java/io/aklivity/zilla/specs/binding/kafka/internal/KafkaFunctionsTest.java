/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.kafka.internal;

import static io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaConditionType.HEADER;
import static io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaConditionType.HEADERS;
import static io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaConditionType.KEY;
import static io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaConditionType.NOT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.kaazing.k3po.lang.internal.el.ExpressionFactoryUtils.newExpressionFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;

import org.agrona.DirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;

import io.aklivity.zilla.specs.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaCapabilities;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaDeltaFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaEvaluation;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaIsolation;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaSkip;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaTransactionFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaTransactionResult;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaValueMatchFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.rebalance.MemberAssignmentFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.rebalance.TopicAssignmentFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaApi;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaBootstrapBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaConsumerBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaConsumerDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaDescribeBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaDescribeDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaFetchBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaFetchDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaFetchFlushExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaGroupBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaGroupFlushExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaGroupMemberMetadataFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaMergedBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaMergedDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaMergedFlushExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaMetaBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaMetaDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaOffsetCommitBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaOffsetCommitDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaOffsetFetchBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaOffsetFetchDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaOffsetFetchTopicFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaProduceBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaProduceDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaProduceFlushExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaResetExFW;

public class KafkaFunctionsTest
{
    private ExpressionFactory factory;
    private ELContext ctx;

    @Before
    public void setUp() throws Exception
    {
        factory = newExpressionFactory();
        ctx = new ExpressionContext();
    }

    @Test
    public void shouldGenerateMemberMetadata()
    {
        byte[] build = KafkaFunctions.memberMetadata()
            .consumerId("localhost:9092")
            .topic("test-1")
                .partitionId(0)
                .partitionId(1)
                .build()
            .topic("test-2")
                .partitionId(0)
                .partitionId(1)
                .partitionId(2)
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaGroupMemberMetadataFW memberMetadata =
            new KafkaGroupMemberMetadataFW().wrap(buffer, 0, buffer.capacity());

        assertEquals("localhost:9092", memberMetadata.consumerId().asString());
        assertEquals(2, memberMetadata.topics().fieldCount());
    }

    @Test
    public void shouldGenerateMemberAssignment()
    {
        byte[] build = KafkaFunctions.memberAssignment()
            .member("memberId-1")
               .assignment()
                .topic("test")
                .partitionId(0)
                .consumer()
                    .id("localhost:9092")
                    .partitionId(0)
                    .build()
                .build()
            .build()
        .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        Array32FW<MemberAssignmentFW> assignments =
            new Array32FW<>(new MemberAssignmentFW()).wrap(buffer, 0, buffer.capacity());

        assignments.forEach(a ->
        {
            assertEquals("memberId-1", a.memberId().asString());
        });
    }

    @Test
    public void shouldGenerateTopicAssignment()
    {
        byte[] build = KafkaFunctions.topicAssignment()
            .topic()
                .id("test")
                .partitionId(0)
                .consumer()
                    .id("localhost:9092")
                    .partitionId(0)
                    .build()
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        Array32FW<TopicAssignmentFW> topics =
            new Array32FW<>(new TopicAssignmentFW()).wrap(buffer, 0, buffer.capacity());

        topics.forEach(t ->
        {
            assertEquals("test", t.topic().asString());
        });
    }

    @Test
    public void shouldGenerateBootstrapBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .bootstrap()
                                         .topic("topic")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.BOOTSTRAP.value(), beginEx.kind());

        final KafkaBootstrapBeginExFW bootstrapBeginEx = beginEx.bootstrap();
        assertEquals("topic", bootstrapBeginEx.topic().asString());
    }

    @Test
    public void shouldGenerateMetaBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .meta()
                                         .topic("topic")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.META.value(), beginEx.kind());

        final KafkaMetaBeginExFW metaBeginEx = beginEx.meta();
        assertEquals("topic", metaBeginEx.topic().asString());
    }

    @Test
    public void shouldGenerateMetaDataExtension()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .meta()
                                         .partition(0, 1)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.META.value(), dataEx.kind());

        final KafkaMetaDataExFW metaDataEx = dataEx.meta();
        final MutableInteger partitionsCount = new MutableInteger();
        metaDataEx.partitions().forEach(f -> partitionsCount.value++);
        assertEquals(1, partitionsCount.value);

        assertNotNull(metaDataEx.partitions()
                .matchFirst(p -> p.partitionId() == 0 && p.leaderId() == 1));
    }

    @Test
    public void shouldGenerateDescribeBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .describe()
                                         .topic("topic")
                                         .config("cleanup.policy")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.DESCRIBE.value(), beginEx.kind());

        final KafkaDescribeBeginExFW describeBeginEx = beginEx.describe();
        assertEquals("topic", describeBeginEx.topic().asString());

        final MutableInteger configsCount = new MutableInteger();
        describeBeginEx.configs().forEach(f -> configsCount.value++);
        assertEquals(1, configsCount.value);

        assertNotNull(describeBeginEx.configs()
                .matchFirst(c -> "cleanup.policy".equals(c.asString())));
    }

    @Test
    public void shouldGenerateDescribeDataExtension()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .describe()
                                         .config("cleanup.policy", "compact")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.DESCRIBE.value(), dataEx.kind());

        final KafkaDescribeDataExFW describeDataEx = dataEx.describe();
        final MutableInteger configsCount = new MutableInteger();
        describeDataEx.configs().forEach(f -> configsCount.value++);
        assertEquals(1, configsCount.value);

        assertNotNull(describeDataEx.configs()
                .matchFirst(c -> "cleanup.policy".equals(c.name().asString()) &&
                                 "compact".equals(c.value().asString())));
    }

    @Test
    public void shouldGenerateMergedBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .merged()
                                         .capabilities("PRODUCE_AND_FETCH")
                                         .topic("topic")
                                         .groupId("groupId")
                                         .consumerId("consumerId")
                                         .partition(0, 1L)
                                         .filter()
                                             .key("match")
                                             .build()
                                         .filter()
                                             .header("name", "value")
                                             .build()
                                         .isolation("READ_UNCOMMITTED")
                                         .deltaType("NONE")
                                         .ackMode("IN_SYNC_REPLICAS")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), beginEx.kind());

        final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
        assertEquals("topic", mergedBeginEx.topic().asString());
        assertEquals("groupId", mergedBeginEx.groupId().asString());
        assertEquals("consumerId", mergedBeginEx.consumerId().asString());

        assertNotNull(mergedBeginEx.partitions()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        final MutableInteger filterCount = new MutableInteger();
        mergedBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == KEY.value() &&
                    "match".equals(c.key()
                                    .value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADER.value() &&
                    "name".equals(c.header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(c.header().value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));

    }

    @Test
    public void shouldGenerateMergedBeginExtensionWithHeaderNotEqualsFilter()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .merged()
                                         .topic("topic")
                                         .partition(0, 1L)
                                         .filter()
                                             .key("match")
                                             .build()
                                         .filter()
                                             .headerNot("name", "value")
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), beginEx.kind());

        final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
        assertEquals("topic", mergedBeginEx.topic().asString());

        assertNotNull(mergedBeginEx.partitions()
                                   .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        final MutableInteger filterCount = new MutableInteger();
        mergedBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == KEY.value() &&
                    "match".equals(c.key()
                                    .value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == NOT.value() && c.not().condition().kind() == HEADER.value() &&
                    "name".equals(c.not().condition().header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(c.not().condition().header().value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
    }

    @Test
    public void shouldGenerateMergedBeginExtensionWithKeyNotEqualsFilter()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .merged()
                                         .topic("topic")
                                         .partition(0, 1L)
                                         .filter()
                                             .keyNot("match")
                                             .build()
                                         .filter()
                                             .header("name", "value")
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), beginEx.kind());

        final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
        assertEquals("topic", mergedBeginEx.topic().asString());

        assertNotNull(mergedBeginEx.partitions()
                                   .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        final MutableInteger filterCount = new MutableInteger();
        mergedBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == NOT.value() && c.not().condition().kind() == KEY.value() &&
                    "match".equals(c.not().condition().key()
                                    .value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADER.value() &&
                    "name".equals(c.header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(c.header().value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
    }

    @Test
    public void shouldGenerateMergedBeginExtensionWithNullKeyOrHeaderNotEqualsFilter()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .merged()
                                         .topic("topic")
                                         .partition(0, 1L)
                                         .filter()
                                             .keyNot(null)
                                             .build()
                                         .filter()
                                             .headerNot("name", null)
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), beginEx.kind());

        final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
        assertEquals("topic", mergedBeginEx.topic().asString());

        assertNotNull(mergedBeginEx.partitions()
                                   .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        final MutableInteger filterCount = new MutableInteger();
        mergedBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == NOT.value() && c.not().condition().kind() == KEY.value() &&
                    Objects.isNull(c.not().condition().key().value())) != null));
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == NOT.value() && c.not().condition().kind() == HEADER.value() &&
                    "name".equals(c.not().condition().header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    Objects.isNull(c.not().condition().header().value())) != null));
    }

    @Test
    public void shouldGenerateMergedBeginExtensionWithNullKeyOrNullHeaderValue()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .merged()
                                         .topic("topic")
                                         .partition(0, 1L)
                                         .filter()
                                             .key(null)
                                             .build()
                                         .filter()
                                             .header("name", null)
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), beginEx.kind());

        final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
        assertEquals("topic", mergedBeginEx.topic().asString());

        final MutableInteger partitionCount = new MutableInteger();
        mergedBeginEx.partitions().forEach(f -> partitionCount.value++);
        assertEquals(1, partitionCount.value);

        assertNotNull(mergedBeginEx.partitions()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        final MutableInteger filterCount = new MutableInteger();
        mergedBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == KEY.value() &&
                    Objects.isNull(c.key().value())) != null));
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADER.value() &&
                    "name".equals(c.header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    Objects.isNull(c.header().value())) != null));
    }

    @Test
    public void shouldGenerateMergedDataExtension()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .merged()
                                         .timestamp(12345678L)
                                         .filters(-1L)
                                         .partition(0, 0L)
                                         .progress(0, 1L)
                                         .key("match")
                                         .hashKey("hashKey")
                                         .header("name", "value")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), dataEx.kind());

        final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
        assertEquals(12345678L, mergedDataEx.timestamp());
        assertEquals(-1L, mergedDataEx.filters());

        final KafkaOffsetFW partition = mergedDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger progressCount = new MutableInteger();
        mergedDataEx.progress().forEach(f -> progressCount.value++);
        assertEquals(1, progressCount.value);

        assertNotNull(mergedDataEx.progress()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertEquals("match", mergedDataEx.key()
                                         .value()
                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        mergedDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(mergedDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(h.value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))));

        assertEquals("hashKey", mergedDataEx.hashKey()
            .value()
            .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
    }

    @Test
    public void shouldGenerateMergedDataExtensionWithByteValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .merged()
                                         .timestamp(12345678L)
                                         .partition(0, 0L)
                                         .progress(0, 1L)
                                         .key("match")
                                         .headerByte("name", (byte) 1)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), dataEx.kind());

        final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
        assertEquals(12345678L, mergedDataEx.timestamp());

        final KafkaOffsetFW partition = mergedDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger progressCount = new MutableInteger();
        mergedDataEx.progress().forEach(f -> progressCount.value++);
        assertEquals(1, progressCount.value);

        assertNotNull(mergedDataEx.progress()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertEquals("match", mergedDataEx.key()
                                         .value()
                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        mergedDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(mergedDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                        h.value().get((b, o, m) -> b.getByte(o)) == (byte) 1));
    }

    @Test
    public void shouldGenerateMergedDataExtensionWithShortValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .merged()
                                         .timestamp(12345678L)
                                         .partition(0, 0L)
                                         .progress(0, 1L)
                                         .key("match")
                                         .headerShort("name", (short) 1)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), dataEx.kind());

        final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
        assertEquals(12345678L, mergedDataEx.timestamp());

        final KafkaOffsetFW partition = mergedDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger progressCount = new MutableInteger();
        mergedDataEx.progress().forEach(f -> progressCount.value++);
        assertEquals(1, progressCount.value);

        assertNotNull(mergedDataEx.progress()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertEquals("match", mergedDataEx.key()
                                         .value()
                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        mergedDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(mergedDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    h.value().get((b, o, m) -> b.getShort(o, ByteOrder.BIG_ENDIAN)) == (short) 1));
    }

    @Test
    public void shouldGenerateMergedDataExtensionWithIntValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .merged()
                                         .timestamp(12345678L)
                                         .partition(0, 0L)
                                         .progress(0, 1L)
                                         .key("match")
                                         .headerInt("name", 1)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), dataEx.kind());

        final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
        assertEquals(12345678L, mergedDataEx.timestamp());

        final KafkaOffsetFW partition = mergedDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger progressCount = new MutableInteger();
        mergedDataEx.progress().forEach(f -> progressCount.value++);
        assertEquals(1, progressCount.value);

        assertNotNull(mergedDataEx.progress()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertEquals("match", mergedDataEx.key()
                                         .value()
                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        mergedDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(mergedDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    h.value().get((b, o, m) -> b.getInt(o, ByteOrder.BIG_ENDIAN)) == 1));
    }

    @Test
    public void shouldGenerateMergedDataExtensionWithLongValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .merged()
                                         .timestamp(12345678L)
                                         .partition(0, 0L)
                                         .progress(0, 1L)
                                         .key("match")
                                         .headerLong("name", 1)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), dataEx.kind());

        final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
        assertEquals(12345678L, mergedDataEx.timestamp());

        final KafkaOffsetFW partition = mergedDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger progressCount = new MutableInteger();
        mergedDataEx.progress().forEach(f -> progressCount.value++);
        assertEquals(1, progressCount.value);

        assertNotNull(mergedDataEx.progress()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertEquals("match", mergedDataEx.key()
                                         .value()
                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        mergedDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(mergedDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    h.value().get((b, o, m) -> b.getLong(o, ByteOrder.BIG_ENDIAN)) == 1L));
    }

    @Test
    public void shouldGenerateMergedDataExtensionWithByteArrayValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .merged()
                                         .deferred(100)
                                         .timestamp(12345678L)
                                         .partition(0, 0L)
                                         .progress(0, 1L)
                                         .key("match")
                                         .headerBytes("name", "value".getBytes(UTF_8))
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), dataEx.kind());

        final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
        assertEquals(100, mergedDataEx.deferred());
        assertEquals(12345678L, mergedDataEx.timestamp());

        final KafkaOffsetFW partition = mergedDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger progressCount = new MutableInteger();
        mergedDataEx.progress().forEach(f -> progressCount.value++);
        assertEquals(1, progressCount.value);

        assertNotNull(mergedDataEx.progress()
                                  .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertEquals("match", mergedDataEx.key()
                                          .value()
                                          .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        mergedDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(mergedDataEx.headers()
                                  .matchFirst(h ->
                                                  "name".equals(h.name()
                                                                 .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                                                      "value".equals(h.value()
                                                             .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))));
    }

    @Test
    public void shouldGenerateMergedDataExtensionWithNullValue()
    {
        byte[] build = KafkaFunctions.dataEx()
            .typeId(0x01)
            .merged()
            .timestamp(12345678L)
            .partition(0, 0L)
            .progress(0, 1L)
            .key("match")
            .headerNull("name")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), dataEx.kind());

        final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
        assertEquals(12345678L, mergedDataEx.timestamp());

        final KafkaOffsetFW partition = mergedDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger progressCount = new MutableInteger();
        mergedDataEx.progress().forEach(f -> progressCount.value++);
        assertEquals(1, progressCount.value);

        assertNotNull(mergedDataEx.progress()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertEquals("match", mergedDataEx.key()
                .value()
                .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        mergedDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(mergedDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    Objects.isNull(h.value())));
    }

    @Test
    public void shouldGenerateMergedDataExtensionWithNullKeyAndNullHeaderValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .merged()
                                         .timestamp(12345678L)
                                         .partition(0, 0L)
                                         .progress(0, 1L)
                                         .key(null)
                                         .header("name", null)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), dataEx.kind());

        final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
        assertEquals(12345678L, mergedDataEx.timestamp());

        final KafkaOffsetFW partition = mergedDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger progressCount = new MutableInteger();
        mergedDataEx.progress().forEach(f -> progressCount.value++);
        assertEquals(1, progressCount.value);

        assertNotNull(mergedDataEx.progress()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertNull(mergedDataEx.key().value());

        final MutableInteger headersCount = new MutableInteger();
        mergedDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(mergedDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    Objects.isNull(h.value())));
    }

    @Test
    public void shouldGenerateMergedDataExtensionWithNullKeyAndNullByteArrayHeaderValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .merged()
                                     .timestamp(12345678L)
                                     .partition(0, 0L)
                                     .progress(0, 1L)
                                     .key(null)
                                     .headerBytes("name", null)
                                     .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), dataEx.kind());

        final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
        assertEquals(12345678L, mergedDataEx.timestamp());

        final KafkaOffsetFW partition = mergedDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger progressCount = new MutableInteger();
        mergedDataEx.progress().forEach(f -> progressCount.value++);
        assertEquals(1, progressCount.value);

        assertNotNull(mergedDataEx.progress()
                                  .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertNull(mergedDataEx.key().value());

        final MutableInteger headersCount = new MutableInteger();
        mergedDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(mergedDataEx.headers()
                                  .matchFirst(h ->
                                                  "name".equals(h.name()
                                                                 .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                                                      Objects.isNull(h.value())));
    }

    @Test
    public void shouldGenerateMergedFetchFlushExtension()
    {
        byte[] build = KafkaFunctions.flushEx()
                                     .typeId(0x01)
                                     .merged()
                                        .fetch()
                                         .partition(1, 2)
                                         .capabilities("PRODUCE_AND_FETCH")
                                         .progress(0, 1L)
                                         .filter()
                                             .key("match")
                                             .build()
                                         .filter()
                                             .header("name", "value")
                                             .build()
                                         .key("key")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaFlushExFW flushEx = new KafkaFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, flushEx.typeId());

        final KafkaMergedFlushExFW mergedFlushEx = flushEx.merged();
        final MutableInteger partitionsCount = new MutableInteger();
        mergedFlushEx.fetch().progress().forEach(f -> partitionsCount.value++);
        assertEquals(1, partitionsCount.value);

        assertNotNull(mergedFlushEx.fetch().progress()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        assertEquals(mergedFlushEx.fetch().key().value().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)), "key");
        assertEquals(mergedFlushEx.fetch().partition().partitionId(), 1);
        assertEquals(mergedFlushEx.fetch().partition().partitionOffset(), 2);

        final MutableInteger filterCount = new MutableInteger();
        mergedFlushEx.fetch().filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(mergedFlushEx.fetch().filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == KEY.value() &&
                    "match".equals(c.key()
                                    .value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(mergedFlushEx.fetch().filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADER.value() &&
                    "name".equals(c.header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(c.header().value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
    }

    @Test
    public void shouldGenerateMergedFetchFlushExtensionWithStableOffset()
    {
        byte[] build = KafkaFunctions.flushEx()
                                     .typeId(0x01)
                                     .merged()
                                        .fetch()
                                         .partition(0, 1L, 1L)
                                         .capabilities("PRODUCE_AND_FETCH")
                                         .progress(0, 1L, 1L, 1L)
                                         .filter()
                                             .key("match")
                                             .build()
                                         .filter()
                                             .header("name", "value")
                                             .build()
                                         .key(null)
                                         .build()
                                    .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaFlushExFW flushEx = new KafkaFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, flushEx.typeId());

        final KafkaMergedFlushExFW mergedFlushEx = flushEx.merged();
        final MutableInteger partitionsCount = new MutableInteger();
        mergedFlushEx.fetch().progress().forEach(f -> partitionsCount.value++);
        assertEquals(1, partitionsCount.value);

        assertEquals(mergedFlushEx.fetch().partition().partitionId(), 0);
        assertEquals(mergedFlushEx.fetch().partition().partitionOffset(), 1L);
        assertEquals(mergedFlushEx.fetch().partition().latestOffset(), 1L);

        assertNotNull(mergedFlushEx.fetch().progress()
                .matchFirst(p -> p.partitionId() == 0 &&
                    p.partitionOffset() == 1L &&
                    p.stableOffset() == 1L &&
                    p.latestOffset() == 1L));

        final MutableInteger filterCount = new MutableInteger();
        mergedFlushEx.fetch().filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(mergedFlushEx.fetch().filters()
                .matchFirst(f -> f.conditions()
                        .matchFirst(c -> c.kind() == KEY.value() &&
                                "match".equals(c.key()
                                        .value()
                                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(mergedFlushEx.fetch().filters()
                .matchFirst(f -> f.conditions()
                        .matchFirst(c -> c.kind() == HEADER.value() &&
                                "name".equals(c.header().name()
                                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                                "value".equals(c.header().value()
                                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
    }

    @Test
    public void shouldGenerateMergedConsumerFlushExtension()
    {
        byte[] build = KafkaFunctions.flushEx()
            .typeId(0x01)
            .merged()
                .consumer()
                    .partition(1, 2)
                    .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaFlushExFW flushEx = new KafkaFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x1, flushEx.typeId());

        final KafkaMergedFlushExFW mergedFlushEx = flushEx.merged();

        assertEquals(mergedFlushEx.consumer().partition().partitionId(), 1);
        assertEquals(mergedFlushEx.consumer().partition().partitionOffset(), 2);
    }

    @Test
    public void shouldMatchMergedDataExtension() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .deferred(100)
                                                 .partition(0, 0L)
                                                 .progress(0, 1L)
                                                 .timestamp(12345678L)
                                                 .key("match")
                                                 .header("name", "value")
                                                 .hashKey("hashKey")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.deferred(100)
                             .timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L))
                             .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .hashKey(k -> k.length(7)
                                 .value(v -> v.set("hashKey".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))
                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(5)
                                                .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionWithLatestOffset() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 0L, 1L)
                                                 .progress(0, 1L, 1L)
                                                 .timestamp(12345678L)
                                                 .key("match")
                                                 .header("name", "value")
                                                 .build()
                                             .build();


        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L).latestOffset(1L))
                             .progressItem(p -> p.partitionId(0).partitionOffset(1L).latestOffset(1L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(5)
                                                .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionWithByteArrayValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 0L)
                                                 .progress(0, 1L)
                                                 .timestamp(12345678L)
                                                 .key("match")
                                                 .headerBytes("name", "value".getBytes(UTF_8))
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L))
                             .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(5)
                                                .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionWithNullValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 0L)
                                                 .progress(0, 1L)
                                                 .timestamp(12345678L)
                                                 .key("match")
                                                 .headerNull("name")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L))
                             .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(-1)
                                                .value((OctetsFW) null)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionWithByteValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 0L)
                                                 .progress(0, 1L)
                                                 .timestamp(12345678L)
                                                 .key("match")
                                                 .headerByte("name", (byte) 1)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);
        byte[] value = ByteBuffer.allocate(1).put((byte) 1).array();

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L))
                             .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(1)
                                                .value(v -> v.set(value))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionWithShortValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 0L)
                                                 .progress(0, 1L)
                                                 .timestamp(12345678L)
                                                 .key("match")
                                                 .headerShort("name", (short) 1)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);
        byte[] value = ByteBuffer.allocate(2).putShort((short) 1).array();

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L))
                             .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(2)
                                                .value(v -> v.set(value))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionWithIntValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 0L)
                                                 .progress(0, 1L)
                                                 .timestamp(12345678L)
                                                 .key("match")
                                                 .headerInt("name", 1)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);
        byte[] value = ByteBuffer.allocate(4).putInt(1).array();

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L))
                             .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(4)
                                                .value(v -> v.set(value))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionWithLongValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 0L)
                                                 .progress(0, 1L)
                                                 .timestamp(12345678L)
                                                 .key("match")
                                                 .headerLong("name", 1L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);
        byte[] value = ByteBuffer.allocate(8).putLong(1L).array();

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L))
                             .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(8)
                                                .value(v -> v.set(value))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionTimestamp() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .timestamp(12345678L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionPartition() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .partition(0, 0L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionProgress() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .progress(0, 1L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .key("match")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionNullKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .key(null)
                                                 .hashKey(null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(-1)
                                   .value((OctetsFW) null))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionDelta() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .delta("NONE", -1L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionHeader() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .header("name", "value")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionHeaderWithNullValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .header("name", null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(-1)
                                           .value((OctetsFW) null)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedDataExtensionHeaderWithNullByteArrayValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .merged()
                                                 .headerBytes("name", null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(-1)
                                           .value((OctetsFW) null)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedDataExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x02)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedDataExtensionTimestamp() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .timestamp(123456789L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedDataExtensionPartition() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 1L)
                                                 .timestamp(12345678L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                                       .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedDataExtensionProgress() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .progress(0, 2L)
                                                 .timestamp(12345678L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                                       .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedDataExtensionKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 0L)
                                                 .timestamp(12345678L)
                                                 .key("no match")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                                       .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedDataExtensionDelta() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .merged()
                                                 .partition(0, 10L)
                                                 .timestamp(12345678L)
                                                 .delta("JSON_PATCH", 9L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(10L))
                        .progressItem(p -> p.partitionId(0).partitionOffset(1L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateFetchBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .topic("topic")
                                         .partition(0, 0L)
                                         .filter()
                                             .key("match")
                                             .build()
                                         .filter()
                                             .header("name", "value")
                                             .build()
                                         .evaluation("LAZY")
                                         .isolation("READ_UNCOMMITTED")
                                         .deltaType("NONE")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), beginEx.kind());

        final KafkaFetchBeginExFW fetchBeginEx = beginEx.fetch();
        assertEquals("topic", fetchBeginEx.topic().asString());

        final KafkaOffsetFW partition = fetchBeginEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger filterCount = new MutableInteger();
        fetchBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == KEY.value() &&
                    "match".equals(c.key()
                                    .value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADER.value() &&
                    "name".equals(c.header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(c.header().value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
    }

    @Test
    public void shouldGenerateFetchBeginExtensionWithLatestOffset()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .topic("topic")
                                         .partition(0, 0L, 0L)
                                         .filter()
                                             .key("match")
                                             .build()
                                         .filter()
                                             .header("name", "value")
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), beginEx.kind());

        final KafkaFetchBeginExFW fetchBeginEx = beginEx.fetch();
        assertEquals("topic", fetchBeginEx.topic().asString());

        final KafkaOffsetFW partition = fetchBeginEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());
        assertEquals(0L, partition.latestOffset());

        final MutableInteger filterCount = new MutableInteger();
        fetchBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == KEY.value() &&
                    "match".equals(c.key()
                                    .value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADER.value() &&
                    "name".equals(c.header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(c.header().value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
    }
    @Test
    public void shouldGenerateFetchBeginExtensionWithNullKeyAndNullHeaderValue()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .topic("topic")
                                         .partition(0, 0L)
                                         .filter()
                                             .key(null)
                                             .build()
                                         .filter()
                                             .header("name", null)
                                             .build()
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), beginEx.kind());

        final KafkaFetchBeginExFW fetchBeginEx = beginEx.fetch();
        assertEquals("topic", fetchBeginEx.topic().asString());

        final KafkaOffsetFW partition = fetchBeginEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        final MutableInteger filterCount = new MutableInteger();
        fetchBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(2, filterCount.value);
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == KEY.value() &&
                    Objects.isNull(c.key().value())) != null));
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADER.value() &&
                    "name".equals(c.header().name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    Objects.isNull(c.header().value())) != null));
    }

    @Test
    public void shouldGenerateFetchDataExtension()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .deferred(10)
                                         .timestamp(12345678L)
                                         .filters(-1L)
                                         .partition(0, 1L)
                                         .key("match")
                                         .delta("JSON_PATCH", 0)
                                         .header("name", "value")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), dataEx.kind());

        final KafkaFetchDataExFW fetchDataEx = dataEx.fetch();
        assertEquals(10, fetchDataEx.deferred());
        assertEquals(12345678L, fetchDataEx.timestamp());
        assertEquals(-1L, fetchDataEx.filters());

        final KafkaOffsetFW partition = fetchDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(1L, partition.partitionOffset());

        assertEquals("match", fetchDataEx.key()
                                         .value()
                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        fetchDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(fetchDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(h.value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))));

        final KafkaDeltaFW delta = fetchDataEx.delta();
        assertEquals(KafkaDeltaType.JSON_PATCH, delta.type().get());
        assertEquals(0L, delta.ancestorOffset());
    }

    @Test
    public void shouldGenerateFetchDataExtensionWithLatestOffset()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .deferred(10)
                                         .timestamp(12345678L)
                                         .partition(0, 0L, 0L)
                                         .key("match")
                                         .header("name", "value")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), dataEx.kind());

        final KafkaFetchDataExFW fetchDataEx = dataEx.fetch();
        assertEquals(10, fetchDataEx.deferred());
        assertEquals(12345678L, fetchDataEx.timestamp());

        final KafkaOffsetFW partition = fetchDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());
        assertEquals(0L, partition.latestOffset());

        assertEquals("match", fetchDataEx.key()
                                         .value()
                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        fetchDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(fetchDataEx.headers()
                                 .matchFirst(h ->
                                     "name".equals(h.name()
                                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                                         "value".equals(h.value()
                                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))));
    }

    @Test
    public void shouldGenerateFetchDataExtensionWithNullKeyAndNullHeaderValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .timestamp(12345678L)
                                         .partition(0, 0L)
                                         .key(null)
                                         .header("name", null)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), dataEx.kind());

        final KafkaFetchDataExFW fetchDataEx = dataEx.fetch();
        assertEquals(12345678L, fetchDataEx.timestamp());

        final KafkaOffsetFW partition = fetchDataEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(0L, partition.partitionOffset());

        assertNull(fetchDataEx.key().value());

        final MutableInteger headersCount = new MutableInteger();
        fetchDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(fetchDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    Objects.isNull(h.value())));
    }

    @Test
    public void shouldGenerateFetchFlushExtension()
    {
        byte[] build = KafkaFunctions.flushEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .partition(0, 1L)
                                         .transaction("COMMIT", 1L)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaFlushExFW flushEx = new KafkaFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, flushEx.typeId());

        final KafkaFetchFlushExFW fetchFlushEx = flushEx.fetch();
        final KafkaOffsetFW partition = fetchFlushEx.partition();
        final Array32FW<KafkaTransactionFW> transactions = fetchFlushEx.transactions();
        KafkaTransactionFW transaction = transactions.matchFirst(t -> true);
        assertEquals(0, partition.partitionId());
        assertEquals(1L, partition.partitionOffset());
        assertFalse(transactions.isEmpty());
        assertEquals(KafkaTransactionResult.COMMIT, transaction.result().get());
        assertEquals(1L, transaction.producerId());
    }

    @Test
    public void shouldGenerateFetchFlushExtensionWithFilter()
    {
        KafkaValueMatchFW valueMatchRO = new KafkaValueMatchFW();
        byte[] build = KafkaFunctions.flushEx()
            .typeId(0x01)
            .fetch()
                .filter()
                    .headers("name")
                        .sequence("one", "two")
                        .skip(1)
                        .sequence("four")
                        .skipMany()
                        .build()
                    .build()
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaFlushExFW flushEx = new KafkaFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, flushEx.typeId());

        final KafkaFetchFlushExFW fetchFlushEx = flushEx.fetch();

        final MutableInteger filterCount = new MutableInteger();
        fetchFlushEx.filters().forEach(f -> filterCount.value++);
        assertEquals(1, filterCount.value);
        assertNotNull(fetchFlushEx.filters()
            .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADERS.value() &&
                    "name".equals(c.headers().name()
                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(fetchFlushEx.filters()
            .matchFirst(f -> f.conditions()
                .matchFirst(c ->
                {
                    boolean matches;
                    final Array32FW<KafkaValueMatchFW> values = c.headers().values();
                    final DirectBuffer items = values.items();

                    int progress = 0;

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches = "one".equals(valueMatchRO.value()
                        .value()
                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= "two".equals(valueMatchRO.value()
                        .value()
                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= KafkaSkip.SKIP == valueMatchRO.skip().get();

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= "four".equals(valueMatchRO.value()
                        .value()
                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= KafkaSkip.SKIP_MANY == valueMatchRO.skip().get();

                    return c.kind() == HEADERS.value() && matches;
                }) != null));

    }

    @Test
    public void shouldGenerateFetchFlushExtensionWithLatestOffset()
    {
        byte[] build = KafkaFunctions.flushEx()
                                     .typeId(0x01)
                                     .fetch()
                                         .partition(0, 1L, 1L)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaFlushExFW flushEx = new KafkaFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, flushEx.typeId());

        final KafkaFetchFlushExFW fetchFlushEx = flushEx.fetch();
        final KafkaOffsetFW partition = fetchFlushEx.partition();
        assertEquals(0, partition.partitionId());
        assertEquals(1L, partition.partitionOffset());
        assertEquals(1L, partition.latestOffset());
    }

    @Test
    public void shouldGenerateGroupFlushExtension()
    {
        byte[] build = KafkaFunctions.flushEx()
            .typeId(0x01)
            .group()
                .leaderId("consumer-1")
                .memberId("consumer-2")
                .members("memberId-1", "test".getBytes())
                .members("memberId-2", "test".getBytes())
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaFlushExFW flushEx = new KafkaFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, flushEx.typeId());

        final KafkaGroupFlushExFW groupFlushEx = flushEx.group();
        final String leaderId = groupFlushEx.leaderId().asString();
        final String memberId = groupFlushEx.memberId().asString();
        assertEquals("consumer-1", leaderId);
        assertEquals("consumer-2", memberId);
        assertEquals(2, groupFlushEx.members().fieldCount());
    }

    @Test
    public void shouldGenerateGroupFlushExtensionWithEmptyMetadata()
    {
        byte[] build = KafkaFunctions.flushEx()
            .typeId(0x01)
            .group()
            .leaderId("consumer-1")
            .memberId("consumer-2")
            .members("memberId-1")
            .members("memberId-2")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaFlushExFW flushEx = new KafkaFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, flushEx.typeId());

        final KafkaGroupFlushExFW groupFlushEx = flushEx.group();
        final String leaderId = groupFlushEx.leaderId().asString();
        final String memberId = groupFlushEx.memberId().asString();
        assertEquals("consumer-1", leaderId);
        assertEquals("consumer-2", memberId);
        assertEquals(2, groupFlushEx.members().fieldCount());
    }

    @Test
    public void shouldMatchFetchDataExtension() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .fetch()
                                                 .timestamp(12345678L)
                                                 .filters(-1L)
                                                 .partition(0, 0L)
                                                 .key("match")
                                                 .header("name", "value")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                             .filters(-1L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(5)
                                                .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionWithLatestOffset() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .fetch()
                                                 .timestamp(12345678L)
                                                 .partition(0, 0L, 0L)
                                                 .key("match")
                                                 .header("name", "value")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L).stableOffset(0L).latestOffset(0L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(5)
                                                .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionWithStableOffset() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .fetch()
                                                 .timestamp(12345678L)
                                                 .partition(0, 0L, 0L, 1L)
                                                 .key("match")
                                                 .header("name", "value")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                             .partition(p -> p.partitionId(0).partitionOffset(0L).stableOffset(0L).latestOffset(1L))
                             .key(k -> k.length(5)
                                        .value(v -> v.set("match".getBytes(UTF_8))))
                             .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                             .headersItem(h -> h.nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(5)
                                                .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchBeginExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .fetch(f -> f
                .topic("test")
                .partition(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8))))))
                .deltaType(d -> d.set(KafkaDeltaType.NONE)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchBeginExtensionTopic() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .fetch()
                                                 .topic("test")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .fetch(f -> f
                .topic("test")
                .partition(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8))))))
                .deltaType(d -> d.set(KafkaDeltaType.NONE)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchBeginExtensionPartition() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .fetch()
                                                 .partition(0, 0)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .fetch(f -> f
                .topic("test")
                .partition(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8))))))
                .deltaType(d -> d.set(KafkaDeltaType.NONE)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchBeginExtensionPartitionWithStableOffset() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                .fetch()
                .partition(0, 0L, 0L, 0L)
                .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
                .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f
                        .topic("test")
                        .partition(p -> p
                            .partitionId(0)
                            .partitionOffset(0L)
                            .stableOffset(0L)
                            .latestOffset(0L))
                        .filtersItem(i -> i
                                .conditionsItem(c -> c
                                        .key(k -> k
                                                .length(3)
                                                .value(v -> v.set("key".getBytes(UTF_8)))))
                                .conditionsItem(c -> c
                                        .header(h -> h
                                                .nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(5)
                                                .value(v -> v.set("value".getBytes(UTF_8))))))
                        .deltaType(d -> d.set(KafkaDeltaType.NONE)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchBeginExtensionFilter() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .fetch()
                                                 .filter()
                                                     .key("key")
                                                     .header("name", "value")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .fetch(f -> f
                .topic("test")
                .partition(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8))))))
                .deltaType(d -> d.set(KafkaDeltaType.NONE)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchBeginExtensionIsolation() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .fetch()
                                                 .isolation("READ_UNCOMMITTED")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f
                    .topic("test")
                    .partition(p -> p.partitionId(0).partitionOffset(0L))
                    .filtersItem(i -> i.conditionsItem(c -> c.key(k -> k.length(3)
                                                                        .value(v -> v.set("key".getBytes(UTF_8))))))
                    .isolation(i -> i.set(KafkaIsolation.READ_UNCOMMITTED))
                    .deltaType(d -> d.set(KafkaDeltaType.NONE)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchBeginExtensionDeltaType() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .fetch()
                                                 .deltaType("NONE")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f
                    .topic("test")
                    .partition(p -> p.partitionId(0).partitionOffset(0L))
                    .filtersItem(i -> i.conditionsItem(c -> c.key(k -> k.length(3)
                                                                        .value(v -> v.set("key".getBytes(UTF_8))))))
                    .deltaType(d -> d.set(KafkaDeltaType.NONE)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchBeginExtensionEvaluation() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                .fetch()
                .evaluation("LAZY")
                .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f
                        .topic("test")
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .filtersItem(i -> i.conditionsItem(c -> c.key(k -> k.length(3)
                                .value(v -> v.set("key".getBytes(UTF_8))))))
                        .evaluation(d -> d.set(KafkaEvaluation.LAZY)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceBeginExtensionTransaction() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .produce()
                                                 .transaction("transaction")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .produce(f -> f
                .transaction("transaction")
                .producerId(1L)
                .topic("test")
                .partition(p -> p.partitionId(0).partitionOffset(0L)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceBeginExtensionProducerId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .produce()
                                                 .producerId(1L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .produce(f -> f
                .transaction("transaction")
                .producerId(1L)
                .topic("test")
                .partition(p -> p.partitionId(0).partitionOffset(0L)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceBeginExtensionTopic() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .produce()
                                                 .topic("test")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .produce(f -> f
                .transaction("transaction")
                .producerId(1L)
                .topic("test")
                .partition(p -> p.partitionId(0).partitionOffset(0L)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceBeginExtensionPartition() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .produce()
                                                 .partition(0, 0)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .produce(f -> f
                .transaction("transaction")
                .producerId(1L)
                .topic("test")
                .partition(p -> p.partitionId(0).partitionOffset(0L)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedBeginExtensionCapabilities() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .merged()
                                                 .capabilities("PRODUCE_AND_FETCH")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .merged(f -> f
                .topic("test")
                .partitionsItem(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8)))))))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedBeginExtensionTopic() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .merged()
                                                 .topic("test")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .merged(f -> f
                .topic("test")
                .partitionsItem(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8)))))))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedBeginExtensionGroupId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
            .merged()
                .topic("topic")
                .groupId("test")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .merged(f -> f
                .topic("topic")
                .groupId("test")
                .partitionsItem(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8)))))))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedBeginExtensionConsumerId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
            .merged()
                .topic("topic")
                .consumerId("test")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .merged(f -> f
                .topic("topic")
                .consumerId("test")
                .partitionsItem(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8)))))))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedBeginExtensionPartitions() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .merged()
                                                 .partition(0, 0)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .merged(f -> f
                .topic("test")
                .partitionsItem(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8)))))))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedBeginExtensionFilters() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .merged()
                                                 .filter()
                                                     .key("key")
                                                     .header("name", "value")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .merged(f -> f
                .topic("test")
                .partitionsItem(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8)))))))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedBeginExtensionIsolation() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .merged()
                                                 .isolation("READ_UNCOMMITTED")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .merged(f -> f
                .topic("test")
                .partitionsItem(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8))))))
                .isolation(i -> i.set(KafkaIsolation.READ_UNCOMMITTED)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedBeginExtensionDeltaType() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .merged()
                                                 .deltaType("NONE")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .merged(f -> f
                .topic("test")
                .partitionsItem(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8)))))))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedBeginExtensionEvaluation() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                .merged()
                .evaluation("LAZY")
                .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
                .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f
                        .topic("test")
                        .partitionsItem(p -> p.partitionId(0).partitionOffset(0L))
                        .filtersItem(i -> i
                                .conditionsItem(c -> c
                                        .key(k -> k
                                                .length(3)
                                                .value(v -> v.set("key".getBytes(UTF_8)))))
                                .conditionsItem(c -> c
                                        .header(h -> h
                                                .nameLen(4)
                                                .name(n -> n.set("name".getBytes(UTF_8)))
                                                .valueLen(5)
                                                .value(v -> v.set("value".getBytes(UTF_8))))))
                        .evaluation(d -> d.set(KafkaEvaluation.LAZY)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }


    @Test
    public void shouldMatchMergedBeginExtensionAckMode() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .merged()
                                                 .ackMode("IN_SYNC_REPLICAS")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .merged(f -> f
                .topic("test")
                .partitionsItem(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8)))))))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBeginExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
                                             .typeId(0x02)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .merged(f -> f
                .topic("test")
                .partitionsItem(p -> p.partitionId(0).partitionOffset(0L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8)))))))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldMatchDataExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionTimestamp() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .timestamp(12345678L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionPartition() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .partition(0, 0L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .key("match")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionNullKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .key(null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(-1)
                                   .value((OctetsFW) null))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionDelta() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .delta("NONE", -1L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionHeader() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .header("name", "value")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchDataExtensionHeaderWithNullValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .fetch()
                                                 .header("name", null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(-1)
                                           .value((OctetsFW) null)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchDataExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x02)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldNotBuildMatcher() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1);

        Object matched = matcher.match(byteBuf);

        assertNull(matched);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchFetchDataExtensionTimestamp() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .fetch()
                                                 .timestamp(123456789L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                                       .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchFetchDataExtensionPartition() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .fetch()
                                                 .timestamp(12345678L)
                                                 .partition(0, 1L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                                       .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchFetchDataExtensionKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .fetch()
                                                 .partition(0, 0L)
                                                 .timestamp(12345678L)
                                                 .key("no match")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.timestamp(12345678L)
                        .partition(p -> p.partitionId(0).partitionOffset(0L))
                        .key(k -> k.value(v -> v.set("match".getBytes(UTF_8))))
                        .delta(d -> d.type(t -> t.set(KafkaDeltaType.NONE)))

                        .headersItem(h -> h.name(n -> n.set("name".getBytes(UTF_8)))
                                                       .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateProduceBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .produce()
                                         .transaction("transaction")
                                         .producerId(1L)
                                         .topic("topic")
                                         .partition(1)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.PRODUCE.value(), beginEx.kind());

        final KafkaProduceBeginExFW produceBeginEx = beginEx.produce();
        assertEquals("transaction", produceBeginEx.transaction().asString());
        assertEquals(1L, produceBeginEx.producerId());
        assertEquals(1, produceBeginEx.partition().partitionId());
        assertEquals("topic", produceBeginEx.topic().asString());
        assertEquals(-1L, produceBeginEx.partition().partitionOffset());
    }

    @Test
    public void shouldGenerateProduceDataExtension()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .produce()
                                         .deferred(10)
                                         .timestamp(12345678L)
                                         .sequence(0)
                                         .ackMode("IN_SYNC_REPLICAS")
                                         .key("match")
                                         .header("name", "value")
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.PRODUCE.value(), dataEx.kind());

        final KafkaProduceDataExFW produceDataEx = dataEx.produce();
        assertEquals(10, produceDataEx.deferred());
        assertEquals(12345678L, produceDataEx.timestamp());
        assertEquals(0, produceDataEx.sequence());
        assertEquals(KafkaAckMode.IN_SYNC_REPLICAS, produceDataEx.ackMode().get());

        assertEquals("match", produceDataEx.key()
                                           .value()
                                           .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

        final MutableInteger headersCount = new MutableInteger();
        produceDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(produceDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    "value".equals(h.value()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))));
    }

    @Test
    public void shouldGenerateProduceResetExtension()
    {
        byte[] build = KafkaFunctions.resetEx()
                .typeId(0x01)
                .error(87)
                .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaResetExFW resetEx = new KafkaResetExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0x01, resetEx.typeId());
        assertEquals(87, resetEx.error());
    }

    @Test
    public void shouldGenerateResetExtensionWithConsumerId()
    {
        byte[] build = KafkaFunctions.resetEx()
            .typeId(0x01)
            .consumerId("consumer-1")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaResetExFW resetEx = new KafkaResetExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0x01, resetEx.typeId());
        assertEquals("consumer-1", resetEx.consumerId().asString());
    }


    @Test
    public void shouldMatchProduceDataExtensionTimestamp() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .produce()
                                                 .timestamp(12345678L)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                               .sequence(0)
                               .key(k -> k.length(5)
                                          .value(v -> v.set("match".getBytes(UTF_8))))
                               .headersItem(h -> h.nameLen(4)
                                                  .name(n -> n.set("name".getBytes(UTF_8)))
                                                  .valueLen(5)
                                                  .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceDataExtensionSequence() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .produce()
                                                 .sequence(0)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceDataExtensionAckMode() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .produce()
                                                 .ackMode("IN_SYNC_REPLICAS")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .ackMode(a -> a.set(KafkaAckMode.IN_SYNC_REPLICAS))
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceDataExtensionKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .produce()
                                                 .key("match")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceDataExtensionNullKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .produce()
                                                 .key(null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(-1)
                                   .value((OctetsFW) null))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceDataExtensionHeader() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .produce()
                                                 .header("name", "value")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceDataExtensionHeaderWithNullValue() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .produce()
                                                 .header("name", null)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(-1)
                                           .value((OctetsFW) null)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchProduceDataExtensionSequence() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .produce()
                                                 .timestamp(12345678L)
                                                 .sequence(1)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchProduceDataExtensionKey() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchDataEx()
                                             .typeId(0x01)
                                             .produce()
                                                 .sequence(0)
                                                 .timestamp(12345678L)
                                                 .key("no match")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .produce(p -> p.timestamp(12345678L)
                        .sequence(0)
                        .key(k -> k.length(5)
                                   .value(v -> v.set("match".getBytes(UTF_8))))
                        .headersItem(h -> h.nameLen(4)
                                           .name(n -> n.set("name".getBytes(UTF_8)))
                                           .valueLen(5)
                                           .value(v -> v.set("value".getBytes(UTF_8)))))
                .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldMatchProduceFlushExtension() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
            .typeId(0x01)
            .produce()
                .partition(1, 2)
                .key("key")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .produce(f -> f.partition(p -> p.partitionId(1).partitionOffset(2))
                .key(k -> k.length(3).value(v -> v.set("key".getBytes(UTF_8)))))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchProduceFlushExtensionWithLatestOffset() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
            .typeId(0x01)
            .produce()
                .partition(0, 1L, 1L)
            .build()
            .build();


        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .produce(f ->
                f.partition(p -> p.partitionId(0).partitionOffset(1L).latestOffset(1L)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateProduceDataExtensionWithNullKeyAndNullHeaderValue()
    {
        byte[] build = KafkaFunctions.dataEx()
                                     .typeId(0x01)
                                     .produce()
                                         .timestamp(12345678L)
                                         .sequence(0)
                                         .key(null)
                                         .header("name", null)
                                         .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.PRODUCE.value(), dataEx.kind());

        final KafkaProduceDataExFW produceDataEx = dataEx.produce();
        assertEquals(12345678L, produceDataEx.timestamp());
        assertEquals(0, produceDataEx.sequence());

        assertNull(produceDataEx.key().value());

        final MutableInteger headersCount = new MutableInteger();
        produceDataEx.headers().forEach(f -> headersCount.value++);
        assertEquals(1, headersCount.value);
        assertNotNull(produceDataEx.headers()
                .matchFirst(h ->
                    "name".equals(h.name()
                                   .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o))) &&
                    Objects.isNull(h.value())));
    }

    @Test
    public void shouldGenerateProduceFlushExtension()
    {
        byte[] build = KafkaFunctions.flushEx()
            .typeId(0x01)
            .produce()
                .partition(1, 2)
                .key("key")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaFlushExFW flushEx = new KafkaFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, flushEx.typeId());

        final KafkaProduceFlushExFW produceFlushEx = flushEx.produce();

        assertEquals(produceFlushEx.key().value().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)), "key");
        assertEquals(produceFlushEx.partition().partitionId(), 1);
        assertEquals(produceFlushEx.partition().partitionOffset(), 2);
    }

    @Test
    public void shouldGenerateProduceFlushExtensionWithStableOffset()
    {
        byte[] build = KafkaFunctions.flushEx()
            .typeId(0x01)
            .produce()
                .partition(1, 2, 2)
                .key(null)
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaFlushExFW flushEx = new KafkaFlushExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, flushEx.typeId());

        final KafkaProduceFlushExFW produceFlushEx = flushEx.produce();

        assertEquals(produceFlushEx.partition().partitionId(), 1);
        assertEquals(produceFlushEx.partition().partitionOffset(), 2);
        assertEquals(produceFlushEx.partition().latestOffset(), 2);
    }

    @Test
    public void shouldGenerateFetchBeginExtensionWithHeadersFilter()
    {
        KafkaValueMatchFW valueMatchRO = new KafkaValueMatchFW();
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .fetch()
                                        .topic("topic")
                                        .partition(0, 1L)
                                        .filter()
                                            .headers("name")
                                                .sequence("one", "two")
                                                .skip(1)
                                                .sequence("four")
                                                .skipMany()
                                                .build()
                                            .build()
                                        .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.FETCH.value(), beginEx.kind());

        final KafkaFetchBeginExFW fetchBeginEx = beginEx.fetch();
        assertEquals("topic", fetchBeginEx.topic().asString());

        final MutableInteger filterCount = new MutableInteger();
        fetchBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(1, filterCount.value);
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADERS.value() &&
                    "name".equals(c.headers().name()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(fetchBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c ->
                {
                    boolean matches;
                    final Array32FW<KafkaValueMatchFW> values = c.headers().values();
                    final DirectBuffer items = values.items();

                    int progress = 0;

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches = "one".equals(valueMatchRO.value()
                                                       .value()
                                                       .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= "two".equals(valueMatchRO.value()
                                                        .value()
                                                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= KafkaSkip.SKIP == valueMatchRO.skip().get();

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= "four".equals(valueMatchRO.value()
                                                         .value()
                                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= KafkaSkip.SKIP_MANY == valueMatchRO.skip().get();

                    return c.kind() == HEADERS.value() && matches;
                }) != null));
    }

    @Test
    public void shouldGenerateMergedBeginExtensionWithHeadersFilter()
    {
        KafkaValueMatchFW valueMatchRO = new KafkaValueMatchFW();
        byte[] build = KafkaFunctions.beginEx()
                                     .typeId(0x01)
                                     .merged()
                                        .topic("topic")
                                        .partition(0, 1L)
                                        .filter()
                                            .headers("name")
                                                .sequence("one", "two")
                                                .skip(1)
                                                .sequence("four")
                                                .skipMany()
                                                .build()
                                            .build()
                                        .build()
                                     .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.MERGED.value(), beginEx.kind());

        final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
        assertEquals("topic", mergedBeginEx.topic().asString());

        assertNotNull(mergedBeginEx.partitions()
                .matchFirst(p -> p.partitionId() == 0 && p.partitionOffset() == 1L));

        final MutableInteger filterCount = new MutableInteger();
        mergedBeginEx.filters().forEach(f -> filterCount.value++);
        assertEquals(1, filterCount.value);
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c -> c.kind() == HEADERS.value() &&
                    "name".equals(c.headers().name()
                                    .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)))) != null));
        assertNotNull(mergedBeginEx.filters()
                .matchFirst(f -> f.conditions()
                .matchFirst(c ->
                {
                    boolean matches;
                    final Array32FW<KafkaValueMatchFW> values = c.headers().values();
                    final DirectBuffer items = values.items();

                    int progress = 0;

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches = "one".equals(valueMatchRO.value()
                                                       .value()
                                                       .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= "two".equals(valueMatchRO.value()
                                                        .value()
                                                        .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= KafkaSkip.SKIP == valueMatchRO.skip().get();

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= "four".equals(valueMatchRO.value()
                                                         .value()
                                                         .get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));

                    valueMatchRO.wrap(items, progress, items.capacity());
                    progress = valueMatchRO.limit();
                    matches &= KafkaSkip.SKIP_MANY == valueMatchRO.skip().get();

                    return c.kind() == HEADERS.value() && matches;
                }) != null));
    }

    @Test
    public void shouldGenerateGroupBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
            .typeId(0x01)
            .group()
                .groupId("test")
                .protocol("roundrobin")
                .timeout(10)
                .metadata("test".getBytes())
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.GROUP.value(), beginEx.kind());

        final KafkaGroupBeginExFW groupBeginEx = beginEx.group();
        assertEquals("test", groupBeginEx.groupId().asString());
        assertEquals("roundrobin", groupBeginEx.protocol().asString());
        assertEquals(10, groupBeginEx.timeout());
    }

    @Test
    public void shouldGenerateGroupBeginWithEmptyMetadataExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
            .typeId(0x01)
            .group()
                .groupId("test")
                .protocol("roundrobin")
                .timeout(10)
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.GROUP.value(), beginEx.kind());

        final KafkaGroupBeginExFW groupBeginEx = beginEx.group();
        assertEquals("test", groupBeginEx.groupId().asString());
        assertEquals("roundrobin", groupBeginEx.protocol().asString());
        assertEquals(10, groupBeginEx.timeout());
    }

    @Test
    public void shouldGenerateConsumerBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
            .typeId(0x01)
                .consumer()
                    .groupId("test")
                    .consumerId("consumer-1")
                    .timeout(10000)
                    .topic("topic")
                    .partition(0)
                    .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.CONSUMER.value(), beginEx.kind());

        final KafkaConsumerBeginExFW consumerBeginEx = beginEx.consumer();
        assertEquals("test", consumerBeginEx.groupId().asString());
        assertEquals("topic", consumerBeginEx.topic().asString());
        assertEquals(1, consumerBeginEx.partitionIds().fieldCount());
    }

    @Test
    public void shouldGenerateOffsetFetchBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
            .typeId(0x01)
            .offsetFetch()
            .groupId("test")
            .topic("topic", 0)
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.OFFSET_FETCH.value(), beginEx.kind());

        final KafkaOffsetFetchBeginExFW offsetFetchBeginEx = beginEx.offsetFetch();
        KafkaOffsetFetchTopicFW topic = offsetFetchBeginEx.topics()
            .matchFirst(t -> t.topic().asString().equals("topic"));
        assertEquals(1, topic.partitions().fieldCount());
    }

    @Test
    public void shouldGenerateOffsetCommitBeginExtension()
    {
        byte[] build = KafkaFunctions.beginEx()
            .typeId(0x01)
            .offsetCommit()
                .groupId("test")
                .topic("topic")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaBeginExFW beginEx = new KafkaBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        assertEquals(KafkaApi.OFFSET_COMMIT.value(), beginEx.kind());

        final KafkaOffsetCommitBeginExFW offsetCommitBeginEx = beginEx.offsetCommit();
        assertEquals("test", offsetCommitBeginEx.groupId().asString());
        assertEquals("topic", offsetCommitBeginEx.topic().asString());
    }

    @Test
    public void shouldMatchGroupBeginExtension() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchBeginEx()
            .group()
                .groupId("test")
                .protocol("roundrobin")
                .timeout(10)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .group(f -> f
                .groupId("test")
                .protocol("roundrobin")
                .timeout(10)
                .metadataLen("test".length())
                .metadata(m -> m.set("test".getBytes())))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateConsumerDataExtension()
    {
        byte[] build = KafkaFunctions.dataEx()
            .typeId(0x03)
            .consumer()
                .partition(0)
                .consumer()
                    .id("localhost:9092")
                    .partition(0)
                    .build()
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x03, dataEx.typeId());
        assertEquals(KafkaApi.CONSUMER.value(), dataEx.kind());

        final KafkaConsumerDataExFW consumerDataEx = dataEx.consumer();
        assertTrue(consumerDataEx.partitions().fieldCount() == 1);
        assertTrue(consumerDataEx.assignments().fieldCount() == 1);
    }

    @Test
    public void shouldGenerateOffsetFetchDataExtension()
    {
        byte[] build = KafkaFunctions.dataEx()
            .typeId(0x01)
            .offsetFetch()
                .topic("test", 0, 1L)
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.OFFSET_FETCH.value(), dataEx.kind());

        final KafkaOffsetFetchDataExFW offsetFetchDataEx = dataEx.offsetFetch();
        KafkaOffsetFW offset = offsetFetchDataEx.topic().offsets().matchFirst(o -> o.partitionId() == 0);
        assertEquals("test", offsetFetchDataEx.topic().topic().asString());
        assertEquals(0, offset.partitionId());
        assertEquals(1L, offset.partitionOffset());
    }

    @Test
    public void shouldGenerateOffsetCommitDataExtension()
    {
        byte[] build = KafkaFunctions.dataEx()
            .typeId(0x01)
            .offsetCommit()
                .partitionId(0)
                .partitionOffset(1L)
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(build);
        KafkaDataExFW dataEx = new KafkaDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        assertEquals(KafkaApi.OFFSET_COMMIT.value(), dataEx.kind());

        final KafkaOffsetCommitDataExFW offsetCommitDataEx = dataEx.offsetCommit();
        assertEquals(0, offsetCommitDataEx.partitionId());
        assertEquals(1L, offsetCommitDataEx.partitionOffset());
    }

    @Test
    public void shouldInvokeLength() throws Exception
    {
        String expressionText = "${kafka:length(\"text\")}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, int.class);
        Object actual = expression.getValue(ctx);
        assertEquals("text".length(), ((Integer) actual).intValue());
    }

    @Test
    public void shouldInvokeLengthAsShort() throws Exception
    {
        String expressionText = "${kafka:lengthAsShort(\"text\")}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, short.class);
        Object actual = expression.getValue(ctx);
        assertEquals("text".length(), ((Short) actual).intValue());
    }

    @Test
    public void shouldInvokeNewRequestId() throws Exception
    {
        String expressionText = "${kafka:newRequestId()}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, Integer.class);
        Object actual = expression.getValue(ctx);
        assertTrue(actual instanceof Integer);
    }

    @Test
    public void shouldInvokeRandomBytes() throws Exception
    {
        String expressionText = "${kafka:randomBytes(10)}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        Object actual = expression.getValue(ctx);
        assertTrue(actual instanceof byte[]);
        assertEquals(10, ((byte[]) actual).length);
    }

    @Test
    public void shouldInvokeTimestamp() throws Exception
    {
        String expressionText = "${kafka:timestamp()}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, Long.class);
        Object actual = expression.getValue(ctx);
        assertTrue(actual instanceof Long);
    }

    @Test
    public void shouldComputeVarintTenBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", Long.MAX_VALUE);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xfe, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintTenBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 62);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintNineBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 62);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintNineBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 55);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintEightBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 55);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintEightBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 48);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintSevenBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 48);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintSevenBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 41);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintSixBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 41);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintSixBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 34);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintFiveBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 34);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff,
                                       (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintFiveBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 27);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80,
                                       (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintFourBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 27);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintFourBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 20);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintThreeBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 20);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintThreeBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 13);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintTwoBytesMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 13);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0xff, 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintTwoBytesMin() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", 1L << 6);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { (byte) 0x80, 0x01 }, actuals);
    }

    @Test
    public void shouldComputeVarintOneByteMax() throws Exception
    {
        String expressionText = String.format("${kafka:varint(%d)}", -1L << 6);
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { 0x7f }, actuals);
    }

    @Test
    public void shouldComputeVarintOneByteMin() throws Exception
    {
        String expressionText = "${kafka:varint(0)}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, byte[].class);
        byte[] actuals = (byte[]) expression.getValue(ctx);
        assertArrayEquals(new byte[] { 0x00 }, actuals);
    }

    @Test
    public void shouldResolveOffsetTypeHistorical()
    {
        assertEquals(-2, KafkaFunctions.offset("HISTORICAL"));
    }

    @Test
    public void shouldResolveOffsetTypeLive()
    {
        assertEquals(-1, KafkaFunctions.offset("LIVE"));
    }

    @Test
    public void shouldMatchMergedFlushExtension() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x01)
                .merged()
                    .fetch()
                        .partition(1, 2)
                        .progress(0, 1L)
                        .capabilities("FETCH_ONLY")
                        .key("key")
                        .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f
                    .fetch(m -> m.partition(p -> p.partitionId(1).partitionOffset(2))
                    .progressItem(p -> p
                        .partitionId(0)
                        .partitionOffset(1L))
                        .capabilities(c -> c.set(KafkaCapabilities.FETCH_ONLY))
                    .key(k -> k.length(3).value(v -> v.set("key".getBytes(UTF_8))))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedFlushExtensionWithLatestOffset() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x01)
                .merged()
                    .fetch()
                    .partition(0, 1L, 1L)
                    .progress(0, 1L, 1L)
                    .build()
                .build();


        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f
                    .fetch(m -> m.partition(p -> p.partitionId(0).partitionOffset(1L).latestOffset(1L))
                    .progressItem(p -> p
                        .partitionId(0)
                        .partitionOffset(1L)
                        .latestOffset(1L))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedFlushExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x01)
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f
                    .fetch(m ->
                    m.progressItem(p -> p
                    .partitionId(0)
                    .partitionOffset(1L))))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedFlushExtensionProgress() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .merged()
                .fetch()
                .progress(0, 1L, 1L, 1L)
                .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f
                    .fetch(m -> m.progressItem(p -> p
                    .partitionId(0)
                    .partitionOffset(1L)
                    .stableOffset(1L)
                    .latestOffset(1L))))
                            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchMergedFlushExtensionFilters() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
            .typeId(0x01)
            .merged()
                .fetch()
                    .filter()
                        .key("key")
                        .header("name", "value")
                        .build()
                    .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .merged(f -> f
                    .fetch(m -> m.filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8)))))))
                )
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedFlushExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x02)
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f
                    .fetch(m -> m.progressItem(p -> p
                    .partitionId(0)
                    .partitionOffset(1L))))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMergedFlushExtensionProgress() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x01)
                .merged()
                .fetch()
                    .progress(0, 2L)
                    .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .merged(f -> f
                    .fetch(m -> m.progressItem(p -> p
                        .partitionId(0)
                        .partitionOffset(1L))))
                .build();

        matcher.match(byteBuf);
    }


    @Test
    public void shouldMatchFetchFlushExtension() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x01)
                .fetch()
                .partition(0, 0L)
                .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.partition(p -> p
                        .partitionId(0)
                        .partitionOffset(0L)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchFlushExtensionWithTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x01)
                .fetch()
                .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.partition(p -> p
                        .partitionId(0)
                        .partitionOffset(0L)
                        .stableOffset(0L)
                        .latestOffset(0L)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchFlushExtensionWithTransaction() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x01)
                .fetch()
                .transaction("ABORT", 1)
                .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f
                        .partition(p -> p
                        .partitionId(0)
                        .partitionOffset(0L)
                        .stableOffset(0L)
                        .latestOffset(1L))
                        .transactionsItem(t -> t
                            .result(r -> r.set(KafkaTransactionResult.ABORT))
                            .producerId(1)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchFlushExtensionWithFilter() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
            .typeId(0x01)
                .fetch()
                .filter()
                    .key("key")
                    .header("name", "value")
                    .build()
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .fetch(f -> f
                .partition(p -> p
                    .partitionId(0)
                    .partitionOffset(0L)
                    .stableOffset(0L)
                    .latestOffset(1L))
                .filtersItem(i -> i
                    .conditionsItem(c -> c
                        .key(k -> k
                            .length(3)
                            .value(v -> v.set("key".getBytes(UTF_8)))))
                    .conditionsItem(c -> c
                        .header(h -> h
                            .nameLen(4)
                            .name(n -> n.set("name".getBytes(UTF_8)))
                            .valueLen(5)
                            .value(v -> v.set("value".getBytes(UTF_8))))))
                .build())
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchFlushExtensionWithLatestOffset() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x01)
                .fetch()
                .partition(0, 0L, 0L)
                .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.partition(p -> p
                                .partitionId(0)
                                .partitionOffset(0L)
                                .stableOffset(0L)
                                .latestOffset(0L)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchFlushExtensionWithStableOffset() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x01)
                .fetch()
                .partition(0, 0L, 0L, 1L)
                .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.partition(p -> p
                                .partitionId(0)
                                .partitionOffset(0L)
                                .stableOffset(0L)
                                .latestOffset(1L)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFetchFlushExtensionPartition() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .fetch()
                .partition(0, 0L)
                .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.partition(p -> p
                                .partitionId(0)
                                .partitionOffset(0L)))
                .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchFetchFlushExtensionPartition() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x01)
                .fetch()
                .partition(0, 1L)
                .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.partition(p -> p
                                .partitionId(0)
                                .partitionOffset(0L)))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchFetchFlushExtensionWithStableOffset() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x01)
                .fetch()
                .partition(0, 0L, 0L, 1L)
                .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.partition(p -> p
                        .partitionId(0)
                        .partitionOffset(0L)
                        .stableOffset(1L)
                        .latestOffset(1L)))
                .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldMatchGroupFlushExtensionMembers() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
            .typeId(0x01)
            .group()
                .leaderId("memberId-1")
                .memberId("memberId-2")
                .members("memberId-1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .group(f -> f.leaderId("memberId-1").memberId("memberId-2").
                members(m -> m.item(i -> i.id("memberId-1"))))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchGroupFlushExtensionMembersMetadata() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
            .typeId(0x01)
            .group()
               .leaderId("memberId-1")
               .memberId("memberId-2")
               .members("memberId-1", "test")
               .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .group(f -> f.leaderId("memberId-1").memberId("memberId-2").
                members(m -> m.item(i -> i.id("memberId-1")
                     .metadataLen("test".length()).metadata(o -> o.set("test".getBytes())))))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }


    @Test(expected = Exception.class)
    public void shouldNotMatchFetchFlushExtensionWithLatestOffset() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x01)
                .fetch()
                .partition(0, 0L, 0L, 1L)
                .build()
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.partition(p -> p
                        .partitionId(0)
                        .partitionOffset(0L)
                        .stableOffset(0L)
                        .latestOffset(0L)))
                .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchFetchFlushExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = KafkaFunctions.matchFlushEx()
                .typeId(0x02)
                .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new KafkaFlushExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
                .typeId(0x01)
                .fetch(f -> f.partition(p -> p
                        .partitionId(0)
                        .partitionOffset(0L)
                        .stableOffset(0L)
                        .latestOffset(0L)))
                .build();

        matcher.match(byteBuf);
    }
}
