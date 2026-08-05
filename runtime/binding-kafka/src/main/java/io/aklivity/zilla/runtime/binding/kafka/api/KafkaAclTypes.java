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

import java.util.Locale;

/**
 * Wire byte values for the Kafka ACL enums ({@code ResourceType}, {@code PatternType},
 * {@code AclOperation}, {@code AclPermissionType}), plus the string name each MCP ACL tool exchanges
 * with callers. Centralized here - rather than duplicated across {@code list_acls}/{@code create_acls}/
 * {@code delete_acls} tool sources - since all three tools parse and render the same four enums.
 */
public final class KafkaAclTypes
{
    public static final byte RESOURCE_TYPE_UNKNOWN = 0;
    public static final byte RESOURCE_TYPE_ANY = 1;
    public static final byte RESOURCE_TYPE_TOPIC = 2;
    public static final byte RESOURCE_TYPE_GROUP = 3;
    public static final byte RESOURCE_TYPE_CLUSTER = 4;
    public static final byte RESOURCE_TYPE_TRANSACTIONAL_ID = 5;
    public static final byte RESOURCE_TYPE_DELEGATION_TOKEN = 6;
    public static final byte RESOURCE_TYPE_USER = 7;

    public static final byte PATTERN_TYPE_UNKNOWN = 0;
    public static final byte PATTERN_TYPE_ANY = 1;
    public static final byte PATTERN_TYPE_MATCH = 2;
    public static final byte PATTERN_TYPE_LITERAL = 3;
    public static final byte PATTERN_TYPE_PREFIXED = 4;

    public static final byte OPERATION_UNKNOWN = 0;
    public static final byte OPERATION_ANY = 1;
    public static final byte OPERATION_ALL = 2;
    public static final byte OPERATION_READ = 3;
    public static final byte OPERATION_WRITE = 4;
    public static final byte OPERATION_CREATE = 5;
    public static final byte OPERATION_DELETE = 6;
    public static final byte OPERATION_ALTER = 7;
    public static final byte OPERATION_DESCRIBE = 8;
    public static final byte OPERATION_CLUSTER_ACTION = 9;
    public static final byte OPERATION_DESCRIBE_CONFIGS = 10;
    public static final byte OPERATION_ALTER_CONFIGS = 11;
    public static final byte OPERATION_IDEMPOTENT_WRITE = 12;
    public static final byte OPERATION_CREATE_TOKENS = 13;
    public static final byte OPERATION_DESCRIBE_TOKENS = 14;
    public static final byte OPERATION_TWO_PHASE_COMMIT = 15;

    public static final byte PERMISSION_TYPE_UNKNOWN = 0;
    public static final byte PERMISSION_TYPE_ANY = 1;
    public static final byte PERMISSION_TYPE_DENY = 2;
    public static final byte PERMISSION_TYPE_ALLOW = 3;

    private KafkaAclTypes()
    {
    }

    /**
     * @return the wire value for {@code name} (a {@code resource_type} tool argument, e.g. {@code "topic"} -
     *         matched case-insensitively), defaulting to {@link #RESOURCE_TYPE_ANY} if {@code name} is
     *         {@code null}, or {@link #RESOURCE_TYPE_UNKNOWN} if {@code name} does not name a known resource type
     */
    public static byte resourceType(
        String name)
    {
        return switch (name == null ? "ANY" : name.toUpperCase(Locale.ROOT))
        {
        case "ANY" -> RESOURCE_TYPE_ANY;
        case "TOPIC" -> RESOURCE_TYPE_TOPIC;
        case "GROUP" -> RESOURCE_TYPE_GROUP;
        case "CLUSTER" -> RESOURCE_TYPE_CLUSTER;
        case "TRANSACTIONAL_ID" -> RESOURCE_TYPE_TRANSACTIONAL_ID;
        case "DELEGATION_TOKEN" -> RESOURCE_TYPE_DELEGATION_TOKEN;
        case "USER" -> RESOURCE_TYPE_USER;
        default -> RESOURCE_TYPE_UNKNOWN;
        };
    }

    public static String resourceTypeName(
        byte type)
    {
        return switch (type)
        {
        case RESOURCE_TYPE_ANY -> "ANY";
        case RESOURCE_TYPE_TOPIC -> "TOPIC";
        case RESOURCE_TYPE_GROUP -> "GROUP";
        case RESOURCE_TYPE_CLUSTER -> "CLUSTER";
        case RESOURCE_TYPE_TRANSACTIONAL_ID -> "TRANSACTIONAL_ID";
        case RESOURCE_TYPE_DELEGATION_TOKEN -> "DELEGATION_TOKEN";
        case RESOURCE_TYPE_USER -> "USER";
        default -> "UNKNOWN";
        };
    }

