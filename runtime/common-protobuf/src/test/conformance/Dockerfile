# Builds a pinned protobuf conformance runner image for ProtobufConformanceIT.
#
# Single base image (JDK, Ubuntu-based) so only one registry pull is needed: build tools and the
# native conformance_test_runner + protoc are installed on top, and the JDK is also what runs the
# common-protobuf testee at test time. The image bundles a FileDescriptorSet of the conformance
# test messages and changes only with PROTOBUF_VERSION; the testee classpath is copied in at
# runtime by the integration test, so iterating on Java never rebuilds this image. Publish it to a
# registry to avoid rebuilding the runner in CI.
#
# The exact protobuf CMake target and option names can drift between releases; pin PROTOBUF_VERSION
# and adjust the build invocation here on a version bump.

ARG PROTOBUF_VERSION=29.3

FROM eclipse-temurin:21-jdk
ARG PROTOBUF_VERSION
RUN apt-get update \
 && apt-get install -y --no-install-recommends git cmake g++ make ca-certificates \
 && rm -rf /var/lib/apt/lists/*
RUN git clone --depth 1 --branch v${PROTOBUF_VERSION} --recurse-submodules \
      https://github.com/protocolbuffers/protobuf.git /src
WORKDIR /src
RUN cmake -S . -B build \
      -DCMAKE_BUILD_TYPE=Release \
      -Dprotobuf_BUILD_TESTS=OFF \
      -Dprotobuf_BUILD_CONFORMANCE=ON \
      -Dprotobuf_ABSL_PROVIDER=module \
 && cmake --build build --target conformance_test_runner protoc -j"$(nproc)" \
 && cp build/conformance_test_runner /usr/local/bin/conformance_test_runner \
 && cp build/protoc /usr/local/bin/protoc
RUN mkdir -p /conformance /app \
 && protoc --proto_path=src --include_imports \
      --descriptor_set_out=/conformance/descriptors.bin \
      src/google/protobuf/test_messages_proto3.proto \
      src/google/protobuf/test_messages_proto2.proto
CMD ["tail", "-f", "/dev/null"]
