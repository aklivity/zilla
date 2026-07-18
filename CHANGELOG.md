# Changelog

## [Unreleased](https://github.com/aklivity/zilla/tree/HEAD)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.2.4...HEAD)

**Implemented enhancements:**

- binding-mcp-http: implement mcp\_http · proxy binding [\#1675](https://github.com/aklivity/zilla/issues/1675) ([jfallows](https://github.com/jfallows))
- Enable MemorySegment field accessors via DirectBufferEx buffer migration [\#1723](https://github.com/aklivity/zilla/issues/1723) ([jfallows](https://github.com/jfallows))
- feat\(binding-kafka\): add missing observability events for broker connectivity and protocol errors [\#1766](https://github.com/aklivity/zilla/issues/1766) ([ankitk-me](https://github.com/ankitk-me))
- common-json: composable streaming JSON pipeline SPI \(validate + project, payload path\) [\#1855](https://github.com/aklivity/zilla/issues/1855) ([jfallows](https://github.com/jfallows))
- model-avro: streaming converter on common-avro + common-json transcoder [\#1856](https://github.com/aklivity/zilla/issues/1856) ([jfallows](https://github.com/jfallows))
- model-protobuf: streaming converter on common-protobuf + common-json transcoder [\#1857](https://github.com/aklivity/zilla/issues/1857) ([jfallows](https://github.com/jfallows))
- Hoist OpenAPI model/view/parser APIs into a runtime/common-openapi module [\#1889](https://github.com/aklivity/zilla/issues/1889) ([jfallows](https://github.com/jfallows))
- Hoist AsyncAPI model/view/parser APIs into a runtime/common-asyncapi module [\#1890](https://github.com/aklivity/zilla/issues/1890) ([jfallows](https://github.com/jfallows))
- common-json: stream window-fragmented unconstrained values through the schema validator \(forward-and-suppress\) [\#1926](https://github.com/aklivity/zilla/issues/1926) ([jfallows](https://github.com/jfallows))
- common-json: stream object keys larger than the input window through the parser and all key-matching consumers [\#1954](https://github.com/aklivity/zilla/issues/1954) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): validate tools/call arguments at the proxy against cached tools/list inputSchema [\#1962](https://github.com/aklivity/zilla/issues/1962) ([jfallows](https://github.com/jfallows))

**Closed issues:**

- Support `tls` deferring to vault for list of keys or trusted certificates, support `filesystem` vault [\#1576](https://github.com/aklivity/zilla/issues/1576) ([jfallows](https://github.com/jfallows))
- binding-mcp-openapi: implement mcp\_openapi · proxy binding [\#1673](https://github.com/aklivity/zilla/issues/1673) ([jfallows](https://github.com/jfallows))
- Support routing based on TLS client certificate presence/signer for mixed-auth endpoints on shared port [\#1697](https://github.com/aklivity/zilla/issues/1697) ([akrambek](https://github.com/akrambek))
- binding-mcp: add cache option to mcp · proxy binding [\#1737](https://github.com/aklivity/zilla/issues/1737) ([jfallows](https://github.com/jfallows))
- binding-mcp server: emit Alt-Svc header advertising engine service hostname [\#1770](https://github.com/aklivity/zilla/issues/1770) ([jfallows](https://github.com/jfallows))
- binding-http: translate Alt-Svc placeholder to wire-level ALPN, host, and physical port [\#1772](https://github.com/aklivity/zilla/issues/1772) ([jfallows](https://github.com/jfallows))
- binding-mcp: add missing peer-to-peer ApplicationIT coverage for 14 scenarios [\#1783](https://github.com/aklivity/zilla/issues/1783) ([jfallows](https://github.com/jfallows))
- binding-mcp: propagate upstream auth challenges to inbound client [\#1795](https://github.com/aklivity/zilla/issues/1795) ([jfallows](https://github.com/jfallows))
- Deprecate Kafka-group session ownership in mqtt-kafka binding [\#1797](https://github.com/aklivity/zilla/issues/1797) ([jfallows](https://github.com/jfallows))
- Migrate mqtt-kafka session ownership to Store SPI [\#1798](https://github.com/aklivity/zilla/issues/1798) ([jfallows](https://github.com/jfallows))
- Move MQTT QoS state into mqtt-sessions topic and consolidate session keys [\#1799](https://github.com/aklivity/zilla/issues/1799) ([jfallows](https://github.com/jfallows))
- Move replica identity from per-binding serverRef to engine-level service config [\#1800](https://github.com/aklivity/zilla/issues/1800) ([jfallows](https://github.com/jfallows))
- Split `Engine.start()` into `init()` + `start()` with optional `beforeStart` hook [\#1807](https://github.com/aklivity/zilla/issues/1807) ([jfallows](https://github.com/jfallows))
- binding-mcp: per-toolkit elicitation for `mcp` proxy [\#1810](https://github.com/aklivity/zilla/issues/1810) ([jfallows](https://github.com/jfallows))
- binding-http: client connection reuse per origin/authority + per-connection exchange/queue scope [\#1811](https://github.com/aklivity/zilla/issues/1811) ([jfallows](https://github.com/jfallows))
- binding-mcp: deterministic cache refresh startup/teardown + coalesced re-arming [\#1813](https://github.com/aklivity/zilla/issues/1813) ([jfallows](https://github.com/jfallows))
- binding-mcp: single session-id contract, negotiated protocol-version, partial-success hydration [\#1815](https://github.com/aklivity/zilla/issues/1815) ([jfallows](https://github.com/jfallows))
- binding-mcp: proxy hydration/forwarding fixes + per-upstream bootstrap credential [\#1817](https://github.com/aklivity/zilla/issues/1817) ([jfallows](https://github.com/jfallows))
- binding-mcp: offer MCP 2025-11-25 + negotiate elicitation.url capability to enable url-mode elicitation [\#1819](https://github.com/aklivity/zilla/issues/1819) ([jfallows](https://github.com/jfallows))
- feat\(binding-kafka\): support guard injection for kafka client credentials [\#1824](https://github.com/aklivity/zilla/issues/1824) ([ankitk-me](https://github.com/ankitk-me))
- metrics-mcp: instrument all MCP methods `initialize`, `tools/*`, `resources/*`, `prompts/*` [\#1825](https://github.com/aklivity/zilla/issues/1825) ([jfallows](https://github.com/jfallows))
- binding-mcp: per-client `tools/list` filtering by per-tool authorization metadata [\#1831](https://github.com/aklivity/zilla/issues/1831) ([jfallows](https://github.com/jfallows))
- binding-mcp: per-route primitive-name allow-set filtering for mcp proxy [\#1833](https://github.com/aklivity/zilla/issues/1833) ([jfallows](https://github.com/jfallows))
- model-json: streaming projecting + validating JSON converter \(on common-json\) [\#1835](https://github.com/aklivity/zilla/issues/1835) ([jfallows](https://github.com/jfallows))
- engine: unify model ConverterHandler and ValidatorHandler into a single streaming model handler [\#1836](https://github.com/aklivity/zilla/issues/1836) ([jfallows](https://github.com/jfallows))
- common-avro: streaming Avro decode/encode + schema validation, with JSON transcoding via common-json [\#1838](https://github.com/aklivity/zilla/issues/1838) ([jfallows](https://github.com/jfallows))
- common-protobuf: streaming Protobuf decode/encode + descriptor validation, with JSON transcoding via common-json [\#1839](https://github.com/aklivity/zilla/issues/1839) ([jfallows](https://github.com/jfallows))
- common-yaml: self-contained streaming YAML parse + generate \(peer to common-json\) [\#1843](https://github.com/aklivity/zilla/issues/1843) ([jfallows](https://github.com/jfallows))
- feat\(binding-kafka\): support SASL/OAUTHBEARER mechanism for Kafka client [\#1868](https://github.com/aklivity/zilla/issues/1868) ([ankitk-me](https://github.com/ankitk-me))
- common-json: streaming JSON-in-JSON escaping \(generator escape mode + consumption-driven segment writing\) [\#1878](https://github.com/aklivity/zilla/issues/1878) ([jfallows](https://github.com/jfallows))
- common-json: unify scalar value delivery — tighten getSegment contract, fold DECODED into STRUCTURED, propagate flow control to the parser [\#1887](https://github.com/aklivity/zilla/issues/1887) ([jfallows](https://github.com/jfallows))
- common-yaml: incremental, buffer-backed YAML parser to remove eager-parse allocation [\#1905](https://github.com/aklivity/zilla/issues/1905) ([jfallows](https://github.com/jfallows))
- Unify model validation-failure diagnostics behind a common-\* reporter SPI [\#1914](https://github.com/aklivity/zilla/issues/1914) ([jfallows](https://github.com/jfallows))
- Surface verbatim schema-validation diagnostics through the streaming JSON pipeline \(validator-throws + line/column tracking\) [\#1919](https://github.com/aklivity/zilla/issues/1919) ([jfallows](https://github.com/jfallows))
- common-json: trie-based pointer matching in JsonProjectorImpl to drop key-name buffering [\#1930](https://github.com/aklivity/zilla/issues/1930) ([jfallows](https://github.com/jfallows))
- common-json: JSON Schema `pattern` compiled with Java Pattern rejects valid ECMA-262 regexes [\#1935](https://github.com/aklivity/zilla/issues/1935) ([jfallows](https://github.com/jfallows))
- engine: add identity\(\) capability to ModelPipeline so callers can skip buffer-and-hold for validators [\#1956](https://github.com/aklivity/zilla/issues/1956) ([jfallows](https://github.com/jfallows))
- Remove Jackson dependency from binding-asyncapi [\#1967](https://github.com/aklivity/zilla/issues/1967) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): agent-callable tool search in mcp proxy with BM25 ranking [\#1969](https://github.com/aklivity/zilla/issues/1969) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): configurable eager tool set under cache.tools.eager [\#1970](https://github.com/aklivity/zilla/issues/1970) ([jfallows](https://github.com/jfallows))
- refactor\(binding-mcp-http\): split the general-purpose proxy stream into per-request-kind stream classes [\#1976](https://github.com/aklivity/zilla/issues/1976) ([jfallows](https://github.com/jfallows))
- common-yaml: YamlJsonParser.getObject\(\)/getValue\(\) corrupt parser position from a JSON-B custom deserializer or adapter [\#1997](https://github.com/aklivity/zilla/issues/1997) ([jfallows](https://github.com/jfallows))
- common-json/common-agrona: relocate DirectBufferInputStreamEx to common-agrona; clean up JsonNode's dependency on it [\#1999](https://github.com/aklivity/zilla/issues/1999) ([jfallows](https://github.com/jfallows))
- common-asyncapi: adopt generic extension/binding access instead of hardcoded consumer types [\#2002](https://github.com/aklivity/zilla/issues/2002) ([jfallows](https://github.com/jfallows))
- common-openapi: model oauth2 securityScheme flows as typed OpenapiOAuthFlow\(s\) instead of raw Object [\#2003](https://github.com/aklivity/zilla/issues/2003) ([jfallows](https://github.com/jfallows))
- common-openapi: support prefix-wildcard extension registration \(withExtension\("x-google-\*", type\)\) [\#2006](https://github.com/aklivity/zilla/issues/2006) ([jfallows](https://github.com/jfallows))
- common-openapi: scope extension registration by OpenAPI object kind, not name alone [\#2008](https://github.com/aklivity/zilla/issues/2008) ([jfallows](https://github.com/jfallows))
- common-asyncapi: introduce AsyncapiExtension for generic x-\* vendor extensions, mirroring OpenapiExtension [\#2011](https://github.com/aklivity/zilla/issues/2011) ([jfallows](https://github.com/jfallows))
- UnsafeBufferEx.asNative\(\) drops wrapAdjustment when wrapping a sub-range of a direct ByteBuffer [\#2014](https://github.com/aklivity/zilla/issues/2014) ([jfallows](https://github.com/jfallows))
- common-json: JsonSchema validator stalls forever \(never REJECTED\) on a scalar value larger than the fed window [\#2016](https://github.com/aklivity/zilla/issues/2016) ([jfallows](https://github.com/jfallows))
- common-json: JsonGeneratorEx's convenience write API has no real bounds check against its wrap\(\) limit [\#2017](https://github.com/aklivity/zilla/issues/2017) ([jfallows](https://github.com/jfallows))
- mcp\_http: remove static prompts support [\#2018](https://github.com/aklivity/zilla/issues/2018) ([jfallows](https://github.com/jfallows))
- common-openapi: resolve full servers\[\] precedence \(operation > path-item > root\) in OpenapiOperationView [\#2020](https://github.com/aklivity/zilla/issues/2020) ([jfallows](https://github.com/jfallows))
- mcp\_openapi: bulk/tag/pattern route selection, when.capability, deterministic tool naming [\#2021](https://github.com/aklivity/zilla/issues/2021) ([jfallows](https://github.com/jfallows))
- mcp\_openapi: params: rebinding and authored tool input schemas [\#2022](https://github.com/aklivity/zilla/issues/2022) ([jfallows](https://github.com/jfallows))
- mcp\_openapi: options.resources overrides, resource/template discrimination, query param omit-when-absent [\#2023](https://github.com/aklivity/zilla/issues/2023) ([jfallows](https://github.com/jfallows))
- engine: add Binding.validate\(BindingConfig\) SPI hook for cross-field config validation at load time [\#2031](https://github.com/aklivity/zilla/issues/2031) ([jfallows](https://github.com/jfallows))
- binding-mcp: support resources/templates/list as a distinct JSON-RPC method \(server, proxy, client kinds\) [\#2033](https://github.com/aklivity/zilla/issues/2033) ([jfallows](https://github.com/jfallows))
- common-json/common-avro/common-protobuf: JsonGeneratorImpl's atomic write methods have no room check [\#2040](https://github.com/aklivity/zilla/issues/2040) ([jfallows](https://github.com/jfallows))
- common-json: JsonGeneratorImpl's GENERATE\_ESCAPED mode under-reserves room, overrunning the wrapped buffer [\#2043](https://github.com/aklivity/zilla/issues/2043) ([jfallows](https://github.com/jfallows))
- binding-mcp: client kind lacks per-tool scope guarding \(when/guarded + securitySchemes injection\) [\#2046](https://github.com/aklivity/zilla/issues/2046) ([jfallows](https://github.com/jfallows))
- mcp\_proxy tools/list omits mcp\_http/mcp\_openapi toolkits under any scoped token [\#2058](https://github.com/aklivity/zilla/issues/2058) ([jfallows](https://github.com/jfallows))
- mcp\_openapi/mcp\_http tool calls with a JSON body fail: call arguments not forwarded to the backend request body [\#2059](https://github.com/aklivity/zilla/issues/2059) ([jfallows](https://github.com/jfallows))
- mcp url-mode elicitation: tool call never resumes/returns its result after elicitation completes [\#2060](https://github.com/aklivity/zilla/issues/2060) ([jfallows](https://github.com/jfallows))
- mcp proxy: routes\[\].with.cache.credentials should fall back to options.cache.authorization's credential [\#2062](https://github.com/aklivity/zilla/issues/2062) ([jfallows](https://github.com/jfallows))
- mcp proxy: toolkit-level guarded routes don't propagate scope into cached tools/list, leaking to unauthenticated callers [\#2063](https://github.com/aklivity/zilla/issues/2063) ([jfallows](https://github.com/jfallows))
- Align route when/with naming \(api-id/operation-id → spec/operation\) and add tag/glob bulk selection across openapi, openapi-asyncapi, asyncapi [\#2067](https://github.com/aklivity/zilla/issues/2067) ([jfallows](https://github.com/jfallows))
- Split servers \(path routing\) from server \(deployment target\) on openapi/asyncapi client/server kinds [\#2069](https://github.com/aklivity/zilla/issues/2069) ([jfallows](https://github.com/jfallows))
- binding-mcp: add telemetry events for session lifecycle, bearer auth rejection, and elicitation timeout [\#2070](https://github.com/aklivity/zilla/issues/2070) ([jfallows](https://github.com/jfallows))
- mcp: split search-active tool catalog into search\_tools / describe\_tool / execute\_tool [\#2081](https://github.com/aklivity/zilla/issues/2081) ([jfallows](https://github.com/jfallows))
- binding-mcp-openapi: support operator-declared guarded routes independent of OpenAPI security schemes [\#2103](https://github.com/aklivity/zilla/issues/2103) ([jfallows](https://github.com/jfallows))
- Follow-up: consolidate remaining options.\* abstraction leaks in openapi/asyncapi [\#2113](https://github.com/aklivity/zilla/issues/2113) ([jfallows](https://github.com/jfallows))
- binding-kafka: cache-client fetch never recovers if the one-shot startup bootstrap fails [\#2116](https://github.com/aklivity/zilla/issues/2116) ([jfallows](https://github.com/jfallows))
- Overlay mechanism for openapi/asyncapi specs: OpenAPI Overlay Specification + shared JSONPath in common-json [\#2127](https://github.com/aklivity/zilla/issues/2127) ([jfallows](https://github.com/jfallows))
- Add a `zilla logs` command to tail engine events, for use as a reliable readiness/health check [\#2131](https://github.com/aklivity/zilla/issues/2131) ([jfallows](https://github.com/jfallows))
- binding-http client kind: support outbound credential injection via options.authorization [\#2132](https://github.com/aklivity/zilla/issues/2132) ([jfallows](https://github.com/jfallows))
- binding-openapi-asyncapi: options.specs.asyncapi.<name>.security is accepted but never consumed [\#2133](https://github.com/aklivity/zilla/issues/2133) ([jfallows](https://github.com/jfallows))
- Readonly Engine attach zeroes live events ring buffer and corrupts Info reads [\#2137](https://github.com/aklivity/zilla/issues/2137) ([jfallows](https://github.com/jfallows))
- Readonly Engine close writes spurious engine.stopped event to live engine's event ring buffer [\#2140](https://github.com/aklivity/zilla/issues/2140) ([jfallows](https://github.com/jfallows))
- Readonly Engine attach \(zilla logs/metrics\) resets the live tuning file, crashing the running engine [\#2143](https://github.com/aklivity/zilla/issues/2143) ([jfallows](https://github.com/jfallows))
- Canonicalize/un-canonicalize request path and location values at the server/client stream-factory boundary [\#2145](https://github.com/aklivity/zilla/issues/2145) ([jfallows](https://github.com/jfallows))
- binding-asyncapi: AsyncAPI 3.0 channel-level `servers` scoping is not parsed [\#2150](https://github.com/aklivity/zilla/issues/2150) ([jfallows](https://github.com/jfallows))
- Put /opt/zilla on PATH in the Docker image so `zilla` works as a bare command [\#2153](https://github.com/aklivity/zilla/issues/2153) ([jfallows](https://github.com/jfallows))
- binding-tls: mutual/trustcacerts defaults computed in two places, wrong for no-refs vault case [\#2157](https://github.com/aklivity/zilla/issues/2157) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- build\(deps\): bump docker/login-action from 3 to 4 [\#1645](https://github.com/aklivity/zilla/pull/1645) ([dependabot[bot]](https://github.com/apps/dependabot))
- feat\(engine\): migrate Agrona buffer types to UnsafeBufferEx extension library [\#1733](https://github.com/aklivity/zilla/pull/1733) ([jfallows](https://github.com/jfallows))
- fix\(binding-kafka\): export telemetry events [\#1768](https://github.com/aklivity/zilla/pull/1768) ([ankitk-me](https://github.com/ankitk-me))
- Support routing based on TLS client certificate presence/signer for mixed-auth endpoints on shared port [\#1769](https://github.com/aklivity/zilla/pull/1769) ([akrambek](https://github.com/akrambek))
- feat\(binding-mcp\): emit Alt-Svc response header from server [\#1773](https://github.com/aklivity/zilla/pull/1773) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): mcp · proxy cache option \(\#1737\) [\#1774](https://github.com/aklivity/zilla/pull/1774) ([jfallows](https://github.com/jfallows))
- feat\(binding-http\): translate Alt-Svc http= placeholder to ALPN id [\#1775](https://github.com/aklivity/zilla/pull/1775) ([jfallows](https://github.com/jfallows))
- build\(deps\): Bump docker/build-push-action from 6 to 7 [\#1777](https://github.com/aklivity/zilla/pull/1777) ([dependabot[bot]](https://github.com/apps/dependabot))
- build\(deps\): Bump azure/setup-helm from 4 to 5 [\#1778](https://github.com/aklivity/zilla/pull/1778) ([dependabot[bot]](https://github.com/apps/dependabot))
- build\(deps\): bump ubuntu from jammy-20260410 to jammy-20260509 in /cloud/docker-image/src/main/docker [\#1779](https://github.com/aklivity/zilla/pull/1779) ([dependabot[bot]](https://github.com/apps/dependabot))
- build\(deps\): bump org.testcontainers:testcontainers-bom from 2.0.2 to 2.0.5 [\#1780](https://github.com/aklivity/zilla/pull/1780) ([dependabot[bot]](https://github.com/apps/dependabot))
- Add MCP lifecycle and toolkit integration tests [\#1784](https://github.com/aklivity/zilla/pull/1784) ([jfallows](https://github.com/jfallows))
- fix\(binding-kafka\): immediate retry metadata refresh [\#1786](https://github.com/aklivity/zilla/pull/1786) ([ankitk-me](https://github.com/ankitk-me))
- Add support for MCP list changed notifications [\#1789](https://github.com/aklivity/zilla/pull/1789) ([jfallows](https://github.com/jfallows))
- Add lock, unlock, and watch operations to StoreHandler [\#1790](https://github.com/aklivity/zilla/pull/1790) ([jfallows](https://github.com/jfallows))
- Support aggregate event IDs for multi-toolkit MCP proxy [\#1791](https://github.com/aklivity/zilla/pull/1791) ([jfallows](https://github.com/jfallows))
- feat\(engine\): add renew operation to StoreHandler [\#1792](https://github.com/aklivity/zilla/pull/1792) ([jfallows](https://github.com/jfallows))
- feat\(guard-identity\): add identity guard for unvalidated bearer pass-through [\#1794](https://github.com/aklivity/zilla/pull/1794) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): propagate upstream Bearer auth challenges and defer per-request app stream end [\#1796](https://github.com/aklivity/zilla/pull/1796) ([jfallows](https://github.com/jfallows))
- feat\(binding-mqtt-kafka\): deprecate Kafka-group session ownership \(\#1797\) [\#1802](https://github.com/aklivity/zilla/pull/1802) ([jfallows](https://github.com/jfallows))
- refactor\(binding-mqtt\): move session-ownership store option and warning from mqtt-kafka [\#1804](https://github.com/aklivity/zilla/pull/1804) ([jfallows](https://github.com/jfallows))
- refactor\(binding-mqtt\): remove session-ownership store option and warning [\#1805](https://github.com/aklivity/zilla/pull/1805) ([jfallows](https://github.com/jfallows))
- \[2.0\] Refactor MQTT session management to use store-based persistence [\#1806](https://github.com/aklivity/zilla/pull/1806) ([jfallows](https://github.com/jfallows))
- Separate engine initialization from startup [\#1808](https://github.com/aklivity/zilla/pull/1808) ([jfallows](https://github.com/jfallows))
- fix\(engine\): release worker buffers in coordinated close to avoid JVM crash on binding fault [\#1809](https://github.com/aklivity/zilla/pull/1809) ([jfallows](https://github.com/jfallows))
- fix\(binding-http\): client connection reuse per origin/authority + per-connection exchange/queue scope [\#1812](https://github.com/aklivity/zilla/pull/1812) ([jfallows](https://github.com/jfallows))
- fix\(binding-mcp\): deterministic cache refresh startup/teardown + coalesced re-arming [\#1814](https://github.com/aklivity/zilla/pull/1814) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): single session-id contract, negotiated protocol-version, partial-success hydration [\#1816](https://github.com/aklivity/zilla/pull/1816) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): proxy hydration/forwarding fixes + per-upstream bootstrap credential [\#1818](https://github.com/aklivity/zilla/pull/1818) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): offer 2025-11-25 and negotiate elicitation url capability [\#1820](https://github.com/aklivity/zilla/pull/1820) ([jfallows](https://github.com/jfallows))
- fix\(binding-mcp\): parse initialize without JPMS-blocked reflection [\#1821](https://github.com/aklivity/zilla/pull/1821) ([jfallows](https://github.com/jfallows))
- fix\(binding-mcp\): decode JSON-RPC requests independent of field order [\#1822](https://github.com/aklivity/zilla/pull/1822) ([jfallows](https://github.com/jfallows))
- fix\(binding-mcp\): keep proxied tool-call result when upstream sends a resumable flush [\#1823](https://github.com/aklivity/zilla/pull/1823) ([jfallows](https://github.com/jfallows))
- feat\(command-dump\): pluggable Wireshark dissector via ZillaDumpDissectorSpi [\#1830](https://github.com/aklivity/zilla/pull/1830) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp-http\): mcp\_http · proxy binding [\#1832](https://github.com/aklivity/zilla/pull/1832) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): per-toolkit elicitation for the mcp proxy \(\#1810\) [\#1834](https://github.com/aklivity/zilla/pull/1834) ([jfallows](https://github.com/jfallows))
- Add MCP metrics support with counters and histograms [\#1837](https://github.com/aklivity/zilla/pull/1837) ([jfallows](https://github.com/jfallows))
- fix\(docker-image\): include metrics-mcp module in the runtime image [\#1840](https://github.com/aklivity/zilla/pull/1840) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): per-route allow-set filtering for tools/prompts/resources \(\#1833\) [\#1841](https://github.com/aklivity/zilla/pull/1841) ([jfallows](https://github.com/jfallows))
- Add test for elicitation challenge after tool result [\#1842](https://github.com/aklivity/zilla/pull/1842) ([jfallows](https://github.com/jfallows))
- fix\(binding-mcp\): defer reply content-type for elicitation-capable sessions [\#1844](https://github.com/aklivity/zilla/pull/1844) ([jfallows](https://github.com/jfallows))
- feat\(common-yaml\): self-contained streaming YAML parse + generate [\#1845](https://github.com/aklivity/zilla/pull/1845) ([jfallows](https://github.com/jfallows))
- feat\(common-json\): composable streaming JSON pipeline SPI \(validate + project\) [\#1846](https://github.com/aklivity/zilla/pull/1846) ([jfallows](https://github.com/jfallows))
- \[codex\] Raise metrics-grpc coverage gate [\#1847](https://github.com/aklivity/zilla/pull/1847) ([jfallows](https://github.com/jfallows))
- \[codex\] Improve binding-mcp coverage gate [\#1848](https://github.com/aklivity/zilla/pull/1848) ([jfallows](https://github.com/jfallows))
- fix\(binding-http\): reset streaming response on remote client disconnect [\#1850](https://github.com/aklivity/zilla/pull/1850) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): SEP-1036 url-mode elicitation \(passthrough + broker\) [\#1851](https://github.com/aklivity/zilla/pull/1851) ([jfallows](https://github.com/jfallows))
- feat\(examples\): mcp.proxy demonstrates url-mode elicitation aggregation [\#1852](https://github.com/aklivity/zilla/pull/1852) ([jfallows](https://github.com/jfallows))
- Refactor MCP client/server configuration and remove route-based headers [\#1853](https://github.com/aklivity/zilla/pull/1853) ([jfallows](https://github.com/jfallows))
- feat\(common-json\): JSON Schema validator with diagnostics and multi-draft parity [\#1859](https://github.com/aklivity/zilla/pull/1859) ([jfallows](https://github.com/jfallows))
- support guard injection for kafka client credentials [\#1860](https://github.com/aklivity/zilla/pull/1860) ([ankitk-me](https://github.com/ankitk-me))
- Add streaming JSON schema validation via ValidatingParser [\#1861](https://github.com/aklivity/zilla/pull/1861) ([jfallows](https://github.com/jfallows))
- Rename watch test barriers for clarity [\#1862](https://github.com/aklivity/zilla/pull/1862) ([jfallows](https://github.com/jfallows))
- Add local test runner script for examples [\#1863](https://github.com/aklivity/zilla/pull/1863) ([jfallows](https://github.com/jfallows))
- chore\(docker-image\): drop stale leadpony justify/joy assembly includes [\#1864](https://github.com/aklivity/zilla/pull/1864) ([jfallows](https://github.com/jfallows))
- Add Jakarta JSON-P provider implementation [\#1869](https://github.com/aklivity/zilla/pull/1869) ([jfallows](https://github.com/jfallows))
- Add segmented delivery mode to JSON streaming pipeline [\#1870](https://github.com/aklivity/zilla/pull/1870) ([jfallows](https://github.com/jfallows))
- Add common-protobuf: descriptor-driven Protobuf wire codec [\#1871](https://github.com/aklivity/zilla/pull/1871) ([jfallows](https://github.com/jfallows))
- Add common-avro module for streaming Avro codec [\#1872](https://github.com/aklivity/zilla/pull/1872) ([jfallows](https://github.com/jfallows))
- Add streaming JSON pipeline to common-json: bounded back-pressure, verbatim values, zero-allocation projection [\#1873](https://github.com/aklivity/zilla/pull/1873) ([jfallows](https://github.com/jfallows))
- Add Docker Hub authentication and ZPM cache to CI workflows [\#1874](https://github.com/aklivity/zilla/pull/1874) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): validate tools/call arguments at the proxy [\#1877](https://github.com/aklivity/zilla/pull/1877) ([jfallows](https://github.com/jfallows))
- Add escape mode to JSON generator for JSON-in-JSON encoding [\#1879](https://github.com/aklivity/zilla/pull/1879) ([jfallows](https://github.com/jfallows))
- Support fragmented JSON values across input windows [\#1880](https://github.com/aklivity/zilla/pull/1880) ([jfallows](https://github.com/jfallows))
- fix\(common-json\): project kept scalar leaves fragmented across input windows [\#1881](https://github.com/aklivity/zilla/pull/1881) ([jfallows](https://github.com/jfallows))
- feat\(common-json\): stream kept scalar leaves via append-only consumed pushback [\#1882](https://github.com/aklivity/zilla/pull/1882) ([jfallows](https://github.com/jfallows))
- Add network-level MCP elicitation callback test with context [\#1883](https://github.com/aklivity/zilla/pull/1883) ([jfallows](https://github.com/jfallows))
- feat\(common-avro\): add JSON ↔ Avro streaming bridge [\#1884](https://github.com/aklivity/zilla/pull/1884) ([jfallows](https://github.com/jfallows))
- feat\(common-json\): expose JsonParserEx.reset\(\) [\#1885](https://github.com/aklivity/zilla/pull/1885) ([jfallows](https://github.com/jfallows))
- feat\(common-protobuf\): protobuf↔JSON bridge via ProtobufJson [\#1886](https://github.com/aklivity/zilla/pull/1886) ([jfallows](https://github.com/jfallows))
- common-json: converge scalar value delivery — canonical structured rendering, unified consumed\(\) flow control, remove Delivery.DECODED [\#1888](https://github.com/aklivity/zilla/pull/1888) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp-openapi\): implement mcp\_openapi proxy binding [\#1891](https://github.com/aklivity/zilla/pull/1891) ([jfallows](https://github.com/jfallows))
- refactor\(common-json\): consolidate scalar and key reads on getStringView [\#1892](https://github.com/aklivity/zilla/pull/1892) ([jfallows](https://github.com/jfallows))
- refactor\(common-avro\): make AvroSink stateless \(resume event + consumed\(\) pushback\) [\#1893](https://github.com/aklivity/zilla/pull/1893) ([jfallows](https://github.com/jfallows))
- perf\(common-yaml\): stream generator output and reduce parse allocations [\#1894](https://github.com/aklivity/zilla/pull/1894) ([jfallows](https://github.com/jfallows))
- refactor\(common-protobuf\): stateless wire sinks \(pump-owned resume cursor + consumed\(\) pushback\) [\#1895](https://github.com/aklivity/zilla/pull/1895) ([jfallows](https://github.com/jfallows))
- fix\(binding-mcp-http\): align JSON key reads with getStringView\(\) [\#1896](https://github.com/aklivity/zilla/pull/1896) ([jfallows](https://github.com/jfallows))
- support `oauthbearer` `sasl` mechanism in kafka binding & support `identity` `guard` options with static identity and credentials [\#1898](https://github.com/aklivity/zilla/pull/1898) ([ankitk-me](https://github.com/ankitk-me))
- build\(deps\): Bump alpine from 3.23.4 to 3.24.1 in /cloud/docker-image/src/main/docker [\#1899](https://github.com/aklivity/zilla/pull/1899) ([dependabot[bot]](https://github.com/apps/dependabot))
- ci: cache and authenticate Docker Hub image pulls to survive rate limits [\#1901](https://github.com/aklivity/zilla/pull/1901) ([jfallows](https://github.com/jfallows))
- Parameterize ZpmInstallTest with dynamic version properties [\#1902](https://github.com/aklivity/zilla/pull/1902) ([jfallows](https://github.com/jfallows))
- build\(deps\): bump hono from 4.12.23 to 4.12.26 in /examples/mcp.proxy/url-elicit [\#1903](https://github.com/aklivity/zilla/pull/1903) ([dependabot[bot]](https://github.com/apps/dependabot))
- perf\(common-json\): cut hot-path allocations in JSON DOM and schema validation [\#1904](https://github.com/aklivity/zilla/pull/1904) ([jfallows](https://github.com/jfallows))
- feat\(common-protobuf\): compile ProtobufSchema from .proto source text [\#1906](https://github.com/aklivity/zilla/pull/1906) ([jfallows](https://github.com/jfallows))
- feat\(model-json\): streaming projecting + validating JSON converter [\#1907](https://github.com/aklivity/zilla/pull/1907) ([jfallows](https://github.com/jfallows))
- refactor\(common-json\): adapt JsonParserEx via Source/Control instead of casting [\#1908](https://github.com/aklivity/zilla/pull/1908) ([jfallows](https://github.com/jfallows))
- feat\(model-avro\): re-platform converter and validator onto common-avro + common-json [\#1909](https://github.com/aklivity/zilla/pull/1909) ([jfallows](https://github.com/jfallows))
- feat\(common-protobuf\): message-index resolution + JsonFormat-compatible JSON options [\#1910](https://github.com/aklivity/zilla/pull/1910) ([jfallows](https://github.com/jfallows))
- Parse YAML through a single-pass streaming scanner and retire the eager parser [\#1911](https://github.com/aklivity/zilla/pull/1911) ([jfallows](https://github.com/jfallows))
- feat\(model-protobuf\): re-platform converter onto common-protobuf, drop protobuf-java [\#1912](https://github.com/aklivity/zilla/pull/1912) ([jfallows](https://github.com/jfallows))
- feat\(common-protobuf\): strict reject-unknown-fields option + pipeline rejection reason [\#1913](https://github.com/aklivity/zilla/pull/1913) ([jfallows](https://github.com/jfallows))
- support `kind` in `guard` [\#1915](https://github.com/aklivity/zilla/pull/1915) ([ankitk-me](https://github.com/ankitk-me))
- Refactor common-json/avro/protobuf streaming window API: remaining\(\) and \(offset, limit\) [\#1916](https://github.com/aklivity/zilla/pull/1916) ([jfallows](https://github.com/jfallows))
- feat\(common-protobuf\): stream large scalar values through bounded windows \(ProtobufJson generator + parser\) [\#1917](https://github.com/aklivity/zilla/pull/1917) ([jfallows](https://github.com/jfallows))
- Add diagnostic reporting for pipeline rejections [\#1918](https://github.com/aklivity/zilla/pull/1918) ([jfallows](https://github.com/jfallows))
- feat\(common-json\): surface verbatim schema diagnostics with accurate line/column [\#1920](https://github.com/aklivity/zilla/pull/1920) ([jfallows](https://github.com/jfallows))
- feat\(common-protobuf\): stream bytes values across input windows without an adapter buffer [\#1921](https://github.com/aklivity/zilla/pull/1921) ([jfallows](https://github.com/jfallows))
- perf\(common-protobuf\): allocation-free finite float/double parse on the JSON→protobuf write path [\#1922](https://github.com/aklivity/zilla/pull/1922) ([jfallows](https://github.com/jfallows))
- refactor\(common-protobuf\): retire the ProtobufJson staging buffers \(key accumulator + value buffer\) [\#1923](https://github.com/aklivity/zilla/pull/1923) ([jfallows](https://github.com/jfallows))
- perf\(common-avro\): zero-allocation AvroJson scalar streaming + shared Numbers utility [\#1924](https://github.com/aklivity/zilla/pull/1924) ([jfallows](https://github.com/jfallows))
- refactor\(common-protobuf\): use shared common-lang Numbers for float/double parse [\#1925](https://github.com/aklivity/zilla/pull/1925) ([jfallows](https://github.com/jfallows))
- feat\(common-json\): consumer-driven value retention \(accumulate on decline, incremental number validation, fail-closed cap, fragment-aware validator\) [\#1927](https://github.com/aklivity/zilla/pull/1927) ([jfallows](https://github.com/jfallows))
- build\(docker-image\): list common-avro explicitly in zpm manifest [\#1928](https://github.com/aklivity/zilla/pull/1928) ([jfallows](https://github.com/jfallows))
- build\(deps\): Bump actions/checkout from 6 to 7 [\#1929](https://github.com/aklivity/zilla/pull/1929) ([dependabot[bot]](https://github.com/apps/dependabot))
- Refactor JsonProjector to use trie-based pointer matching [\#1931](https://github.com/aklivity/zilla/pull/1931) ([jfallows](https://github.com/jfallows))
- Add ModelHandler and ModelPipeline abstractions for data validation [\#1933](https://github.com/aklivity/zilla/pull/1933) ([jfallows](https://github.com/jfallows))
- Introduce BudgetCredit and BudgetDebit handle APIs [\#1934](https://github.com/aklivity/zilla/pull/1934) ([jfallows](https://github.com/jfallows))
- fix\(common-json\): compile JSON Schema pattern with ECMA-262 brace semantics [\#1936](https://github.com/aklivity/zilla/pull/1936) ([jfallows](https://github.com/jfallows))
- Convert test config assertions from JSON to YAML format [\#1938](https://github.com/aklivity/zilla/pull/1938) ([jfallows](https://github.com/jfallows))
- Add common-json dependency to model-protobuf module [\#1939](https://github.com/aklivity/zilla/pull/1939) ([jfallows](https://github.com/jfallows))
- ci\(release\): route Docker Hub pulls through mirror.gcr.io and cache layers in GHCR [\#1940](https://github.com/aklivity/zilla/pull/1940) ([jfallows](https://github.com/jfallows))
- ci\(build\): route Docker Hub pulls through mirror.gcr.io and make PR builds uniform [\#1941](https://github.com/aklivity/zilla/pull/1941) ([jfallows](https://github.com/jfallows))
- feat\(common-json\): generalize the verbatim transform pipeline — validate, prune, inject [\#1943](https://github.com/aklivity/zilla/pull/1943) ([jfallows](https://github.com/jfallows))
- fix\(manager\): pin in-JVM resolver lock factory to fix ZPM install deadlock [\#1944](https://github.com/aklivity/zilla/pull/1944) ([jfallows](https://github.com/jfallows))
- ci: declare Maven Central first to avoid slow aklivity-repo misses [\#1945](https://github.com/aklivity/zilla/pull/1945) ([jfallows](https://github.com/jfallows))
- test\(engine\): make store watch assertion deterministic via reply flush [\#1946](https://github.com/aklivity/zilla/pull/1946) ([jfallows](https://github.com/jfallows))
- Remove Maven Central repository, use Aklivity packages only [\#1947](https://github.com/aklivity/zilla/pull/1947) ([jfallows](https://github.com/jfallows))
- ci: pass docker image between build jobs via artifact instead of cache [\#1948](https://github.com/aklivity/zilla/pull/1948) ([jfallows](https://github.com/jfallows))
- test\(engine\): emit reply flush when store assertion chain completes [\#1949](https://github.com/aklivity/zilla/pull/1949) ([jfallows](https://github.com/jfallows))
- Migrate tshark Docker image to ghcr.io/aklivity registry [\#1950](https://github.com/aklivity/zilla/pull/1950) ([jfallows](https://github.com/jfallows))
- fix\(model\): preserve content across bounded output windows on json/avro/protobuf view paths [\#1951](https://github.com/aklivity/zilla/pull/1951) ([jfallows](https://github.com/jfallows))
- Remove Docker Hub authentication from CI workflows [\#1952](https://github.com/aklivity/zilla/pull/1952) ([jfallows](https://github.com/jfallows))
- fix\(catalog-schema-registry\): strip framing prefix on ModelPipeline read path [\#1953](https://github.com/aklivity/zilla/pull/1953) ([jfallows](https://github.com/jfallows))
- fix\(common-json\): preserve document trailing bytes on the verbatim-forward path [\#1955](https://github.com/aklivity/zilla/pull/1955) ([jfallows](https://github.com/jfallows))
- feat\(engine\): add identity\(\) capability to ModelPipeline [\#1957](https://github.com/aklivity/zilla/pull/1957) ([jfallows](https://github.com/jfallows))
- build\(deps\): Bump actions/github-script from 7 to 9 [\#1958](https://github.com/aklivity/zilla/pull/1958) ([dependabot[bot]](https://github.com/apps/dependabot))
- build\(deps\): Bump actions/download-artifact from 7 to 8 [\#1959](https://github.com/aklivity/zilla/pull/1959) ([dependabot[bot]](https://github.com/apps/dependabot))
- test\(binding-mcp-http\): cover config adapters and prune dead McpHttpState helpers [\#1960](https://github.com/aklivity/zilla/pull/1960) ([jfallows](https://github.com/jfallows))
- refactor\(engine\): remove legacy ValidatorHandler and ConverterHandler SPI [\#1961](https://github.com/aklivity/zilla/pull/1961) ([jfallows](https://github.com/jfallows))
- Enforce store requirement for MQTT server bindings [\#1963](https://github.com/aklivity/zilla/pull/1963) ([jfallows](https://github.com/jfallows))
- feat\(binding-mqtt\)!: require store on mqtt server binding [\#1964](https://github.com/aklivity/zilla/pull/1964) ([jfallows](https://github.com/jfallows))
- ci: share one cached image set across examples tests [\#1965](https://github.com/aklivity/zilla/pull/1965) ([jfallows](https://github.com/jfallows))
- test\(examples\): make smoke-test CI deterministic with readiness gating [\#1966](https://github.com/aklivity/zilla/pull/1966) ([jfallows](https://github.com/jfallows))
- Replace Unsafe with Foreign Function & Memory API in UnsafeBufferEx [\#1968](https://github.com/aklivity/zilla/pull/1968) ([jfallows](https://github.com/jfallows))
- chore\(binding-asyncapi\): remove unused Jackson dependency [\#1971](https://github.com/aklivity/zilla/pull/1971) ([jfallows](https://github.com/jfallows))
- Add scope-based filtering for MCP tools list with guard support [\#1972](https://github.com/aklivity/zilla/pull/1972) ([jfallows](https://github.com/jfallows))
- Enforce --sun-misc-unsafe-memory-access=deny [\#1973](https://github.com/aklivity/zilla/pull/1973) ([jfallows](https://github.com/jfallows))
- Add offline mode support to ZPM cache resolution [\#1974](https://github.com/aklivity/zilla/pull/1974) ([jfallows](https://github.com/jfallows))
- perf\(binding-mcp-http\): stream the proxy request and response paths, removing per-message JSON DOM/String allocations [\#1975](https://github.com/aklivity/zilla/pull/1975) ([jfallows](https://github.com/jfallows))
- Refactor MCP HTTP proxy into kind-specific implementations [\#1977](https://github.com/aklivity/zilla/pull/1977) ([jfallows](https://github.com/jfallows))
- feat\(common-openapi\): hoist OpenAPI model/view/parser API into shared module [\#1978](https://github.com/aklivity/zilla/pull/1978) ([jfallows](https://github.com/jfallows))
- fix\(manager\): generate delegate module-info with strict jdeps validation [\#1979](https://github.com/aklivity/zilla/pull/1979) ([jfallows](https://github.com/jfallows))
- ci\(release\): support/1.x-aware release workflow with major/minor docker tags [\#1981](https://github.com/aklivity/zilla/pull/1981) ([jfallows](https://github.com/jfallows))
- ci\(release\): force Maven to IPv4 and skip tests on release deploy [\#1983](https://github.com/aklivity/zilla/pull/1983) ([jfallows](https://github.com/jfallows))
- build\(deps\): Bump ubuntu from jammy-20260509 to jammy-20260627 in /cloud/docker-image/src/main/docker [\#1989](https://github.com/aklivity/zilla/pull/1989) ([dependabot[bot]](https://github.com/apps/dependabot))
- ci\(release\): make CHANGELOG generation resilient to transient failures [\#1990](https://github.com/aklivity/zilla/pull/1990) ([jfallows](https://github.com/jfallows))
- ci\(release\): generalize no-merge/tag-first protection on develop's own release.yml [\#1993](https://github.com/aklivity/zilla/pull/1993) ([jfallows](https://github.com/jfallows))
- feat\(common-openapi\): generic access to x-\* specification extensions [\#1994](https://github.com/aklivity/zilla/pull/1994) ([jfallows](https://github.com/jfallows))
- feat\(manager\): support enabling incubator modules via --incubator [\#1995](https://github.com/aklivity/zilla/pull/1995) ([jfallows](https://github.com/jfallows))
- feat\(common-asyncapi\): hoist AsyncAPI model/view/parser API into shared module [\#1996](https://github.com/aklivity/zilla/pull/1996) ([jfallows](https://github.com/jfallows))
- fix\(common-yaml\): advance parser cursor when materializing getObject\(\)/getValue\(\)/getArray\(\) [\#1998](https://github.com/aklivity/zilla/pull/1998) ([jfallows](https://github.com/jfallows))
- refactor\(common-json\): relocate DirectBufferInputStreamEx to common-agrona [\#2000](https://github.com/aklivity/zilla/pull/2000) ([jfallows](https://github.com/jfallows))
- Refactor AsyncAPI bindings to support generic binding registration [\#2004](https://github.com/aklivity/zilla/pull/2004) ([jfallows](https://github.com/jfallows))
- Add OAuth2 flows and scopes support to OpenAPI security schemes [\#2005](https://github.com/aklivity/zilla/pull/2005) ([jfallows](https://github.com/jfallows))
- Support prefix wildcard extensions in OpenAPI parser [\#2007](https://github.com/aklivity/zilla/pull/2007) ([jfallows](https://github.com/jfallows))
- feat\(common-asyncapi\): generic access to x-\* specification extensions [\#2009](https://github.com/aklivity/zilla/pull/2009) ([jfallows](https://github.com/jfallows))
- Add scope-based extension registration for OpenAPI parser [\#2010](https://github.com/aklivity/zilla/pull/2010) ([jfallows](https://github.com/jfallows))
- Support scoped AsyncAPI extensions [\#2012](https://github.com/aklivity/zilla/pull/2012) ([jfallows](https://github.com/jfallows))
- fix\(examples\): fix flaky grpc.kafka.fanout test and harden zilla healthchecks [\#2013](https://github.com/aklivity/zilla/pull/2013) ([jfallows](https://github.com/jfallows))
- Fix UnsafeBufferEx.asNative\(\) to preserve offset for sub-range wraps [\#2015](https://github.com/aklivity/zilla/pull/2015) ([jfallows](https://github.com/jfallows))
- Bound buffer writes in JSON assembly and staging [\#2024](https://github.com/aklivity/zilla/pull/2024) ([jfallows](https://github.com/jfallows))
- fix\(common-openapi\): resolve full servers\[\] precedence in OpenapiOperationView [\#2026](https://github.com/aklivity/zilla/pull/2026) ([jfallows](https://github.com/jfallows))
- Optimize MemorySegment.copy calls by avoiding wrapper allocation [\#2027](https://github.com/aklivity/zilla/pull/2027) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp-openapi\): pick up OpenAPI operation summary and description for generated tools [\#2028](https://github.com/aklivity/zilla/pull/2028) ([jfallows](https://github.com/jfallows))
- Support bulk operation selection by tag and glob pattern [\#2029](https://github.com/aklivity/zilla/pull/2029) ([jfallows](https://github.com/jfallows))
- Remove prompts support from MCP HTTP binding [\#2030](https://github.com/aklivity/zilla/pull/2030) ([jfallows](https://github.com/jfallows))
- fix\(common-json\): resolve terminal-window STARVED to REJECTED [\#2032](https://github.com/aklivity/zilla/pull/2032) ([jfallows](https://github.com/jfallows))
- feat\(engine\): add Binding.validate\(BindingConfig\) SPI hook for config load-time validation [\#2034](https://github.com/aklivity/zilla/pull/2034) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp-openapi\): options.resources overrides, resource/template classification, optional query param omission [\#2035](https://github.com/aklivity/zilla/pull/2035) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): support resources/templates/list as a distinct JSON-RPC method [\#2036](https://github.com/aklivity/zilla/pull/2036) ([jfallows](https://github.com/jfallows))
- feat\(common-json\): stream window-fragmented values with no content keyword \(forward-and-suppress\) [\#2037](https://github.com/aklivity/zilla/pull/2037) ([jfallows](https://github.com/jfallows))
- fix\(binding-kafka\): guard fanout initial abort/reset against unopened stream [\#2038](https://github.com/aklivity/zilla/pull/2038) ([jfallows](https://github.com/jfallows))
- fix\(binding-mcp-http\): release encode/decode slots on every close path [\#2039](https://github.com/aklivity/zilla/pull/2039) ([jfallows](https://github.com/jfallows))
- fix\(common-json\): guard JsonGeneratorImpl's unconditional quote/comma writes [\#2041](https://github.com/aklivity/zilla/pull/2041) ([jfallows](https://github.com/jfallows))
- fix\(common-json,common-avro,common-protobuf\): add room checks to atomic generator writes [\#2042](https://github.com/aklivity/zilla/pull/2042) ([jfallows](https://github.com/jfallows))
- fix\(common-json\): account for double-escaping cost in GENERATE\_ESCAPED width budgets [\#2044](https://github.com/aklivity/zilla/pull/2044) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp-openapi\): params: binding map, schemas.input, and path/query/header/cookie rebinding [\#2045](https://github.com/aklivity/zilla/pull/2045) ([jfallows](https://github.com/jfallows))
- fix\(binding-mcp\): support per-tool guarded routes for client kind [\#2047](https://github.com/aklivity/zilla/pull/2047) ([jfallows](https://github.com/jfallows))
- refactor\(binding-mcp-http\): nest body/bodyTemplate into McpHttpBodyConfig [\#2048](https://github.com/aklivity/zilla/pull/2048) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp-http\): align route when/guarded semantics with mcp binding [\#2049](https://github.com/aklivity/zilla/pull/2049) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): validate inbound bearer token against guard for kind:server [\#2050](https://github.com/aklivity/zilla/pull/2050) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): agent-callable tool search in mcp proxy with BM25 ranking [\#2051](https://github.com/aklivity/zilla/pull/2051) ([jfallows](https://github.com/jfallows))
- fix\(binding-mcp\): allow anonymous requests to reach unguarded mcp kind:server routes [\#2052](https://github.com/aklivity/zilla/pull/2052) ([jfallows](https://github.com/jfallows))
- feat\(examples\): demonstrate all mcp\* bindings with guarded JWT authorization [\#2053](https://github.com/aklivity/zilla/pull/2053) ([jfallows](https://github.com/jfallows))
- fix\(docker-image\): package binding-mcp-http and binding-mcp-openapi modules [\#2054](https://github.com/aklivity/zilla/pull/2054) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): configurable eager tool set under cache.tools.eager [\#2055](https://github.com/aklivity/zilla/pull/2055) ([jfallows](https://github.com/jfallows))
- fix\(binding-mcp\): thread inherited authorization into guard fallback calls [\#2061](https://github.com/aklivity/zilla/pull/2061) ([jfallows](https://github.com/jfallows))
- fix\(binding-mcp\): fall back to shared cache credentials for hydration south connections [\#2064](https://github.com/aklivity/zilla/pull/2064) ([jfallows](https://github.com/jfallows))
- feat\(common-json\): apply decline-to-N key streaming to related consumers [\#2065](https://github.com/aklivity/zilla/pull/2065) ([jfallows](https://github.com/jfallows))
- fix: remove orphaned deprecated config ahead of 2.0.0 [\#2071](https://github.com/aklivity/zilla/pull/2071) ([jfallows](https://github.com/jfallows))
- feat\(binding-asyncapi,binding-openapi-asyncapi\): align route when/with vocabulary with mcp-openapi [\#2072](https://github.com/aklivity/zilla/pull/2072) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): add telemetry events for session lifecycle, bearer auth, and elicitation timeout [\#2074](https://github.com/aklivity/zilla/pull/2074) ([jfallows](https://github.com/jfallows))
- feat\(examples\): demonstrate mcp.proxy tool search and eager tool loading [\#2075](https://github.com/aklivity/zilla/pull/2075) ([jfallows](https://github.com/jfallows))
- Add route filtering by operation tag and glob patterns [\#2076](https://github.com/aklivity/zilla/pull/2076) ([jfallows](https://github.com/jfallows))
- feat\(binding-openapi,binding-asyncapi\): servers subset-selection [\#2079](https://github.com/aklivity/zilla/pull/2079) ([jfallows](https://github.com/jfallows))
- fix\(engine\): defer k3po startable until engine start to close IT startup races [\#2082](https://github.com/aklivity/zilla/pull/2082) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp\): split search-active catalog into search\_tools/describe\_tool/execute\_tool [\#2083](https://github.com/aklivity/zilla/pull/2083) ([jfallows](https://github.com/jfallows))
- fix\(engine,manager,common-json\): remove parsson from the modular runtime [\#2084](https://github.com/aklivity/zilla/pull/2084) ([jfallows](https://github.com/jfallows))
- fix\(build\): don't fail PR on cleanup, skip cleanup if testing failed [\#2098](https://github.com/aklivity/zilla/pull/2098) ([jfallows](https://github.com/jfallows))
- feat\(binding-mcp-openapi\): support operator-declared guarded routes [\#2105](https://github.com/aklivity/zilla/pull/2105) ([jfallows](https://github.com/jfallows))
- feat\(engine\): support extension on rejected k3po transport streams [\#2110](https://github.com/aklivity/zilla/pull/2110) ([jfallows](https://github.com/jfallows))
- test\(binding-tcp\): skip IPv6 tests when unavailable in the build environment [\#2111](https://github.com/aklivity/zilla/pull/2111) ([jfallows](https://github.com/jfallows))
- fix\(engine\): skip TrustedTest when javax.net.ssl.trustStore is overridden [\#2112](https://github.com/aklivity/zilla/pull/2112) ([jfallows](https://github.com/jfallows))
- feat\(vault\): resolve every entry when no refs are given [\#2114](https://github.com/aklivity/zilla/pull/2114) ([jfallows](https://github.com/jfallows))
- feat\(engine\): support connect aborted zilla:reset.ext matcher [\#2115](https://github.com/aklivity/zilla/pull/2115) ([jfallows](https://github.com/jfallows))
- fix\(binding-asyncapi,binding-openapi,binding-openapi-asyncapi,binding-mcp-openapi\): close options.\* abstraction leaks and align spec/apiId terminology [\#2128](https://github.com/aklivity/zilla/pull/2128) ([jfallows](https://github.com/jfallows))
- fix\(binding-kafka\): don't reject cache fetch when partition leader isn't known yet [\#2129](https://github.com/aklivity/zilla/pull/2129) ([jfallows](https://github.com/jfallows))
- feat\(common-json,binding-openapi\): OpenAPI Overlay Specification support [\#2130](https://github.com/aklivity/zilla/pull/2130) ([jfallows](https://github.com/jfallows))
- fix\(engine\): stop readonly Engine attach from zeroing live event ring buffers [\#2134](https://github.com/aklivity/zilla/pull/2134) ([jfallows](https://github.com/jfallows))
- feat\(binding-http\): support outbound credential injection on kind: client [\#2135](https://github.com/aklivity/zilla/pull/2135) ([jfallows](https://github.com/jfallows))
- feat\(command-logs\): add zilla logs command for engine event readiness checks [\#2136](https://github.com/aklivity/zilla/pull/2136) ([jfallows](https://github.com/jfallows))
- fix\(engine\): stop readonly Engine close from writing engine.stopped event [\#2139](https://github.com/aklivity/zilla/pull/2139) ([jfallows](https://github.com/jfallows))
- fix\(engine\): stop readonly Engine attach from resetting the live tuning file [\#2142](https://github.com/aklivity/zilla/pull/2142) ([jfallows](https://github.com/jfallows))
- fix\(binding-mcp\): retry tools/list hydration forever instead of giving up after 5 attempts [\#2146](https://github.com/aklivity/zilla/pull/2146) ([jfallows](https://github.com/jfallows))
- fix\(binding-openapi-asyncapi\): remove inert asyncapi-side security instead of falling back to it [\#2148](https://github.com/aklivity/zilla/pull/2148) ([jfallows](https://github.com/jfallows))
- fix\(common-asyncapi\): parse channel-level servers scoping and missing server fields [\#2151](https://github.com/aklivity/zilla/pull/2151) ([jfallows](https://github.com/jfallows))
- Canonicalize/un-canonicalize request path at server/client stream-factory boundary [\#2152](https://github.com/aklivity/zilla/pull/2152) ([jfallows](https://github.com/jfallows))
- feat\(docker-image\): put /opt/zilla on PATH [\#2154](https://github.com/aklivity/zilla/pull/2154) ([jfallows](https://github.com/jfallows))
- fix\(binding-tls\): consolidate mutual/trustcacerts defaulting to runtime, after vault resolution [\#2159](https://github.com/aklivity/zilla/pull/2159) ([jfallows](https://github.com/jfallows))
- fix\(binding-asyncapi,binding-openapi,binding-openapi-asyncapi\): close remaining options.\* abstraction leaks, add diagnostic events [\#2161](https://github.com/aklivity/zilla/pull/2161) ([jfallows](https://github.com/jfallows))
- fix\(manager\): preserve uses clauses from real modules merged into the zpm delegate [\#2162](https://github.com/aklivity/zilla/pull/2162) ([jfallows](https://github.com/jfallows))
- ci\(release\): trial aklivity/gitflow-changelog on develop's release flow [\#2165](https://github.com/aklivity/zilla/pull/2165) ([jfallows](https://github.com/jfallows))
- ci\(release\): drive changelog tag-pattern from .gitflow-changelog.yml [\#2166](https://github.com/aklivity/zilla/pull/2166) ([jfallows](https://github.com/jfallows))
- ci\(release\): pin gitflow-changelog to v0, not the WIP branch [\#2168](https://github.com/aklivity/zilla/pull/2168) ([jfallows](https://github.com/jfallows))

## [1.2.4](https://github.com/aklivity/zilla/tree/1.2.4) (2026-05-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.2.3...1.2.4)

**Closed issues:**

- `binding-mcp` support elicitation [\#1739](https://github.com/aklivity/zilla/issues/1739) ([jfallows](https://github.com/jfallows))
- binding-mcp server: align mcp-session-id worker affinity via REDIRECT [\#1761](https://github.com/aklivity/zilla/issues/1761) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Bump github/codeql-action from 3 to 4 [\#1584](https://github.com/aklivity/zilla/pull/1584) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump lycheeverse/lychee-action from 2.7.0 to 2.8.0 [\#1643](https://github.com/aklivity/zilla/pull/1643) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/upload-artifact from 6 to 7 [\#1644](https://github.com/aklivity/zilla/pull/1644) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump alpine from 3.23.2 to 3.23.4 in /cloud/docker-image/src/main/docker [\#1726](https://github.com/aklivity/zilla/pull/1726) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump ubuntu from jammy-20260109 to jammy-20260410 in /cloud/docker-image/src/main/docker [\#1727](https://github.com/aklivity/zilla/pull/1727) ([dependabot[bot]](https://github.com/apps/dependabot))
- feat\(binding-mcp\): MCP elicitation across server, proxy, and client kinds \(\#1739\) [\#1752](https://github.com/aklivity/zilla/pull/1752) ([jfallows](https://github.com/jfallows))
- Cache Maven repository in release workflow using actions/cache and generated cache key [\#1763](https://github.com/aklivity/zilla/pull/1763) ([jfallows](https://github.com/jfallows))
- mcp: session id alignment + redirect handling and configurable attempts [\#1764](https://github.com/aklivity/zilla/pull/1764) ([jfallows](https://github.com/jfallows))
- Refactor Docker image publishing to use build-push-action [\#1771](https://github.com/aklivity/zilla/pull/1771) ([jfallows](https://github.com/jfallows))

## [1.2.3](https://github.com/aklivity/zilla/tree/1.2.3) (2026-05-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.2.2...1.2.3)

**Merged pull requests:**

- Fix test binding guard auth [\#1762](https://github.com/aklivity/zilla/pull/1762) ([jfallows](https://github.com/jfallows))

## [1.2.2](https://github.com/aklivity/zilla/tree/1.2.2) (2026-05-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.2.1...1.2.2)

**Closed issues:**

- Add REDIRECT control frame to defer affinity routing to sender [\#1753](https://github.com/aklivity/zilla/issues/1753) ([jfallows](https://github.com/jfallows))
- Add RouterFactorySpi for pluggable engine stream factory composition [\#1754](https://github.com/aklivity/zilla/issues/1754) ([jfallows](https://github.com/jfallows))
- binding-http: handle REDIRECT and remove `with.affinity` syntax [\#1756](https://github.com/aklivity/zilla/issues/1756) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- feat\(binding-mcp\): SSE response transport \(Streamable HTTP\) [\#1742](https://github.com/aklivity/zilla/pull/1742) ([jfallows](https://github.com/jfallows))
- feat\(engine\): drive guard preauthorize from test binding; resolveId on GuardConfig [\#1743](https://github.com/aklivity/zilla/pull/1743) ([akrambek](https://github.com/akrambek))
- feat\(engine, binding-http\): per-route affinity extraction with worker pinning \(\#1744\) [\#1745](https://github.com/aklivity/zilla/pull/1745) ([jfallows](https://github.com/jfallows))
- support both Claude Code and Codex via hierarchical `AGENTS.md` [\#1747](https://github.com/aklivity/zilla/pull/1747) ([jfallows](https://github.com/jfallows))
- update binding-mcp schema to disable catalog, vault & guarded [\#1748](https://github.com/aklivity/zilla/pull/1748) ([ankitk-me](https://github.com/ankitk-me))
- Update `store-memory` to disable `options` [\#1749](https://github.com/aklivity/zilla/pull/1749) ([ankitk-me](https://github.com/ankitk-me))
- feat\(engine\): add REDIRECT control frame primitive [\#1755](https://github.com/aklivity/zilla/pull/1755) ([jfallows](https://github.com/jfallows))
- feat\(engine\): add RouterFactorySpi for pluggable stream factory composition [\#1757](https://github.com/aklivity/zilla/pull/1757) ([jfallows](https://github.com/jfallows))
- feat\(binding-http\): handle REDIRECT and remove `with.affinity` syntax [\#1758](https://github.com/aklivity/zilla/pull/1758) ([jfallows](https://github.com/jfallows))
- feat\(command-dump\): add REDIRECT control frame support [\#1759](https://github.com/aklivity/zilla/pull/1759) ([jfallows](https://github.com/jfallows))
- fix\(engine\): await worker/boss onStart before EngineManager registration [\#1760](https://github.com/aklivity/zilla/pull/1760) ([jfallows](https://github.com/jfallows))

## [1.2.1](https://github.com/aklivity/zilla/tree/1.2.1) (2026-04-30)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.2.0...1.2.1)

**Implemented enhancements:**

- binding-mcp: implement mcp · proxy binding [\#1669](https://github.com/aklivity/zilla/issues/1669) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- feat\(binding-mcp\): implement mcp proxy binding [\#1709](https://github.com/aklivity/zilla/pull/1709) ([jfallows](https://github.com/jfallows))
- feat\(engine\): add store reference, preauthorize, and roles on GuardHandler [\#1734](https://github.com/aklivity/zilla/pull/1734) ([akrambek](https://github.com/akrambek))
- feat\(schema\): add entry: false to all binding schemas except kafka-grpc [\#1740](https://github.com/aklivity/zilla/pull/1740) ([ankitk-me](https://github.com/ankitk-me))
- feat\(engine\): add async reauthorize overload on GuardHandler [\#1741](https://github.com/aklivity/zilla/pull/1741) ([akrambek](https://github.com/akrambek))

## [1.2.0](https://github.com/aklivity/zilla/tree/1.2.0) (2026-04-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.1.9...1.2.0)

**Implemented enhancements:**

- binding-mcp: implement mcp · client binding [\#1670](https://github.com/aklivity/zilla/issues/1670) ([jfallows](https://github.com/jfallows))
- binding-http: derive ProxyBeginEx from :authority in HttpBeginEx for stream-driven destination routing [\#1676](https://github.com/aklivity/zilla/issues/1676) ([jfallows](https://github.com/jfallows))
- engine: implement sys: system namespace with built-in egress bindings [\#1677](https://github.com/aklivity/zilla/issues/1677) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- feat\(binding-mcp\): implement mcp client binding [\#1711](https://github.com/aklivity/zilla/pull/1711) ([jfallows](https://github.com/jfallows))
- feat\(binding-http\): derive ProxyBeginEx from :authority for stream-driven routing [\#1713](https://github.com/aklivity/zilla/pull/1713) ([jfallows](https://github.com/jfallows))
- feat\(engine\): add sys:http\_client, sys:tls\_client, sys:tcp\_client built-in egress bindings \(\#1677\) [\#1717](https://github.com/aklivity/zilla/pull/1717) ([jfallows](https://github.com/jfallows))
- fix\(binding-tcp\): prevent NPE when retrying accept across contexts [\#1735](https://github.com/aklivity/zilla/pull/1735) ([akrambek](https://github.com/akrambek))
- feat\(flyweight-maven-plugin\): add inject\(Consumer&lt;Builder&gt;\) on generated struct + union builders [\#1738](https://github.com/aklivity/zilla/pull/1738) ([jfallows](https://github.com/jfallows))

## [1.1.9](https://github.com/aklivity/zilla/tree/1.1.9) (2026-04-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.1.8...1.1.9)

**Merged pull requests:**

- test\(engine\): add store operation drivers to TestBinding [\#1732](https://github.com/aklivity/zilla/pull/1732) ([akrambek](https://github.com/akrambek))

## [1.1.8](https://github.com/aklivity/zilla/tree/1.1.8) (2026-04-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.1.7...1.1.8)

**Merged pull requests:**

- feat\(binding-mcp\): implement mcp · server binding [\#1705](https://github.com/aklivity/zilla/pull/1705) ([jfallows](https://github.com/jfallows))
- fix\(ci\): wrap teardown if-expression entirely in ${{ }} [\#1729](https://github.com/aklivity/zilla/pull/1729) ([jfallows](https://github.com/jfallows))
- Enhance metric resolution for raw protocol bindings [\#1730](https://github.com/aklivity/zilla/pull/1730) ([akrambek](https://github.com/akrambek))

## [1.1.7](https://github.com/aklivity/zilla/tree/1.1.7) (2026-04-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.1.6...1.1.7)

**Closed issues:**

- `SafeBuffer`: `AtomicBuffer` implementation backed by `MemorySegment` [\#1719](https://github.com/aklivity/zilla/issues/1719) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Support configuration syntax for metrics with arbitrary attributes [\#1696](https://github.com/aklivity/zilla/pull/1696) ([akrambek](https://github.com/akrambek))
- feat\(engine\): add SafeBuffer backed by MemorySegment FFM API [\#1720](https://github.com/aklivity/zilla/pull/1720) ([jfallows](https://github.com/jfallows))
- Enhance metric resolution for raw protocol bindings [\#1725](https://github.com/aklivity/zilla/pull/1725) ([akrambek](https://github.com/akrambek))

## [1.1.6](https://github.com/aklivity/zilla/tree/1.1.6) (2026-04-08)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.1.5...1.1.6)

**Implemented enhancements:**

- engine: add Store concept \(Store, StoreContext, StoreHandler, StoreFactorySpi\) [\#1666](https://github.com/aklivity/zilla/issues/1666) ([jfallows](https://github.com/jfallows))
- store-memory: implement in-process memory store [\#1667](https://github.com/aklivity/zilla/issues/1667) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- feat\(engine\): add Store concept \(Store, StoreContext, StoreHandler, StoreFactorySpi\) [\#1707](https://github.com/aklivity/zilla/pull/1707) ([jfallows](https://github.com/jfallows))
- feat\(store-memory\): implement in-process memory store [\#1710](https://github.com/aklivity/zilla/pull/1710) ([jfallows](https://github.com/jfallows))

## [1.1.5](https://github.com/aklivity/zilla/tree/1.1.5) (2026-04-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.1.4...1.1.5)

**Fixed bugs:**

- OTLP exporter crashes with JsonParsingException when event message contains double quotes [\#1703](https://github.com/aklivity/zilla/issues/1703) ([ankitk-me](https://github.com/ankitk-me))

**Merged pull requests:**

- OTLP exporter to handle event message with special character [\#1704](https://github.com/aklivity/zilla/pull/1704) ([ankitk-me](https://github.com/ankitk-me))

## [1.1.4](https://github.com/aklivity/zilla/tree/1.1.4) (2026-04-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.11...1.1.4)

**Merged pull requests:**

- Bump eclipse-temurin from 21-alpine to 25-alpine in /cloud/docker-image/src/main/docker [\#1574](https://github.com/aklivity/zilla/pull/1574) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.commons:commons-lang3 from 3.17.0 to 3.18.0 in /incubator/command-dump [\#1617](https://github.com/aklivity/zilla/pull/1617) ([dependabot[bot]](https://github.com/apps/dependabot))
- Upgrade to JDK 25 and Agrona 2.4.0 [\#1665](https://github.com/aklivity/zilla/pull/1665) ([jfallows](https://github.com/jfallows))

## [1.0.11](https://github.com/aklivity/zilla/tree/1.0.11) (2026-04-03)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.10...1.0.11)

**Closed issues:**

- Support `zilla start ... --diagnostics-directory` to capture engine state [\#1663](https://github.com/aklivity/zilla/issues/1663) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Fix tls handshake timeout description [\#1691](https://github.com/aklivity/zilla/pull/1691) ([jfallows](https://github.com/jfallows))
- Support capturing engine diagnostics [\#1692](https://github.com/aklivity/zilla/pull/1692) ([ankitk-me](https://github.com/ankitk-me))
- Fix event log cleanup [\#1693](https://github.com/aklivity/zilla/pull/1693) ([jfallows](https://github.com/jfallows))
- Fix kafka cache segment cleanup [\#1694](https://github.com/aklivity/zilla/pull/1694) ([jfallows](https://github.com/jfallows))
- omit examples without test script [\#1695](https://github.com/aklivity/zilla/pull/1695) ([ankitk-me](https://github.com/ankitk-me))

## [1.0.10](https://github.com/aklivity/zilla/tree/1.0.10) (2026-03-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.9...1.0.10)

**Closed issues:**

- Logging strategy for auth errors in `kafka` client binding [\#1662](https://github.com/aklivity/zilla/issues/1662) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- logging for auth errors in kafka client binding [\#1664](https://github.com/aklivity/zilla/pull/1664) ([ankitk-me](https://github.com/ankitk-me))

## [1.0.9](https://github.com/aklivity/zilla/tree/1.0.9) (2026-03-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.7...1.0.9)

**Merged pull requests:**

- Fix connection leak in http1 client [\#1661](https://github.com/aklivity/zilla/pull/1661) ([akrambek](https://github.com/akrambek))

## [1.0.7](https://github.com/aklivity/zilla/tree/1.0.7) (2026-03-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.6...1.0.7)

**Merged pull requests:**

- Fix HTTP/1.1 keep-alive stall after 400 error response reset [\#1658](https://github.com/aklivity/zilla/pull/1658) ([akrambek](https://github.com/akrambek))
- Fix Kafka produce idle timeout [\#1659](https://github.com/aklivity/zilla/pull/1659) ([jfallows](https://github.com/jfallows))

## [1.0.6](https://github.com/aklivity/zilla/tree/1.0.6) (2026-03-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.4...1.0.6)

**Merged pull requests:**

- Enhance dump command to support explicit initial offsets [\#1654](https://github.com/aklivity/zilla/pull/1654) ([jfallows](https://github.com/jfallows))
- Ensure dump command can advance beyond padding to reach last record [\#1655](https://github.com/aklivity/zilla/pull/1655) ([jfallows](https://github.com/jfallows))
- Reset http 1.1 response when request is reset [\#1656](https://github.com/aklivity/zilla/pull/1656) ([jfallows](https://github.com/jfallows))

## [1.0.4](https://github.com/aklivity/zilla/tree/1.0.4) (2026-03-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.3...1.0.4)

**Merged pull requests:**

- Update README.md [\#1650](https://github.com/aklivity/zilla/pull/1650) ([llukyanov](https://github.com/llukyanov))
- Fix NPE in KafkaClientDescribeFactory when broker returns partial configs [\#1652](https://github.com/aklivity/zilla/pull/1652) ([akrambek](https://github.com/akrambek))
- Fix HTTP 1.1 request flow control sequence [\#1653](https://github.com/aklivity/zilla/pull/1653) ([jfallows](https://github.com/jfallows))

## [1.0.3](https://github.com/aklivity/zilla/tree/1.0.3) (2026-03-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.2...1.0.3)

**Merged pull requests:**

- Fix HTTP 1.1 server binding flow control for GET echo [\#1651](https://github.com/aklivity/zilla/pull/1651) ([jfallows](https://github.com/jfallows))

## [1.0.2](https://github.com/aklivity/zilla/tree/1.0.2) (2026-03-06)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.1...1.0.2)

**Merged pull requests:**

- fix null binding during connection accept [\#1649](https://github.com/aklivity/zilla/pull/1649) ([ankitk-me](https://github.com/ankitk-me))

## [1.0.1](https://github.com/aklivity/zilla/tree/1.0.1) (2026-03-06)

[Full Changelog](https://github.com/aklivity/zilla/compare/1.0.0...1.0.1)

**Fixed bugs:**

- Engine can trigger an `IllegalStateException` during shutdown [\#1646](https://github.com/aklivity/zilla/issues/1646) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- add example `sse.kafka.fanout.filter` [\#1640](https://github.com/aklivity/zilla/pull/1640) ([ankitk-me](https://github.com/ankitk-me))
- Avoid receiving new TCP connections during engine shutdown [\#1647](https://github.com/aklivity/zilla/pull/1647) ([jfallows](https://github.com/jfallows))
- Prevent potential NPE for rejected HTTP 1.1 request with content [\#1648](https://github.com/aklivity/zilla/pull/1648) ([jfallows](https://github.com/jfallows))

## [1.0.0](https://github.com/aklivity/zilla/tree/1.0.0) (2026-02-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.182...1.0.0)

**Merged pull requests:**

- fix link checker github action [\#1641](https://github.com/aklivity/zilla/pull/1641) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.182](https://github.com/aklivity/zilla/tree/0.9.182) (2026-02-06)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.181...0.9.182)

**Closed issues:**

- Support `max-idle-time` for `sse` `server` binding [\#1638](https://github.com/aklivity/zilla/issues/1638) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Support sse server max idle time [\#1639](https://github.com/aklivity/zilla/pull/1639) ([jfallows](https://github.com/jfallows))

## [0.9.181](https://github.com/aklivity/zilla/tree/0.9.181) (2026-01-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.180...0.9.181)

**Merged pull requests:**

- Bump actions/setup-java from 4 to 5 [\#1541](https://github.com/aklivity/zilla/pull/1541) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/checkout from 5 to 6 [\#1619](https://github.com/aklivity/zilla/pull/1619) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/cache from 4 to 5 [\#1621](https://github.com/aklivity/zilla/pull/1621) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/upload-artifact from 5 to 6 [\#1624](https://github.com/aklivity/zilla/pull/1624) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump alpine from 3.23.0 to 3.23.2 in /cloud/docker-image/src/main/docker [\#1625](https://github.com/aklivity/zilla/pull/1625) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump ubuntu from jammy-20251013 to jammy-20260109 in /cloud/docker-image/src/main/docker [\#1631](https://github.com/aklivity/zilla/pull/1631) ([dependabot[bot]](https://github.com/apps/dependabot))
- Push CHANGELOG commit to develop [\#1634](https://github.com/aklivity/zilla/pull/1634) ([jfallows](https://github.com/jfallows))

## [0.9.180](https://github.com/aklivity/zilla/tree/0.9.180) (2026-01-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.179...0.9.180)

**Fixed bugs:**

- Kafka DescribeConfigs uses API version 0 against brokers \(Kafka 4.1\) that require v1+ [\#1614](https://github.com/aklivity/zilla/issues/1614) ([MultiStorm](https://github.com/MultiStorm))

**Merged pull requests:**

- upgrade DescribeConfigs API `v1` [\#1629](https://github.com/aklivity/zilla/pull/1629) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.179](https://github.com/aklivity/zilla/tree/0.9.179) (2026-01-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.178...0.9.179)

**Merged pull requests:**

- Dynamic context support for resolvers + change nonce generation [\#1632](https://github.com/aklivity/zilla/pull/1632) ([bmaidics](https://github.com/bmaidics))

## [0.9.178](https://github.com/aklivity/zilla/tree/0.9.178) (2026-01-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.177...0.9.178)

**Merged pull requests:**

- Fix config watcher to skip unregistered watch keys [\#1630](https://github.com/aklivity/zilla/pull/1630) ([akrambek](https://github.com/akrambek))

## [0.9.177](https://github.com/aklivity/zilla/tree/0.9.177) (2025-12-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.176...0.9.177)

**Merged pull requests:**

- Fix integer overflow in http client [\#1627](https://github.com/aklivity/zilla/pull/1627) ([akrambek](https://github.com/akrambek))

## [0.9.176](https://github.com/aklivity/zilla/tree/0.9.176) (2025-12-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.175...0.9.176)

**Merged pull requests:**

- Support populating identity override without guarded route [\#1600](https://github.com/aklivity/zilla/pull/1600) ([nageshwaravijay1117](https://github.com/nageshwaravijay1117))
- Correctly send http end when no upgrade header [\#1626](https://github.com/aklivity/zilla/pull/1626) ([bmaidics](https://github.com/bmaidics))

## [0.9.175](https://github.com/aklivity/zilla/tree/0.9.175) (2025-12-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.174...0.9.175)

**Merged pull requests:**

- Include blocking select with timeout in doWork loops [\#1620](https://github.com/aklivity/zilla/pull/1620) ([jfallows](https://github.com/jfallows))

## [0.9.174](https://github.com/aklivity/zilla/tree/0.9.174) (2025-12-09)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.173...0.9.174)

**Closed issues:**

- Support `model-avro` validation where catalog expects prefix with schema id [\#1611](https://github.com/aklivity/zilla/issues/1611) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Bump actions/checkout from 4 to 5 [\#1535](https://github.com/aklivity/zilla/pull/1535) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/upload-artifact from 4 to 5 [\#1597](https://github.com/aklivity/zilla/pull/1597) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump lycheeverse/lychee-action from 2.4.1 to 2.7.0 [\#1601](https://github.com/aklivity/zilla/pull/1601) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump ubuntu from jammy-20250404 to jammy-20251013 in /cloud/docker-image/src/main/docker [\#1607](https://github.com/aklivity/zilla/pull/1607) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump alpine from 3.21.3 to 3.23.0 in /cloud/docker-image/src/main/docker [\#1613](https://github.com/aklivity/zilla/pull/1613) ([dependabot[bot]](https://github.com/apps/dependabot))
- `avro` validator implementation with `encoded` strategy [\#1615](https://github.com/aklivity/zilla/pull/1615) ([ankitk-me](https://github.com/ankitk-me))
- Fix testcontainers by upgrading [\#1616](https://github.com/aklivity/zilla/pull/1616) ([jfallows](https://github.com/jfallows))
- Fix command dump pcap timestamps [\#1618](https://github.com/aklivity/zilla/pull/1618) ([jfallows](https://github.com/jfallows))

## [0.9.173](https://github.com/aklivity/zilla/tree/0.9.173) (2025-11-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.172...0.9.173)

**Merged pull requests:**

- Handle abort aborted race condition in IT scripts [\#1608](https://github.com/aklivity/zilla/pull/1608) ([jfallows](https://github.com/jfallows))

## [0.9.172](https://github.com/aklivity/zilla/tree/0.9.172) (2025-11-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.171...0.9.172)

**Closed issues:**

- Support `model-json` validation where catalog expects prefix with schema id [\#1605](https://github.com/aklivity/zilla/issues/1605) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Support `encoded` schema ID for validator [\#1604](https://github.com/aklivity/zilla/pull/1604) ([ankitk-me](https://github.com/ankitk-me))
- Support catalog handler validate [\#1606](https://github.com/aklivity/zilla/pull/1606) ([jfallows](https://github.com/jfallows))

## [0.9.171](https://github.com/aklivity/zilla/tree/0.9.171) (2025-10-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.170...0.9.171)

**Merged pull requests:**

- Enhance engine extensions API [\#1599](https://github.com/aklivity/zilla/pull/1599) ([jfallows](https://github.com/jfallows))

## [0.9.170](https://github.com/aklivity/zilla/tree/0.9.170) (2025-10-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.169...0.9.170)

**Merged pull requests:**

- Fix filesystem vault in local config [\#1598](https://github.com/aklivity/zilla/pull/1598) ([jfallows](https://github.com/jfallows))

## [0.9.169](https://github.com/aklivity/zilla/tree/0.9.169) (2025-10-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.168...0.9.169)

**Merged pull requests:**

- update `sse.kafka.fanout` to support `jwt` [\#1593](https://github.com/aklivity/zilla/pull/1593) ([ankitk-me](https://github.com/ankitk-me))
- Add basic auth username/password accessor to HTTP server [\#1596](https://github.com/aklivity/zilla/pull/1596) ([bmaidics](https://github.com/bmaidics))

## [0.9.168](https://github.com/aklivity/zilla/tree/0.9.168) (2025-10-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.167...0.9.168)

**Merged pull requests:**

- Fix TestGuardHandler when no attributes are specified [\#1592](https://github.com/aklivity/zilla/pull/1592) ([bmaidics](https://github.com/bmaidics))

## [0.9.167](https://github.com/aklivity/zilla/tree/0.9.167) (2025-10-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.166...0.9.167)

**Merged pull requests:**

- support multiple replacer with dynamic pattern [\#1590](https://github.com/aklivity/zilla/pull/1590) ([ankitk-me](https://github.com/ankitk-me))
- Fix http 1.1 client upgrade then close [\#1591](https://github.com/aklivity/zilla/pull/1591) ([jfallows](https://github.com/jfallows))

## [0.9.166](https://github.com/aklivity/zilla/tree/0.9.166) (2025-10-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.165...0.9.166)

**Merged pull requests:**

- Fix http 1.1 client decode of response empty reason phrase [\#1589](https://github.com/aklivity/zilla/pull/1589) ([jfallows](https://github.com/jfallows))

## [0.9.165](https://github.com/aklivity/zilla/tree/0.9.165) (2025-10-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.164...0.9.165)

**Merged pull requests:**

- Protobuf2 Model Support in Zilla [\#1548](https://github.com/aklivity/zilla/pull/1548) ([nageshwaravijay1117](https://github.com/nageshwaravijay1117))
- Fix http filesystem module-info [\#1588](https://github.com/aklivity/zilla/pull/1588) ([jfallows](https://github.com/jfallows))

## [0.9.164](https://github.com/aklivity/zilla/tree/0.9.164) (2025-10-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.162...0.9.164)

**Merged pull requests:**

- Append local config if configured [\#1583](https://github.com/aklivity/zilla/pull/1583) ([bmaidics](https://github.com/bmaidics))

## [0.9.162](https://github.com/aklivity/zilla/tree/0.9.162) (2025-10-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.161...0.9.162)

**Merged pull requests:**

- validate attributes in test binding [\#1587](https://github.com/aklivity/zilla/pull/1587) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.161](https://github.com/aklivity/zilla/tree/0.9.161) (2025-10-09)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.159...0.9.161)

**Closed issues:**

- Support guard attributes [\#1578](https://github.com/aklivity/zilla/issues/1578) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Support guard attributes [\#1582](https://github.com/aklivity/zilla/pull/1582) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.159](https://github.com/aklivity/zilla/tree/0.9.159) (2025-09-30)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.158...0.9.159)

**Closed issues:**

- Support specifying credentials for OTLP exporter [\#1556](https://github.com/aklivity/zilla/issues/1556) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Migrate engine started and stopped to telemetry events [\#1555](https://github.com/aklivity/zilla/pull/1555) ([bmaidics](https://github.com/bmaidics))
- fix schema for catalog modules [\#1557](https://github.com/aklivity/zilla/pull/1557) ([ankitk-me](https://github.com/ankitk-me))
- support secure `otlp` exporter [\#1579](https://github.com/aklivity/zilla/pull/1579) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.158](https://github.com/aklivity/zilla/tree/0.9.158) (2025-09-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.157...0.9.158)

**Merged pull requests:**

- Add engine authorization config [\#1547](https://github.com/aklivity/zilla/pull/1547) ([bmaidics](https://github.com/bmaidics))

## [0.9.157](https://github.com/aklivity/zilla/tree/0.9.157) (2025-09-06)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.156...0.9.157)

**Merged pull requests:**

- Handle slow TCP client handshake [\#1546](https://github.com/aklivity/zilla/pull/1546) ([jfallows](https://github.com/jfallows))
- Default worker capacity calculation fix [\#1550](https://github.com/aklivity/zilla/pull/1550) ([bmaidics](https://github.com/bmaidics))
- Use ephemeral name as sender binding [\#1551](https://github.com/aklivity/zilla/pull/1551) ([jfallows](https://github.com/jfallows))

## [0.9.156](https://github.com/aklivity/zilla/tree/0.9.156) (2025-08-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.155...0.9.156)

**Merged pull requests:**

- Add convenience setter for string field in union or struct builder [\#1545](https://github.com/aklivity/zilla/pull/1545) ([jfallows](https://github.com/jfallows))

## [0.9.155](https://github.com/aklivity/zilla/tree/0.9.155) (2025-08-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.154...0.9.155)

**Fixed bugs:**

- Using Maps and Arrays with Avro, cause app to crash due to unsafe memory access operation [\#1525](https://github.com/aklivity/zilla/issues/1525) ([Yoni-Weisberg](https://github.com/Yoni-Weisberg))

**Closed issues:**

- Support PATCH as HTTP method [\#1419](https://github.com/aklivity/zilla/issues/1419) ([Evox145](https://github.com/Evox145))
- Support JWT identity claims for dynamic `http-kafka` and `sse-kafka` topic routing [\#1511](https://github.com/aklivity/zilla/issues/1511) ([hoegertn](https://github.com/hoegertn))

**Merged pull requests:**

- dynamic topic routing based on JWT identity claims [\#1524](https://github.com/aklivity/zilla/pull/1524) ([ankitk-me](https://github.com/ankitk-me))
- config: support padding for arrays and maps in `JSON` view [\#1527](https://github.com/aklivity/zilla/pull/1527) ([ankitk-me](https://github.com/ankitk-me))
- Support http patch [\#1530](https://github.com/aklivity/zilla/pull/1530) ([Qianyu2021](https://github.com/Qianyu2021))
- Use piped input and output stream [\#1531](https://github.com/aklivity/zilla/pull/1531) ([jfallows](https://github.com/jfallows))
- fix `mqtt` & `mqtt-kafka` topic pattern to allow `.` [\#1532](https://github.com/aklivity/zilla/pull/1532) ([ankitk-me](https://github.com/ankitk-me))
- Disable `ws.reflect` example [\#1536](https://github.com/aklivity/zilla/pull/1536) ([jfallows](https://github.com/jfallows))
- Disable tcp.reflect example [\#1540](https://github.com/aklivity/zilla/pull/1540) ([jfallows](https://github.com/jfallows))
- Support remote\_client binding kind [\#1542](https://github.com/aklivity/zilla/pull/1542) ([jfallows](https://github.com/jfallows))

## [0.9.154](https://github.com/aklivity/zilla/tree/0.9.154) (2025-07-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.153...0.9.154)

**Merged pull requests:**

- Support trustcacerts in test binding vault assertions [\#1522](https://github.com/aklivity/zilla/pull/1522) ([jfallows](https://github.com/jfallows))

## [0.9.153](https://github.com/aklivity/zilla/tree/0.9.153) (2025-07-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.152...0.9.153)

**Merged pull requests:**

- Log worker usage instead of utilization [\#1515](https://github.com/aklivity/zilla/pull/1515) ([akrambek](https://github.com/akrambek))

## [0.9.152](https://github.com/aklivity/zilla/tree/0.9.152) (2025-07-08)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.151...0.9.152)

**Fixed bugs:**

- Zilla can appear unhealthy to tcp health check mechanisms at full engine worker utilization [\#1495](https://github.com/aklivity/zilla/issues/1495) ([jfallows](https://github.com/jfallows))

**Closed issues:**

- Validate `$ref` in AsyncAPI Schema [\#1466](https://github.com/aklivity/zilla/issues/1466) ([ankitk-me](https://github.com/ankitk-me))
- Validate `$ref` in OpenAPI Schema [\#1467](https://github.com/aklivity/zilla/issues/1467) ([ankitk-me](https://github.com/ankitk-me))

**Merged pull requests:**

- fix: Schema declaration in message & record unresolved `$ref` [\#1488](https://github.com/aklivity/zilla/pull/1488) ([ankitk-me](https://github.com/ankitk-me))
- Support load balancer health check even at full capacity [\#1509](https://github.com/aklivity/zilla/pull/1509) ([akrambek](https://github.com/akrambek))
- record unresolved $ref in OpenAPI [\#1510](https://github.com/aklivity/zilla/pull/1510) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.151](https://github.com/aklivity/zilla/tree/0.9.151) (2025-06-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.150...0.9.151)

**Merged pull requests:**

- Add CRL checks [\#1474](https://github.com/aklivity/zilla/pull/1474) ([bmaidics](https://github.com/bmaidics))

## [0.9.150](https://github.com/aklivity/zilla/tree/0.9.150) (2025-06-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.149...0.9.150)

**Merged pull requests:**

- Ensure matchCN thread safety when engine task parallelism > 1 [\#1503](https://github.com/aklivity/zilla/pull/1503) ([jfallows](https://github.com/jfallows))
- Enhance stdout exporter to include trace id [\#1504](https://github.com/aklivity/zilla/pull/1504) ([jfallows](https://github.com/jfallows))
- Add specific event kind for TLS handshake timeout [\#1505](https://github.com/aklivity/zilla/pull/1505) ([jfallows](https://github.com/jfallows))

## [0.9.149](https://github.com/aklivity/zilla/tree/0.9.149) (2025-06-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.148...0.9.149)

**Merged pull requests:**

- Use GaugesLayout for engine worker capacity metric [\#1496](https://github.com/aklivity/zilla/pull/1496) ([jfallows](https://github.com/jfallows))
- Add `engine.workers.capacity` metric and rename other `engine.worker` metrics to `engine.workers` [\#1499](https://github.com/aklivity/zilla/pull/1499) ([jfallows](https://github.com/jfallows))
- Support `engine.worker.capacity.unbounded` configuration [\#1500](https://github.com/aklivity/zilla/pull/1500) ([jfallows](https://github.com/jfallows))
- Report exceptions on zilla start, such as invalid power of 2 for config [\#1501](https://github.com/aklivity/zilla/pull/1501) ([jfallows](https://github.com/jfallows))

## [0.9.148](https://github.com/aklivity/zilla/tree/0.9.148) (2025-06-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.147...0.9.148)

**Merged pull requests:**

- Cancel pending TLS handshake task when streams closed in both directions [\#1497](https://github.com/aklivity/zilla/pull/1497) ([jfallows](https://github.com/jfallows))

## [0.9.147](https://github.com/aklivity/zilla/tree/0.9.147) (2025-06-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.146...0.9.147)

**Merged pull requests:**

- Fix typo increase counter on connection close instead of decreasing it [\#1494](https://github.com/aklivity/zilla/pull/1494) ([akrambek](https://github.com/akrambek))

## [0.9.146](https://github.com/aklivity/zilla/tree/0.9.146) (2025-06-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.145...0.9.146)

**Closed issues:**

- Custom Role Claim Support in Zilla JWT Validation [\#1476](https://github.com/aklivity/zilla/issues/1476) ([JVaghela-Fintech](https://github.com/JVaghela-Fintech))

**Merged pull requests:**

- Update README.md [\#1464](https://github.com/aklivity/zilla/pull/1464) ([anujkarn002](https://github.com/anujkarn002))
- Support custom role claim [\#1492](https://github.com/aklivity/zilla/pull/1492) ([akrambek](https://github.com/akrambek))
- Test missing namespace and binding name [\#1493](https://github.com/aklivity/zilla/pull/1493) ([akrambek](https://github.com/akrambek))

## [0.9.145](https://github.com/aklivity/zilla/tree/0.9.145) (2025-06-05)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.144...0.9.145)

**Merged pull requests:**

- Bump lycheeverse/lychee-action from 2.3.0 to 2.4.1 [\#1473](https://github.com/aklivity/zilla/pull/1473) ([dependabot[bot]](https://github.com/apps/dependabot))
- Support conditional Thread.interrupt\(\) on exception in EngineRule [\#1489](https://github.com/aklivity/zilla/pull/1489) ([jfallows](https://github.com/jfallows))

## [0.9.144](https://github.com/aklivity/zilla/tree/0.9.144) (2025-05-30)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.143...0.9.144)

**Closed issues:**

- Support Overriding `correlation-id` via AsyncAPI Specification [\#1471](https://github.com/aklivity/zilla/issues/1471) ([ankitk-me](https://github.com/ankitk-me))

**Merged pull requests:**

- Overriding `correlation-id` via AsyncAPI Specification [\#1482](https://github.com/aklivity/zilla/pull/1482) ([ankitk-me](https://github.com/ankitk-me))
- Enhance tcp unbinding spec test [\#1483](https://github.com/aklivity/zilla/pull/1483) ([akrambek](https://github.com/akrambek))
- Remove unused gitbook files [\#1484](https://github.com/aklivity/zilla/pull/1484) ([jfallows](https://github.com/jfallows))
- Use flyweights for TLS proxy decoder [\#1485](https://github.com/aklivity/zilla/pull/1485) ([jfallows](https://github.com/jfallows))

## [0.9.143](https://github.com/aklivity/zilla/tree/0.9.143) (2025-05-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.142...0.9.143)

**Closed issues:**

- Collect exportable engine metric representing worker capacity usage for auto scaling [\#1465](https://github.com/aklivity/zilla/issues/1465) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Engine worker metrics support [\#1470](https://github.com/aklivity/zilla/pull/1470) ([akrambek](https://github.com/akrambek))

## [0.9.142](https://github.com/aklivity/zilla/tree/0.9.142) (2025-05-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.141...0.9.142)

**Merged pull requests:**

- Fix large message hashing in http-kafka [\#1480](https://github.com/aklivity/zilla/pull/1480) ([bmaidics](https://github.com/bmaidics))

## [0.9.141](https://github.com/aklivity/zilla/tree/0.9.141) (2025-05-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.140...0.9.141)

**Merged pull requests:**

- support dynamic guarded route [\#1478](https://github.com/aklivity/zilla/pull/1478) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.140](https://github.com/aklivity/zilla/tree/0.9.140) (2025-05-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.139...0.9.140)

**Implemented enhancements:**

- Persist gRPC error messages [\#988](https://github.com/aklivity/zilla/issues/988) ([hedhyw](https://github.com/hedhyw))

**Fixed bugs:**

- `curl` fails to connect to `zilla` in `asyncapi.sse.proxy` and `asyncapi.sse.kafka.proxy` examples [\#1417](https://github.com/aklivity/zilla/issues/1417) ([ankitk-me](https://github.com/ankitk-me))

**Merged pull requests:**

- added support for validation in asyncapi.sse flow & example fix [\#1462](https://github.com/aklivity/zilla/pull/1462) ([ankitk-me](https://github.com/ankitk-me))
- fix `asyncapi.sse.kafka.proxy` example & enable `test` [\#1463](https://github.com/aklivity/zilla/pull/1463) ([ankitk-me](https://github.com/ankitk-me))
- Persist gRPC custom error messages [\#1468](https://github.com/aklivity/zilla/pull/1468) ([ankitk-me](https://github.com/ankitk-me))
- Remove `-e` option from `echo` & add `--exclude-internal` in `list` [\#1469](https://github.com/aklivity/zilla/pull/1469) ([ankitk-me](https://github.com/ankitk-me))
- support `guarded` `routes` in TestBindingFactory [\#1477](https://github.com/aklivity/zilla/pull/1477) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.139](https://github.com/aklivity/zilla/tree/0.9.139) (2025-04-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.138...0.9.139)

**Fixed bugs:**

- MQTT client not receiving events in `asyncapi.mqtt.proxy` Example [\#1416](https://github.com/aklivity/zilla/issues/1416) ([ankitk-me](https://github.com/ankitk-me))

**Merged pull requests:**

- Fix `asyncapi.mqtt.proxy` example & enable test [\#1460](https://github.com/aklivity/zilla/pull/1460) ([ankitk-me](https://github.com/ankitk-me))
- Ensure SslEngine delegated task completes with signal … [\#1461](https://github.com/aklivity/zilla/pull/1461) ([jfallows](https://github.com/jfallows))

## [0.9.138](https://github.com/aklivity/zilla/tree/0.9.138) (2025-04-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.137...0.9.138)

**Merged pull requests:**

- Simplify TLS decode loop by inlining decodeHandshakeNeedTask [\#1459](https://github.com/aklivity/zilla/pull/1459) ([jfallows](https://github.com/jfallows))

## [0.9.137](https://github.com/aklivity/zilla/tree/0.9.137) (2025-04-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.136...0.9.137)

**Merged pull requests:**

- Use long not int when calculating ack max [\#1458](https://github.com/aklivity/zilla/pull/1458) ([jfallows](https://github.com/jfallows))

## [0.9.136](https://github.com/aklivity/zilla/tree/0.9.136) (2025-04-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.135...0.9.136)

**Closed issues:**

- Support mqtt topics with path parameters required to match guarded identity [\#1382](https://github.com/aklivity/zilla/issues/1382) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- MQTT topics with path parameters required to match guarded identity [\#1387](https://github.com/aklivity/zilla/pull/1387) ([epieffe](https://github.com/epieffe))
- Bump ubuntu from jammy-20250126 to jammy-20250404 in /cloud/docker-image/src/main/docker [\#1453](https://github.com/aklivity/zilla/pull/1453) ([dependabot[bot]](https://github.com/apps/dependabot))
- Use distinct idle strategy instance per agent [\#1457](https://github.com/aklivity/zilla/pull/1457) ([jfallows](https://github.com/jfallows))

## [0.9.135](https://github.com/aklivity/zilla/tree/0.9.135) (2025-04-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.134...0.9.135)

**Fixed bugs:**

- Problem with oneOf ref in schema [\#1418](https://github.com/aklivity/zilla/issues/1418) ([Evox145](https://github.com/Evox145))
- Miscalculating default worker capacity [\#1456](https://github.com/aklivity/zilla/issues/1456) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- enable Incubator features : zilla examples [\#1451](https://github.com/aklivity/zilla/pull/1451) ([ankitk-me](https://github.com/ankitk-me))
- Add `test.sh` for `amqp.reflect` & skip fix required examples [\#1452](https://github.com/aklivity/zilla/pull/1452) ([ankitk-me](https://github.com/ankitk-me))
- support `oneOf` `allOf` & `anyOf` schema in `binding-openapi` & `binding-asyncapi` [\#1454](https://github.com/aklivity/zilla/pull/1454) ([ankitk-me](https://github.com/ankitk-me))
- Fix default worker capacity [\#1455](https://github.com/aklivity/zilla/pull/1455) ([akrambek](https://github.com/akrambek))

## [0.9.134](https://github.com/aklivity/zilla/tree/0.9.134) (2025-04-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.133...0.9.134)

**Merged pull requests:**

- Bump alpine from 3.21.2 to 3.21.3 in /cloud/docker-image/src/main/docker [\#1404](https://github.com/aklivity/zilla/pull/1404) ([dependabot[bot]](https://github.com/apps/dependabot))
- Set TCP flags appropriately to avoid Wireshark TCP dissector errors [\#1442](https://github.com/aklivity/zilla/pull/1442) ([jfallows](https://github.com/jfallows))
- Set default buffer slot capacity to 32K [\#1449](https://github.com/aklivity/zilla/pull/1449) ([akrambek](https://github.com/akrambek))

## [0.9.133](https://github.com/aklivity/zilla/tree/0.9.133) (2025-04-03)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.132...0.9.133)

**Implemented enhancements:**

- Add a GH action to build the head of `develop` and run example tests [\#1263](https://github.com/aklivity/zilla/issues/1263) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- Update workflow to run test on PRs & use `develop-SNAPSHOT` image [\#1436](https://github.com/aklivity/zilla/pull/1436) ([ankitk-me](https://github.com/ankitk-me))
- shade jose4j in guard-jwt [\#1447](https://github.com/aklivity/zilla/pull/1447) ([ankitk-me](https://github.com/ankitk-me))
- Enforce worker capacity limit across both client and server tcp connections [\#1448](https://github.com/aklivity/zilla/pull/1448) ([akrambek](https://github.com/akrambek))

## [0.9.132](https://github.com/aklivity/zilla/tree/0.9.132) (2025-04-01)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.131...0.9.132)

**Merged pull requests:**

- Increase task.parallelism by default to match workers [\#1445](https://github.com/aklivity/zilla/pull/1445) ([akrambek](https://github.com/akrambek))
- Bind server socket per core [\#1446](https://github.com/aklivity/zilla/pull/1446) ([akrambek](https://github.com/akrambek))

## [0.9.131](https://github.com/aklivity/zilla/tree/0.9.131) (2025-03-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.130...0.9.131)

**Closed issues:**

- Auto discover available host resources and dynamically set internal limits [\#984](https://github.com/aklivity/zilla/issues/984) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- Auto discover available host resources and dynamically set internal limits [\#1438](https://github.com/aklivity/zilla/pull/1438) ([akrambek](https://github.com/akrambek))

## [0.9.130](https://github.com/aklivity/zilla/tree/0.9.130) (2025-03-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.129...0.9.130)

**Merged pull requests:**

- Verify varuint32\(n\) unpadded size [\#1440](https://github.com/aklivity/zilla/pull/1440) ([jfallows](https://github.com/jfallows))

## [0.9.129](https://github.com/aklivity/zilla/tree/0.9.129) (2025-03-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.128...0.9.129)

**Merged pull requests:**

- Fix failing test when JVM default locale is not US [\#1359](https://github.com/aklivity/zilla/pull/1359) ([epieffe](https://github.com/epieffe))
- Handle reserved budget in HTTP end frame [\#1430](https://github.com/aklivity/zilla/pull/1430) ([akrambek](https://github.com/akrambek))
- rename: `sse.jwt` example to `sse.proxy.jwt` [\#1435](https://github.com/aklivity/zilla/pull/1435) ([ankitk-me](https://github.com/ankitk-me))
- Enhance core test functions to support padded length varstring [\#1439](https://github.com/aklivity/zilla/pull/1439) ([jfallows](https://github.com/jfallows))

## [0.9.128](https://github.com/aklivity/zilla/tree/0.9.128) (2025-03-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.127...0.9.128)

**Fixed bugs:**

- Uploading more than 40k size file into filesystem doesn't get fully uploaded [\#1420](https://github.com/aklivity/zilla/issues/1420) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- fix: TLS Client debug logging [\#1422](https://github.com/aklivity/zilla/pull/1422) ([ankitk-me](https://github.com/ankitk-me))
- Ignore ztable and zmaterialized views from relavent non z show commands [\#1424](https://github.com/aklivity/zilla/pull/1424) ([akrambek](https://github.com/akrambek))
- Fix data fragmentation in http/1.1 and flowcontrol issue in filesystem [\#1426](https://github.com/aklivity/zilla/pull/1426) ([akrambek](https://github.com/akrambek))
- FLUSH after insert to make SHOW command predictable [\#1427](https://github.com/aklivity/zilla/pull/1427) ([akrambek](https://github.com/akrambek))

## [0.9.127](https://github.com/aklivity/zilla/tree/0.9.127) (2025-03-04)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.126...0.9.127)

**Fixed bugs:**

- zilla crashes with `java.lang.NoClassDefFoundError: com/google/gson/JsonElement` [\#1413](https://github.com/aklivity/zilla/issues/1413) ([ankitk-me](https://github.com/ankitk-me))

**Merged pull requests:**

- fix: NoClassDefFoundError: com/google/gson/JsonElement [\#1414](https://github.com/aklivity/zilla/pull/1414) ([ankitk-me](https://github.com/ankitk-me))
- Allow binding kind proxy to contribute to zilla dump dissector protocol [\#1421](https://github.com/aklivity/zilla/pull/1421) ([jfallows](https://github.com/jfallows))

## [0.9.126](https://github.com/aklivity/zilla/tree/0.9.126) (2025-02-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.125...0.9.126)

**Implemented enhancements:**

- Type boolean possible in schema [\#1408](https://github.com/aklivity/zilla/issues/1408) ([Evox145](https://github.com/Evox145))

**Fixed bugs:**

- Handle double quote when defining the table name in risingwave [\#1379](https://github.com/aklivity/zilla/issues/1379) ([akrambek](https://github.com/akrambek))
- Java Agent Error while sending Data to Open Telemetry Endpoint \(OTEL Endpoint\) [\#1406](https://github.com/aklivity/zilla/issues/1406) ([ankitk-me](https://github.com/ankitk-me))

**Merged pull requests:**

- Bump alpine from 3.21.0 to 3.21.2 in /cloud/docker-image/src/main/docker [\#1367](https://github.com/aklivity/zilla/pull/1367) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump ubuntu from jammy-20240808 to jammy-20250126 in /cloud/docker-image/src/main/docker [\#1393](https://github.com/aklivity/zilla/pull/1393) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump lycheeverse/lychee-action from 2.2.0 to 2.3.0 [\#1397](https://github.com/aklivity/zilla/pull/1397) ([dependabot[bot]](https://github.com/apps/dependabot))
- Append missing system schema to avoid exposing view in show command [\#1398](https://github.com/aklivity/zilla/pull/1398) ([akrambek](https://github.com/akrambek))
- fix: NPE due to empty Inline Catalog [\#1399](https://github.com/aklivity/zilla/pull/1399) ([ankitk-me](https://github.com/ankitk-me))
- fix: mqtt-kafka routing fix [\#1402](https://github.com/aklivity/zilla/pull/1402) ([ankitk-me](https://github.com/ankitk-me))
- fix: MQTT subscribe routing [\#1403](https://github.com/aklivity/zilla/pull/1403) ([ankitk-me](https://github.com/ankitk-me))
- fix: resolveKind flow for composite binding [\#1407](https://github.com/aklivity/zilla/pull/1407) ([ankitk-me](https://github.com/ankitk-me))
- support boolean model [\#1409](https://github.com/aklivity/zilla/pull/1409) ([ankitk-me](https://github.com/ankitk-me))
- Handle TLS Alert.USER\_CANCELED then deferred Alert.CLOSE\_NOTIFY [\#1411](https://github.com/aklivity/zilla/pull/1411) ([jfallows](https://github.com/jfallows))
- Handle double quote in z prefix resources [\#1412](https://github.com/aklivity/zilla/pull/1412) ([akrambek](https://github.com/akrambek))

## [0.9.125](https://github.com/aklivity/zilla/tree/0.9.125) (2025-02-05)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.124...0.9.125)

**Fixed bugs:**

- Schema Retrieval Failure Causes Validation Error in Zilla [\#1391](https://github.com/aklivity/zilla/issues/1391) ([ankitk-me](https://github.com/ankitk-me))
- Zilla Crashes on invalid request payload [\#1394](https://github.com/aklivity/zilla/issues/1394) ([ankitk-me](https://github.com/ankitk-me))

**Closed issues:**

- Support gRPC client streaming to Kafka directly [\#642](https://github.com/aklivity/zilla/issues/642) ([dudo](https://github.com/dudo))

**Merged pull requests:**

- Support gRPC client stream/unary oneway [\#1384](https://github.com/aklivity/zilla/pull/1384) ([ankitk-me](https://github.com/ankitk-me))
- fix: locale-specific formatting due to `MessageFormat.format()` [\#1390](https://github.com/aklivity/zilla/pull/1390) ([ankitk-me](https://github.com/ankitk-me))
- fix: Zilla Crashes on invalid request payload [\#1395](https://github.com/aklivity/zilla/pull/1395) ([ankitk-me](https://github.com/ankitk-me))
- Use `OpenapiView` and `AsyncapiView` to generate composite namespaces [\#1396](https://github.com/aklivity/zilla/pull/1396) ([jfallows](https://github.com/jfallows))

## [0.9.124](https://github.com/aklivity/zilla/tree/0.9.124) (2025-01-30)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.123...0.9.124)

**Merged pull requests:**

- Use `OpenapiView` to generate composite namespaces [\#1388](https://github.com/aklivity/zilla/pull/1388) ([jfallows](https://github.com/jfallows))

## [0.9.123](https://github.com/aklivity/zilla/tree/0.9.123) (2025-01-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.122...0.9.123)

**Implemented enhancements:**

- Allow separate Auth config for Catalog definitions [\#1195](https://github.com/aklivity/zilla/issues/1195) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- Support secure schema access [\#1369](https://github.com/aklivity/zilla/pull/1369) ([ankitk-me](https://github.com/ankitk-me))
- Decode network unconditionally when received window on MQTT session stream [\#1386](https://github.com/aklivity/zilla/pull/1386) ([bmaidics](https://github.com/bmaidics))

## [0.9.122](https://github.com/aklivity/zilla/tree/0.9.122) (2025-01-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.121...0.9.122)

**Fixed bugs:**

- Logging wrong accept url when http proxy is configured [\#1380](https://github.com/aklivity/zilla/issues/1380) ([akrambek](https://github.com/akrambek))

**Closed issues:**

- Unify ZFUNCTION and ZSTREAM into a single concept [\#1376](https://github.com/aklivity/zilla/issues/1376) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- Unify ZFUNCTION and ZSTREAM into a single concept [\#1377](https://github.com/aklivity/zilla/pull/1377) ([akrambek](https://github.com/akrambek))
- Log accepted before overriding the headers [\#1381](https://github.com/aklivity/zilla/pull/1381) ([akrambek](https://github.com/akrambek))
- Support configurable TLS client SNI validation and handle FQDNs … [\#1383](https://github.com/aklivity/zilla/pull/1383) ([jfallows](https://github.com/jfallows))

## [0.9.121](https://github.com/aklivity/zilla/tree/0.9.121) (2025-01-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.120...0.9.121)

**Merged pull requests:**

- Ignore appending allow origin if already present [\#1371](https://github.com/aklivity/zilla/pull/1371) ([akrambek](https://github.com/akrambek))

## [0.9.120](https://github.com/aklivity/zilla/tree/0.9.120) (2025-01-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.119...0.9.120)

**Merged pull requests:**

- Fix checkstyle in TLS Server [\#1373](https://github.com/aklivity/zilla/pull/1373) ([ankitk-me](https://github.com/ankitk-me))
- Make TLS client HTTPS endpoint identification configurable [\#1375](https://github.com/aklivity/zilla/pull/1375) ([jfallows](https://github.com/jfallows))

## [0.9.119](https://github.com/aklivity/zilla/tree/0.9.119) (2025-01-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.118...0.9.119)

**Merged pull requests:**

- Handle CN with spaces [\#1372](https://github.com/aklivity/zilla/pull/1372) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.118](https://github.com/aklivity/zilla/tree/0.9.118) (2025-01-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.117...0.9.118)

**Merged pull requests:**

- Bump lycheeverse/lychee-action from 2.1.0 to 2.2.0 [\#1358](https://github.com/aklivity/zilla/pull/1358) ([dependabot[bot]](https://github.com/apps/dependabot))
- update extract `CN` logic to handle entry with `CN` in any order [\#1368](https://github.com/aklivity/zilla/pull/1368) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.117](https://github.com/aklivity/zilla/tree/0.9.117) (2025-01-09)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.116...0.9.117)

**Merged pull requests:**

- Flush the insert to have immediate effect [\#1365](https://github.com/aklivity/zilla/pull/1365) ([akrambek](https://github.com/akrambek))

## [0.9.116](https://github.com/aklivity/zilla/tree/0.9.116) (2025-01-08)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.115...0.9.116)

**Fixed bugs:**

- Asyncapi kafka header extraction expression don’t match zilla yaml expressions  [\#1138](https://github.com/aklivity/zilla/issues/1138) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- Zfunction and Zstream support [\#1354](https://github.com/aklivity/zilla/pull/1354) ([akrambek](https://github.com/akrambek))
- Support GSS encrypt request decoding as part of psql 14.15 client [\#1361](https://github.com/aklivity/zilla/pull/1361) ([akrambek](https://github.com/akrambek))
- fix: kafka header extraction expression in composite zilla.yaml [\#1362](https://github.com/aklivity/zilla/pull/1362) ([ankitk-me](https://github.com/ankitk-me))
- Minor bug fixes in zstream [\#1364](https://github.com/aklivity/zilla/pull/1364) ([akrambek](https://github.com/akrambek))

## [0.9.115](https://github.com/aklivity/zilla/tree/0.9.115) (2024-12-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.114...0.9.115)

**Merged pull requests:**

- enabling error reporting using `supplyReporter` [\#1348](https://github.com/aklivity/zilla/pull/1348) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.114](https://github.com/aklivity/zilla/tree/0.9.114) (2024-12-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.113...0.9.114)

**Merged pull requests:**

- Update show commands type oid [\#1357](https://github.com/aklivity/zilla/pull/1357) ([akrambek](https://github.com/akrambek))

## [0.9.113](https://github.com/aklivity/zilla/tree/0.9.113) (2024-12-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.112...0.9.113)

**Merged pull requests:**

- Set correct reserved on flush in risingwave [\#1356](https://github.com/aklivity/zilla/pull/1356) ([akrambek](https://github.com/akrambek))

## [0.9.112](https://github.com/aklivity/zilla/tree/0.9.112) (2024-12-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.111...0.9.112)

**Merged pull requests:**

- Bump org.apache.avro:avro from 1.11.3 to 1.12.0 [\#1290](https://github.com/aklivity/zilla/pull/1290) ([dependabot[bot]](https://github.com/apps/dependabot))
- Improve gRPC parser to allow complex types in optionValue [\#1350](https://github.com/aklivity/zilla/pull/1350) ([bmaidics](https://github.com/bmaidics))
- Ztable support and convert transformation logic to state machine  [\#1352](https://github.com/aklivity/zilla/pull/1352) ([akrambek](https://github.com/akrambek))

## [0.9.111](https://github.com/aklivity/zilla/tree/0.9.111) (2024-12-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.110...0.9.111)

**Merged pull requests:**

- Replace Ivy APIs with embedded Maven during zpmw install [\#1334](https://github.com/aklivity/zilla/pull/1334) ([bmaidics](https://github.com/bmaidics))

## [0.9.110](https://github.com/aklivity/zilla/tree/0.9.110) (2024-12-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.108...0.9.110)

**Merged pull requests:**

- Support ZVIEW command [\#1329](https://github.com/aklivity/zilla/pull/1329) ([akrambek](https://github.com/akrambek))

## [0.9.108](https://github.com/aklivity/zilla/tree/0.9.108) (2024-12-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.107...0.9.108)

**Merged pull requests:**

- Bump ubuntu from jammy-20240530 to jammy-20240808 in /cloud/docker-image/src/main/docker [\#1205](https://github.com/aklivity/zilla/pull/1205) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/checkout from 3 to 4 [\#1219](https://github.com/aklivity/zilla/pull/1219) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump lycheeverse/lychee-action from 1.8.0 to 2.1.0 [\#1325](https://github.com/aklivity/zilla/pull/1325) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump alpine from 3.20.3 to 3.21.0 in /cloud/docker-image/src/main/docker [\#1346](https://github.com/aklivity/zilla/pull/1346) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.107](https://github.com/aklivity/zilla/tree/0.9.107) (2024-12-09)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.106...0.9.107)

**Merged pull requests:**

- Bump com.google.protobuf:protobuf-java from 3.24.4 to 3.25.5 in /runtime/model-protobuf [\#1257](https://github.com/aklivity/zilla/pull/1257) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.avro:avro from 1.11.3 to 1.11.4 [\#1284](https://github.com/aklivity/zilla/pull/1284) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.106](https://github.com/aklivity/zilla/tree/0.9.106) (2024-12-03)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.105...0.9.106)

**Merged pull requests:**

- Exclude package-info.class from delegate module to avoid empty packages [\#1343](https://github.com/aklivity/zilla/pull/1343) ([jfallows](https://github.com/jfallows))

## [0.9.105](https://github.com/aklivity/zilla/tree/0.9.105) (2024-12-03)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.104...0.9.105)

**Merged pull requests:**

- Json deserialization of nullable fields [\#1342](https://github.com/aklivity/zilla/pull/1342) ([akrambek](https://github.com/akrambek))

## [0.9.104](https://github.com/aklivity/zilla/tree/0.9.104) (2024-12-01)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.103...0.9.104)

**Merged pull requests:**

- Json serialization of nullable fields [\#1341](https://github.com/aklivity/zilla/pull/1341) ([akrambek](https://github.com/akrambek))

## [0.9.103](https://github.com/aklivity/zilla/tree/0.9.103) (2024-11-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.102...0.9.103)

**Merged pull requests:**

- Support `directory` syntax & functionality [\#1323](https://github.com/aklivity/zilla/pull/1323) ([ankitk-me](https://github.com/ankitk-me))
- validate `begin` frame type for `ws` client [\#1332](https://github.com/aklivity/zilla/pull/1332) ([ankitk-me](https://github.com/ankitk-me))
- Enhance test vault and test exporter [\#1333](https://github.com/aklivity/zilla/pull/1333) ([jfallows](https://github.com/jfallows))
- resolve nested `$ref` in schema [\#1337](https://github.com/aklivity/zilla/pull/1337) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.102](https://github.com/aklivity/zilla/tree/0.9.102) (2024-11-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.101...0.9.102)

**Merged pull requests:**

- Support ALTER STREAM and ALTER TABLE transformation [\#1320](https://github.com/aklivity/zilla/pull/1320) ([akrambek](https://github.com/akrambek))
- Fix MQTT binding selecting incorrect publish stream [\#1328](https://github.com/aklivity/zilla/pull/1328) ([bmaidics](https://github.com/bmaidics))

## [0.9.101](https://github.com/aklivity/zilla/tree/0.9.101) (2024-11-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.100...0.9.101)

**Merged pull requests:**

- support file write in binding-filesystem & binding-http-filesystem [\#1300](https://github.com/aklivity/zilla/pull/1300) ([ankitk-me](https://github.com/ankitk-me))
- `number` -> `integer` for `max-age` attribute [\#1316](https://github.com/aklivity/zilla/pull/1316) ([ankitk-me](https://github.com/ankitk-me))
- Support instrumentation via Java agent [\#1321](https://github.com/aklivity/zilla/pull/1321) ([jfallows](https://github.com/jfallows))

## [0.9.100](https://github.com/aklivity/zilla/tree/0.9.100) (2024-10-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.99...0.9.100)

**Implemented enhancements:**

- syntax check mqtt topic names and topic filters in zilla.yaml [\#521](https://github.com/aklivity/zilla/issues/521) ([vordimous](https://github.com/vordimous))
- Support `pgsql-kafka` binding [\#1058](https://github.com/aklivity/zilla/issues/1058) ([jfallows](https://github.com/jfallows))
- Replace jsqlparser with antlr grammar [\#1301](https://github.com/aklivity/zilla/issues/1301) ([akrambek](https://github.com/akrambek))

**Fixed bugs:**

- JSON to Protobuf breaks after processing an invalid message [\#1282](https://github.com/aklivity/zilla/issues/1282) ([vordimous](https://github.com/vordimous))
- Zilla `asyncapi.mqtt.kafka.proxy` crash on startup with NPE: Cannot read field "values" because the return value of "...AsyncapiServerVariableResolver.resolve\(String\)" is null [\#1304](https://github.com/aklivity/zilla/issues/1304) ([vordimous](https://github.com/vordimous))

**Closed issues:**

- Log missing enviroment variables.  [\#1188](https://github.com/aklivity/zilla/issues/1188) ([vordimous](https://github.com/vordimous))
- `pgsql` `DROP TOPIC` command to `KafkaDeleteTopicsBeginEx` [\#1307](https://github.com/aklivity/zilla/issues/1307) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- Support DROP TABLE, STREAM, and MATERIALIZED VIEW [\#1266](https://github.com/aklivity/zilla/pull/1266) ([akrambek](https://github.com/akrambek))
- Upgrade agrona version [\#1281](https://github.com/aklivity/zilla/pull/1281) ([bmaidics](https://github.com/bmaidics))
- protobuf validation failure fix [\#1292](https://github.com/aklivity/zilla/pull/1292) ([ankitk-me](https://github.com/ankitk-me))
- Include prefer wait in watch request even after initial 404 read request [\#1295](https://github.com/aklivity/zilla/pull/1295) ([jfallows](https://github.com/jfallows))
- syntax check mqtt topic names in zilla.yaml [\#1297](https://github.com/aklivity/zilla/pull/1297) ([ankitk-me](https://github.com/ankitk-me))
- Replace jsqlparser with antlr gramma [\#1298](https://github.com/aklivity/zilla/pull/1298) ([akrambek](https://github.com/akrambek))
- Log missing enviroment variables [\#1299](https://github.com/aklivity/zilla/pull/1299) ([ankitk-me](https://github.com/ankitk-me))
- Fix IndexOutOfBoundsException at KafkaCacheClientProduceFactory [\#1303](https://github.com/aklivity/zilla/pull/1303) ([bmaidics](https://github.com/bmaidics))
- pgsql ALTER TOPIC command to register new schema [\#1309](https://github.com/aklivity/zilla/pull/1309) ([akrambek](https://github.com/akrambek))
- Fix kafka cache fetch server retention issue [\#1310](https://github.com/aklivity/zilla/pull/1310) ([bmaidics](https://github.com/bmaidics))
- Add missing dependency [\#1311](https://github.com/aklivity/zilla/pull/1311) ([akrambek](https://github.com/akrambek))
- Support `https` scheme for zilla.yaml config watcher [\#1313](https://github.com/aklivity/zilla/pull/1313) ([jfallows](https://github.com/jfallows))
- Support asyncapi server variables locally and via references [\#1314](https://github.com/aklivity/zilla/pull/1314) ([jfallows](https://github.com/jfallows))

## [0.9.99](https://github.com/aklivity/zilla/tree/0.9.99) (2024-10-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.98...0.9.99)

**Merged pull requests:**

- Support cancel request [\#1293](https://github.com/aklivity/zilla/pull/1293) ([akrambek](https://github.com/akrambek))
- Update advertised protocol version in pgsql server binding [\#1294](https://github.com/aklivity/zilla/pull/1294) ([akrambek](https://github.com/akrambek))

## [0.9.98](https://github.com/aklivity/zilla/tree/0.9.98) (2024-10-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.97...0.9.98)

**Fixed bugs:**

- Propagate error code in risingwave binding that's coming either from pgsql-kafka or risingwave [\#1286](https://github.com/aklivity/zilla/issues/1286) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- Increase write buffer size to accomidate longer path [\#1287](https://github.com/aklivity/zilla/pull/1287) ([akrambek](https://github.com/akrambek))
- Propagate error code in risingwave binding that's coming either from pgsql-kafka or risingwave [\#1288](https://github.com/aklivity/zilla/pull/1288) ([akrambek](https://github.com/akrambek))

## [0.9.97](https://github.com/aklivity/zilla/tree/0.9.97) (2024-10-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.96...0.9.97)

**Implemented enhancements:**

- Support `insert into` to seed `kafka` messages via `risingwave` binding [\#1274](https://github.com/aklivity/zilla/issues/1274) ([jfallows](https://github.com/jfallows))

**Closed issues:**

- Support `jwt` guarded identity via custom token claim [\#1276](https://github.com/aklivity/zilla/issues/1276) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Support insert into to seed kafka messages via risingwave binding [\#1275](https://github.com/aklivity/zilla/pull/1275) ([akrambek](https://github.com/akrambek))
- Support jwt guarded identity via custom token claim [\#1277](https://github.com/aklivity/zilla/pull/1277) ([akrambek](https://github.com/akrambek))
- external udf - python support [\#1278](https://github.com/aklivity/zilla/pull/1278) ([ankitk-me](https://github.com/ankitk-me))
- `pgsql` DROP TOPIC command to KafkaDeleteTopicsBeginEx plus catalog unregister subject [\#1280](https://github.com/aklivity/zilla/pull/1280) ([akrambek](https://github.com/akrambek))

## [0.9.96](https://github.com/aklivity/zilla/tree/0.9.96) (2024-10-01)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.95...0.9.96)

**Implemented enhancements:**

- Support Kafka topics create, alter, delete [\#1059](https://github.com/aklivity/zilla/issues/1059) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- `zilla` Fails to Load Configuration from Specified location if the initial attempts are unsuccessful [\#1226](https://github.com/aklivity/zilla/issues/1226) ([ankitk-me](https://github.com/ankitk-me))

**Merged pull requests:**

-  Support Kafka topics alter, delete [\#1265](https://github.com/aklivity/zilla/pull/1265) ([akrambek](https://github.com/akrambek))
- Detect config update after initial 404 status [\#1267](https://github.com/aklivity/zilla/pull/1267) ([jfallows](https://github.com/jfallows))
- External header pattern fix [\#1269](https://github.com/aklivity/zilla/pull/1269) ([ankitk-me](https://github.com/ankitk-me))
- Remove produceRecordFramingSize constraints [\#1270](https://github.com/aklivity/zilla/pull/1270) ([akrambek](https://github.com/akrambek))
- create external function issue fix [\#1271](https://github.com/aklivity/zilla/pull/1271) ([ankitk-me](https://github.com/ankitk-me))
- Risingwave and PsqlKafka bug fixes [\#1272](https://github.com/aklivity/zilla/pull/1272) ([akrambek](https://github.com/akrambek))
- Risingwave SInk primary key fix [\#1273](https://github.com/aklivity/zilla/pull/1273) ([akrambek](https://github.com/akrambek))

## [0.9.95](https://github.com/aklivity/zilla/tree/0.9.95) (2024-09-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.94...0.9.95)

**Merged pull requests:**

- Bump alpine from 3.20.2 to 3.20.3 in /cloud/docker-image/src/main/docker [\#1235](https://github.com/aklivity/zilla/pull/1235) ([dependabot[bot]](https://github.com/apps/dependabot))
- Fix KafkaMerged evaluation storage [\#1264](https://github.com/aklivity/zilla/pull/1264) ([bmaidics](https://github.com/bmaidics))

## [0.9.94](https://github.com/aklivity/zilla/tree/0.9.94) (2024-09-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.93...0.9.94)

**Implemented enhancements:**

- Support `catalog` register new schema version [\#1060](https://github.com/aklivity/zilla/issues/1060) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- NPE when a schema isn't found in a schema registry [\#1170](https://github.com/aklivity/zilla/issues/1170) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- `risingwave` binding support [\#1211](https://github.com/aklivity/zilla/pull/1211) ([akrambek](https://github.com/akrambek))
- fix: fix schema syntax and naming [\#1217](https://github.com/aklivity/zilla/pull/1217) ([vordimous](https://github.com/vordimous))
- Support Kafka topic create, alter, delete [\#1234](https://github.com/aklivity/zilla/pull/1234) ([akrambek](https://github.com/akrambek))
- Initialize pgsql-kafka binding [\#1239](https://github.com/aklivity/zilla/pull/1239) ([jfallows](https://github.com/jfallows))
- fix: NPE when a schema isn't found in a schema registry [\#1241](https://github.com/aklivity/zilla/pull/1241) ([ankitk-me](https://github.com/ankitk-me))
- `zilla` build fix [\#1242](https://github.com/aklivity/zilla/pull/1242) ([ankitk-me](https://github.com/ankitk-me))
- Support pgsql-kafka binding [\#1245](https://github.com/aklivity/zilla/pull/1245) ([akrambek](https://github.com/akrambek))
- Support catalog register new schema version [\#1246](https://github.com/aklivity/zilla/pull/1246) ([akrambek](https://github.com/akrambek))
- Ensure extract-key precedes extract-headers … [\#1247](https://github.com/aklivity/zilla/pull/1247) ([jfallows](https://github.com/jfallows))
- create `function` support in `risingwave` binding [\#1248](https://github.com/aklivity/zilla/pull/1248) ([ankitk-me](https://github.com/ankitk-me))
- Fix mqtt abort issue [\#1249](https://github.com/aklivity/zilla/pull/1249) ([bmaidics](https://github.com/bmaidics))
- Describe cluster API Support [\#1250](https://github.com/aklivity/zilla/pull/1250) ([akrambek](https://github.com/akrambek))
- fix: add or update the transforms pattern regex [\#1251](https://github.com/aklivity/zilla/pull/1251) ([vordimous](https://github.com/vordimous))
- fix: use integer for challenge type [\#1252](https://github.com/aklivity/zilla/pull/1252) ([vordimous](https://github.com/vordimous))
- Risingwave demo bug fixes [\#1254](https://github.com/aklivity/zilla/pull/1254) ([akrambek](https://github.com/akrambek))
- Use END instead of ABORT at network end in MQTT [\#1256](https://github.com/aklivity/zilla/pull/1256) ([bmaidics](https://github.com/bmaidics))
- Support risingwave include keyword [\#1259](https://github.com/aklivity/zilla/pull/1259) ([akrambek](https://github.com/akrambek))
- Explicitly set http version for schema registration [\#1260](https://github.com/aklivity/zilla/pull/1260) ([akrambek](https://github.com/akrambek))
- Support embedded risngwave functions [\#1261](https://github.com/aklivity/zilla/pull/1261) ([akrambek](https://github.com/akrambek))
- Fix parsing newline before end of stream [\#1262](https://github.com/aklivity/zilla/pull/1262) ([akrambek](https://github.com/akrambek))

## [0.9.93](https://github.com/aklivity/zilla/tree/0.9.93) (2024-09-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.92...0.9.93)

**Implemented enhancements:**

- Support `pgsql` binding [\#1057](https://github.com/aklivity/zilla/issues/1057) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Investigate connection pool reconnect when Kafka not yet available [\#1153](https://github.com/aklivity/zilla/issues/1153) ([jfallows](https://github.com/jfallows))
- `400 Bad Request` response from Zilla when Using Java HttpClient [\#1192](https://github.com/aklivity/zilla/issues/1192) ([ankitk-me](https://github.com/ankitk-me))
- Using catalog::apicurio triggers unexpected behaviour [\#1202](https://github.com/aklivity/zilla/issues/1202) ([ankitk-me](https://github.com/ankitk-me))
- Zilla OpenAPI not supporting filesystem catalog [\#1225](https://github.com/aklivity/zilla/issues/1225) ([casanova-thiago](https://github.com/casanova-thiago))

**Closed issues:**

- Handle large HTTP headers [\#1046](https://github.com/aklivity/zilla/issues/1046) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- Ensure streams are cleaned up on authentication failure… [\#1191](https://github.com/aklivity/zilla/pull/1191) ([jfallows](https://github.com/jfallows))
- Kafka cache client: mark entry dirty at flush before notifying the server to process [\#1193](https://github.com/aklivity/zilla/pull/1193) ([bmaidics](https://github.com/bmaidics))
- Allow content-length header with h2c upgrade [\#1194](https://github.com/aklivity/zilla/pull/1194) ([jfallows](https://github.com/jfallows))
- Compute kafka produce checksum without staging headers [\#1196](https://github.com/aklivity/zilla/pull/1196) ([jfallows](https://github.com/jfallows))
- Fix: Using `asyncapi client` binding trigger NPE & crashes Zilla [\#1197](https://github.com/aklivity/zilla/pull/1197) ([ankitk-me](https://github.com/ankitk-me))
- Initial pgsql binding projects [\#1198](https://github.com/aklivity/zilla/pull/1198) ([jfallows](https://github.com/jfallows))
- Support pgsql binding [\#1200](https://github.com/aklivity/zilla/pull/1200) ([akrambek](https://github.com/akrambek))
- Update mqtt session stream to report correct origin id for zilla dump command [\#1203](https://github.com/aklivity/zilla/pull/1203) ([jfallows](https://github.com/jfallows))
- Ensure id encoding is consistent for encode and decode [\#1204](https://github.com/aklivity/zilla/pull/1204) ([jfallows](https://github.com/jfallows))
- Initial risingwave binding projects [\#1209](https://github.com/aklivity/zilla/pull/1209) ([jfallows](https://github.com/jfallows))
- Disable JVM class sharing to avoid error message during build [\#1210](https://github.com/aklivity/zilla/pull/1210) ([jfallows](https://github.com/jfallows))
- Eclipse IDE import maven projects [\#1212](https://github.com/aklivity/zilla/pull/1212) ([jfallows](https://github.com/jfallows))
- Reduce compile warnings [\#1213](https://github.com/aklivity/zilla/pull/1213) ([jfallows](https://github.com/jfallows))
- Fix incorrect CRC combine in Kafka produce client [\#1214](https://github.com/aklivity/zilla/pull/1214) ([bmaidics](https://github.com/bmaidics))
- Link checker [\#1216](https://github.com/aklivity/zilla/pull/1216) ([vordimous](https://github.com/vordimous))
- Update asyncapi binding module-info to open parser package [\#1227](https://github.com/aklivity/zilla/pull/1227) ([jfallows](https://github.com/jfallows))
- http binding update to support header overrides  at route level [\#1231](https://github.com/aklivity/zilla/pull/1231) ([ankitk-me](https://github.com/ankitk-me))
- Fix incorrect flush acknowledgement in KafkaCacheClientProduceFactory [\#1232](https://github.com/aklivity/zilla/pull/1232) ([bmaidics](https://github.com/bmaidics))
- Mqtt flow control fix [\#1233](https://github.com/aklivity/zilla/pull/1233) ([bmaidics](https://github.com/bmaidics))
- Refactor vault handler [\#1236](https://github.com/aklivity/zilla/pull/1236) ([jfallows](https://github.com/jfallows))

## [0.9.92](https://github.com/aklivity/zilla/tree/0.9.92) (2024-08-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.91...0.9.92)

**Implemented enhancements:**

- Support `extract-key` kafka message transform [\#1176](https://github.com/aklivity/zilla/issues/1176) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Support extract-key kafka message transform  [\#1183](https://github.com/aklivity/zilla/pull/1183) ([akrambek](https://github.com/akrambek))
- Align subject names when using inline catalog [\#1190](https://github.com/aklivity/zilla/pull/1190) ([jfallows](https://github.com/jfallows))

## [0.9.91](https://github.com/aklivity/zilla/tree/0.9.91) (2024-08-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.90...0.9.91)

**Fixed bugs:**

- `asyncapi` binding triggers zilla crash when used with `catalog::apicurio` [\#1185](https://github.com/aklivity/zilla/issues/1185) ([ankitk-me](https://github.com/ankitk-me))

**Merged pull requests:**

- Enables `bindings:asyncapi` to use `catalog::apicurio` [\#1186](https://github.com/aklivity/zilla/pull/1186) ([ankitk-me](https://github.com/ankitk-me))
- Support SKIP\_MANY only kafka headers sequence filter [\#1189](https://github.com/aklivity/zilla/pull/1189) ([jfallows](https://github.com/jfallows))

## [0.9.90](https://github.com/aklivity/zilla/tree/0.9.90) (2024-08-05)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.89...0.9.90)

**Implemented enhancements:**

- Support `extract-headers` kafka message transform [\#1175](https://github.com/aklivity/zilla/issues/1175) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Connecting to Aiven Kafka over TLS Throws an `java.security.InvalidAlgorithmParameterException: the trustAnchors parameter must be non-empty` Error [\#1115](https://github.com/aklivity/zilla/issues/1115) ([vordimous](https://github.com/vordimous))
- Support topic pattern wildcards in `mqtt-kafka` clients [\#1178](https://github.com/aklivity/zilla/issues/1178) ([jfallows](https://github.com/jfallows))

**Closed issues:**

- Simplify `sse` support in AsyncAPI specs [\#1151](https://github.com/aklivity/zilla/issues/1151) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- `tls` binding require `vault` and `keys` or `signers` in `options` [\#1159](https://github.com/aklivity/zilla/pull/1159) ([ankitk-me](https://github.com/ankitk-me))
- Bump alpine from 3.20.1 to 3.20.2 in /cloud/docker-image/src/main/docker [\#1165](https://github.com/aklivity/zilla/pull/1165) ([dependabot[bot]](https://github.com/apps/dependabot))
- Add validation for invalid path in http-kafka [\#1168](https://github.com/aklivity/zilla/pull/1168) ([bmaidics](https://github.com/bmaidics))
- Refactor asyncapi binding to simplify SSE asyncapi bindings extension [\#1171](https://github.com/aklivity/zilla/pull/1171) ([jfallows](https://github.com/jfallows))
- Support extract-headers kafka message transform [\#1177](https://github.com/aklivity/zilla/pull/1177) ([akrambek](https://github.com/akrambek))
- Update MQTT wildcard processing for client topic patterns [\#1179](https://github.com/aklivity/zilla/pull/1179) ([jfallows](https://github.com/jfallows))
- Apply minimum mqtt timeout constraint … [\#1180](https://github.com/aklivity/zilla/pull/1180) ([jfallows](https://github.com/jfallows))
- Resolve server binding protocol type dissector … [\#1181](https://github.com/aklivity/zilla/pull/1181) ([jfallows](https://github.com/jfallows))

## [0.9.89](https://github.com/aklivity/zilla/tree/0.9.89) (2024-07-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.88...0.9.89)

**Fixed bugs:**

- Fix WS large message bug [\#725](https://github.com/aklivity/zilla/pull/725) ([bmaidics](https://github.com/bmaidics))
- Avro validation returns 204 and produces blank message [\#1143](https://github.com/aklivity/zilla/issues/1143) ([vordimous](https://github.com/vordimous))
- custom metadata populated by grpc-server missing  [\#1155](https://github.com/aklivity/zilla/issues/1155) ([ankitk-me](https://github.com/ankitk-me))

**Closed issues:**

- Support `grpc` custom metadata pass through [\#730](https://github.com/aklivity/zilla/issues/730) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- grpc custom metadata passthrough implementation [\#1097](https://github.com/aklivity/zilla/pull/1097) ([ankitk-me](https://github.com/ankitk-me))
- Support karapace-schema-registry, schema-registry and apicurio-registry catalogs [\#1134](https://github.com/aklivity/zilla/pull/1134) ([jfallows](https://github.com/jfallows))
- custom metadata populated by grpc-server missing fix [\#1156](https://github.com/aklivity/zilla/pull/1156) ([ankitk-me](https://github.com/ankitk-me))
- Avro validation bug fix [\#1157](https://github.com/aklivity/zilla/pull/1157) ([ankitk-me](https://github.com/ankitk-me))
- grpc: mutable byte arrays to non-static instance fields [\#1160](https://github.com/aklivity/zilla/pull/1160) ([ankitk-me](https://github.com/ankitk-me))
- Kafka debug log fix [\#1163](https://github.com/aklivity/zilla/pull/1163) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.88](https://github.com/aklivity/zilla/tree/0.9.88) (2024-07-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.87...0.9.88)

**Implemented enhancements:**

- AsyncAPI `http-kafka` header overrides support [\#1141](https://github.com/aklivity/zilla/issues/1141) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- AsyncAPI sse kafka filtering support [\#1137](https://github.com/aklivity/zilla/issues/1137) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- update ingress values and implementation [\#1142](https://github.com/aklivity/zilla/pull/1142) ([vordimous](https://github.com/vordimous))
- Support http authorization in asyncapi generation [\#1145](https://github.com/aklivity/zilla/pull/1145) ([akrambek](https://github.com/akrambek))
- fix: update readme links [\#1146](https://github.com/aklivity/zilla/pull/1146) ([vordimous](https://github.com/vordimous))
- Support `http-kafka` header overrides from AsyncAPI http operation [\#1147](https://github.com/aklivity/zilla/pull/1147) ([jfallows](https://github.com/jfallows))
- Support `sse-kafka` header filters from AsyncAPI sse operation [\#1148](https://github.com/aklivity/zilla/pull/1148) ([jfallows](https://github.com/jfallows))
- MInor fixes for asyncapi sse-kafka and http-kafka binding support [\#1149](https://github.com/aklivity/zilla/pull/1149) ([akrambek](https://github.com/akrambek))
- Support asyncapi authorization in http kafka and sse kafka [\#1150](https://github.com/aklivity/zilla/pull/1150) ([akrambek](https://github.com/akrambek))

## [0.9.87](https://github.com/aklivity/zilla/tree/0.9.87) (2024-07-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.86...0.9.87)

**Merged pull requests:**

- Support multiple requests with single window ack on shared connection [\#1144](https://github.com/aklivity/zilla/pull/1144) ([jfallows](https://github.com/jfallows))

## [0.9.86](https://github.com/aklivity/zilla/tree/0.9.86) (2024-07-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.85...0.9.86)

**Merged pull requests:**

- Handle incremental key validation when length increases [\#1140](https://github.com/aklivity/zilla/pull/1140) ([akrambek](https://github.com/akrambek))

## [0.9.85](https://github.com/aklivity/zilla/tree/0.9.85) (2024-07-09)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.84...0.9.85)

**Fixed bugs:**

- Support key validation in kafka asyncapi generation [\#1105](https://github.com/aklivity/zilla/issues/1105) ([akrambek](https://github.com/akrambek))

**Closed issues:**

- Add more context to the Kafka API event code log formatter. [\#1126](https://github.com/aklivity/zilla/issues/1126) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- Bump alpine from 3.20.0 to 3.20.1 in /cloud/docker-image/src/main/docker [\#1102](https://github.com/aklivity/zilla/pull/1102) ([dependabot[bot]](https://github.com/apps/dependabot))
- Enhance TLS key pair verification tests [\#1119](https://github.com/aklivity/zilla/pull/1119) ([jfallows](https://github.com/jfallows))
- Support reentrant kafka write key for converter [\#1120](https://github.com/aklivity/zilla/pull/1120) ([jfallows](https://github.com/jfallows))
- Add zilla version command [\#1121](https://github.com/aklivity/zilla/pull/1121) ([bmaidics](https://github.com/bmaidics))
- Bug fixes and improvements to support asyncapi http, sse, and kafka integration [\#1124](https://github.com/aklivity/zilla/pull/1124) ([akrambek](https://github.com/akrambek))
- Enhance Kafka event descriptions [\#1127](https://github.com/aklivity/zilla/pull/1127) ([jfallows](https://github.com/jfallows))
- Detect missing events in test exporter [\#1128](https://github.com/aklivity/zilla/pull/1128) ([jfallows](https://github.com/jfallows))
- fix: Add custom pod labels and fix notes for connection instructions [\#1130](https://github.com/aklivity/zilla/pull/1130) ([vordimous](https://github.com/vordimous))
- Lint helm chart on local builds and PR builds [\#1132](https://github.com/aklivity/zilla/pull/1132) ([jfallows](https://github.com/jfallows))
- Add CatalogConfig.builder\(\) methods [\#1133](https://github.com/aklivity/zilla/pull/1133) ([jfallows](https://github.com/jfallows))
- Ensure SASL handshake occurs for JoinGroupRequest as needed… [\#1139](https://github.com/aklivity/zilla/pull/1139) ([jfallows](https://github.com/jfallows))

## [0.9.84](https://github.com/aklivity/zilla/tree/0.9.84) (2024-06-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.83...0.9.84)

**Fixed bugs:**

- Asyncapi doesn't generate schema for catalog with avro format [\#1104](https://github.com/aklivity/zilla/issues/1104) ([akrambek](https://github.com/akrambek))

**Closed issues:**

- feat: improve troubleshooting capabilities [\#903](https://github.com/aklivity/zilla/issues/903) ([hedhyw](https://github.com/hedhyw))
- Verify public-private key pair obtained from vault used for TLS handshake [\#1073](https://github.com/aklivity/zilla/issues/1073) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Verify public-private tls key pair [\#1108](https://github.com/aklivity/zilla/pull/1108) ([attilakreiner](https://github.com/attilakreiner))
- Add logging of cluster authorization failed error to kafka binding [\#1112](https://github.com/aklivity/zilla/pull/1112) ([attilakreiner](https://github.com/attilakreiner))
- Generate asyncapi  schema catalog with avro, protobuf format support [\#1113](https://github.com/aklivity/zilla/pull/1113) ([akrambek](https://github.com/akrambek))
- Include engine test sources JAR in release [\#1116](https://github.com/aklivity/zilla/pull/1116) ([jfallows](https://github.com/jfallows))
- Require test exporter event properties via test schema [\#1117](https://github.com/aklivity/zilla/pull/1117) ([jfallows](https://github.com/jfallows))
- Use default config when missing [\#1118](https://github.com/aklivity/zilla/pull/1118) ([jfallows](https://github.com/jfallows))

## [0.9.83](https://github.com/aklivity/zilla/tree/0.9.83) (2024-06-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.82...0.9.83)

**Implemented enhancements:**

- Use full Event ID and the event name [\#1013](https://github.com/aklivity/zilla/issues/1013) ([vordimous](https://github.com/vordimous))
- Promote `filesystem` catalog out of incubator [\#1068](https://github.com/aklivity/zilla/issues/1068) ([vordimous](https://github.com/vordimous))

**Fixed bugs:**

- Intermittent NPE when trying to resolve guards [\#994](https://github.com/aklivity/zilla/issues/994) ([bmaidics](https://github.com/bmaidics))
- MqttSessionBeginEx missing packetIds in zilla dump [\#1028](https://github.com/aklivity/zilla/issues/1028) ([bmaidics](https://github.com/bmaidics))
- iNotify error when multiple Zilla instances are started in K8s Pods on a Portainer.io host [\#1081](https://github.com/aklivity/zilla/issues/1081) ([vordimous](https://github.com/vordimous))

**Closed issues:**

- Support remote zilla configuration with change detection [\#1061](https://github.com/aklivity/zilla/issues/1061) ([jfallows](https://github.com/jfallows))
- Support filtering by kafka structured value field\(s\) [\#1062](https://github.com/aklivity/zilla/issues/1062) ([jfallows](https://github.com/jfallows))
- Use miliseconds in metrics [\#1069](https://github.com/aklivity/zilla/issues/1069) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- feat: replace static event name with dynamic based on event id [\#1029](https://github.com/aklivity/zilla/pull/1029) ([vordimous](https://github.com/vordimous))
- Fix TlsNetworkIT by adding cipherSuites [\#1043](https://github.com/aklivity/zilla/pull/1043) ([attilakreiner](https://github.com/attilakreiner))
- Bump alpine from 3.19.1 to 3.20.0 in /cloud/docker-image/src/main/docker [\#1047](https://github.com/aklivity/zilla/pull/1047) ([dependabot[bot]](https://github.com/apps/dependabot))
- Added declarative helmfile [\#1054](https://github.com/aklivity/zilla/pull/1054) ([ttimot24](https://github.com/ttimot24))
- Add publish qos max options for mqtt-kafka binding [\#1065](https://github.com/aklivity/zilla/pull/1065) ([bmaidics](https://github.com/bmaidics))
- Fix NPE when trying to resolve guards [\#1067](https://github.com/aklivity/zilla/pull/1067) ([attilakreiner](https://github.com/attilakreiner))
- feat: replace port 8080 with 12345 [\#1070](https://github.com/aklivity/zilla/pull/1070) ([vordimous](https://github.com/vordimous))
- Support remote zilla configuration with change detection [\#1071](https://github.com/aklivity/zilla/pull/1071) ([attilakreiner](https://github.com/attilakreiner))
- Http-Kafka AsyncAPI [\#1072](https://github.com/aklivity/zilla/pull/1072) ([bmaidics](https://github.com/bmaidics))
- Fix mqtt-kafka non compact test [\#1074](https://github.com/aklivity/zilla/pull/1074) ([bmaidics](https://github.com/bmaidics))
- Catalog Handler interface to support dynamic encode padding by length [\#1075](https://github.com/aklivity/zilla/pull/1075) ([ankitk-me](https://github.com/ankitk-me))
- Dynamic decode padding by length fix [\#1078](https://github.com/aklivity/zilla/pull/1078) ([ankitk-me](https://github.com/ankitk-me))
- Bump ubuntu from jammy-20240427 to jammy-20240530 in /cloud/docker-image/src/main/docker [\#1079](https://github.com/aklivity/zilla/pull/1079) ([dependabot[bot]](https://github.com/apps/dependabot))
- Java 17 source compatibility [\#1084](https://github.com/aklivity/zilla/pull/1084) ([jfallows](https://github.com/jfallows))
- SSE asyncapi server, client [\#1085](https://github.com/aklivity/zilla/pull/1085) ([bmaidics](https://github.com/bmaidics))
- Update k3po dependency [\#1086](https://github.com/aklivity/zilla/pull/1086) ([jfallows](https://github.com/jfallows))
- Upgrade zilla docker image to use jdk 22 [\#1088](https://github.com/aklivity/zilla/pull/1088) ([jfallows](https://github.com/jfallows))
- Ensure stdout flush without newline before comparison to expected output [\#1089](https://github.com/aklivity/zilla/pull/1089) ([jfallows](https://github.com/jfallows))
- Await non-empty output before verifying expected vs actual [\#1090](https://github.com/aklivity/zilla/pull/1090) ([jfallows](https://github.com/jfallows))
- Ensure engine closes after stdout generated [\#1091](https://github.com/aklivity/zilla/pull/1091) ([jfallows](https://github.com/jfallows))
- Add sse payload validation [\#1092](https://github.com/aklivity/zilla/pull/1092) ([bmaidics](https://github.com/bmaidics))
- filtering by structured value field\(s\) [\#1093](https://github.com/aklivity/zilla/pull/1093) ([ankitk-me](https://github.com/ankitk-me))
- Implement millisecond conversion to metrics [\#1094](https://github.com/aklivity/zilla/pull/1094) ([attilakreiner](https://github.com/attilakreiner))
- Fix imports [\#1095](https://github.com/aklivity/zilla/pull/1095) ([attilakreiner](https://github.com/attilakreiner))
- Promote catalog-filesystem out of incubator [\#1096](https://github.com/aklivity/zilla/pull/1096) ([attilakreiner](https://github.com/attilakreiner))
- Fix dump mqtt session begin [\#1098](https://github.com/aklivity/zilla/pull/1098) ([attilakreiner](https://github.com/attilakreiner))
- Asyncapi sse kafka proxy [\#1099](https://github.com/aklivity/zilla/pull/1099) ([bmaidics](https://github.com/bmaidics))
- Fix NegativeArraySizeException when receiving mqttFlush [\#1100](https://github.com/aklivity/zilla/pull/1100) ([bmaidics](https://github.com/bmaidics))
- Support special characters for resolving channel ref [\#1101](https://github.com/aklivity/zilla/pull/1101) ([akrambek](https://github.com/akrambek))
- Support engine events and detect config watcher failed [\#1107](https://github.com/aklivity/zilla/pull/1107) ([jfallows](https://github.com/jfallows))
- fix: add volume mounts into the deployment yaml [\#1110](https://github.com/aklivity/zilla/pull/1110) ([vordimous](https://github.com/vordimous))
- Refactor signaler class name [\#1111](https://github.com/aklivity/zilla/pull/1111) ([jfallows](https://github.com/jfallows))

## [0.9.82](https://github.com/aklivity/zilla/tree/0.9.82) (2024-05-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.81...0.9.82)

**Fixed bugs:**

- `http-kafka` will `fetch` messages that have been deleted by a retention policy [\#897](https://github.com/aklivity/zilla/issues/897) ([vordimous](https://github.com/vordimous))
- Zilla crashes with `IllegalArgumentException: cannot accept missingValue` when using `defaultOffset: live` [\#1051](https://github.com/aklivity/zilla/issues/1051) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- Fix: http-kafka will fetch messages that have been deleted by a reten… [\#1033](https://github.com/aklivity/zilla/pull/1033) ([ankitk-me](https://github.com/ankitk-me))
- Add detection of non-compacted session topic [\#1044](https://github.com/aklivity/zilla/pull/1044) ([bmaidics](https://github.com/bmaidics))
- Set decoder to ignoreAll after session is taken over by other MQTT client [\#1045](https://github.com/aklivity/zilla/pull/1045) ([bmaidics](https://github.com/bmaidics))
- Support kafka cache bootstrap with topic default offset live [\#1052](https://github.com/aklivity/zilla/pull/1052) ([jfallows](https://github.com/jfallows))
- Queue as different kafka produce request if producerId or producerEpoch varies [\#1053](https://github.com/aklivity/zilla/pull/1053) ([akrambek](https://github.com/akrambek))
- Update to handle catalog IT validation\(resolve schema from subject\) [\#1055](https://github.com/aklivity/zilla/pull/1055) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.81](https://github.com/aklivity/zilla/tree/0.9.81) (2024-05-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.80...0.9.81)

**Implemented enhancements:**

- Split protocol testing into separate ITs for `zilla dump` command [\#958](https://github.com/aklivity/zilla/issues/958) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Telemetry attribute service.name doesn't get sent correctly [\#1007](https://github.com/aklivity/zilla/issues/1007) ([vordimous](https://github.com/vordimous))

**Closed issues:**

- Generate `zilla dump` packet captures in timestamp order including across workers [\#959](https://github.com/aklivity/zilla/issues/959) ([jfallows](https://github.com/jfallows))
- Improve Starting Zilla with the CLI [\#1016](https://github.com/aklivity/zilla/issues/1016) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- Split protocol testing into separate ITs for zilla dump command [\#989](https://github.com/aklivity/zilla/pull/989) ([attilakreiner](https://github.com/attilakreiner))
- Add zilla context to MQTT consumer groups [\#1035](https://github.com/aklivity/zilla/pull/1035) ([bmaidics](https://github.com/bmaidics))
- Ensure new mqtt subscriptions are not empty [\#1040](https://github.com/aklivity/zilla/pull/1040) ([jfallows](https://github.com/jfallows))
- Sort frames by timestamp in dump command [\#1041](https://github.com/aklivity/zilla/pull/1041) ([attilakreiner](https://github.com/attilakreiner))
- Starting Zilla with the CLI improvement [\#1042](https://github.com/aklivity/zilla/pull/1042) ([ankitk-me](https://github.com/ankitk-me))
- Add service.name attribute to metrics [\#1048](https://github.com/aklivity/zilla/pull/1048) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.80](https://github.com/aklivity/zilla/tree/0.9.80) (2024-05-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.79...0.9.80)

**Implemented enhancements:**

- Update bug report template [\#820](https://github.com/aklivity/zilla/pull/820) ([vordimous](https://github.com/vordimous))
- Enhance validation for `openapi` and `asyncapi` bindings [\#950](https://github.com/aklivity/zilla/issues/950) ([jfallows](https://github.com/jfallows))
- Integrate JMH into `tls` binding [\#961](https://github.com/aklivity/zilla/issues/961) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Running zilla with the `kafka-grpc` binding in a cluster with multiple instances results in each instance delivering a message to the configured `remote_server` [\#882](https://github.com/aklivity/zilla/issues/882) ([vordimous](https://github.com/vordimous))
- Flow control issue in openapi binding [\#1004](https://github.com/aklivity/zilla/issues/1004) ([akrambek](https://github.com/akrambek))
- Zilla crashes with `IllegalArgumentException` when an Avro payload is fetched as `json` [\#1025](https://github.com/aklivity/zilla/issues/1025) ([vordimous](https://github.com/vordimous))

**Closed issues:**

- Report unused properties based on binding definition [\#808](https://github.com/aklivity/zilla/issues/808) ([vordimous](https://github.com/vordimous))
- Resiliently handle `karapace` catalog unreachable [\#937](https://github.com/aklivity/zilla/issues/937) ([jfallows](https://github.com/jfallows))
- Resiliently handle `apicurio` catalog unreachable [\#938](https://github.com/aklivity/zilla/issues/938) ([jfallows](https://github.com/jfallows))
- Support `mqtt` access log [\#945](https://github.com/aklivity/zilla/issues/945) ([jfallows](https://github.com/jfallows))
- Remove `zilla generate` command [\#960](https://github.com/aklivity/zilla/issues/960) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Bump org.agrona:agrona from 1.6.0 to 1.21.1 [\#890](https://github.com/aklivity/zilla/pull/890) ([dependabot[bot]](https://github.com/apps/dependabot))
- binding config schema validation for unused properties [\#929](https://github.com/aklivity/zilla/pull/929) ([ankitk-me](https://github.com/ankitk-me))
- Use format to construct get openapi operation for async rquest [\#967](https://github.com/aklivity/zilla/pull/967) ([akrambek](https://github.com/akrambek))
- Support reading empty file payload [\#976](https://github.com/aklivity/zilla/pull/976) ([jfallows](https://github.com/jfallows))
- unify caching across workers to maximize cache hits [\#977](https://github.com/aklivity/zilla/pull/977) ([ankitk-me](https://github.com/ankitk-me))
- Fix multiple exporters issue [\#978](https://github.com/aklivity/zilla/pull/978) ([attilakreiner](https://github.com/attilakreiner))
- MqttKafka publish intern fix [\#979](https://github.com/aklivity/zilla/pull/979) ([bmaidics](https://github.com/bmaidics))
- `echo` `server` handshake benchmark [\#980](https://github.com/aklivity/zilla/pull/980) ([akrambek](https://github.com/akrambek))
- Remove event script in favor of handshake script [\#981](https://github.com/aklivity/zilla/pull/981) ([attilakreiner](https://github.com/attilakreiner))
- Support multiple specs in asyncapi binding [\#982](https://github.com/aklivity/zilla/pull/982) ([bmaidics](https://github.com/bmaidics))
- Update Java build matrix [\#983](https://github.com/aklivity/zilla/pull/983) ([jfallows](https://github.com/jfallows))
- Bump alpine from 3.19.0 to 3.19.1 in /cloud/docker-image/src/main/docker [\#986](https://github.com/aklivity/zilla/pull/986) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump ubuntu from jammy-20240111 to jammy-20240416 in /cloud/docker-image/src/main/docker [\#987](https://github.com/aklivity/zilla/pull/987) ([dependabot[bot]](https://github.com/apps/dependabot))
- `tls`  `client/server/echo` handshake benchmark  [\#990](https://github.com/aklivity/zilla/pull/990) ([akrambek](https://github.com/akrambek))
- Add MQTT client authentication [\#992](https://github.com/aklivity/zilla/pull/992) ([bmaidics](https://github.com/bmaidics))
- MQTT Websocket bugfix [\#993](https://github.com/aklivity/zilla/pull/993) ([bmaidics](https://github.com/bmaidics))
- Bump org.bitbucket.b\_c:jose4j from 0.9.3 to 0.9.6 [\#995](https://github.com/aklivity/zilla/pull/995) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump ubuntu from jammy-20240416 to jammy-20240427 in /cloud/docker-image/src/main/docker [\#996](https://github.com/aklivity/zilla/pull/996) ([dependabot[bot]](https://github.com/apps/dependabot))
- Support asyncapi mqtt streetlights mapping to kafka streetlights [\#997](https://github.com/aklivity/zilla/pull/997) ([bmaidics](https://github.com/bmaidics))
- Enhancing validation for openapi and asyncapi bindings [\#1001](https://github.com/aklivity/zilla/pull/1001) ([ankitk-me](https://github.com/ankitk-me))
- Fix secure http detection in OpenAPI [\#1002](https://github.com/aklivity/zilla/pull/1002) ([bmaidics](https://github.com/bmaidics))
- Support multiple specs in openapi binding [\#1005](https://github.com/aklivity/zilla/pull/1005) ([bmaidics](https://github.com/bmaidics))
- Support multiple specs in openapi-asyncapi binding [\#1008](https://github.com/aklivity/zilla/pull/1008) ([bmaidics](https://github.com/bmaidics))
- Support configuration of timestamps in zilla transport for k3po [\#1009](https://github.com/aklivity/zilla/pull/1009) ([jfallows](https://github.com/jfallows))
- Remove generate command [\#1010](https://github.com/aklivity/zilla/pull/1010) ([attilakreiner](https://github.com/attilakreiner))
- Generate correct crc32c value for the messages with different produceId [\#1011](https://github.com/aklivity/zilla/pull/1011) ([akrambek](https://github.com/akrambek))
- Increase mqtt client id limit to 256 [\#1015](https://github.com/aklivity/zilla/pull/1015) ([bmaidics](https://github.com/bmaidics))
- Bump commons-cli:commons-cli from 1.6.0 to 1.7.0 [\#1020](https://github.com/aklivity/zilla/pull/1020) ([dependabot[bot]](https://github.com/apps/dependabot))
- Unsubscribe on partition reassignment [\#1021](https://github.com/aklivity/zilla/pull/1021) ([akrambek](https://github.com/akrambek))
- MQTT clients access log implementation [\#1023](https://github.com/aklivity/zilla/pull/1023) ([ankitk-me](https://github.com/ankitk-me))
- Use binding id instead of route Id for resolved Id [\#1026](https://github.com/aklivity/zilla/pull/1026) ([akrambek](https://github.com/akrambek))
- catalog:apicurio - unify caching across workers to maximize cache hits [\#1027](https://github.com/aklivity/zilla/pull/1027) ([ankitk-me](https://github.com/ankitk-me))
- Use flyweight fields instead of class fields for control [\#1030](https://github.com/aklivity/zilla/pull/1030) ([akrambek](https://github.com/akrambek))
- Honor MQTT clean start at QoS2 produce [\#1031](https://github.com/aklivity/zilla/pull/1031) ([bmaidics](https://github.com/bmaidics))
- Bump junit.version from 5.10.1 to 5.10.2 [\#1032](https://github.com/aklivity/zilla/pull/1032) ([dependabot[bot]](https://github.com/apps/dependabot))
- Fix typo to send abort on abort instead of end [\#1036](https://github.com/aklivity/zilla/pull/1036) ([akrambek](https://github.com/akrambek))
- Handle & calculate complex schema padding [\#1038](https://github.com/aklivity/zilla/pull/1038) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.79](https://github.com/aklivity/zilla/tree/0.9.79) (2024-04-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.78...0.9.79)

**Fixed bugs:**

- Fix k3po does not reload labels when labels file size decreases [\#972](https://github.com/aklivity/zilla/pull/972) ([bmaidics](https://github.com/bmaidics))

**Merged pull requests:**

- Implement filesystem catalog [\#962](https://github.com/aklivity/zilla/pull/962) ([bmaidics](https://github.com/bmaidics))
- Add vault parameter to exporter [\#966](https://github.com/aklivity/zilla/pull/966) ([attilakreiner](https://github.com/attilakreiner))
- Use default kafka client id for kafka client instance id [\#968](https://github.com/aklivity/zilla/pull/968) ([jfallows](https://github.com/jfallows))
- Support config for mqtt publish qos max [\#971](https://github.com/aklivity/zilla/pull/971) ([jfallows](https://github.com/jfallows))

## [0.9.78](https://github.com/aklivity/zilla/tree/0.9.78) (2024-04-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.77...0.9.78)

**Merged pull requests:**

- Ensure binding types are populated for `zilla dump` to dissect protocol-specific frames [\#928](https://github.com/aklivity/zilla/pull/928) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.77](https://github.com/aklivity/zilla/tree/0.9.77) (2024-04-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.76...0.9.77)

**Merged pull requests:**

- README Docs links and formatting fixes [\#926](https://github.com/aklivity/zilla/pull/926) ([vordimous](https://github.com/vordimous))
- zilla dump : bindings not found in /var/runtime/zilla directory [\#927](https://github.com/aklivity/zilla/pull/927) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.76](https://github.com/aklivity/zilla/tree/0.9.76) (2024-04-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.75...0.9.76)

**Merged pull requests:**

- Fix helm chart logo URL [\#920](https://github.com/aklivity/zilla/pull/920) ([attilakreiner](https://github.com/attilakreiner))
- Fix validation bug [\#922](https://github.com/aklivity/zilla/pull/922) ([akrambek](https://github.com/akrambek))
- Convert non-null payloads only, … [\#923](https://github.com/aklivity/zilla/pull/923) ([jfallows](https://github.com/jfallows))
- IT to validate null message in binding-kafka with Model configured  [\#925](https://github.com/aklivity/zilla/pull/925) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.75](https://github.com/aklivity/zilla/tree/0.9.75) (2024-04-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.74...0.9.75)

**Implemented enhancements:**

- Integrate `openapi` and `asyncapi` with `catalog` [\#813](https://github.com/aklivity/zilla/issues/813) ([jfallows](https://github.com/jfallows))
- Promote components out of incubator [\#879](https://github.com/aklivity/zilla/issues/879) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Error in JsonValidatorHandler when the remote registry can't be accessed [\#817](https://github.com/aklivity/zilla/issues/817) ([attilakreiner](https://github.com/attilakreiner))

**Closed issues:**

- Support HTTP prefer async with OpenAPI [\#876](https://github.com/aklivity/zilla/issues/876) ([jfallows](https://github.com/jfallows))
- Support specific server in OpenAPI spec in `openapi` binding [\#877](https://github.com/aklivity/zilla/issues/877) ([jfallows](https://github.com/jfallows))
- Helm chart QoL improvements [\#884](https://github.com/aklivity/zilla/issues/884) ([vordimous](https://github.com/vordimous))
- Support logging of events caused by Model [\#887](https://github.com/aklivity/zilla/issues/887) ([ankitk-me](https://github.com/ankitk-me))

**Merged pull requests:**

- Integer Validator improvement to support OpenAPI & AsyncAPI specs [\#830](https://github.com/aklivity/zilla/pull/830) ([ankitk-me](https://github.com/ankitk-me))
- Update README.md [\#867](https://github.com/aklivity/zilla/pull/867) ([llukyanov](https://github.com/llukyanov))
- Support metrics in openapi and asyncapi [\#868](https://github.com/aklivity/zilla/pull/868) ([akrambek](https://github.com/akrambek))
- Fix metrics [\#869](https://github.com/aklivity/zilla/pull/869) ([attilakreiner](https://github.com/attilakreiner))
- Support logging of schema without expressions [\#871](https://github.com/aklivity/zilla/pull/871) ([jfallows](https://github.com/jfallows))
- Schema fixes + avoiding duplicate reply begin on mqtt-kafka subscribe stream [\#872](https://github.com/aklivity/zilla/pull/872) ([bmaidics](https://github.com/bmaidics))
- String Validator improvement to support OpenAPI & AsyncAPI specs [\#873](https://github.com/aklivity/zilla/pull/873) ([ankitk-me](https://github.com/ankitk-me))
- Event logs for Model [\#874](https://github.com/aklivity/zilla/pull/874) ([ankitk-me](https://github.com/ankitk-me))
- Number Validator improvement to support OpenAPI & AsyncAPI specs [\#875](https://github.com/aklivity/zilla/pull/875) ([ankitk-me](https://github.com/ankitk-me))
- Support specific server in AsyncAPI spec in asyncapi binding [\#883](https://github.com/aklivity/zilla/pull/883) ([bmaidics](https://github.com/bmaidics))
- Support specific server in OpenAPI spec in openapi binding [\#888](https://github.com/aklivity/zilla/pull/888) ([akrambek](https://github.com/akrambek))
- zilla crash while using model-json and schema is not found [\#889](https://github.com/aklivity/zilla/pull/889) ([ankitk-me](https://github.com/ankitk-me))
- Cleanup warnings for JDK 21 tools [\#891](https://github.com/aklivity/zilla/pull/891) ([jfallows](https://github.com/jfallows))
- Support BindingConfig attach and detach of composite namespaces [\#892](https://github.com/aklivity/zilla/pull/892) ([jfallows](https://github.com/jfallows))
- Support karapace catalog [\#893](https://github.com/aklivity/zilla/pull/893) ([bmaidics](https://github.com/bmaidics))
- Asyncapi mqtt improvements [\#898](https://github.com/aklivity/zilla/pull/898) ([bmaidics](https://github.com/bmaidics))
-  Support HTTP prefer async with OpenAPI [\#899](https://github.com/aklivity/zilla/pull/899) ([akrambek](https://github.com/akrambek))
- Integrate openapi and asyncapi with catalog [\#900](https://github.com/aklivity/zilla/pull/900) ([akrambek](https://github.com/akrambek))
- Update helm chart [\#901](https://github.com/aklivity/zilla/pull/901) ([attilakreiner](https://github.com/attilakreiner))
- Update schema to fix leaking implementation details [\#904](https://github.com/aklivity/zilla/pull/904) ([ankitk-me](https://github.com/ankitk-me))
- Remove name from asyncapi.specs.servers [\#906](https://github.com/aklivity/zilla/pull/906) ([bmaidics](https://github.com/bmaidics))
- Support latest version in Apicurio [\#907](https://github.com/aklivity/zilla/pull/907) ([bmaidics](https://github.com/bmaidics))
- Fix schema validation parsing [\#909](https://github.com/aklivity/zilla/pull/909) ([akrambek](https://github.com/akrambek))
- Use per worker registration for composite namespaces [\#910](https://github.com/aklivity/zilla/pull/910) ([jfallows](https://github.com/jfallows))
- openapi-asyncapi route bug fixes [\#911](https://github.com/aklivity/zilla/pull/911) ([akrambek](https://github.com/akrambek))
- Fix pom.xml for helm-chart [\#912](https://github.com/aklivity/zilla/pull/912) ([attilakreiner](https://github.com/attilakreiner))
- Add apicurio latest version test [\#914](https://github.com/aklivity/zilla/pull/914) ([bmaidics](https://github.com/bmaidics))
- Handle race condition between k3po and engine… [\#915](https://github.com/aklivity/zilla/pull/915) ([jfallows](https://github.com/jfallows))
- Ignore case while checking guard type [\#916](https://github.com/aklivity/zilla/pull/916) ([akrambek](https://github.com/akrambek))
- Promote components out of incubator [\#917](https://github.com/aklivity/zilla/pull/917) ([jfallows](https://github.com/jfallows))
- Fix remaing jwt issues [\#918](https://github.com/aklivity/zilla/pull/918) ([akrambek](https://github.com/akrambek))

## [0.9.74](https://github.com/aklivity/zilla/tree/0.9.74) (2024-03-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.73...0.9.74)

**Merged pull requests:**

- Support non-404 status codes on authorization failure [\#864](https://github.com/aklivity/zilla/pull/864) ([jfallows](https://github.com/jfallows))
- Fix http header value offset [\#865](https://github.com/aklivity/zilla/pull/865) ([akrambek](https://github.com/akrambek))

## [0.9.73](https://github.com/aklivity/zilla/tree/0.9.73) (2024-03-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.72...0.9.73)

**Merged pull requests:**

- Support guarded qname for composite namespaces [\#857](https://github.com/aklivity/zilla/pull/857) ([jfallows](https://github.com/jfallows))
- Fix qvault on asyncapi composite binding [\#858](https://github.com/aklivity/zilla/pull/858) ([bmaidics](https://github.com/bmaidics))
- Populate guarded qname for composite namespaces [\#860](https://github.com/aklivity/zilla/pull/860) ([jfallows](https://github.com/jfallows))
- Openapi bug fixes [\#861](https://github.com/aklivity/zilla/pull/861) ([akrambek](https://github.com/akrambek))
- Resolve top level namespace guards in composite namespaces [\#862](https://github.com/aklivity/zilla/pull/862) ([jfallows](https://github.com/jfallows))
- Read buffer pool size from file when readonly [\#863](https://github.com/aklivity/zilla/pull/863) ([jfallows](https://github.com/jfallows))

## [0.9.72](https://github.com/aklivity/zilla/tree/0.9.72) (2024-03-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.71...0.9.72)

**Merged pull requests:**

- Fix exporter-otlp schema [\#852](https://github.com/aklivity/zilla/pull/852) ([attilakreiner](https://github.com/attilakreiner))
- Conditionally release buffer slot on clean up [\#854](https://github.com/aklivity/zilla/pull/854) ([akrambek](https://github.com/akrambek))
- Fail when failed to acquire budget index [\#855](https://github.com/aklivity/zilla/pull/855) ([bmaidics](https://github.com/bmaidics))
- Log http access event before validation in both http/1.1 and h2 [\#856](https://github.com/aklivity/zilla/pull/856) ([jfallows](https://github.com/jfallows))

## [0.9.71](https://github.com/aklivity/zilla/tree/0.9.71) (2024-03-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.70...0.9.71)

**Fixed bugs:**

- Zilla Quickstart gRPC RouteGuide service hangs after lots of messages [\#719](https://github.com/aklivity/zilla/issues/719) ([vordimous](https://github.com/vordimous))
- Openapi and asyncapi parsers throw a null pointer when a none 0 patch version is used. [\#841](https://github.com/aklivity/zilla/issues/841) ([vordimous](https://github.com/vordimous))

**Closed issues:**

- Support remote logging of events via `otlp` [\#785](https://github.com/aklivity/zilla/issues/785) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Add Apicurio catalog [\#827](https://github.com/aklivity/zilla/pull/827) ([bmaidics](https://github.com/bmaidics))
- Add event logs to open telemetry exporter [\#839](https://github.com/aklivity/zilla/pull/839) ([attilakreiner](https://github.com/attilakreiner))
- Fix mosquitto\_pub fails validation with a valid message [\#840](https://github.com/aklivity/zilla/pull/840) ([bmaidics](https://github.com/bmaidics))
- Fix patch detection in openapi and asyncapi [\#843](https://github.com/aklivity/zilla/pull/843) ([akrambek](https://github.com/akrambek))
- Implement JoinGroup request as first class stream [\#844](https://github.com/aklivity/zilla/pull/844) ([akrambek](https://github.com/akrambek))
- CacheProduceIT.shouldRejectMessageValues nondeterministic failure fix [\#845](https://github.com/aklivity/zilla/pull/845) ([ankitk-me](https://github.com/ankitk-me))
- Fix binding metadata for composite bindings [\#847](https://github.com/aklivity/zilla/pull/847) ([attilakreiner](https://github.com/attilakreiner))
- Use correct offset when response has no record set [\#849](https://github.com/aklivity/zilla/pull/849) ([akrambek](https://github.com/akrambek))
- Support verbose output of internally generated composite namespaces [\#850](https://github.com/aklivity/zilla/pull/850) ([jfallows](https://github.com/jfallows))
- Add key filter support in openapi asyncapi mapping [\#851](https://github.com/aklivity/zilla/pull/851) ([akrambek](https://github.com/akrambek))

## [0.9.70](https://github.com/aklivity/zilla/tree/0.9.70) (2024-03-06)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.69...0.9.70)

**Fixed bugs:**

- Zilla crashes when a lot of MQTT clients are connected [\#762](https://github.com/aklivity/zilla/issues/762) ([vordimous](https://github.com/vordimous))
- Using parameter expansion in bash doesn't work in the docker containers.  [\#829](https://github.com/aklivity/zilla/issues/829) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- Refactoring event logs [\#821](https://github.com/aklivity/zilla/pull/821) ([attilakreiner](https://github.com/attilakreiner))
- Fix NPE in connection pool due to race condition [\#828](https://github.com/aklivity/zilla/pull/828) ([akrambek](https://github.com/akrambek))
- Simplify zilla shell script logic for sh on container images [\#831](https://github.com/aklivity/zilla/pull/831) ([jfallows](https://github.com/jfallows))
- Stabilize Asyncapi test with race condition [\#832](https://github.com/aklivity/zilla/pull/832) ([bmaidics](https://github.com/bmaidics))
- Fix options name, port resolving [\#833](https://github.com/aklivity/zilla/pull/833) ([bmaidics](https://github.com/bmaidics))

## [0.9.69](https://github.com/aklivity/zilla/tree/0.9.69) (2024-03-04)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.68...0.9.69)

**Implemented enhancements:**

- Support parameters in KafkaTopicsConfig [\#809](https://github.com/aklivity/zilla/pull/809) ([bmaidics](https://github.com/bmaidics))

**Fixed bugs:**

- Zilla is validating `env` vars before replacing them.  [\#795](https://github.com/aklivity/zilla/issues/795) ([vordimous](https://github.com/vordimous))

**Closed issues:**

- Support local logging of events caused by external actors [\#679](https://github.com/aklivity/zilla/issues/679) ([jfallows](https://github.com/jfallows))
- Support `asyncapi` `mqtt` proxy using `asyncapi.yaml` [\#738](https://github.com/aklivity/zilla/issues/738) ([jfallows](https://github.com/jfallows))
- Support `openapi` `http` proxy using `openapi.yaml` [\#740](https://github.com/aklivity/zilla/issues/740) ([jfallows](https://github.com/jfallows))
- Support `http` to `kafka` proxy using `openapi.yaml` and `asyncapi.yaml` [\#742](https://github.com/aklivity/zilla/issues/742) ([jfallows](https://github.com/jfallows))
- Use dedicated env var to enable Incubator features [\#800](https://github.com/aklivity/zilla/issues/800) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- Support local logging of events [\#755](https://github.com/aklivity/zilla/pull/755) ([attilakreiner](https://github.com/attilakreiner))
- Support asyncapi mqtt proxy using asyncapi.yaml [\#764](https://github.com/aklivity/zilla/pull/764) ([bmaidics](https://github.com/bmaidics))
- Support openapi http proxy using openapi.yaml [\#778](https://github.com/aklivity/zilla/pull/778) ([akrambek](https://github.com/akrambek))
- Zilla is validating env vars before replacing them [\#797](https://github.com/aklivity/zilla/pull/797) ([akrambek](https://github.com/akrambek))
- Fix kafka sasl schema validation to support expressions [\#798](https://github.com/aklivity/zilla/pull/798) ([akrambek](https://github.com/akrambek))
- Support asyncapi http proxy using asyncapi.yaml [\#799](https://github.com/aklivity/zilla/pull/799) ([bmaidics](https://github.com/bmaidics))
- Support k3po ephemeral option [\#801](https://github.com/aklivity/zilla/pull/801) ([akrambek](https://github.com/akrambek))
- Kafka asyncapi client [\#804](https://github.com/aklivity/zilla/pull/804) ([bmaidics](https://github.com/bmaidics))
- Include config exception cause [\#805](https://github.com/aklivity/zilla/pull/805) ([jfallows](https://github.com/jfallows))
- Include qualified vault name on binding [\#806](https://github.com/aklivity/zilla/pull/806) ([jfallows](https://github.com/jfallows))
- Structured models require `catalog` config [\#807](https://github.com/aklivity/zilla/pull/807) ([ankitk-me](https://github.com/ankitk-me))
- Support http to kafka proxy using openapi.yaml and asyncapi.yaml [\#810](https://github.com/aklivity/zilla/pull/810) ([akrambek](https://github.com/akrambek))
- Use env var to add incubator java option [\#811](https://github.com/aklivity/zilla/pull/811) ([vordimous](https://github.com/vordimous))
- Fix kafka client composite resolvedId [\#816](https://github.com/aklivity/zilla/pull/816) ([bmaidics](https://github.com/bmaidics))
- MQTT-Kafka asyncapi proxy [\#818](https://github.com/aklivity/zilla/pull/818) ([bmaidics](https://github.com/bmaidics))
- Add incubating annotation for stdout exporter [\#819](https://github.com/aklivity/zilla/pull/819) ([jfallows](https://github.com/jfallows))
- Fix early flush sending for retained stream [\#822](https://github.com/aklivity/zilla/pull/822) ([bmaidics](https://github.com/bmaidics))
- Fix NPE in KafkaSignalStream [\#823](https://github.com/aklivity/zilla/pull/823) ([bmaidics](https://github.com/bmaidics))
- Asyncapi catalog implementation [\#825](https://github.com/aklivity/zilla/pull/825) ([bmaidics](https://github.com/bmaidics))
- Asyncapi and Openapi bug fixes [\#826](https://github.com/aklivity/zilla/pull/826) ([akrambek](https://github.com/akrambek))

## [0.9.68](https://github.com/aklivity/zilla/tree/0.9.68) (2024-02-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.67...0.9.68)

**Merged pull requests:**

- Require group host and port for `kafka` coordinator-specific streams [\#794](https://github.com/aklivity/zilla/pull/794) ([jfallows](https://github.com/jfallows))

## [0.9.67](https://github.com/aklivity/zilla/tree/0.9.67) (2024-02-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.66...0.9.67)

**Implemented enhancements:**

- Support inbound message transformation from `json` to `protobuf` [\#457](https://github.com/aklivity/zilla/issues/457) ([jfallows](https://github.com/jfallows))
- Support outbound message transformation from `protobuf` to `json` [\#458](https://github.com/aklivity/zilla/issues/458) ([jfallows](https://github.com/jfallows))
- Catalog cache TTL implementation [\#658](https://github.com/aklivity/zilla/pull/658) ([ankitk-me](https://github.com/ankitk-me))

**Fixed bugs:**

- `tls binding` should handle `null` key returned from `vault` [\#395](https://github.com/aklivity/zilla/issues/395) ([jfallows](https://github.com/jfallows))
- `mqtt-kafka` binding uses 2 different consumer groups per `mqtt` client [\#698](https://github.com/aklivity/zilla/issues/698) ([jfallows](https://github.com/jfallows))
- Limit sharding to mqtt 5 [\#760](https://github.com/aklivity/zilla/pull/760) ([bmaidics](https://github.com/bmaidics))
- Zilla crashes when it tries to send flush on retain stream [\#770](https://github.com/aklivity/zilla/issues/770) ([akrambek](https://github.com/akrambek))
- Fix zilla crash when it tries to send flush on retain stream [\#784](https://github.com/aklivity/zilla/pull/784) ([bmaidics](https://github.com/bmaidics))
- TLSv1.3 client handshake stall [\#791](https://github.com/aklivity/zilla/issues/791) ([jfallows](https://github.com/jfallows))

**Closed issues:**

- Support obtaining `protobuf` schemas from `schema registry` for `grpc` services [\#697](https://github.com/aklivity/zilla/issues/697) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Validator Interface Update & Converter Changes [\#533](https://github.com/aklivity/zilla/pull/533) ([ankitk-me](https://github.com/ankitk-me))
- Protobuf Validation & Conversion [\#691](https://github.com/aklivity/zilla/pull/691) ([ankitk-me](https://github.com/ankitk-me))
- Improve mqtt-kafka to use only one kafka consumer group per mqtt client. [\#727](https://github.com/aklivity/zilla/pull/727) ([akrambek](https://github.com/akrambek))
- Catch up dump command with kafka extension changes [\#728](https://github.com/aklivity/zilla/pull/728) ([attilakreiner](https://github.com/attilakreiner))
- migrating from Validator to Converter [\#729](https://github.com/aklivity/zilla/pull/729) ([ankitk-me](https://github.com/ankitk-me))
- Mqtt-kafka single group support cont [\#731](https://github.com/aklivity/zilla/pull/731) ([akrambek](https://github.com/akrambek))
- Qos2 idempotent producer [\#733](https://github.com/aklivity/zilla/pull/733) ([bmaidics](https://github.com/bmaidics))
- Fragment validator interface & implementation [\#735](https://github.com/aklivity/zilla/pull/735) ([ankitk-me](https://github.com/ankitk-me))
- Bump actions/cache from 3 to 4 [\#748](https://github.com/aklivity/zilla/pull/748) ([dependabot[bot]](https://github.com/apps/dependabot))
- Support obtaining protobuf schemas from schema registry for grpc services [\#757](https://github.com/aklivity/zilla/pull/757) ([akrambek](https://github.com/akrambek))
- Json Fragment Validator Implementation [\#761](https://github.com/aklivity/zilla/pull/761) ([ankitk-me](https://github.com/ankitk-me))
- model and view changes [\#763](https://github.com/aklivity/zilla/pull/763) ([ankitk-me](https://github.com/ankitk-me))
- feature/schema-registry catchup with develop [\#765](https://github.com/aklivity/zilla/pull/765) ([ankitk-me](https://github.com/ankitk-me))
- Model specific cache detect schema change update [\#767](https://github.com/aklivity/zilla/pull/767) ([ankitk-me](https://github.com/ankitk-me))
- HTTP response bug fix and other minor refactoring [\#769](https://github.com/aklivity/zilla/pull/769) ([ankitk-me](https://github.com/ankitk-me))
- TTL based cache update cleanup [\#772](https://github.com/aklivity/zilla/pull/772) ([ankitk-me](https://github.com/ankitk-me))
- Refactoring supplyValidator to MqttServerFactory [\#773](https://github.com/aklivity/zilla/pull/773) ([ankitk-me](https://github.com/ankitk-me))
-  Skip invalid Kafka messages during Fetch [\#774](https://github.com/aklivity/zilla/pull/774) ([ankitk-me](https://github.com/ankitk-me))
- update docker-image pom.xml to refer model modules [\#775](https://github.com/aklivity/zilla/pull/775) ([ankitk-me](https://github.com/ankitk-me))
- Refactor to use kafka server config per client network stream… [\#777](https://github.com/aklivity/zilla/pull/777) ([jfallows](https://github.com/jfallows))
- Handle unknown vault keys in tls binding [\#779](https://github.com/aklivity/zilla/pull/779) ([jfallows](https://github.com/jfallows))
- Supply client id by host only, and move defaulting to caller [\#780](https://github.com/aklivity/zilla/pull/780) ([jfallows](https://github.com/jfallows))
-  Log validation failure of HTTP messages \(stdout\) [\#781](https://github.com/aklivity/zilla/pull/781) ([ankitk-me](https://github.com/ankitk-me))
- Align affinity for kafka group coordinator [\#788](https://github.com/aklivity/zilla/pull/788) ([jfallows](https://github.com/jfallows))
- Refactor NamespacedId to public API [\#789](https://github.com/aklivity/zilla/pull/789) ([jfallows](https://github.com/jfallows))
- Support TLSv1.3 handshake completion [\#790](https://github.com/aklivity/zilla/pull/790) ([jfallows](https://github.com/jfallows))
- Simplify TLSv1.3 handshake check [\#792](https://github.com/aklivity/zilla/pull/792) ([jfallows](https://github.com/jfallows))

## [0.9.66](https://github.com/aklivity/zilla/tree/0.9.66) (2024-01-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.65...0.9.66)

**Fixed bugs:**

- Schema validation fails before the `${{env.*}}` parameters have been removed [\#583](https://github.com/aklivity/zilla/issues/583) ([vordimous](https://github.com/vordimous))

**Closed issues:**

- Support incubator features preview in zilla release docker image [\#670](https://github.com/aklivity/zilla/issues/670) ([jfallows](https://github.com/jfallows))
- Support `openapi` `http` response validation [\#684](https://github.com/aklivity/zilla/issues/684) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

-  Implement response validation in http client binding [\#732](https://github.com/aklivity/zilla/pull/732) ([attilakreiner](https://github.com/attilakreiner))
- Support  expression for primitive type in json schema [\#751](https://github.com/aklivity/zilla/pull/751) ([akrambek](https://github.com/akrambek))
- Support incubator features preview in zilla release docker image [\#753](https://github.com/aklivity/zilla/pull/753) ([akrambek](https://github.com/akrambek))
- Fix docker file path [\#756](https://github.com/aklivity/zilla/pull/756) ([akrambek](https://github.com/akrambek))
- Refactor resolvers to support configuration [\#758](https://github.com/aklivity/zilla/pull/758) ([jfallows](https://github.com/jfallows))
- update license exclude path to include both zpmw files [\#759](https://github.com/aklivity/zilla/pull/759) ([vordimous](https://github.com/vordimous))

## [0.9.65](https://github.com/aklivity/zilla/tree/0.9.65) (2024-01-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.64...0.9.65)

**Implemented enhancements:**

- Handle data fragmentation for MQTT binding [\#282](https://github.com/aklivity/zilla/issues/282) ([bmaidics](https://github.com/bmaidics))
- Add the option to route by `port` in the `tls` binding [\#564](https://github.com/aklivity/zilla/issues/564) ([vordimous](https://github.com/vordimous))
- Support MQTT fragmented messages [\#651](https://github.com/aklivity/zilla/pull/651) ([bmaidics](https://github.com/bmaidics))
- Add `sse`, `ws`, `fs` extension parsing to `dump` command [\#660](https://github.com/aklivity/zilla/pull/660) ([attilakreiner](https://github.com/attilakreiner))
- separating publish streams based on qos [\#726](https://github.com/aklivity/zilla/pull/726) ([bmaidics](https://github.com/bmaidics))

**Fixed bugs:**

- http2.network.ConnectionManagementIT.serverSent100kMessage test fails sporadically due to race [\#134](https://github.com/aklivity/zilla/issues/134) ([akrambek](https://github.com/akrambek))
- Mqtt session takeover is not working when the second client connects to the same Zilla instance [\#620](https://github.com/aklivity/zilla/issues/620) ([bmaidics](https://github.com/bmaidics))
- Handle large message in grpc binding [\#648](https://github.com/aklivity/zilla/issues/648) ([akrambek](https://github.com/akrambek))
- Kafka Merge is getting stall because of intermediate partition offset state [\#666](https://github.com/aklivity/zilla/issues/666) ([akrambek](https://github.com/akrambek))
- connection pool stops handling signals after while causing mqtt client to hang [\#667](https://github.com/aklivity/zilla/issues/667) ([akrambek](https://github.com/akrambek))
- Send disconnect even without mqtt reset extension [\#689](https://github.com/aklivity/zilla/pull/689) ([bmaidics](https://github.com/bmaidics))
- Optimize memory allocation for mqtt-kafka offset tracking [\#694](https://github.com/aklivity/zilla/pull/694) ([bmaidics](https://github.com/bmaidics))
- Fix tcp flow control issue [\#704](https://github.com/aklivity/zilla/pull/704) ([bmaidics](https://github.com/bmaidics))
- Http1 server not progressing after reaching full buffer slot size [\#715](https://github.com/aklivity/zilla/issues/715) ([akrambek](https://github.com/akrambek))

**Closed issues:**

- Simplify kafka client bootstrap server names and ports config [\#619](https://github.com/aklivity/zilla/issues/619) ([jfallows](https://github.com/jfallows))
- Prototype composite binding support with nested namespaces [\#685](https://github.com/aklivity/zilla/issues/685) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Bump org.eclipse:yasson from 2.0.3 to 3.0.3 [\#346](https://github.com/aklivity/zilla/pull/346) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump com.github.biboudis:jmh-profilers from 0.1.3 to 0.1.4 [\#385](https://github.com/aklivity/zilla/pull/385) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.slf4j:slf4j-simple from 1.7.21 to 2.0.9 [\#392](https://github.com/aklivity/zilla/pull/392) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump eclipse-temurin from 20-jdk to 21-jdk in /cloud/docker-image/src/main/docker/incubator [\#505](https://github.com/aklivity/zilla/pull/505) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump eclipse-temurin from 20-alpine to 21-alpine in /cloud/docker-image/src/main/docker/release [\#506](https://github.com/aklivity/zilla/pull/506) ([dependabot[bot]](https://github.com/apps/dependabot))
- Feature/tls ports [\#591](https://github.com/aklivity/zilla/pull/591) ([lukefallows](https://github.com/lukefallows))
- Handle large message in grpc [\#649](https://github.com/aklivity/zilla/pull/649) ([akrambek](https://github.com/akrambek))
- Release kafka connection pool budget [\#659](https://github.com/aklivity/zilla/pull/659) ([akrambek](https://github.com/akrambek))
- Update latest and stable offset if it was in stabilizing state [\#661](https://github.com/aklivity/zilla/pull/661) ([akrambek](https://github.com/akrambek))
- Bump org.hamcrest:hamcrest-library from 1.3 to 2.2 [\#663](https://github.com/aklivity/zilla/pull/663) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.slf4j:slf4j-api from 1.7.36 to 2.0.10 [\#664](https://github.com/aklivity/zilla/pull/664) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.mockito:mockito-core from 5.3.1 to 5.8.0 [\#665](https://github.com/aklivity/zilla/pull/665) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump com.guicedee.services:commons-collections4 from 1.1.0.7 to 1.2.2.1 [\#672](https://github.com/aklivity/zilla/pull/672) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump jmh.version from 1.12 to 1.37 [\#673](https://github.com/aklivity/zilla/pull/673) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump jakarta.json:jakarta.json-api from 2.0.1 to 2.1.3 [\#674](https://github.com/aklivity/zilla/pull/674) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump junit.version from 5.8.2 to 5.10.1 [\#686](https://github.com/aklivity/zilla/pull/686) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump commons-cli:commons-cli from 1.3.1 to 1.6.0 [\#687](https://github.com/aklivity/zilla/pull/687) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.moditect:moditect-maven-plugin from 1.0.0.Final to 1.1.0 [\#688](https://github.com/aklivity/zilla/pull/688) ([dependabot[bot]](https://github.com/apps/dependabot))
- Remove wrong state assignment in the group cache [\#692](https://github.com/aklivity/zilla/pull/692) ([akrambek](https://github.com/akrambek))
- Reject stream if deferred is not set for the fragmented message [\#693](https://github.com/aklivity/zilla/pull/693) ([akrambek](https://github.com/akrambek))
- Add mqtt extension parsing to dump command [\#695](https://github.com/aklivity/zilla/pull/695) ([attilakreiner](https://github.com/attilakreiner))
- Reset back initial max once ack is fully caught up with seq [\#696](https://github.com/aklivity/zilla/pull/696) ([akrambek](https://github.com/akrambek))
- Refactor dispatch agent [\#699](https://github.com/aklivity/zilla/pull/699) ([jfallows](https://github.com/jfallows))
- Unnecessary deferred value causes the connection to stall [\#700](https://github.com/aklivity/zilla/pull/700) ([akrambek](https://github.com/akrambek))
- Bump org.jacoco:jacoco-maven-plugin from 0.8.10 to 0.8.11 [\#701](https://github.com/aklivity/zilla/pull/701) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.codehaus.mojo:exec-maven-plugin from 3.1.0 to 3.1.1 [\#703](https://github.com/aklivity/zilla/pull/703) ([dependabot[bot]](https://github.com/apps/dependabot))
- Add kafka extension parsing to dump command [\#706](https://github.com/aklivity/zilla/pull/706) ([attilakreiner](https://github.com/attilakreiner))
- Align tcp net read window [\#709](https://github.com/aklivity/zilla/pull/709) ([jfallows](https://github.com/jfallows))
- Simplify kafka client bootstrap server names and ports config [\#710](https://github.com/aklivity/zilla/pull/710) ([akrambek](https://github.com/akrambek))
- Bump org.apache.maven.plugins:maven-compiler-plugin from 3.11.0 to 3.12.1 [\#711](https://github.com/aklivity/zilla/pull/711) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.maven:maven from 3.9.4 to 3.9.6 [\#712](https://github.com/aklivity/zilla/pull/712) ([dependabot[bot]](https://github.com/apps/dependabot))
- Http1 server not progressing after reaching full buffer slot size [\#714](https://github.com/aklivity/zilla/pull/714) ([akrambek](https://github.com/akrambek))
- Bump byteman.version from 4.0.21 to 4.0.22 [\#717](https://github.com/aklivity/zilla/pull/717) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump com.fasterxml.jackson.dataformat:jackson-dataformat-yaml from 2.15.2 to 2.16.1 [\#718](https://github.com/aklivity/zilla/pull/718) ([dependabot[bot]](https://github.com/apps/dependabot))
- Ignore line length check for import and package statements [\#720](https://github.com/aklivity/zilla/pull/720) ([jfallows](https://github.com/jfallows))
- Suppress checkstyle for generated sources [\#721](https://github.com/aklivity/zilla/pull/721) ([jfallows](https://github.com/jfallows))
- Add amqp extension parsing to dump command [\#723](https://github.com/aklivity/zilla/pull/723) ([attilakreiner](https://github.com/attilakreiner))
- Support composite binding config [\#737](https://github.com/aklivity/zilla/pull/737) ([jfallows](https://github.com/jfallows))
- Bump ubuntu from jammy-20231128 to jammy-20240111 in /cloud/docker-image/src/main/docker/release [\#746](https://github.com/aklivity/zilla/pull/746) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump ubuntu from jammy-20231128 to jammy-20240111 in /cloud/docker-image/src/main/docker/incubator [\#747](https://github.com/aklivity/zilla/pull/747) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.64](https://github.com/aklivity/zilla/tree/0.9.64) (2023-12-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.63...0.9.64)

**Merged pull requests:**

- Move everything except fetch and produce to use connection pool [\#656](https://github.com/aklivity/zilla/pull/656) ([akrambek](https://github.com/akrambek))
- MQTT topic sharding [\#657](https://github.com/aklivity/zilla/pull/657) ([jfallows](https://github.com/jfallows))

## [0.9.63](https://github.com/aklivity/zilla/tree/0.9.63) (2023-12-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.62...0.9.63)

**Implemented enhancements:**

- Improve server sent DISCONNECT reasonCodes [\#634](https://github.com/aklivity/zilla/pull/634) ([bmaidics](https://github.com/bmaidics))
- Implement mqtt message expiry [\#640](https://github.com/aklivity/zilla/pull/640) ([bmaidics](https://github.com/bmaidics))
- Add end-to-end testing for the `dump` command [\#646](https://github.com/aklivity/zilla/pull/646) ([attilakreiner](https://github.com/attilakreiner))
- Add grpc extension parsing to the dump command [\#652](https://github.com/aklivity/zilla/pull/652) ([attilakreiner](https://github.com/attilakreiner))

**Fixed bugs:**

- Mqtt-kakfa will message bugfixes [\#644](https://github.com/aklivity/zilla/pull/644) ([bmaidics](https://github.com/bmaidics))
- OffsetFetch Request should connect to the coordinator instead of a random member of cluster [\#653](https://github.com/aklivity/zilla/issues/653) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- Bump ubuntu from jammy-20230916 to jammy-20231128 in /cloud/docker-image/src/main/docker/release [\#607](https://github.com/aklivity/zilla/pull/607) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump ubuntu from jammy-20230916 to jammy-20231128 in /cloud/docker-image/src/main/docker/incubator [\#608](https://github.com/aklivity/zilla/pull/608) ([dependabot[bot]](https://github.com/apps/dependabot))
- Add jumbograms and proxy extension parsing to dump command [\#635](https://github.com/aklivity/zilla/pull/635) ([attilakreiner](https://github.com/attilakreiner))
- Json schema errors [\#638](https://github.com/aklivity/zilla/pull/638) ([vordimous](https://github.com/vordimous))
- Fix `java.util.MissingFormatArgumentException` when using Kafka debugging. [\#639](https://github.com/aklivity/zilla/pull/639) ([voutilad](https://github.com/voutilad))
- Bump github/codeql-action from 2 to 3 [\#643](https://github.com/aklivity/zilla/pull/643) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/upload-artifact from 3 to 4 [\#645](https://github.com/aklivity/zilla/pull/645) ([dependabot[bot]](https://github.com/apps/dependabot))
- OffsetFetch Request should connect to the coordinator instead of a random member of cluster  [\#654](https://github.com/aklivity/zilla/pull/654) ([akrambek](https://github.com/akrambek))
- Fix static field [\#655](https://github.com/aklivity/zilla/pull/655) ([akrambek](https://github.com/akrambek))

## [0.9.62](https://github.com/aklivity/zilla/tree/0.9.62) (2023-12-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.61...0.9.62)

**Merged pull requests:**

- Bump alpine from 3.18.5 to 3.19.0 in /cloud/docker-image/src/main/docker/release [\#626](https://github.com/aklivity/zilla/pull/626) ([dependabot[bot]](https://github.com/apps/dependabot))
- Zpm install instrument [\#632](https://github.com/aklivity/zilla/pull/632) ([jfallows](https://github.com/jfallows))
- Support ability to connect to specific kafka cluster node hostname [\#633](https://github.com/aklivity/zilla/pull/633) ([akrambek](https://github.com/akrambek))
- Reinitiate initialId and replyId on  mqtt session reconnection [\#636](https://github.com/aklivity/zilla/pull/636) ([akrambek](https://github.com/akrambek))

## [0.9.61](https://github.com/aklivity/zilla/tree/0.9.61) (2023-12-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.60...0.9.61)

**Implemented enhancements:**

- Enhance inspection of internal streams [\#154](https://github.com/aklivity/zilla/issues/154) ([jfallows](https://github.com/jfallows))
- Kafka GRPC consumer Group Support [\#597](https://github.com/aklivity/zilla/issues/597) ([akrambek](https://github.com/akrambek))

**Fixed bugs:**

- WebSocket inbound `ping` frames are rejected [\#606](https://github.com/aklivity/zilla/issues/606) ([jfallows](https://github.com/jfallows))
- Follow kafka consumer protocol data structure for userdata parsing [\#617](https://github.com/aklivity/zilla/issues/617) ([akrambek](https://github.com/akrambek))
- Group Coordinator sasl scram doesn't have complete full handshake [\#624](https://github.com/aklivity/zilla/issues/624) ([akrambek](https://github.com/akrambek))
- Fix encoding error when no properties defined by the client [\#627](https://github.com/aklivity/zilla/pull/627) ([bmaidics](https://github.com/bmaidics))

**Closed issues:**

- MQTT client is disconnected and cannot reconnect after sending message [\#623](https://github.com/aklivity/zilla/issues/623) ([gavinheale](https://github.com/gavinheale))

**Merged pull requests:**

- Enhance inspection of internal streams [\#596](https://github.com/aklivity/zilla/pull/596) ([attilakreiner](https://github.com/attilakreiner))
- Kafka GRPC consumer Group Support [\#598](https://github.com/aklivity/zilla/pull/598) ([akrambek](https://github.com/akrambek))
- Follow kafka consumer protocol data structure for userdata parsing [\#618](https://github.com/aklivity/zilla/pull/618) ([akrambek](https://github.com/akrambek))
- Include kafka client id consistently in all kafka protocol encoders [\#621](https://github.com/aklivity/zilla/pull/621) ([jfallows](https://github.com/jfallows))
- Fix handeling sasl scram error in group coordinator [\#622](https://github.com/aklivity/zilla/pull/622) ([akrambek](https://github.com/akrambek))
- Update kafka client group session timeout defaults [\#625](https://github.com/aklivity/zilla/pull/625) ([jfallows](https://github.com/jfallows))
- Split qos0 and qos12 publish streams, add ISR [\#628](https://github.com/aklivity/zilla/pull/628) ([bmaidics](https://github.com/bmaidics))
- WebSocket inbound ping frames support [\#629](https://github.com/aklivity/zilla/pull/629) ([akrambek](https://github.com/akrambek))

## [0.9.60](https://github.com/aklivity/zilla/tree/0.9.60) (2023-12-04)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.59...0.9.60)

**Implemented enhancements:**

- Generate `http` server request `validators` from `OpenAPI` specification [\#459](https://github.com/aklivity/zilla/issues/459) ([jfallows](https://github.com/jfallows))
- MQTT 3.1.1 implementation [\#582](https://github.com/aklivity/zilla/pull/582) ([bmaidics](https://github.com/bmaidics))
- Consumer group message acknowledgement support [\#588](https://github.com/aklivity/zilla/issues/588) ([akrambek](https://github.com/akrambek))
- Include metadata in merge reply begin ex [\#601](https://github.com/aklivity/zilla/issues/601) ([akrambek](https://github.com/akrambek))
- MQTT subscribe QoS 1 as stateful Kafka fetch with `consumerId` for message delivery retry [\#602](https://github.com/aklivity/zilla/issues/602) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- java.lang.IllegalStateException: missing file for budgets : /var/run/zilla/budgets127 [\#578](https://github.com/aklivity/zilla/issues/578) ([vordimous](https://github.com/vordimous))
- Offset commit request should have next offset instead of consumer message offset [\#592](https://github.com/aklivity/zilla/issues/592) ([akrambek](https://github.com/akrambek))
- the `tls` binding throws NPE if there are no `options` defined [\#612](https://github.com/aklivity/zilla/issues/612) ([vordimous](https://github.com/vordimous))

**Closed issues:**

- `prometheus` schema Port and `tcp` schema Port have different validation [\#569](https://github.com/aklivity/zilla/issues/569) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- Include data payload as hex in the output of log command [\#523](https://github.com/aklivity/zilla/pull/523) ([attilakreiner](https://github.com/attilakreiner))
- Consumer group message acknowledgement support [\#538](https://github.com/aklivity/zilla/pull/538) ([akrambek](https://github.com/akrambek))
- MQTT 3.1.1 support - specs [\#570](https://github.com/aklivity/zilla/pull/570) ([bmaidics](https://github.com/bmaidics))
- Fix mergedReplyBudgetId [\#579](https://github.com/aklivity/zilla/pull/579) ([attilakreiner](https://github.com/attilakreiner))
- Include validation in the `openapi.http.proxy` generator [\#586](https://github.com/aklivity/zilla/pull/586) ([attilakreiner](https://github.com/attilakreiner))
- Fix prometheus exporter schema [\#587](https://github.com/aklivity/zilla/pull/587) ([attilakreiner](https://github.com/attilakreiner))
- Implement QoS 1 and QoS 2 [\#589](https://github.com/aklivity/zilla/pull/589) ([bmaidics](https://github.com/bmaidics))
- Offset commit fixes [\#593](https://github.com/aklivity/zilla/pull/593) ([akrambek](https://github.com/akrambek))
- Bump actions/setup-java from 3 to 4 [\#594](https://github.com/aklivity/zilla/pull/594) ([dependabot[bot]](https://github.com/apps/dependabot))
- Update Helm chart Zilla description [\#595](https://github.com/aklivity/zilla/pull/595) ([vordimous](https://github.com/vordimous))
- Include metadata and partitionOffset into merge reply begin [\#599](https://github.com/aklivity/zilla/pull/599) ([akrambek](https://github.com/akrambek))
- Bump alpine from 3.18.4 to 3.18.5 in /cloud/docker-image/src/main/docker/release [\#600](https://github.com/aklivity/zilla/pull/600) ([dependabot[bot]](https://github.com/apps/dependabot))
- Fix mqtt-kakfa qos1,2 issues [\#609](https://github.com/aklivity/zilla/pull/609) ([bmaidics](https://github.com/bmaidics))
- Fix not closing retained stream [\#610](https://github.com/aklivity/zilla/pull/610) ([bmaidics](https://github.com/bmaidics))
- Tls binding options not required [\#611](https://github.com/aklivity/zilla/pull/611) ([jfallows](https://github.com/jfallows))
- Start from historical messages if no consumer offsets were committed [\#613](https://github.com/aklivity/zilla/pull/613) ([akrambek](https://github.com/akrambek))
- Fix qos12 [\#614](https://github.com/aklivity/zilla/pull/614) ([bmaidics](https://github.com/bmaidics))
- Wait for replyFlush at commit before closing retained stream [\#615](https://github.com/aklivity/zilla/pull/615) ([bmaidics](https://github.com/bmaidics))
- Fix typo [\#616](https://github.com/aklivity/zilla/pull/616) ([bmaidics](https://github.com/bmaidics))

## [0.9.59](https://github.com/aklivity/zilla/tree/0.9.59) (2023-11-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.58...0.9.59)

**Implemented enhancements:**

- Generate `http` server request `validators` from `AsyncAPI` specification [\#460](https://github.com/aklivity/zilla/issues/460) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- MQTT topic routing doesn't correctly reject pub/sub requests [\#572](https://github.com/aklivity/zilla/issues/572) ([vordimous](https://github.com/vordimous))
- Fix MQTT topic routing doesn't correctly reject pub/sub requests [\#573](https://github.com/aklivity/zilla/pull/573) ([bmaidics](https://github.com/bmaidics))
- Fix producing empty message to retained topic [\#577](https://github.com/aklivity/zilla/pull/577) ([bmaidics](https://github.com/bmaidics))

**Closed issues:**

- Empty messages on `retained` topic for all MQTT messages [\#575](https://github.com/aklivity/zilla/issues/575) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- Include validation in the `asyncapi.http.proxy` generator [\#574](https://github.com/aklivity/zilla/pull/574) ([attilakreiner](https://github.com/attilakreiner))
- Fix json validator to also accept arrays [\#576](https://github.com/aklivity/zilla/pull/576) ([attilakreiner](https://github.com/attilakreiner))
- Consumer group session timeout defaults [\#584](https://github.com/aklivity/zilla/pull/584) ([jfallows](https://github.com/jfallows))

## [0.9.58](https://github.com/aklivity/zilla/tree/0.9.58) (2023-11-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.57...0.9.58)

**Fixed bugs:**

- \[MQTT-Kafka\] Exception runtime.binding.mqtt.kafka.internal.types.MqttExpirySignalFW.wrap\(MqttExpirySignalFW.java:45\) [\#563](https://github.com/aklivity/zilla/issues/563) ([akrambek](https://github.com/akrambek))
- Fix IndexOutOfBoundsException when receiving expiry signal [\#567](https://github.com/aklivity/zilla/pull/567) ([bmaidics](https://github.com/bmaidics))

**Merged pull requests:**

- Fix flow conrol bug + indexoutofbound exception [\#568](https://github.com/aklivity/zilla/pull/568) ([bmaidics](https://github.com/bmaidics))
- Integrate http binding with validators [\#571](https://github.com/aklivity/zilla/pull/571) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.57](https://github.com/aklivity/zilla/tree/0.9.57) (2023-11-03)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.56...0.9.57)

**Fixed bugs:**

- `http-kafka` fetch binding returns malformed JSON when the payload is large  [\#528](https://github.com/aklivity/zilla/issues/528) ([vordimous](https://github.com/vordimous))
- \[Connection Pool\] Signaling can trigger exception [\#557](https://github.com/aklivity/zilla/issues/557) ([akrambek](https://github.com/akrambek))
- Gracefully handle out of slot exception in kafka cache client produce [\#558](https://github.com/aklivity/zilla/issues/558) ([akrambek](https://github.com/akrambek))
- \[Connection Pool\] binding.kafka.internal.stream.KafkaClientConnectionPool$KafkaClientConnection.doConnectionWindow\(KafkaClientConnectionPool.java:1318\) [\#565](https://github.com/aklivity/zilla/issues/565) ([akrambek](https://github.com/akrambek))

**Closed issues:**

- Feature: Adding contributors section to the README.md file. [\#545](https://github.com/aklivity/zilla/issues/545) ([Kalyanimhala](https://github.com/Kalyanimhala))

**Merged pull requests:**

- Fix: Added Contribution Section to Readme [\#550](https://github.com/aklivity/zilla/pull/550) ([Kalyanimhala](https://github.com/Kalyanimhala))
- Added Contributors section in readme [\#553](https://github.com/aklivity/zilla/pull/553) ([DhanushNehru](https://github.com/DhanushNehru))
- Handle fragmentation in HttpFetchManyProxy [\#556](https://github.com/aklivity/zilla/pull/556) ([akrambek](https://github.com/akrambek))
- Better handling negative  edge cases in the connection pool [\#560](https://github.com/aklivity/zilla/pull/560) ([akrambek](https://github.com/akrambek))
- Gracefully handle out of slot exception in kafka client produce [\#561](https://github.com/aklivity/zilla/pull/561) ([akrambek](https://github.com/akrambek))
- Fix bootstrap test [\#562](https://github.com/aklivity/zilla/pull/562) ([bmaidics](https://github.com/bmaidics))
- Ignore removing ack before receiving complete response even if the stream reply is closed [\#566](https://github.com/aklivity/zilla/pull/566) ([akrambek](https://github.com/akrambek))

## [0.9.56](https://github.com/aklivity/zilla/tree/0.9.56) (2023-10-31)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.55...0.9.56)

**Implemented enhancements:**

- Distribute MQTT topic space across different Kafka topics [\#426](https://github.com/aklivity/zilla/issues/426) ([jfallows](https://github.com/jfallows))
- Support `extraEnv` in helm chart [\#520](https://github.com/aklivity/zilla/issues/520) ([attilakreiner](https://github.com/attilakreiner))

**Fixed bugs:**

- Unable to write to streams buffer under bidi-stream [\#368](https://github.com/aklivity/zilla/issues/368) ([sneakstarberry](https://github.com/sneakstarberry))
- Fix flow control bug in mqtt-kakfa publish [\#524](https://github.com/aklivity/zilla/pull/524) ([bmaidics](https://github.com/bmaidics))
- Sporadic github action build failures [\#526](https://github.com/aklivity/zilla/issues/526) ([akrambek](https://github.com/akrambek))
- MQTT client connections cause errors/crashes [\#527](https://github.com/aklivity/zilla/issues/527) ([vordimous](https://github.com/vordimous))
- \[Consumer Group\] Race  condition while joining simultaneously to the same group id [\#542](https://github.com/aklivity/zilla/issues/542) ([akrambek](https://github.com/akrambek))
- Unexpected flush causes NPE in connection pool [\#546](https://github.com/aklivity/zilla/issues/546) ([akrambek](https://github.com/akrambek))
- BudgetDebitor fails to claim budget after sometime [\#548](https://github.com/aklivity/zilla/issues/548) ([akrambek](https://github.com/akrambek))
- Etag header field name MUST be converted to lowercase prior to their encoding in HTTP/2 [\#551](https://github.com/aklivity/zilla/issues/551) ([akrambek](https://github.com/akrambek))

**Closed issues:**

- gRPC method call doesn't respond when status code is not OK [\#504](https://github.com/aklivity/zilla/issues/504) ([fgarciamacias](https://github.com/fgarciamacias))

**Merged pull requests:**

- Mqtt topic space [\#493](https://github.com/aklivity/zilla/pull/493) ([bmaidics](https://github.com/bmaidics))
- Client topic space [\#507](https://github.com/aklivity/zilla/pull/507) ([bmaidics](https://github.com/bmaidics))
- Add extraEnv to Deployment in the helm chart [\#511](https://github.com/aklivity/zilla/pull/511) ([attilakreiner](https://github.com/attilakreiner))
- Propagate gRPC status code when not ok [\#519](https://github.com/aklivity/zilla/pull/519) ([jfallows](https://github.com/jfallows))
- Sporadic github action build failure fix [\#522](https://github.com/aklivity/zilla/pull/522) ([akrambek](https://github.com/akrambek))
- Fixed a typo in README.md [\#529](https://github.com/aklivity/zilla/pull/529) ([saakshii12](https://github.com/saakshii12))
- fix typos in README.md [\#532](https://github.com/aklivity/zilla/pull/532) ([shresthasurav](https://github.com/shresthasurav))
- Fix dump command to truncate output file if exists [\#534](https://github.com/aklivity/zilla/pull/534) ([attilakreiner](https://github.com/attilakreiner))
- Adjust padding for larger message header and don't include partial data while computing crc32c  [\#536](https://github.com/aklivity/zilla/pull/536) ([akrambek](https://github.com/akrambek))
- Create an appropriate buffer with the size that accommodates signal frame payload [\#537](https://github.com/aklivity/zilla/pull/537) ([akrambek](https://github.com/akrambek))
- Add "Back to Top" in Readme.md [\#539](https://github.com/aklivity/zilla/pull/539) ([PrajwalGraj](https://github.com/PrajwalGraj))
- Retry sync group request if there is inflight request [\#543](https://github.com/aklivity/zilla/pull/543) ([akrambek](https://github.com/akrambek))
- Use coordinator member list to check if the heartbeat is allowed [\#547](https://github.com/aklivity/zilla/pull/547) ([akrambek](https://github.com/akrambek))
- Don't send window before connection budgetId is assigned [\#549](https://github.com/aklivity/zilla/pull/549) ([akrambek](https://github.com/akrambek))
- Etag header field name MUST be converted to lowercase prior to their encoding in HTTP/2 [\#552](https://github.com/aklivity/zilla/pull/552) ([akrambek](https://github.com/akrambek))
- Fix mqtt connect decoding bug when remainingLenght.size > 1 [\#554](https://github.com/aklivity/zilla/pull/554) ([bmaidics](https://github.com/bmaidics))

## [0.9.55](https://github.com/aklivity/zilla/tree/0.9.55) (2023-10-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.54...0.9.55)

**Implemented enhancements:**

- Use 1-1 helm chart versioning strategy [\#487](https://github.com/aklivity/zilla/issues/487) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Connection pool flowcontrol can trigger exception [\#482](https://github.com/aklivity/zilla/issues/482) ([akrambek](https://github.com/akrambek))
- Not cleaning up group stream on group leave response. [\#491](https://github.com/aklivity/zilla/issues/491) ([akrambek](https://github.com/akrambek))
- Group stream with same group id may get hang [\#500](https://github.com/aklivity/zilla/issues/500) ([akrambek](https://github.com/akrambek))
- 0 for no mqtt session expiry should be mapped to max value for the group stream [\#501](https://github.com/aklivity/zilla/issues/501) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- Feature/m1 docker build support [\#376](https://github.com/aklivity/zilla/pull/376) ([vordimous](https://github.com/vordimous))
- Schema Config Update [\#481](https://github.com/aklivity/zilla/pull/481) ([ankitk-me](https://github.com/ankitk-me))
- Fix publish timeout bug, increase default timeout [\#483](https://github.com/aklivity/zilla/pull/483) ([bmaidics](https://github.com/bmaidics))
- Bump alpine from 3.18.3 to 3.18.4 in /cloud/docker-image/src/main/docker/release [\#484](https://github.com/aklivity/zilla/pull/484) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.avro:avro from 1.11.2 to 1.11.3 [\#486](https://github.com/aklivity/zilla/pull/486) ([dependabot[bot]](https://github.com/apps/dependabot))
- update helm configs so appVersion is used as the tag [\#489](https://github.com/aklivity/zilla/pull/489) ([vordimous](https://github.com/vordimous))
- Connection pool flowcontrol cleanup and minor bug fixes on group [\#490](https://github.com/aklivity/zilla/pull/490) ([akrambek](https://github.com/akrambek))
- Remove stream on group leave response [\#492](https://github.com/aklivity/zilla/pull/492) ([akrambek](https://github.com/akrambek))
- Better handle request with same group id [\#498](https://github.com/aklivity/zilla/pull/498) ([akrambek](https://github.com/akrambek))
- 0 for no mqtt session expiry should be mapped to max integer value for the group stream [\#502](https://github.com/aklivity/zilla/pull/502) ([akrambek](https://github.com/akrambek))

## [0.9.54](https://github.com/aklivity/zilla/tree/0.9.54) (2023-09-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.53...0.9.54)

**Implemented enhancements:**

- Design `zilla.yaml` configuration syntax for schema types [\#310](https://github.com/aklivity/zilla/issues/310) ([jfallows](https://github.com/jfallows))
- Enforce inbound type checking; `kafka cache produce client` [\#312](https://github.com/aklivity/zilla/issues/312) ([jfallows](https://github.com/jfallows))
- Integrate Schema Registry / Karapace [\#404](https://github.com/aklivity/zilla/issues/404) ([jfallows](https://github.com/jfallows))
- Support `inline` catalog for validators [\#453](https://github.com/aklivity/zilla/issues/453) ([jfallows](https://github.com/jfallows))
- Integrate `mqtt` binding with `validators` [\#456](https://github.com/aklivity/zilla/issues/456) ([jfallows](https://github.com/jfallows))
- Generate `mqtt` server publish `validators` from `AsyncAPI` specification [\#461](https://github.com/aklivity/zilla/issues/461) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Additional scenarios in connection pool cleanup can trigger exception [\#475](https://github.com/aklivity/zilla/issues/475) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- API abstraction for SchemaRegistry in Zilla [\#311](https://github.com/aklivity/zilla/pull/311) ([ankitk-me](https://github.com/ankitk-me))
- UTF16 Validation implementation [\#401](https://github.com/aklivity/zilla/pull/401) ([ankitk-me](https://github.com/ankitk-me))
- Schema registry Implementation [\#402](https://github.com/aklivity/zilla/pull/402) ([ankitk-me](https://github.com/ankitk-me))
- Schema registry docker build shaded [\#411](https://github.com/aklivity/zilla/pull/411) ([jfallows](https://github.com/jfallows))
- Schema syntax validation for validator config  [\#412](https://github.com/aklivity/zilla/pull/412) ([attilakreiner](https://github.com/attilakreiner))
- Move validators from binding-kafka to engine [\#415](https://github.com/aklivity/zilla/pull/415) ([attilakreiner](https://github.com/attilakreiner))
- Schema Registry and Kafka Produce Validator [\#434](https://github.com/aklivity/zilla/pull/434) ([ankitk-me](https://github.com/ankitk-me))
- Introduce validators in the http binding [\#435](https://github.com/aklivity/zilla/pull/435) ([attilakreiner](https://github.com/attilakreiner))
- Extract avro validator  [\#440](https://github.com/aklivity/zilla/pull/440) ([attilakreiner](https://github.com/attilakreiner))
- Inline Catalog [\#445](https://github.com/aklivity/zilla/pull/445) ([ankitk-me](https://github.com/ankitk-me))
- Cache support for Schema Registry [\#447](https://github.com/aklivity/zilla/pull/447) ([ankitk-me](https://github.com/ankitk-me))
- Mqtt validator implementation [\#452](https://github.com/aklivity/zilla/pull/452) ([bmaidics](https://github.com/bmaidics))
- Extract core validators [\#463](https://github.com/aklivity/zilla/pull/463) ([attilakreiner](https://github.com/attilakreiner))
- Json Validator [\#466](https://github.com/aklivity/zilla/pull/466) ([ankitk-me](https://github.com/ankitk-me))
- Include validators in the mqtt config generator [\#467](https://github.com/aklivity/zilla/pull/467) ([attilakreiner](https://github.com/attilakreiner))
- Avro validator module fix [\#470](https://github.com/aklivity/zilla/pull/470) ([ankitk-me](https://github.com/ankitk-me))
- Http request validators feature flag [\#472](https://github.com/aklivity/zilla/pull/472) ([jfallows](https://github.com/jfallows))
- Json Validator and Inline Schema fix [\#473](https://github.com/aklivity/zilla/pull/473) ([ankitk-me](https://github.com/ankitk-me))
- Cleanup log statements [\#474](https://github.com/aklivity/zilla/pull/474) ([ankitk-me](https://github.com/ankitk-me))
- Remove streams only related to specific connection for additional scenarios [\#476](https://github.com/aklivity/zilla/pull/476) ([akrambek](https://github.com/akrambek))
- Refactor config command [\#477](https://github.com/aklivity/zilla/pull/477) ([jfallows](https://github.com/jfallows))
- Integrate inline catalog and json validator with mqtt binding [\#479](https://github.com/aklivity/zilla/pull/479) ([jfallows](https://github.com/jfallows))

## [0.9.53](https://github.com/aklivity/zilla/tree/0.9.53) (2023-09-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.52...0.9.53)

**Fixed bugs:**

- Connection cleanup can trigger exception  [\#468](https://github.com/aklivity/zilla/issues/468) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- Release debitor on stream close and remove streams only related to specific connection [\#469](https://github.com/aklivity/zilla/pull/469) ([akrambek](https://github.com/akrambek))

## [0.9.52](https://github.com/aklivity/zilla/tree/0.9.52) (2023-09-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.51...0.9.52)

**Implemented enhancements:**

- Support `kafka` consumer groups [\#215](https://github.com/aklivity/zilla/issues/215) ([jfallows](https://github.com/jfallows))
- Generate `zilla.yaml` from `OpenAPI` specification\(s\) [\#254](https://github.com/aklivity/zilla/issues/254) ([jfallows](https://github.com/jfallows))
- Generate `zilla.yaml` from `AsyncAPI` specification [\#256](https://github.com/aklivity/zilla/issues/256) ([jfallows](https://github.com/jfallows))
- MQTT guard implementation [\#307](https://github.com/aklivity/zilla/pull/307) ([bmaidics](https://github.com/bmaidics))
- Connection pool for `kafka` binding `heartbeat` requests [\#462](https://github.com/aklivity/zilla/issues/462) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Index out of bounds exception with HTTP-Kafka proxy [\#293](https://github.com/aklivity/zilla/issues/293) ([vordimous](https://github.com/vordimous))
- `grpc` server binding sends incorrect `DATA` `flags` for fragmented messages [\#397](https://github.com/aklivity/zilla/issues/397) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Support Kafka consumer groups [\#262](https://github.com/aklivity/zilla/pull/262) ([akrambek](https://github.com/akrambek))
- Mqtt retained feature [\#290](https://github.com/aklivity/zilla/pull/290) ([bmaidics](https://github.com/bmaidics))
- Redirect on mqtt reset using server reference [\#303](https://github.com/aklivity/zilla/pull/303) ([bmaidics](https://github.com/bmaidics))
- Mqtt kafka options [\#304](https://github.com/aklivity/zilla/pull/304) ([bmaidics](https://github.com/bmaidics))
- Mqtt kafka sessions [\#318](https://github.com/aklivity/zilla/pull/318) ([bmaidics](https://github.com/bmaidics))
- Support local zpmw install [\#321](https://github.com/aklivity/zilla/pull/321) ([jfallows](https://github.com/jfallows))
- Ignore heartbeat if the handshake request hasn't completed yet [\#322](https://github.com/aklivity/zilla/pull/322) ([akrambek](https://github.com/akrambek))
- Support zilla.yaml config reader and writer [\#323](https://github.com/aklivity/zilla/pull/323) ([jfallows](https://github.com/jfallows))
- Generate zilla.yaml from an OpenAPI definition [\#324](https://github.com/aklivity/zilla/pull/324) ([attilakreiner](https://github.com/attilakreiner))
- Request-response mqtt-kafka [\#325](https://github.com/aklivity/zilla/pull/325) ([bmaidics](https://github.com/bmaidics))
- Include member count as part of group data ex [\#327](https://github.com/aklivity/zilla/pull/327) ([akrambek](https://github.com/akrambek))
- Default group session timeout  [\#328](https://github.com/aklivity/zilla/pull/328) ([akrambek](https://github.com/akrambek))
- Add hashKey support to merged stream [\#329](https://github.com/aklivity/zilla/pull/329) ([bmaidics](https://github.com/bmaidics))
- Config builders [\#330](https://github.com/aklivity/zilla/pull/330) ([jfallows](https://github.com/jfallows))
- Bump antlr4.version from 4.11.1 to 4.13.0 [\#331](https://github.com/aklivity/zilla/pull/331) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump byteman.version from 4.0.20 to 4.0.21 [\#332](https://github.com/aklivity/zilla/pull/332) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.maven.plugins:maven-source-plugin from 3.0.1 to 3.3.0 [\#333](https://github.com/aklivity/zilla/pull/333) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump com.mycila:license-maven-plugin from 4.1 to 4.2 [\#334](https://github.com/aklivity/zilla/pull/334) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.moditect:moditect-maven-plugin from 1.0.0.RC1 to 1.0.0.Final [\#335](https://github.com/aklivity/zilla/pull/335) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/cache from 2 to 3 [\#336](https://github.com/aklivity/zilla/pull/336) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/checkout from 2 to 3 [\#337](https://github.com/aklivity/zilla/pull/337) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/setup-java from 1 to 3 [\#338](https://github.com/aklivity/zilla/pull/338) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.maven.plugins:maven-compiler-plugin from 3.8.0 to 3.11.0 [\#339](https://github.com/aklivity/zilla/pull/339) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump eclipse-temurin from 17-alpine to 20-alpine in /cloud/docker-image/src/main/docker/release [\#340](https://github.com/aklivity/zilla/pull/340) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump alpine from 3.18.2 to 3.18.3 in /cloud/docker-image/src/main/docker/incubator [\#341](https://github.com/aklivity/zilla/pull/341) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump eclipse-temurin from 17-alpine to 20-alpine in /cloud/docker-image/src/main/docker/incubator [\#342](https://github.com/aklivity/zilla/pull/342) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump alpine from 3.18.2 to 3.18.3 in /cloud/docker-image/src/main/docker/release [\#343](https://github.com/aklivity/zilla/pull/343) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.maven.plugin-tools:maven-plugin-annotations from 3.5 to 3.9.0 [\#344](https://github.com/aklivity/zilla/pull/344) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump io.kokuwa.maven:helm-maven-plugin from 6.6.0 to 6.10.0 [\#345](https://github.com/aklivity/zilla/pull/345) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.sonatype.plexus:plexus-sec-dispatcher from 1.3 to 1.4 [\#347](https://github.com/aklivity/zilla/pull/347) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump io.fabric8:docker-maven-plugin from 0.39.1 to 0.43.2 [\#348](https://github.com/aklivity/zilla/pull/348) ([dependabot[bot]](https://github.com/apps/dependabot))
- Metadata for group merged stream [\#349](https://github.com/aklivity/zilla/pull/349) ([akrambek](https://github.com/akrambek))
- Ignore CacheFetchIT.shouldFetchFilterSyncWithData [\#351](https://github.com/aklivity/zilla/pull/351) ([attilakreiner](https://github.com/attilakreiner))
- Include JDK 20 in build matrix [\#352](https://github.com/aklivity/zilla/pull/352) ([jfallows](https://github.com/jfallows))
- Bump com.squareup:javapoet from 1.9.0 to 1.13.0 [\#355](https://github.com/aklivity/zilla/pull/355) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.maven.plugins:maven-jar-plugin from 3.2.0 to 3.3.0 [\#357](https://github.com/aklivity/zilla/pull/357) ([dependabot[bot]](https://github.com/apps/dependabot))
- Support dynamic behavior injection in config builder fluent API [\#358](https://github.com/aklivity/zilla/pull/358) ([jfallows](https://github.com/jfallows))
- Merge consumer group metadata [\#359](https://github.com/aklivity/zilla/pull/359) ([akrambek](https://github.com/akrambek))
- Bump org.apache.maven:maven-core from 3.6.0 to 3.8.1 [\#361](https://github.com/aklivity/zilla/pull/361) ([dependabot[bot]](https://github.com/apps/dependabot))
- Sanitize zip entry path [\#362](https://github.com/aklivity/zilla/pull/362) ([jfallows](https://github.com/jfallows))
- Send will message as data frame + reject large packets [\#363](https://github.com/aklivity/zilla/pull/363) ([bmaidics](https://github.com/bmaidics))
- Bump junit:junit from 4.13.1 to 4.13.2 [\#365](https://github.com/aklivity/zilla/pull/365) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.maven.plugins:maven-plugin-plugin from 3.5 to 3.9.0 [\#366](https://github.com/aklivity/zilla/pull/366) ([dependabot[bot]](https://github.com/apps/dependabot))
- Mqtt kafka will message delivery [\#367](https://github.com/aklivity/zilla/pull/367) ([bmaidics](https://github.com/bmaidics))
- Generate zilla.yaml from an AsyncAPI definition [\#369](https://github.com/aklivity/zilla/pull/369) ([attilakreiner](https://github.com/attilakreiner))
- Bump org.codehaus.mojo:exec-maven-plugin from 1.6.0 to 3.1.0 [\#370](https://github.com/aklivity/zilla/pull/370) ([dependabot[bot]](https://github.com/apps/dependabot))
- Support config builder for MQTT config [\#372](https://github.com/aklivity/zilla/pull/372) ([jfallows](https://github.com/jfallows))
- Support binding config builder exit [\#373](https://github.com/aklivity/zilla/pull/373) ([jfallows](https://github.com/jfallows))
- Review budget debitors [\#374](https://github.com/aklivity/zilla/pull/374) ([jfallows](https://github.com/jfallows))
- Generate zilla.yaml for asyncapi.mqtt.proxy from an AsyncAPI definition [\#375](https://github.com/aklivity/zilla/pull/375) ([attilakreiner](https://github.com/attilakreiner))
- Bump org.apache.ivy:ivy from 2.5.1 to 2.5.2 in /manager [\#377](https://github.com/aklivity/zilla/pull/377) ([dependabot[bot]](https://github.com/apps/dependabot))
- Mqtt kafka redirect [\#381](https://github.com/aklivity/zilla/pull/381) ([bmaidics](https://github.com/bmaidics))
- Support configuration property definitions for custom type... [\#382](https://github.com/aklivity/zilla/pull/382) ([jfallows](https://github.com/jfallows))
- Fix mqtt-kafka publish bug [\#383](https://github.com/aklivity/zilla/pull/383) ([bmaidics](https://github.com/bmaidics))
- Request data length is non-negative [\#386](https://github.com/aklivity/zilla/pull/386) ([jfallows](https://github.com/jfallows))
- Session expiry [\#387](https://github.com/aklivity/zilla/pull/387) ([bmaidics](https://github.com/bmaidics))
- Merged consumer group support [\#390](https://github.com/aklivity/zilla/pull/390) ([akrambek](https://github.com/akrambek))
- Bump actions/checkout from 3 to 4 [\#393](https://github.com/aklivity/zilla/pull/393) ([dependabot[bot]](https://github.com/apps/dependabot))
- Adapt to consumer group changes [\#394](https://github.com/aklivity/zilla/pull/394) ([bmaidics](https://github.com/bmaidics))
- Support build after local docker zpm install [\#396](https://github.com/aklivity/zilla/pull/396) ([jfallows](https://github.com/jfallows))
- Mqtt client implementation [\#398](https://github.com/aklivity/zilla/pull/398) ([bmaidics](https://github.com/bmaidics))
- Support consumer protocol [\#400](https://github.com/aklivity/zilla/pull/400) ([akrambek](https://github.com/akrambek))
- Remove unused extends OptionsConfig from non-options config classes [\#403](https://github.com/aklivity/zilla/pull/403) ([jfallows](https://github.com/jfallows))
- Consumer related bug fixes [\#405](https://github.com/aklivity/zilla/pull/405) ([akrambek](https://github.com/akrambek))
- Add test to validate merge produce rejection on wrong partition  [\#410](https://github.com/aklivity/zilla/pull/410) ([akrambek](https://github.com/akrambek))
- Fix consumer assignment causing decoding issue [\#414](https://github.com/aklivity/zilla/pull/414) ([akrambek](https://github.com/akrambek))
- Remove default kafka topic names [\#416](https://github.com/aklivity/zilla/pull/416) ([bmaidics](https://github.com/bmaidics))
- Don't end subscribe stream when unsubscribe, no subscription [\#418](https://github.com/aklivity/zilla/pull/418) ([bmaidics](https://github.com/bmaidics))
- Fix finding next partition id [\#419](https://github.com/aklivity/zilla/pull/419) ([akrambek](https://github.com/akrambek))
- Serverref change [\#422](https://github.com/aklivity/zilla/pull/422) ([bmaidics](https://github.com/bmaidics))
- Fix flow control bug [\#423](https://github.com/aklivity/zilla/pull/423) ([akrambek](https://github.com/akrambek))
- Buffer fragmented kafka session signal messages [\#424](https://github.com/aklivity/zilla/pull/424) ([bmaidics](https://github.com/bmaidics))
- Enhance mqtt binding configuration syntax [\#425](https://github.com/aklivity/zilla/pull/425) ([bmaidics](https://github.com/bmaidics))
- Fix known issues in group client [\#428](https://github.com/aklivity/zilla/pull/428) ([akrambek](https://github.com/akrambek))
- Fix flow control issue in kafka-grpc [\#430](https://github.com/aklivity/zilla/pull/430) ([akrambek](https://github.com/akrambek))
- Set init flag for data fragmentation in grpc [\#431](https://github.com/aklivity/zilla/pull/431) ([akrambek](https://github.com/akrambek))
- Add affinity to mqtt server and client binding [\#436](https://github.com/aklivity/zilla/pull/436) ([bmaidics](https://github.com/bmaidics))
- Connection pool for kafka group client [\#438](https://github.com/aklivity/zilla/pull/438) ([akrambek](https://github.com/akrambek))
- Mqtt subscription handling bugfix [\#439](https://github.com/aklivity/zilla/pull/439) ([bmaidics](https://github.com/bmaidics))
- Ensure socket channel has finished connecting before attempting to read [\#441](https://github.com/aklivity/zilla/pull/441) ([jfallows](https://github.com/jfallows))
- Remove unused engine configuration [\#442](https://github.com/aklivity/zilla/pull/442) ([jfallows](https://github.com/jfallows))
- Engine configuration worker capacity [\#443](https://github.com/aklivity/zilla/pull/443) ([jfallows](https://github.com/jfallows))
- Don't close group stream on cluster and describe streams closer [\#444](https://github.com/aklivity/zilla/pull/444) ([akrambek](https://github.com/akrambek))
- Adjust engine backoff strategy configuration [\#446](https://github.com/aklivity/zilla/pull/446) ([jfallows](https://github.com/jfallows))
- Do not include generated subcsriptionId [\#448](https://github.com/aklivity/zilla/pull/448) ([bmaidics](https://github.com/bmaidics))
- Rename config command to generate [\#449](https://github.com/aklivity/zilla/pull/449) ([attilakreiner](https://github.com/attilakreiner))
- Remove clientId from subscribeKey [\#450](https://github.com/aklivity/zilla/pull/450) ([bmaidics](https://github.com/bmaidics))
- Fix implicit subscribe no packetId reconnection [\#451](https://github.com/aklivity/zilla/pull/451) ([bmaidics](https://github.com/bmaidics))
- Mqtt client publish fix [\#464](https://github.com/aklivity/zilla/pull/464) ([bmaidics](https://github.com/bmaidics))

## [0.9.51](https://github.com/aklivity/zilla/tree/0.9.51) (2023-07-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.50...0.9.51)

**Implemented enhancements:**

- Enhance `tcp` binding to route by `port` [\#294](https://github.com/aklivity/zilla/issues/294) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Create OpenTelemetry exporter and refactor code [\#279](https://github.com/aklivity/zilla/pull/279) ([attilakreiner](https://github.com/attilakreiner))
- Support for tcp binding to route by port numbers [\#299](https://github.com/aklivity/zilla/pull/299) ([lukefallows](https://github.com/lukefallows))
- Readme overhaul [\#305](https://github.com/aklivity/zilla/pull/305) ([llukyanov](https://github.com/llukyanov))
- README Formatting and wording changes [\#306](https://github.com/aklivity/zilla/pull/306) ([vordimous](https://github.com/vordimous))
- Fix kafka cache cursor buffer copy [\#317](https://github.com/aklivity/zilla/pull/317) ([bmaidics](https://github.com/bmaidics))

## [0.9.50](https://github.com/aklivity/zilla/tree/0.9.50) (2023-07-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.49...0.9.50)

**Merged pull requests:**

- update Helm logo & details and clean up README [\#273](https://github.com/aklivity/zilla/pull/273) ([vordimous](https://github.com/vordimous))

## [0.9.49](https://github.com/aklivity/zilla/tree/0.9.49) (2023-06-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.48...0.9.49)

**Merged pull requests:**

- KafkaMerged acknowledge flush frame [\#258](https://github.com/aklivity/zilla/pull/258) ([bmaidics](https://github.com/bmaidics))
- Send kafka flush even if data frames were sent to notify client from HISTORICAL to LIVE transition [\#284](https://github.com/aklivity/zilla/pull/284) ([bmaidics](https://github.com/bmaidics))
- Eliminate zilla.json warning if file not present [\#286](https://github.com/aklivity/zilla/pull/286) ([jfallows](https://github.com/jfallows))
- Remove unnecessary cursor assignment [\#288](https://github.com/aklivity/zilla/pull/288) ([akrambek](https://github.com/akrambek))

## [0.9.48](https://github.com/aklivity/zilla/tree/0.9.48) (2023-06-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.47...0.9.48)

**Fixed bugs:**

- CacheFetchIT.shouldReceiveMessagesWithIsolationReadUncommittedWhenAborting fails only on GitHub Actions [\#236](https://github.com/aklivity/zilla/issues/236) ([jfallows](https://github.com/jfallows))
- CacheMergedIT.shouldFetchMergedMessagesWithIsolationReadCommitted fails only on GitHub Actions [\#239](https://github.com/aklivity/zilla/issues/239) ([jfallows](https://github.com/jfallows))

**Closed issues:**

- Null pointer when Headers are null [\#281](https://github.com/aklivity/zilla/issues/281) ([vordimous](https://github.com/vordimous))

**Merged pull requests:**

- Fix the requirements versions in helm chart readme [\#249](https://github.com/aklivity/zilla/pull/249) ([attilakreiner](https://github.com/attilakreiner))
- Update README.md [\#250](https://github.com/aklivity/zilla/pull/250) ([jfallows](https://github.com/jfallows))
- GitHub Actions fix [\#251](https://github.com/aklivity/zilla/pull/251) ([ankitk-me](https://github.com/ankitk-me))
- Cleanup obsolete load counters [\#253](https://github.com/aklivity/zilla/pull/253) ([attilakreiner](https://github.com/attilakreiner))
- CacheMergedIT.shouldFetchMergedMessagesWithIsolationReadCommitted fix [\#260](https://github.com/aklivity/zilla/pull/260) ([ankitk-me](https://github.com/ankitk-me))
- Implement counter value reader in EngineExtContext [\#261](https://github.com/aklivity/zilla/pull/261) ([attilakreiner](https://github.com/attilakreiner))
- Fix mqtt-kafka subscribe stream initial offset [\#270](https://github.com/aklivity/zilla/pull/270) ([bmaidics](https://github.com/bmaidics))
- Bump jose4j from 0.7.10 to 0.9.3 in /runtime/guard-jwt [\#271](https://github.com/aklivity/zilla/pull/271) ([dependabot[bot]](https://github.com/apps/dependabot))
- Allow Kafka merged stream to change fetch filters dynamically [\#272](https://github.com/aklivity/zilla/pull/272) ([bmaidics](https://github.com/bmaidics))
- Fix mqtt decoding bug [\#275](https://github.com/aklivity/zilla/pull/275) ([bmaidics](https://github.com/bmaidics))
- Include failsafe reports for failed builds [\#276](https://github.com/aklivity/zilla/pull/276) ([ankitk-me](https://github.com/ankitk-me))
- Fix bug with histograms in Prometheus exporter [\#278](https://github.com/aklivity/zilla/pull/278) ([attilakreiner](https://github.com/attilakreiner))
- Fix dependencies to run TcpServerBM from command line [\#280](https://github.com/aklivity/zilla/pull/280) ([akrambek](https://github.com/akrambek))
- Fix message fragmentation in sse-kafka and flow control in kafka merge [\#283](https://github.com/aklivity/zilla/pull/283) ([akrambek](https://github.com/akrambek))

## [0.9.47](https://github.com/aklivity/zilla/tree/0.9.47) (2023-05-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.45...0.9.47)

**Merged pull requests:**

- Update README.md of helm-chart [\#248](https://github.com/aklivity/zilla/pull/248) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.45](https://github.com/aklivity/zilla/tree/0.9.45) (2023-05-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.44...0.9.45)

**Implemented enhancements:**

- Generic helm chart [\#242](https://github.com/aklivity/zilla/issues/242) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Generic helm chart [\#230](https://github.com/aklivity/zilla/pull/230) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.44](https://github.com/aklivity/zilla/tree/0.9.44) (2023-05-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.43...0.9.44)

**Implemented enhancements:**

- Design observable metrics configuration syntax [\#100](https://github.com/aklivity/zilla/issues/100) ([jfallows](https://github.com/jfallows))
- Migrate implicit stream open, close, error, bytes metrics to explicit configuration syntax [\#109](https://github.com/aklivity/zilla/issues/109) ([jfallows](https://github.com/jfallows))
- Provide zilla metrics command to report current values locally [\#110](https://github.com/aklivity/zilla/issues/110) ([jfallows](https://github.com/jfallows))
- Support additional http specific metrics [\#111](https://github.com/aklivity/zilla/issues/111) ([jfallows](https://github.com/jfallows))
- Minimize on-stack performance overhead of metrics recording [\#213](https://github.com/aklivity/zilla/issues/213) ([jfallows](https://github.com/jfallows))
- Remove `zilla load` now that we have `zilla metrics` instead [\#214](https://github.com/aklivity/zilla/issues/214) ([jfallows](https://github.com/jfallows))
- Simplify `zilla.yaml` errors on invalid input [\#222](https://github.com/aklivity/zilla/issues/222) ([jfallows](https://github.com/jfallows))
- Support additional grpc specific metrics [\#233](https://github.com/aklivity/zilla/issues/233) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Metrics schema, extensibility, storage and command line support [\#173](https://github.com/aklivity/zilla/pull/173) ([attilakreiner](https://github.com/attilakreiner))
- mqtt binding specs [\#191](https://github.com/aklivity/zilla/pull/191) ([bmaidics](https://github.com/bmaidics))
- Mqtt binding implementation [\#201](https://github.com/aklivity/zilla/pull/201) ([bmaidics](https://github.com/bmaidics))
- Engine support to exporters [\#202](https://github.com/aklivity/zilla/pull/202) ([attilakreiner](https://github.com/attilakreiner))
- Prometheus Exporter [\#203](https://github.com/aklivity/zilla/pull/203) ([attilakreiner](https://github.com/attilakreiner))
-  Fix metrics command, find layout files in the engine dir [\#204](https://github.com/aklivity/zilla/pull/204) ([attilakreiner](https://github.com/attilakreiner))
- Minimize performance overhead for metric collection [\#217](https://github.com/aklivity/zilla/pull/217) ([attilakreiner](https://github.com/attilakreiner))
- Remove zilla load command [\#223](https://github.com/aklivity/zilla/pull/223) ([attilakreiner](https://github.com/attilakreiner))
- Introducing Stream Direction to Optimize Metric Collection [\#224](https://github.com/aklivity/zilla/pull/224) ([attilakreiner](https://github.com/attilakreiner))
- Add http.active.requests and http.duration metrics [\#227](https://github.com/aklivity/zilla/pull/227) ([attilakreiner](https://github.com/attilakreiner))
- Mqtt-kafka binding implementation [\#235](https://github.com/aklivity/zilla/pull/235) ([bmaidics](https://github.com/bmaidics))
- Introduce grpc metrics [\#241](https://github.com/aklivity/zilla/pull/241) ([attilakreiner](https://github.com/attilakreiner))
- Fix grpc last message id decoding [\#243](https://github.com/aklivity/zilla/pull/243) ([akrambek](https://github.com/akrambek))
- telemetry metrics feature baseline [\#244](https://github.com/aklivity/zilla/pull/244) ([jfallows](https://github.com/jfallows))
- Simplify zilla yaml errors [\#245](https://github.com/aklivity/zilla/pull/245) ([jfallows](https://github.com/jfallows))
- Readme Updates [\#247](https://github.com/aklivity/zilla/pull/247) ([llukyanov](https://github.com/llukyanov))

## [0.9.43](https://github.com/aklivity/zilla/tree/0.9.43) (2023-05-09)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.42...0.9.43)

**Implemented enhancements:**

- Simplify configuration for JWT identity provider signing keys [\#68](https://github.com/aklivity/zilla/issues/68) ([jfallows](https://github.com/jfallows))
- Enhance idempotency support for HTTP-Kafka binding [\#113](https://github.com/aklivity/zilla/issues/113) ([jfallows](https://github.com/jfallows))
- Support dynamic `zilla` configuration via `https` [\#139](https://github.com/aklivity/zilla/issues/139) ([jfallows](https://github.com/jfallows))
- When starting up in verbose mode make sure there is a newline after printing the config [\#157](https://github.com/aklivity/zilla/issues/157) ([attilakreiner](https://github.com/attilakreiner))
- Convert zilla spec config .json files to .yaml extension and syntax [\#164](https://github.com/aklivity/zilla/issues/164) ([jfallows](https://github.com/jfallows))
- Enhance kafka binding to notify transition from historical to live messages [\#172](https://github.com/aklivity/zilla/issues/172) ([jfallows](https://github.com/jfallows))
- Refactor core.idl with originId and routedId [\#195](https://github.com/aklivity/zilla/pull/195) ([jfallows](https://github.com/jfallows))
- Support `eager` evaluation of all `kafka` filters and indicate which filters matched [\#209](https://github.com/aklivity/zilla/issues/209) ([jfallows](https://github.com/jfallows))
- Enhance `grpc` related binding configuration [\#226](https://github.com/aklivity/zilla/issues/226) ([jfallows](https://github.com/jfallows))
- Support gRPC proxying [\#171](https://github.com/aklivity/zilla/issues/171) ([jfallows](https://github.com/jfallows))
- Support gRPC Kafka mapping [\#184](https://github.com/aklivity/zilla/issues/184) ([jfallows](https://github.com/jfallows))
- Support Kafka gRPC mapping [\#185](https://github.com/aklivity/zilla/issues/185) ([jfallows](https://github.com/jfallows))
- Support gRPC Kafka server streaming [\#186](https://github.com/aklivity/zilla/issues/186) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- CacheMergedIT.shouldFetchMergedMessageValues fails only on GitHub Actions [\#131](https://github.com/aklivity/zilla/issues/131) ([jfallows](https://github.com/jfallows))
- Flyweight wrapping error race condition [\#146](https://github.com/aklivity/zilla/issues/146) ([jfallows](https://github.com/jfallows))
- Add log + rollback on reconfigure errors [\#178](https://github.com/aklivity/zilla/pull/178) ([bmaidics](https://github.com/bmaidics))

**Closed issues:**

- Migrate `zilla` README from `zilla.json` to `zilla.yaml` [\#159](https://github.com/aklivity/zilla/issues/159) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Add schema for specifying an OpenID provider discovery endpoint [\#106](https://github.com/aklivity/zilla/pull/106) ([Alfusainey](https://github.com/Alfusainey))
- Dynamic config [\#141](https://github.com/aklivity/zilla/pull/141) ([bmaidics](https://github.com/bmaidics))
- Http dynamic configuration [\#156](https://github.com/aklivity/zilla/pull/156) ([bmaidics](https://github.com/bmaidics))
- Add a newline to the end of the config if it is not present [\#158](https://github.com/aklivity/zilla/pull/158) ([attilakreiner](https://github.com/attilakreiner))
- Flyweight wrapping error race condition fix [\#161](https://github.com/aklivity/zilla/pull/161) ([ankitk-me](https://github.com/ankitk-me))
- Update zilla readme to address yaml changes [\#162](https://github.com/aklivity/zilla/pull/162) ([ankitk-me](https://github.com/ankitk-me))
- Convert zilla spec config .json files to .yaml extension and syntax [\#165](https://github.com/aklivity/zilla/pull/165) ([ankitk-me](https://github.com/ankitk-me))
- Provide http\(s\) configuration server for zilla.yaml [\#166](https://github.com/aklivity/zilla/pull/166) ([bmaidics](https://github.com/bmaidics))
- Ignore shouldReconfigureWhenModifiedUsingComplexSymlinkChain [\#169](https://github.com/aklivity/zilla/pull/169) ([bmaidics](https://github.com/bmaidics))
- `grpc` binding spec and implementation [\#174](https://github.com/aklivity/zilla/pull/174) ([akrambek](https://github.com/akrambek))
- Support verbose schema output on startup [\#175](https://github.com/aklivity/zilla/pull/175) ([jfallows](https://github.com/jfallows))
- Enhance kafka binding to notify transition from historical to live messages [\#181](https://github.com/aklivity/zilla/pull/181) ([ankitk-me](https://github.com/ankitk-me))
- `grpc-kafka` mapping implementation [\#187](https://github.com/aklivity/zilla/pull/187) ([akrambek](https://github.com/akrambek))
- Fix incorrect Assertion in KafkaFunctionsTest [\#192](https://github.com/aklivity/zilla/pull/192) ([bmaidics](https://github.com/bmaidics))
- Change DumpCommandTest [\#194](https://github.com/aklivity/zilla/pull/194) ([bmaidics](https://github.com/bmaidics))
- Fix typo and add missing dependency [\#197](https://github.com/aklivity/zilla/pull/197) ([akrambek](https://github.com/akrambek))
- `kafka-grpc` mapping [\#198](https://github.com/aklivity/zilla/pull/198) ([akrambek](https://github.com/akrambek))
- Support `options` in grpc-kafka [\#199](https://github.com/aklivity/zilla/pull/199) ([akrambek](https://github.com/akrambek))
- Grpc one way streaming [\#205](https://github.com/aklivity/zilla/pull/205) ([akrambek](https://github.com/akrambek))
- Include license header check [\#206](https://github.com/aklivity/zilla/pull/206) ([jfallows](https://github.com/jfallows))
- Fix imports and null filter if both key and headers are not specified [\#208](https://github.com/aklivity/zilla/pull/208) ([akrambek](https://github.com/akrambek))
- Fix number of signals in Kafka Grpc [\#210](https://github.com/aklivity/zilla/pull/210) ([akrambek](https://github.com/akrambek))
- Support eager evaluation of all Kafka filters [\#212](https://github.com/aklivity/zilla/pull/212) ([ankitk-me](https://github.com/ankitk-me))
- Encode kafka progress as last message id [\#216](https://github.com/aklivity/zilla/pull/216) ([akrambek](https://github.com/akrambek))
- Move kafka-grpc options for grpc to with section of config [\#219](https://github.com/aklivity/zilla/pull/219) ([akrambek](https://github.com/akrambek))
- CacheMergedIT.shouldFetchMergedMessageValues failure on GitHub Actions fix [\#221](https://github.com/aklivity/zilla/pull/221) ([ankitk-me](https://github.com/ankitk-me))
- `grpc-kafka` feature baseline [\#225](https://github.com/aklivity/zilla/pull/225) ([jfallows](https://github.com/jfallows))
- Enhance config [\#228](https://github.com/aklivity/zilla/pull/228) ([akrambek](https://github.com/akrambek))
- Consumer group kafka function support [\#232](https://github.com/aklivity/zilla/pull/232) ([akrambek](https://github.com/akrambek))
- Fix typo in flow control, use `responseMax` instead of `requestMax` [\#237](https://github.com/aklivity/zilla/pull/237) ([akrambek](https://github.com/akrambek))
- Fix NPE caused by overrides [\#238](https://github.com/aklivity/zilla/pull/238) ([akrambek](https://github.com/akrambek))

## [0.9.42](https://github.com/aklivity/zilla/tree/0.9.42) (2023-01-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.41...0.9.42)

**Implemented enhancements:**

- Support YAML syntax for Zilla configuration [\#144](https://github.com/aklivity/zilla/issues/144) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Adding yaml support for zilla config [\#150](https://github.com/aklivity/zilla/pull/150) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.41](https://github.com/aklivity/zilla/tree/0.9.41) (2023-01-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.40...0.9.41)

**Merged pull requests:**

- Pass correct flag for delete payload [\#155](https://github.com/aklivity/zilla/pull/155) ([akrambek](https://github.com/akrambek))

## [0.9.40](https://github.com/aklivity/zilla/tree/0.9.40) (2023-01-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.37...0.9.40)

**Implemented enhancements:**

- Support `{{ mustache }}` syntax in zilla.json [\#91](https://github.com/aklivity/zilla/issues/91) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Adding support for Expression Resolver [\#143](https://github.com/aklivity/zilla/pull/143) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.37](https://github.com/aklivity/zilla/tree/0.9.37) (2023-01-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.35...0.9.37)

**Implemented enhancements:**

- Follow standard layout for zilla files in docker image [\#140](https://github.com/aklivity/zilla/issues/140) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- NPE when reloading browser page, mid produce [\#151](https://github.com/aklivity/zilla/issues/151) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Modify the layout of zilla files on the docker image [\#142](https://github.com/aklivity/zilla/pull/142) ([attilakreiner](https://github.com/attilakreiner))
- Prevent NPE when kafka produce is canceled … [\#152](https://github.com/aklivity/zilla/pull/152) ([jfallows](https://github.com/jfallows))

## [0.9.35](https://github.com/aklivity/zilla/tree/0.9.35) (2023-01-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.34...0.9.35)

**Fixed bugs:**

- kafka cache treats non-compacted topics as compacted [\#147](https://github.com/aklivity/zilla/issues/147) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Skip ancestor messages for compacted topic configuration only [\#148](https://github.com/aklivity/zilla/pull/148) ([jfallows](https://github.com/jfallows))
- Resolve stall for large files served over HTTP/2 [\#149](https://github.com/aklivity/zilla/pull/149) ([jfallows](https://github.com/jfallows))

## [0.9.34](https://github.com/aklivity/zilla/tree/0.9.34) (2023-01-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.33...0.9.34)

**Implemented enhancements:**

- Support guarded identities in http-kafka and sse-kafka [\#145](https://github.com/aklivity/zilla/pull/145) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Add link to http proxy example [\#137](https://github.com/aklivity/zilla/pull/137) ([akrambek](https://github.com/akrambek))

## [0.9.33](https://github.com/aklivity/zilla/tree/0.9.33) (2022-12-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.32...0.9.33)

**Merged pull requests:**

- Http2 client support [\#127](https://github.com/aklivity/zilla/pull/127) ([akrambek](https://github.com/akrambek))
- Added Info & link for SASL/SCRAM Examples [\#132](https://github.com/aklivity/zilla/pull/132) ([ankitk-me](https://github.com/ankitk-me))
- Upgrade byteman and mockito to support JDK 19 class file format [\#133](https://github.com/aklivity/zilla/pull/133) ([jfallows](https://github.com/jfallows))

## [0.9.32](https://github.com/aklivity/zilla/tree/0.9.32) (2022-11-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.31...0.9.32)

**Implemented enhancements:**

- Implement `zilla dump` command similar to `tcpdump` [\#114](https://github.com/aklivity/zilla/issues/114) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Implement zilla dump command similar to tcpdump [\#121](https://github.com/aklivity/zilla/pull/121) ([bmaidics](https://github.com/bmaidics))
- Support SASL SCRAM authentication [\#126](https://github.com/aklivity/zilla/pull/126) ([ankitk-me](https://github.com/ankitk-me))
- Reduce pcap frame encoding overhead [\#129](https://github.com/aklivity/zilla/pull/129) ([jfallows](https://github.com/jfallows))
- Use try-with-resources to manage lifecycle of writer [\#130](https://github.com/aklivity/zilla/pull/130) ([jfallows](https://github.com/jfallows))

## [0.9.31](https://github.com/aklivity/zilla/tree/0.9.31) (2022-11-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.30...0.9.31)

**Implemented enhancements:**

- Remove merged from kafka binding configuration [\#108](https://github.com/aklivity/zilla/issues/108) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Zilla build fails on timeout [\#102](https://github.com/aklivity/zilla/issues/102) ([Alfusainey](https://github.com/Alfusainey))
- Error running http.kafka.oneway from zilla-examples [\#117](https://github.com/aklivity/zilla/issues/117) ([attilakreiner](https://github.com/attilakreiner))

**Merged pull requests:**

- BugFix: process configURL if the protocol is http [\#101](https://github.com/aklivity/zilla/pull/101) ([Alfusainey](https://github.com/Alfusainey))
- Document how to run the benchmark with Java 16+ [\#103](https://github.com/aklivity/zilla/pull/103) ([antonmry](https://github.com/antonmry))
- Increase frame count for byteman to find matching call stack method [\#104](https://github.com/aklivity/zilla/pull/104) ([jfallows](https://github.com/jfallows))
- Replace HttpRequest with buffer slot [\#105](https://github.com/aklivity/zilla/pull/105) ([akrambek](https://github.com/akrambek))
- Upgrade ANTLR version [\#118](https://github.com/aklivity/zilla/pull/118) ([jfallows](https://github.com/jfallows))
- Mark flyweight plugin goals @threadSafe [\#119](https://github.com/aklivity/zilla/pull/119) ([jfallows](https://github.com/jfallows))
- Fix NPE caused by no KafkaMergedDataEx present on the DATA frame... [\#120](https://github.com/aklivity/zilla/pull/120) ([attilakreiner](https://github.com/attilakreiner))
- Remove merged from kafka binding configuration [\#122](https://github.com/aklivity/zilla/pull/122) ([ankitk-me](https://github.com/ankitk-me))
- Adjust expectations to handle the case where we extend window max … [\#125](https://github.com/aklivity/zilla/pull/125) ([jfallows](https://github.com/jfallows))
- Fix uint32 as array length [\#128](https://github.com/aklivity/zilla/pull/128) ([akrambek](https://github.com/akrambek))

## [0.9.30](https://github.com/aklivity/zilla/tree/0.9.30) (2022-09-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.29...0.9.30)

**Fixed bugs:**

- Handle produce error [\#97](https://github.com/aklivity/zilla/pull/97) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- Support reset extension in test engine [\#96](https://github.com/aklivity/zilla/pull/96) ([akrambek](https://github.com/akrambek))

## [0.9.29](https://github.com/aklivity/zilla/tree/0.9.29) (2022-08-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.28...0.9.29)

**Implemented enhancements:**

- Configurable acknowledgement mode for kafka binding [\#84](https://github.com/aklivity/zilla/issues/84) ([jfallows](https://github.com/jfallows))
- Configurable isolation level for kafka binding [\#85](https://github.com/aklivity/zilla/issues/85) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Add tests for distinct partition leader [\#92](https://github.com/aklivity/zilla/pull/92) ([akrambek](https://github.com/akrambek))
- Support configuration of produce acks [\#93](https://github.com/aklivity/zilla/pull/93) ([jfallows](https://github.com/jfallows))
- Support read committed and read uncommitted kafka isolation levels [\#94](https://github.com/aklivity/zilla/pull/94) ([jfallows](https://github.com/jfallows))
- Enhance kafka transaction scenarios… [\#95](https://github.com/aklivity/zilla/pull/95) ([jfallows](https://github.com/jfallows))
- Support SSE proxy [\#98](https://github.com/aklivity/zilla/pull/98) ([jfallows](https://github.com/jfallows))

## [0.9.28](https://github.com/aklivity/zilla/tree/0.9.28) (2022-07-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.27...0.9.28)

**Fixed bugs:**

- Scope topic partition leader info by both resolved binding and topic … [\#90](https://github.com/aklivity/zilla/pull/90) ([jfallows](https://github.com/jfallows))

## [0.9.27](https://github.com/aklivity/zilla/tree/0.9.27) (2022-07-08)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.26...0.9.27)

**Fixed bugs:**

- Investigate GitHub Actions build inconsistencies [\#23](https://github.com/aklivity/zilla/issues/23) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Fix github action [\#78](https://github.com/aklivity/zilla/pull/78) ([akrambek](https://github.com/akrambek))
- Increase jacoco coverage ratio [\#81](https://github.com/aklivity/zilla/pull/81) ([akrambek](https://github.com/akrambek))
- Execute engine openjmh microbenchmarks via shaded test jar [\#82](https://github.com/aklivity/zilla/pull/82) ([jfallows](https://github.com/jfallows))
- Updated the README [\#87](https://github.com/aklivity/zilla/pull/87) ([llukyanov](https://github.com/llukyanov))
- Make authorization accessor one instance per thread [\#88](https://github.com/aklivity/zilla/pull/88) ([akrambek](https://github.com/akrambek))
- Support SASL PLAIN mechanism [\#89](https://github.com/aklivity/zilla/pull/89) ([jfallows](https://github.com/jfallows))

## [0.9.26](https://github.com/aklivity/zilla/tree/0.9.26) (2022-06-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.25...0.9.26)

**Merged pull requests:**

- Support produce with no reply-to directly to Kafka topic [\#79](https://github.com/aklivity/zilla/pull/79) ([jfallows](https://github.com/jfallows))

## [0.9.25](https://github.com/aklivity/zilla/tree/0.9.25) (2022-06-09)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.24...0.9.25)

**Fixed bugs:**

- Ignore query parameter in http-filesystem mapping… [\#77](https://github.com/aklivity/zilla/pull/77) ([jfallows](https://github.com/jfallows))

## [0.9.24](https://github.com/aklivity/zilla/tree/0.9.24) (2022-06-08)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.23...0.9.24)

**Fixed bugs:**

- Malformed if-match value triggers exception [\#38](https://github.com/aklivity/zilla/issues/38) ([akrambek](https://github.com/akrambek))
- Extract credentials from HTTP path query string even when non-terminal parameter [\#73](https://github.com/aklivity/zilla/issues/73) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Skip adding if-match header to kafka message if etag not present [\#67](https://github.com/aklivity/zilla/pull/67) ([jfallows](https://github.com/jfallows))
- Include diagram in README [\#69](https://github.com/aklivity/zilla/pull/69) ([jfallows](https://github.com/jfallows))
- Add simple example and roadmap to README [\#70](https://github.com/aklivity/zilla/pull/70) ([jfallows](https://github.com/jfallows))
- Update README [\#72](https://github.com/aklivity/zilla/pull/72) ([jfallows](https://github.com/jfallows))
- Use & as end of token separator for query parameter values [\#74](https://github.com/aklivity/zilla/pull/74) ([jfallows](https://github.com/jfallows))
- Support path with query when routing http path conditions [\#75](https://github.com/aklivity/zilla/pull/75) ([jfallows](https://github.com/jfallows))
- Support query parameter in sse handshake [\#76](https://github.com/aklivity/zilla/pull/76) ([jfallows](https://github.com/jfallows))

## [0.9.23](https://github.com/aklivity/zilla/tree/0.9.23) (2022-05-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.22...0.9.23)

**Fixed bugs:**

- Ws to tls proxy misinterprets begin extension  [\#63](https://github.com/aklivity/zilla/issues/63) ([akrambek](https://github.com/akrambek))
- Allow tls trustcacerts option to work without vault [\#65](https://github.com/aklivity/zilla/issues/65) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- Ignore Github Actions test [\#62](https://github.com/aklivity/zilla/pull/62) ([jfallows](https://github.com/jfallows))
- Check extension type id is proxy metadata in tls client and tcp client [\#64](https://github.com/aklivity/zilla/pull/64) ([jfallows](https://github.com/jfallows))
- Support trustcacerts without requiring a vault [\#66](https://github.com/aklivity/zilla/pull/66) ([jfallows](https://github.com/jfallows))

## [0.9.22](https://github.com/aklivity/zilla/tree/0.9.22) (2022-05-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.21...0.9.22)

**Merged pull requests:**

- Prepare for JWT example [\#61](https://github.com/aklivity/zilla/pull/61) ([jfallows](https://github.com/jfallows))

## [0.9.21](https://github.com/aklivity/zilla/tree/0.9.21) (2022-05-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.20...0.9.21)

**Implemented enhancements:**

- Require exit be omitted from tcp client configuration [\#40](https://github.com/aklivity/zilla/issues/40) ([jfallows](https://github.com/jfallows))
- Refer to sse-kafka event id progress as etag instead [\#43](https://github.com/aklivity/zilla/issues/43) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Ensure single fan-in point for fan server... [\#57](https://github.com/aklivity/zilla/pull/57) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Ignore Github Actions test [\#58](https://github.com/aklivity/zilla/pull/58) ([akrambek](https://github.com/akrambek))
- Rename progress to etag in sse-kafka binding event id format [\#59](https://github.com/aklivity/zilla/pull/59) ([jfallows](https://github.com/jfallows))
- Remove exit from tcp client binding schema [\#60](https://github.com/aklivity/zilla/pull/60) ([jfallows](https://github.com/jfallows))

## [0.9.20](https://github.com/aklivity/zilla/tree/0.9.20) (2022-05-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.19...0.9.20)

**Fixed bugs:**

- Clarify semantics of stream client index… [\#56](https://github.com/aklivity/zilla/pull/56) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Add the readme logo [\#54](https://github.com/aklivity/zilla/pull/54) ([akrambek](https://github.com/akrambek))
- Ignore GitHub Actions tests [\#55](https://github.com/aklivity/zilla/pull/55) ([akrambek](https://github.com/akrambek))

## [0.9.19](https://github.com/aklivity/zilla/tree/0.9.19) (2022-05-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.18...0.9.19)

**Merged pull requests:**

- Describe Zilla in README.md [\#52](https://github.com/aklivity/zilla/pull/52) ([jfallows](https://github.com/jfallows))
- Ensure single writer for kafka cache\_server and echo server [\#53](https://github.com/aklivity/zilla/pull/53) ([jfallows](https://github.com/jfallows))

## [0.9.18](https://github.com/aklivity/zilla/tree/0.9.18) (2022-05-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.17...0.9.18)

**Implemented enhancements:**

- Allow list of merged topics in kafka binding options to be optional [\#41](https://github.com/aklivity/zilla/issues/41) ([jfallows](https://github.com/jfallows))
- Optimize transfer-encoding for http-kafka correlated response [\#45](https://github.com/aklivity/zilla/issues/45) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Ensure kafka cache entry cannot grow... [\#49](https://github.com/aklivity/zilla/pull/49) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Ensure kafka producer stream has initialized with available window... [\#46](https://github.com/aklivity/zilla/pull/46) ([jfallows](https://github.com/jfallows))
- Use application/octet-stream as default content-type [\#47](https://github.com/aklivity/zilla/pull/47) ([jfallows](https://github.com/jfallows))
- Use content-length instead of transfer-encoding chunked for correlated responses [\#50](https://github.com/aklivity/zilla/pull/50) ([jfallows](https://github.com/jfallows))
- Support merged topics across partitions by default … [\#51](https://github.com/aklivity/zilla/pull/51) ([jfallows](https://github.com/jfallows))

## [0.9.17](https://github.com/aklivity/zilla/tree/0.9.17) (2022-05-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.16...0.9.17)

**Merged pull requests:**

- Support message trailers in kafka cache produce client [\#37](https://github.com/aklivity/zilla/pull/37) ([jfallows](https://github.com/jfallows))

## [0.9.16](https://github.com/aklivity/zilla/tree/0.9.16) (2022-05-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.15...0.9.16)

**Implemented enhancements:**

- Support tombstone messages via sse-kafka binding [\#25](https://github.com/aklivity/zilla/issues/25) ([jfallows](https://github.com/jfallows))
- Support etag in event id field for sse-kafka binding [\#26](https://github.com/aklivity/zilla/issues/26) ([jfallows](https://github.com/jfallows))
- Support key in event id field for sse-kafka binding [\#27](https://github.com/aklivity/zilla/issues/27) ([jfallows](https://github.com/jfallows))
- Enhance http-kafka idempotency key [\#28](https://github.com/aklivity/zilla/issues/28) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Handle null kafka message payload [\#29](https://github.com/aklivity/zilla/pull/29) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Include content length 0 in explicit status 404 response [\#24](https://github.com/aklivity/zilla/pull/24) ([jfallows](https://github.com/jfallows))
- Send SSE delete event for sse-kafka binding … [\#30](https://github.com/aklivity/zilla/pull/30) ([jfallows](https://github.com/jfallows))
- Support key and etag in event id [\#31](https://github.com/aklivity/zilla/pull/31) ([jfallows](https://github.com/jfallows))
- Support SuppressWarnings annotation [\#32](https://github.com/aklivity/zilla/pull/32) ([akrambek](https://github.com/akrambek))
- Support M1 chip docker image [\#33](https://github.com/aklivity/zilla/pull/33) ([akrambek](https://github.com/akrambek))
- Support md5 hash of headers and payload to augment zilla:correlation-id [\#34](https://github.com/aklivity/zilla/pull/34) ([jfallows](https://github.com/jfallows))
- Include content-length 0 on implicit 404 and 400 responses [\#35](https://github.com/aklivity/zilla/pull/35) ([jfallows](https://github.com/jfallows))
- Support idempotencyKey for http-kafka produce key [\#36](https://github.com/aklivity/zilla/pull/36) ([jfallows](https://github.com/jfallows))

## [0.9.15](https://github.com/aklivity/zilla/tree/0.9.15) (2022-05-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.14...0.9.15)

**Implemented enhancements:**

- http-kafka binding [\#17](https://github.com/aklivity/zilla/pull/17) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Support omitting options from http-kafka binding config [\#20](https://github.com/aklivity/zilla/pull/20) ([jfallows](https://github.com/jfallows))
- Support initialSeq and initialAck being equal but non-zero [\#21](https://github.com/aklivity/zilla/pull/21) ([jfallows](https://github.com/jfallows))
- Support default idempotency key and sync request response … [\#22](https://github.com/aklivity/zilla/pull/22) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- http binding implicit transfer-encoding chunked … [\#18](https://github.com/aklivity/zilla/pull/18) ([jfallows](https://github.com/jfallows))
- Improve http-kafka get items scenarios … [\#19](https://github.com/aklivity/zilla/pull/19) ([jfallows](https://github.com/jfallows))

## [0.9.14](https://github.com/aklivity/zilla/tree/0.9.14) (2022-03-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.13...0.9.14)

**Implemented enhancements:**

- Promote guard-jwt from incubator [\#16](https://github.com/aklivity/zilla/pull/16) ([jfallows](https://github.com/jfallows))

## [0.9.13](https://github.com/aklivity/zilla/tree/0.9.13) (2022-03-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.12...0.9.13)

**Implemented enhancements:**

- Promote binding-filesystem and binding-http-filesystem from incubator [\#15](https://github.com/aklivity/zilla/pull/15) ([jfallows](https://github.com/jfallows))

## [0.9.12](https://github.com/aklivity/zilla/tree/0.9.12) (2022-03-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.9...0.9.12)

**Implemented enhancements:**

- Promote binding-sse-kafka from incubator [\#14](https://github.com/aklivity/zilla/pull/14) ([jfallows](https://github.com/jfallows))

## [0.9.9](https://github.com/aklivity/zilla/tree/0.9.9) (2022-03-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.5.0...0.9.9)

**Implemented enhancements:**

- Guard API [\#8](https://github.com/aklivity/zilla/pull/8) ([jfallows](https://github.com/jfallows))
- filesystem binding and http-filesystem binding [\#10](https://github.com/aklivity/zilla/pull/10) ([jfallows](https://github.com/jfallows))
- Support docker image [\#13](https://github.com/aklivity/zilla/pull/13) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Improve coverage for sse binding deferred end and fix state transition bug [\#11](https://github.com/aklivity/zilla/pull/11) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Binding to map from SSE to Kafka [\#4](https://github.com/aklivity/zilla/pull/4) ([jfallows](https://github.com/jfallows))
- Support CORS http/1.1 and http/2 [\#5](https://github.com/aklivity/zilla/pull/5) ([jfallows](https://github.com/jfallows))
- Test verify JWT roles when index differs [\#9](https://github.com/aklivity/zilla/pull/9) ([jfallows](https://github.com/jfallows))

## [0.5.0](https://github.com/aklivity/zilla/tree/0.5.0) (2021-12-29)

**Fixed bugs:**

- Mockito test failure only on GitHub Actions [\#3](https://github.com/aklivity/zilla/issues/3) ([jfallows](https://github.com/jfallows))
