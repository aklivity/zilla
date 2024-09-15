/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

import java.util.Map;

import org.agrona.collections.Object2ObjectHashMap;

public final class RisingwavePgsqlTypeMapping
{
    private static final Map<Integer, String> OID_TO_TYPE_NAME = new Object2ObjectHashMap<>();

    static
    {
        // Initialize the mapping of OIDs to uppercase type names
        OID_TO_TYPE_NAME.put(16, "BOOL");
        OID_TO_TYPE_NAME.put(17, "BYTEA");
        OID_TO_TYPE_NAME.put(18, "CHAR");
        OID_TO_TYPE_NAME.put(19, "NAME");
        OID_TO_TYPE_NAME.put(20, "INT8");
        OID_TO_TYPE_NAME.put(21, "INT2");
        OID_TO_TYPE_NAME.put(22, "INT2VECTOR");
        OID_TO_TYPE_NAME.put(23, "INT4");
        OID_TO_TYPE_NAME.put(24, "REGPROC");
        OID_TO_TYPE_NAME.put(25, "TEXT");
        OID_TO_TYPE_NAME.put(26, "OID");
        OID_TO_TYPE_NAME.put(27, "TID");
        OID_TO_TYPE_NAME.put(28, "XID");
        OID_TO_TYPE_NAME.put(29, "CID");
        OID_TO_TYPE_NAME.put(30, "OIDVECTOR");
        OID_TO_TYPE_NAME.put(114, "JSON");
        OID_TO_TYPE_NAME.put(142, "XML");
        OID_TO_TYPE_NAME.put(194, "PG_NODE_TREE");
        OID_TO_TYPE_NAME.put(600, "POINT");
        OID_TO_TYPE_NAME.put(601, "LSEG");
        OID_TO_TYPE_NAME.put(602, "PATH");
        OID_TO_TYPE_NAME.put(603, "BOX");
        OID_TO_TYPE_NAME.put(604, "POLYGON");
        OID_TO_TYPE_NAME.put(628, "LINE");
        OID_TO_TYPE_NAME.put(700, "FLOAT4");
        OID_TO_TYPE_NAME.put(701, "FLOAT8");
        OID_TO_TYPE_NAME.put(705, "UNKNOWN");
        OID_TO_TYPE_NAME.put(718, "CIRCLE");
        OID_TO_TYPE_NAME.put(790, "MONEY");
        OID_TO_TYPE_NAME.put(829, "MACADDR");
        OID_TO_TYPE_NAME.put(869, "INET");
        OID_TO_TYPE_NAME.put(1000, "_BOOL");
        OID_TO_TYPE_NAME.put(1001, "_BYTEA");
        OID_TO_TYPE_NAME.put(1002, "_CHAR");
        OID_TO_TYPE_NAME.put(1003, "_NAME");
        OID_TO_TYPE_NAME.put(1005, "_INT2");
        OID_TO_TYPE_NAME.put(1006, "_INT2VECTOR");
        OID_TO_TYPE_NAME.put(1007, "_INT4");
        OID_TO_TYPE_NAME.put(1008, "_REGPROC");
        OID_TO_TYPE_NAME.put(1009, "_TEXT");
        OID_TO_TYPE_NAME.put(1010, "_TID");
        OID_TO_TYPE_NAME.put(1011, "_XID");
        OID_TO_TYPE_NAME.put(1012, "_CID");
        OID_TO_TYPE_NAME.put(1013, "_OIDVECTOR");
        OID_TO_TYPE_NAME.put(1014, "_BPCHAR");
        OID_TO_TYPE_NAME.put(1015, "_VARCHAR");
        OID_TO_TYPE_NAME.put(1016, "_INT8");
        OID_TO_TYPE_NAME.put(1017, "_POINT");
        OID_TO_TYPE_NAME.put(1018, "_LSEG");
        OID_TO_TYPE_NAME.put(1019, "_PATH");
        OID_TO_TYPE_NAME.put(1020, "_BOX");
        OID_TO_TYPE_NAME.put(1021, "_FLOAT4");
        OID_TO_TYPE_NAME.put(1022, "_FLOAT8");
        OID_TO_TYPE_NAME.put(1028, "_OID");
        OID_TO_TYPE_NAME.put(1033, "ACLITEM");
        OID_TO_TYPE_NAME.put(1034, "_ACLITEM");
        OID_TO_TYPE_NAME.put(1042, "BPCHAR");
        OID_TO_TYPE_NAME.put(1043, "VARCHAR");
        OID_TO_TYPE_NAME.put(1082, "DATE");
        OID_TO_TYPE_NAME.put(1083, "TIME");
        OID_TO_TYPE_NAME.put(1114, "TIMESTAMP");
        OID_TO_TYPE_NAME.put(1184, "TIMESTAMPTZ");
        OID_TO_TYPE_NAME.put(1186, "INTERVAL");
        OID_TO_TYPE_NAME.put(1266, "TIMETZ");
        OID_TO_TYPE_NAME.put(1560, "BIT");
        OID_TO_TYPE_NAME.put(1562, "VARBIT");
        OID_TO_TYPE_NAME.put(1700, "NUMERIC");
        OID_TO_TYPE_NAME.put(1790, "REFCURSOR");
        OID_TO_TYPE_NAME.put(2202, "REGPROCEDURE");
        OID_TO_TYPE_NAME.put(2203, "REGOPER");
        OID_TO_TYPE_NAME.put(2204, "REGOPERATOR");
        OID_TO_TYPE_NAME.put(2205, "REGCLASS");
        OID_TO_TYPE_NAME.put(2206, "REGTYPE");
        OID_TO_TYPE_NAME.put(2950, "UUID");
        OID_TO_TYPE_NAME.put(3220, "PG_LSN");
        OID_TO_TYPE_NAME.put(3614, "TSVECTOR");
        OID_TO_TYPE_NAME.put(3615, "TSQUERY");
        OID_TO_TYPE_NAME.put(3734, "REGCONFIG");
        OID_TO_TYPE_NAME.put(3769, "REGDICTIONARY");
        OID_TO_TYPE_NAME.put(3802, "JSONB");
        OID_TO_TYPE_NAME.put(3904, "INT4RANGE");
        OID_TO_TYPE_NAME.put(3906, "NUMRANGE");
        OID_TO_TYPE_NAME.put(3908, "TSRANGE");
        OID_TO_TYPE_NAME.put(3910, "TSTZRANGE");
        OID_TO_TYPE_NAME.put(3912, "DATERANGE");
        OID_TO_TYPE_NAME.put(3926, "INT8RANGE");
        OID_TO_TYPE_NAME.put(4072, "JSONPATH");
    }

    private RisingwavePgsqlTypeMapping()
    {
    }

    public static String typeName(
        int oid)
    {
        return OID_TO_TYPE_NAME.get(oid);
    }
}
