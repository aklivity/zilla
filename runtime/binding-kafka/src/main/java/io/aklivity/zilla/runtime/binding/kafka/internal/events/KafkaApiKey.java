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
package io.aklivity.zilla.runtime.binding.kafka.internal.events;

import java.util.regex.Pattern;

public enum KafkaApiKey
{
    PRODUCE(0),
    FETCH(1),
    LIST_OFFSETS(2),
    METADATA(3),
    LEADER_AND_ISR(4),
    STOP_REPLICA(5),
    UPDATE_METADATA(6),
    CONTROLLED_SHUTDOWN(7),
    OFFSET_COMMIT(8),
    OFFSET_FETCH(9),
    FIND_COORDINATOR(10),
    JOIN_GROUP(11),
    HEARTBEAT(12),
    LEAVE_GROUP(13),
    SYNC_GROUP(14),
    DESCRIBE_GROUPS(15),
    LIST_GROUPS(16),
    SASL_HANDSHAKE(17),
    API_VERSIONS(18),
    CREATE_TOPICS(19),
    DELETE_TOPICS(20),
    DELETE_RECORDS(21),
    INIT_PRODUCER_ID(22),
    OFFSET_FOR_LEADER_EPOCH(23),
    ADD_PARTITIONS_TO_TXN(24),
    ADD_OFFSETS_TO_TXN(25),
    END_TXN(26),
    WRITE_TXN_MARKERS(27),
    TXN_OFFSET_COMMIT(28),
    DESCRIBE_ACLS(29),
    CREATE_ACLS(30),
    DELETE_ACLS(31),
    DESCRIBE_CONFIGS(32),
    ALTER_CONFIGS(33),
    ALTER_REPLICA_LOG_DIRS(34),
    DESCRIBE_LOG_DIRS(35),
    SASL_AUTHENTICATE(36),
    CREATE_PARTITIONS(37),
    CREATE_DELEGATION_TOKEN(38),
    RENEW_DELEGATION_TOKEN(39),
    EXPIRE_DELEGATION_TOKEN(40),
    DESCRIBE_DELEGATION_TOKEN(41),
    DELETE_GROUPS(42),
    ELECT_LEADERS(43),
    INCREMENTAL_ALTER_CONFIGS(44),
    ALTER_PARTITION_REASSIGNMENTS(45),
    LIST_PARTITION_REASSIGNMENTS(46),
    OFFSET_DELETE(47),
    DESCRIBE_CLIENT_QUOTAS(48),
    ALTER_CLIENT_QUOTAS(49),
    DESCRIBE_USER_SCRAM_CREDENTIALS(50),
    ALTER_USER_SCRAM_CREDENTIALS(51),
    DESCRIBE_QUORUM(55),
    ALTER_PARTITION(56),
    UPDATE_FEATURES(57),
    ENVELOPE(58),
    DESCRIBE_CLUSTER(60),
    DESCRIBE_PRODUCERS(61),
    UNREGISTER_BROKER(64),
    DESCRIBE_TRANSACTIONS(65),
    LIST_TRANSACTIONS(66),
    ALLOCATE_PRODUCER_IDS(67),
    CONSUMER_GROUP_HEARTBEAT(68),
    CONSUMER_GROUP_DESCRIBE(69),
    GET_TELEMETRY_SUBSCRIPTIONS(71),
    PUSH_TELEMETRY(72),
    LIST_CLIENT_METRICS_RESOURCES(74);

    private final int key;
    private final String title;

    KafkaApiKey(
        int key)
    {
        this.key = key;
        this.title = toTitleCase(name());
    }

    public String title()
    {
        return title;
    }

    public static KafkaApiKey of(
        int key)
    {
        KafkaApiKey value = null;

        final KafkaApiKey[] values = KafkaApiKey.values();
        for (int i = 0; i < values.length; i++)
        {
            if (values[i].key == key)
            {
                value = values[i];
                break;
            }
        }

        return value;
    }

    private static String toTitleCase(
        String name)
    {
        return Pattern.compile("(?:_|^)([a-z])")
                .matcher(name.toLowerCase())
                .replaceAll(m -> m.group(1).toUpperCase());
    }
}
