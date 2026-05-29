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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

enum KafkaError
{
    UNKNOWN_SERVER_ERROR(-1),
    NONE(0),
    OFFSET_OUT_OF_RANGE(1),
    CORRUPT_MESSAGE(2, true),
    UNKNOWN_TOPIC_OR_PARTITION(3, true),
    INVALID_FETCH_SIZE(4),
    LEADER_NOT_AVAILABLE(5, true),
    NOT_LEADER_OR_FOLLOWER(6, true),
    REQUEST_TIMED_OUT(7, true),
    BROKER_NOT_AVAILABLE(8),
    REPLICA_NOT_AVAILABLE(9, true),
    MESSAGE_TOO_LARGE(10),
    NETWORK_EXCEPTION(13, true),
    COORDINATOR_LOAD_IN_PROGRESS(14, true),
    COORDINATOR_NOT_AVAILABLE(15, true),
    NOT_COORDINATOR(16, true),
    INVALID_TOPIC_EXCEPTION(17),
    RECORD_LIST_TOO_LARGE(18),
    NOT_ENOUGH_REPLICAS(19, true),
    NOT_ENOUGH_REPLICAS_AFTER_APPEND(20, true),
    ILLEGAL_GENERATION(22),
    UNKNOWN_MEMBER_ID(25),
    REBALANCE_IN_PROGRESS(27),
    TOPIC_AUTHORIZATION_FAILED(29),
    GROUP_AUTHORIZATION_FAILED(30),
    CLUSTER_AUTHORIZATION_FAILED(31),
    UNSUPPORTED_SASL_MECHANISM(33),
    ILLEGAL_SASL_STATE(34),
    UNSUPPORTED_VERSION(35),
    NOT_CONTROLLER(41, true),
    INVALID_REQUEST(42),
    INVALID_PRODUCER_EPOCH(47),
    CONCURRENT_TRANSACTIONS(51, true),
    KAFKA_STORAGE_ERROR(56, true),
    SASL_AUTHENTICATION_FAILED(58),
    FETCH_SESSION_ID_NOT_FOUND(70, true),
    INVALID_FETCH_SESSION_EPOCH(71, true),
    FENCED_LEADER_EPOCH(74, true),
    UNKNOWN_LEADER_EPOCH(75, true),
    OFFSET_NOT_AVAILABLE(78, true),
    MEMBER_ID_REQUIRED(79),
    PREFERRED_LEADER_NOT_AVAILABLE(80, true),
    FENCED_INSTANCE_ID(82),
    INVALID_RECORD(87),
    UNSTABLE_OFFSET_COMMIT(88, true),
    THROTTLING_QUOTA_EXCEEDED(89, true),
    UNKNOWN_TOPIC_ID(100, true),
    INCONSISTENT_TOPIC_ID(103, true),
    FETCH_SESSION_TOPIC_ID_ERROR(106, true);

    private final int code;
    private final boolean retriable;

    KafkaError(
        int code)
    {
        this(code, false);
    }

    KafkaError(
        int code,
        boolean retriable)
    {
        this.code = code;
        this.retriable = retriable;
    }

    public boolean isRetriable()
    {
        return retriable;
    }

    static KafkaError of(
        int code)
    {
        return switch (code)
        {
        case 0 -> NONE;
        case 1 -> OFFSET_OUT_OF_RANGE;
        case 2 -> CORRUPT_MESSAGE;
        case 3 -> UNKNOWN_TOPIC_OR_PARTITION;
        case 4 -> INVALID_FETCH_SIZE;
        case 5 -> LEADER_NOT_AVAILABLE;
        case 6 -> NOT_LEADER_OR_FOLLOWER;
        case 7 -> REQUEST_TIMED_OUT;
        case 8 -> BROKER_NOT_AVAILABLE;
        case 9 -> REPLICA_NOT_AVAILABLE;
        case 10 -> MESSAGE_TOO_LARGE;
        case 13 -> NETWORK_EXCEPTION;
        case 14 -> COORDINATOR_LOAD_IN_PROGRESS;
        case 15 -> COORDINATOR_NOT_AVAILABLE;
        case 16 -> NOT_COORDINATOR;
        case 17 -> INVALID_TOPIC_EXCEPTION;
        case 18 -> RECORD_LIST_TOO_LARGE;
        case 19 -> NOT_ENOUGH_REPLICAS;
        case 20 -> NOT_ENOUGH_REPLICAS_AFTER_APPEND;
        case 22 -> ILLEGAL_GENERATION;
        case 25 -> UNKNOWN_MEMBER_ID;
        case 27 -> REBALANCE_IN_PROGRESS;
        case 29 -> TOPIC_AUTHORIZATION_FAILED;
        case 30 -> GROUP_AUTHORIZATION_FAILED;
        case 31 -> CLUSTER_AUTHORIZATION_FAILED;
        case 33 -> UNSUPPORTED_SASL_MECHANISM;
        case 34 -> ILLEGAL_SASL_STATE;
        case 35 -> UNSUPPORTED_VERSION;
        case 41 -> NOT_CONTROLLER;
        case 42 -> INVALID_REQUEST;
        case 47 -> INVALID_PRODUCER_EPOCH;
        case 51 -> CONCURRENT_TRANSACTIONS;
        case 56 -> KAFKA_STORAGE_ERROR;
        case 58 -> SASL_AUTHENTICATION_FAILED;
        case 70 -> FETCH_SESSION_ID_NOT_FOUND;
        case 71 -> INVALID_FETCH_SESSION_EPOCH;
        case 74 -> FENCED_LEADER_EPOCH;
        case 75 -> UNKNOWN_LEADER_EPOCH;
        case 78 -> OFFSET_NOT_AVAILABLE;
        case 79 -> MEMBER_ID_REQUIRED;
        case 80 -> PREFERRED_LEADER_NOT_AVAILABLE;
        case 82 -> FENCED_INSTANCE_ID;
        case 87 -> INVALID_RECORD;
        case 88 -> UNSTABLE_OFFSET_COMMIT;
        case 89 -> THROTTLING_QUOTA_EXCEEDED;
        case 100 -> UNKNOWN_TOPIC_ID;
        case 103 -> INCONSISTENT_TOPIC_ID;
        case 106 -> FETCH_SESSION_TOPIC_ID_ERROR;
        default -> UNKNOWN_SERVER_ERROR;
        };
    }
}
