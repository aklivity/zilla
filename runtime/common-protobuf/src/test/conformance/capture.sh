#!/usr/bin/env bash
#
# Copyright 2021-2024 Aklivity Inc
#
# Licensed under the Aklivity Community License (the "License"); you may not use
# this file except in compliance with the License.  You may obtain a copy of the
# License at
#
#   https://www.aklivity.io/aklivity-community-license/
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OF ANY KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations under the License.
#

set -euo pipefail

: "${PROTOBUF_SRC:?set PROTOBUF_SRC to a protobuf source checkout}"
: "${PROTOBUF_VERSION:?set PROTOBUF_VERSION to the pinned protobuf version}"

MODULE_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
RES="$MODULE_DIR/src/test/resources/io/aklivity/zilla/runtime/common/protobuf/conformance"
CASES="$RES/cases"
mkdir -p "$CASES"

echo "Capturing protobuf $PROTOBUF_VERSION binary round-trip corpus into $RES"

# 1. Descriptors for the conformance test messages, decoded at test time by common-protobuf itself.
protoc \
  --proto_path="$PROTOBUF_SRC/src" \
  --include_imports \
  --descriptor_set_out="$RES/descriptors.bin" \
  google/protobuf/test_messages_proto3.proto \
  google/protobuf/test_messages_proto2.proto

# 2. Drive the runner against the capture testee to emit cases/<name>.in, cases/<name>.expected,
#    and append <name>\t<MessageType> rows to manifest.tsv. The capture testee is a thin program
#    that, for each PROTOBUF-output ConformanceRequest, writes the request payload (.in) and the
#    runner's expected canonical serialization (.expected). Wire it to your protobuf checkout's
#    conformance_test_runner here:
#
#    "$PROTOBUF_SRC/bazel-bin/conformance/conformance_test_runner" \
#       --output_dir "$RES" \
#       "$MODULE_DIR/src/test/conformance/capture_testee"
#
echo "TODO: invoke conformance_test_runner with the capture testee (see comments above)."
echo "Pin PROTOBUF_VERSION=$PROTOBUF_VERSION in the corpus and commit $RES."
