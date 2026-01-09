/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal;

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_CLEANUP_POLICY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_CLIENT_CLEANUP_DELAY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_CLIENT_RECONNECT_DELAY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_CLIENT_TRAILERS_SIZE_MAX;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_DELETE_RETENTION_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_DIRECTORY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_MAX_COMPACTION_LAG_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_MAX_MESSAGE_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_MIN_CLEANUP_DIRTY_RATIO;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_MIN_COMPACTION_LAG_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_PRODUCE_CAPACITY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_RETENTION_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_RETENTION_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_RETENTION_MILLIS_MAX;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SEGMENT_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SEGMENT_INDEX_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SEGMENT_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_BOOTSTRAP;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_RECONNECT_DELAY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_CONNECTION_POOL;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_CONNECTION_POOL_CLEANUP_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_DESCRIBE_MAX_AGE_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_FETCH_MAX_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_FETCH_MAX_WAIT_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_FETCH_PARTITION_MAX_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_GROUP_INITIAL_REBALANCE_DELAY_DEFAULT;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_GROUP_MAX_SESSION_TIMEOUT_DEFAULT;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_GROUP_MIN_SESSION_TIMEOUT_DEFAULT;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_GROUP_REBALANCE_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_ID;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_INSTANCE_ID;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_MAX_IDLE_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_META_MAX_AGE_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_PRODUCE_MAX_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_PRODUCE_MAX_RESPONSE_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_PRODUCE_RECORD_FRAMING_SIZE;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_SASL_SCRAM_NONCE;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_VERBOSE;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KafkaConfigurationTest
{
    public static final String KAFKA_CLIENT_ID_NAME = "zilla.binding.kafka.client.id";
    public static final String KAFKA_CLIENT_INSTANCE_ID_NAME = "zilla.binding.kafka.client.instance.id";
    public static final String KAFKA_CLIENT_MAX_IDLE_MILLIS_NAME = "zilla.binding.kafka.client.max.idle.ms";
    public static final String KAFKA_CLIENT_CONNECTION_POOL_CLEANUP_MILLIS_NAME =
        "zilla.binding.kafka.client.connection.pool.cleanup.millis";
    public static final String KAFKA_CLIENT_META_MAX_AGE_MILLIS_NAME = "zilla.binding.kafka.client.meta.max.age.ms";
    public static final String KAFKA_CLIENT_DESCRIBE_MAX_AGE_MILLIS_NAME = "zilla.binding.kafka.client.describe.max.age.ms";
    public static final String KAFKA_CLIENT_FETCH_MAX_WAIT_MILLIS_NAME = "zilla.binding.kafka.client.fetch.max.wait.millis";
    public static final String KAFKA_CLIENT_FETCH_MAX_BYTES_NAME = "zilla.binding.kafka.client.fetch.max.bytes";
    public static final String KAFKA_CLIENT_FETCH_PARTITION_MAX_BYTES_NAME =
        "zilla.binding.kafka.client.fetch.partition.max.bytes";
    public static final String KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS_NAME =
            "zilla.binding.kafka.client.produce.max.request.millis";
    public static final String KAFKA_CLIENT_PRODUCE_MAX_RESPONSE_MILLIS_NAME =
        "zilla.binding.kafka.client.produce.max.response.millis";
    public static final String KAFKA_CLIENT_PRODUCE_MAX_BYTES_NAME = "zilla.binding.kafka.client.produce.max.bytes";
    public static final String KAFKA_CLIENT_PRODUCE_RECORD_FRAMING_SIZE_NAME =
        "zilla.binding.kafka.client.produce.record.framing.size";
    public static final String KAFKA_CLIENT_SASL_SCRAM_NONCE_NAME = "zilla.binding.kafka.client.sasl.scram.nonce";
    public static final String KAFKA_CLIENT_GROUP_REBALANCE_TIMEOUT_NAME = "zilla.binding.kafka.client.group.rebalance.timeout";
    public static final String KAFKA_CLIENT_GROUP_MIN_SESSION_TIMEOUT_DEFAULT_NAME =
        "zilla.binding.kafka.client.group.min.session.timeout.default";
    public static final String KAFKA_CLIENT_GROUP_MAX_SESSION_TIMEOUT_DEFAULT_NAME =
        "zilla.binding.kafka.client.group.max.session.timeout.default";
    public static final String KAFKA_CLIENT_GROUP_INITIAL_REBALANCE_DELAY_DEFAULT_NAME =
        "zilla.binding.kafka.client.group.initial.rebalance.delay.default";
    public static final String KAFKA_CACHE_DIRECTORY_NAME = "zilla.binding.kafka.cache.directory";
    public static final String KAFKA_CACHE_SERVER_BOOTSTRAP_NAME = "zilla.binding.kafka.cache.server.bootstrap";
    public static final String KAFKA_CACHE_PRODUCE_CAPACITY_NAME = "zilla.binding.kafka.cache.produce.capacity";
    public static final String KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME = "zilla.binding.kafka.cache.server.reconnect";
    public static final String KAFKA_CACHE_CLIENT_RECONNECT_DELAY_NAME = "zilla.binding.kafka.cache.client.reconnect";
    public static final String KAFKA_CACHE_CLIENT_CLEANUP_DELAY_NAME = "zilla.binding.kafka.cache.client.cleanup.delay";
    public static final String KAFKA_CACHE_CLEANUP_POLICY_NAME = "zilla.binding.kafka.cache.cleanup.policy";
    public static final String KAFKA_CACHE_MAX_MESSAGE_BYTES_NAME = "zilla.binding.kafka.cache.max.message.bytes";
    public static final String KAFKA_CACHE_RETENTION_MILLIS_NAME = "zilla.binding.kafka.cache.retention.ms";
    public static final String KAFKA_CACHE_RETENTION_MILLIS_MAX_NAME = "zilla.binding.kafka.cache.retention.ms.max";
    public static final String KAFKA_CACHE_RETENTION_BYTES_NAME = "zilla.binding.kafka.cache.retention.bytes";
    public static final String KAFKA_CACHE_DELETE_RETENTION_MILLIS_NAME = "zilla.binding.kafka.cache.delete.retention.ms";
    public static final String KAFKA_CACHE_MIN_COMPACTION_LAG_MILLIS_NAME = "zilla.binding.kafka.cache.min.compaction.lag.ms";
    public static final String KAFKA_CACHE_MAX_COMPACTION_LAG_MILLIS_NAME = "zilla.binding.kafka.cache.max.compaction.lag.ms";
    public static final String KAFKA_CACHE_MIN_CLEANABLE_DIRTY_RATIO_NAME = "zilla.binding.kafka.cache.min.cleanable.dirty.ratio";
    public static final String KAFKA_CACHE_SEGMENT_MILLIS_NAME = "zilla.binding.kafka.cache.segment.ms";
    public static final String KAFKA_CACHE_SEGMENT_BYTES_NAME = "zilla.binding.kafka.cache.segment.bytes";
    public static final String KAFKA_CACHE_SEGMENT_INDEX_BYTES_NAME = "zilla.binding.kafka.cache.segment.index.bytes";
    public static final String KAFKA_CACHE_CLIENT_TRAILERS_SIZE_MAX_NAME = "zilla.binding.kafka.cache.client.trailers.size.max";
    public static final String KAFKA_CLIENT_CONNECTION_POOL_NAME = "zilla.binding.kafka.client.connection.pool";
    public static final String KAFKA_VERBOSE_NAME = "zilla.binding.kafka.verbose";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(KAFKA_CLIENT_ID.name(), KAFKA_CLIENT_ID_NAME);
        assertEquals(KAFKA_CLIENT_INSTANCE_ID.name(), KAFKA_CLIENT_INSTANCE_ID_NAME);
        assertEquals(KAFKA_CLIENT_MAX_IDLE_MILLIS.name(), KAFKA_CLIENT_MAX_IDLE_MILLIS_NAME);
        assertEquals(KAFKA_CLIENT_CONNECTION_POOL_CLEANUP_MILLIS.name(), KAFKA_CLIENT_CONNECTION_POOL_CLEANUP_MILLIS_NAME);
        assertEquals(KAFKA_CLIENT_META_MAX_AGE_MILLIS.name(), KAFKA_CLIENT_META_MAX_AGE_MILLIS_NAME);
        assertEquals(KAFKA_CLIENT_DESCRIBE_MAX_AGE_MILLIS.name(), KAFKA_CLIENT_DESCRIBE_MAX_AGE_MILLIS_NAME);
        assertEquals(KAFKA_CLIENT_FETCH_MAX_WAIT_MILLIS.name(), KAFKA_CLIENT_FETCH_MAX_WAIT_MILLIS_NAME);
        assertEquals(KAFKA_CLIENT_FETCH_MAX_BYTES.name(), KAFKA_CLIENT_FETCH_MAX_BYTES_NAME);
        assertEquals(KAFKA_CLIENT_FETCH_PARTITION_MAX_BYTES.name(), KAFKA_CLIENT_FETCH_PARTITION_MAX_BYTES_NAME);
        assertEquals(KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS.name(), KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS_NAME);
        assertEquals(KAFKA_CLIENT_PRODUCE_MAX_RESPONSE_MILLIS.name(), KAFKA_CLIENT_PRODUCE_MAX_RESPONSE_MILLIS_NAME);
        assertEquals(KAFKA_CLIENT_PRODUCE_MAX_BYTES.name(), KAFKA_CLIENT_PRODUCE_MAX_BYTES_NAME);
        assertEquals(KAFKA_CLIENT_PRODUCE_RECORD_FRAMING_SIZE.name(), KAFKA_CLIENT_PRODUCE_RECORD_FRAMING_SIZE_NAME);
        assertEquals(KAFKA_CLIENT_SASL_SCRAM_NONCE.name(), KAFKA_CLIENT_SASL_SCRAM_NONCE_NAME);
        assertEquals(KAFKA_CLIENT_GROUP_REBALANCE_TIMEOUT.name(), KAFKA_CLIENT_GROUP_REBALANCE_TIMEOUT_NAME);
        assertEquals(KAFKA_CLIENT_GROUP_MIN_SESSION_TIMEOUT_DEFAULT.name(), KAFKA_CLIENT_GROUP_MIN_SESSION_TIMEOUT_DEFAULT_NAME);
        assertEquals(KAFKA_CLIENT_GROUP_MAX_SESSION_TIMEOUT_DEFAULT.name(), KAFKA_CLIENT_GROUP_MAX_SESSION_TIMEOUT_DEFAULT_NAME);
        assertEquals(KAFKA_CLIENT_GROUP_INITIAL_REBALANCE_DELAY_DEFAULT.name(), KAFKA_CLIENT_GROUP_INITIAL_REBALANCE_DELAY_DEFAULT_NAME);
        assertEquals(KAFKA_CACHE_DIRECTORY.name(), KAFKA_CACHE_DIRECTORY_NAME);
        assertEquals(KAFKA_CACHE_SERVER_BOOTSTRAP.name(), KAFKA_CACHE_SERVER_BOOTSTRAP_NAME);
        assertEquals(KAFKA_CACHE_PRODUCE_CAPACITY.name(), KAFKA_CACHE_PRODUCE_CAPACITY_NAME);
        assertEquals(KAFKA_CACHE_SERVER_RECONNECT_DELAY.name(), KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME);
        assertEquals(KAFKA_CACHE_CLIENT_RECONNECT_DELAY.name(), KAFKA_CACHE_CLIENT_RECONNECT_DELAY_NAME);
        assertEquals(KAFKA_CACHE_CLIENT_CLEANUP_DELAY.name(), KAFKA_CACHE_CLIENT_CLEANUP_DELAY_NAME);
        assertEquals(KAFKA_CACHE_CLEANUP_POLICY.name(), KAFKA_CACHE_CLEANUP_POLICY_NAME);
        assertEquals(KAFKA_CACHE_MAX_MESSAGE_BYTES.name(), KAFKA_CACHE_MAX_MESSAGE_BYTES_NAME);
        assertEquals(KAFKA_CACHE_RETENTION_MILLIS.name(), KAFKA_CACHE_RETENTION_MILLIS_NAME);
        assertEquals(KAFKA_CACHE_RETENTION_MILLIS_MAX.name(), KAFKA_CACHE_RETENTION_MILLIS_MAX_NAME);
        assertEquals(KAFKA_CACHE_RETENTION_BYTES.name(), KAFKA_CACHE_RETENTION_BYTES_NAME);
        assertEquals(KAFKA_CACHE_DELETE_RETENTION_MILLIS.name(), KAFKA_CACHE_DELETE_RETENTION_MILLIS_NAME);
        assertEquals(KAFKA_CACHE_MIN_COMPACTION_LAG_MILLIS.name(), KAFKA_CACHE_MIN_COMPACTION_LAG_MILLIS_NAME);
        assertEquals(KAFKA_CACHE_MAX_COMPACTION_LAG_MILLIS.name(), KAFKA_CACHE_MAX_COMPACTION_LAG_MILLIS_NAME);
        assertEquals(KAFKA_CACHE_MIN_CLEANUP_DIRTY_RATIO.name(), KAFKA_CACHE_MIN_CLEANABLE_DIRTY_RATIO_NAME);
        assertEquals(KAFKA_CACHE_SEGMENT_MILLIS.name(), KAFKA_CACHE_SEGMENT_MILLIS_NAME);
        assertEquals(KAFKA_CACHE_SEGMENT_BYTES.name(), KAFKA_CACHE_SEGMENT_BYTES_NAME);
        assertEquals(KAFKA_CACHE_SEGMENT_INDEX_BYTES.name(), KAFKA_CACHE_SEGMENT_INDEX_BYTES_NAME);
        assertEquals(KAFKA_CACHE_CLIENT_TRAILERS_SIZE_MAX.name(), KAFKA_CACHE_CLIENT_TRAILERS_SIZE_MAX_NAME);
        assertEquals(KAFKA_CLIENT_CONNECTION_POOL.name(), KAFKA_CLIENT_CONNECTION_POOL_NAME);
        assertEquals(KAFKA_VERBOSE.name(), KAFKA_VERBOSE_NAME);
    }
}
