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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KafkaAclTypesTest
{
    @Test
    public void shouldRoundTripResourceType()
    {
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_TOPIC, KafkaAclTypes.resourceType("TOPIC"));
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_TOPIC, KafkaAclTypes.resourceType("topic"));
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_GROUP, KafkaAclTypes.resourceType("GROUP"));
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_CLUSTER, KafkaAclTypes.resourceType("CLUSTER"));
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_TRANSACTIONAL_ID, KafkaAclTypes.resourceType("TRANSACTIONAL_ID"));
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_DELEGATION_TOKEN, KafkaAclTypes.resourceType("DELEGATION_TOKEN"));
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_USER, KafkaAclTypes.resourceType("USER"));
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_ANY, KafkaAclTypes.resourceType(null));
        assertEquals(KafkaAclTypes.RESOURCE_TYPE_UNKNOWN, KafkaAclTypes.resourceType("bogus"));

        assertEquals("TOPIC", KafkaAclTypes.resourceTypeName(KafkaAclTypes.RESOURCE_TYPE_TOPIC));
        assertEquals("UNKNOWN", KafkaAclTypes.resourceTypeName((byte) 99));
    }

    @Test
    public void shouldRoundTripPatternType()
    {
        assertEquals(KafkaAclTypes.PATTERN_TYPE_LITERAL, KafkaAclTypes.patternType("LITERAL"));
        assertEquals(KafkaAclTypes.PATTERN_TYPE_PREFIXED, KafkaAclTypes.patternType("prefixed"));
        assertEquals(KafkaAclTypes.PATTERN_TYPE_MATCH, KafkaAclTypes.patternType("MATCH"));
        assertEquals(KafkaAclTypes.PATTERN_TYPE_ANY, KafkaAclTypes.patternType("ANY"));
        assertEquals(KafkaAclTypes.PATTERN_TYPE_LITERAL, KafkaAclTypes.patternType(null));

        assertEquals("PREFIXED", KafkaAclTypes.patternTypeName(KafkaAclTypes.PATTERN_TYPE_PREFIXED));
        assertEquals("UNKNOWN", KafkaAclTypes.patternTypeName((byte) 99));
    }

    @Test
    public void shouldRoundTripOperation()
    {
        assertEquals(KafkaAclTypes.OPERATION_READ, KafkaAclTypes.operation("READ"));
        assertEquals(KafkaAclTypes.OPERATION_READ, KafkaAclTypes.operation("read"));
        assertEquals(KafkaAclTypes.OPERATION_WRITE, KafkaAclTypes.operation("WRITE"));
        assertEquals(KafkaAclTypes.OPERATION_ALL, KafkaAclTypes.operation("ALL"));
        assertEquals(KafkaAclTypes.OPERATION_TWO_PHASE_COMMIT, KafkaAclTypes.operation("TWO_PHASE_COMMIT"));
        assertEquals(KafkaAclTypes.OPERATION_ANY, KafkaAclTypes.operation(null));
        assertEquals(KafkaAclTypes.OPERATION_UNKNOWN, KafkaAclTypes.operation("bogus"));

        assertEquals("READ", KafkaAclTypes.operationName(KafkaAclTypes.OPERATION_READ));
        assertEquals("UNKNOWN", KafkaAclTypes.operationName((byte) 99));
    }

    @Test
    public void shouldRoundTripPermissionType()
    {
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_ALLOW, KafkaAclTypes.permissionType("ALLOW"));
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_ALLOW, KafkaAclTypes.permissionType("allow"));
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_DENY, KafkaAclTypes.permissionType("DENY"));
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_ANY, KafkaAclTypes.permissionType(null));
        assertEquals(KafkaAclTypes.PERMISSION_TYPE_UNKNOWN, KafkaAclTypes.permissionType("bogus"));

        assertEquals("ALLOW", KafkaAclTypes.permissionTypeName(KafkaAclTypes.PERMISSION_TYPE_ALLOW));
        assertEquals("UNKNOWN", KafkaAclTypes.permissionTypeName((byte) 99));
    }
}