    /**
     * @return the wire value for {@code name} (a {@code pattern_type} tool argument, e.g. {@code "prefixed"} -
     *         matched case-insensitively), defaulting to {@link #PATTERN_TYPE_LITERAL} if {@code name} is
     *         {@code null} or unrecognized
     */
    public static byte patternType(
        String name)
    {
        return switch (name == null ? "LITERAL" : name.toUpperCase(Locale.ROOT))
        {
        case "ANY" -> PATTERN_TYPE_ANY;
        case "MATCH" -> PATTERN_TYPE_MATCH;
        case "PREFIXED" -> PATTERN_TYPE_PREFIXED;
        default -> PATTERN_TYPE_LITERAL;
        };
    }

    public static String patternTypeName(
        byte type)
    {
        return switch (type)
        {
        case PATTERN_TYPE_ANY -> "ANY";
        case PATTERN_TYPE_MATCH -> "MATCH";
        case PATTERN_TYPE_PREFIXED -> "PREFIXED";
        case PATTERN_TYPE_LITERAL -> "LITERAL";
        default -> "UNKNOWN";
        };
    }

    /**
     * @return the wire value for {@code name} (an {@code operation} tool argument, e.g. {@code "read"} -
     *         matched case-insensitively), defaulting to {@link #OPERATION_ANY} if {@code name} is
     *         {@code null}, or {@link #OPERATION_UNKNOWN} if {@code name} does not name a known operation
     */
    public static byte operation(
        String name)
    {
        return switch (name == null ? "ANY" : name.toUpperCase(Locale.ROOT))
        {
        case "ANY" -> OPERATION_ANY;
        case "ALL" -> OPERATION_ALL;
        case "READ" -> OPERATION_READ;
        case "WRITE" -> OPERATION_WRITE;
        case "CREATE" -> OPERATION_CREATE;
        case "DELETE" -> OPERATION_DELETE;
        case "ALTER" -> OPERATION_ALTER;
        case "DESCRIBE" -> OPERATION_DESCRIBE;
        case "CLUSTER_ACTION" -> OPERATION_CLUSTER_ACTION;
        case "DESCRIBE_CONFIGS" -> OPERATION_DESCRIBE_CONFIGS;
        case "ALTER_CONFIGS" -> OPERATION_ALTER_CONFIGS;
        case "IDEMPOTENT_WRITE" -> OPERATION_IDEMPOTENT_WRITE;
        case "CREATE_TOKENS" -> OPERATION_CREATE_TOKENS;
        case "DESCRIBE_TOKENS" -> OPERATION_DESCRIBE_TOKENS;
        case "TWO_PHASE_COMMIT" -> OPERATION_TWO_PHASE_COMMIT;
        default -> OPERATION_UNKNOWN;
        };
    }

    public static String operationName(
        byte operation)
    {
        return switch (operation)
        {
        case OPERATION_ANY -> "ANY";
        case OPERATION_ALL -> "ALL";
        case OPERATION_READ -> "READ";
        case OPERATION_WRITE -> "WRITE";
        case OPERATION_CREATE -> "CREATE";
        case OPERATION_DELETE -> "DELETE";
        case OPERATION_ALTER -> "ALTER";
        case OPERATION_DESCRIBE -> "DESCRIBE";
        case OPERATION_CLUSTER_ACTION -> "CLUSTER_ACTION";
        case OPERATION_DESCRIBE_CONFIGS -> "DESCRIBE_CONFIGS";
        case OPERATION_ALTER_CONFIGS -> "ALTER_CONFIGS";
        case OPERATION_IDEMPOTENT_WRITE -> "IDEMPOTENT_WRITE";
        case OPERATION_CREATE_TOKENS -> "CREATE_TOKENS";
        case OPERATION_DESCRIBE_TOKENS -> "DESCRIBE_TOKENS";
        case OPERATION_TWO_PHASE_COMMIT -> "TWO_PHASE_COMMIT";
        default -> "UNKNOWN";
        };
    }

    /**
     * @return the wire value for {@code name} (a {@code permission_type} tool argument, e.g. {@code "allow"} -
     *         matched case-insensitively), defaulting to {@link #PERMISSION_TYPE_ANY} if {@code name} is
     *         {@code null}, or {@link #PERMISSION_TYPE_UNKNOWN} if {@code name} does not name a known permission type
     */
    public static byte permissionType(
        String name)
    {
        return switch (name == null ? "ANY" : name.toUpperCase(Locale.ROOT))
        {
        case "ANY" -> PERMISSION_TYPE_ANY;
        case "DENY" -> PERMISSION_TYPE_DENY;
        case "ALLOW" -> PERMISSION_TYPE_ALLOW;
        default -> PERMISSION_TYPE_UNKNOWN;
        };
    }

    public static String permissionTypeName(
        byte type)
    {
        return switch (type)
        {
        case PERMISSION_TYPE_ANY -> "ANY";
        case PERMISSION_TYPE_DENY -> "DENY";
        case PERMISSION_TYPE_ALLOW -> "ALLOW";
        default -> "UNKNOWN";
        };
    }
}
