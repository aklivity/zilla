# Changelog

## [0.9.151](https://github.com/aklivity/zilla/tree/0.9.151) (2025-06-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.150...0.9.151)

**Merged pull requests:**

- Add CRL checks [\#1474](https://github.com/aklivity/zilla/pull/1474) ([bmaidics](https://github.com/bmaidics))

## [0.9.150](https://github.com/aklivity/zilla/tree/0.9.150) (2025-06-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.149...0.9.150)

**Merged pull requests:**

- Add specific event kind for TLS handshake timeout [\#1505](https://github.com/aklivity/zilla/pull/1505) ([jfallows](https://github.com/jfallows))
- Enhance stdout exporter to include trace id [\#1504](https://github.com/aklivity/zilla/pull/1504) ([jfallows](https://github.com/jfallows))
- Ensure matchCN thread safety when engine task parallelism \> 1 [\#1503](https://github.com/aklivity/zilla/pull/1503) ([jfallows](https://github.com/jfallows))

## [0.9.149](https://github.com/aklivity/zilla/tree/0.9.149) (2025-06-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.148...0.9.149)

**Merged pull requests:**

- Report exceptions on zilla start, such as invalid power of 2 for config [\#1501](https://github.com/aklivity/zilla/pull/1501) ([jfallows](https://github.com/jfallows))
- Support `engine.worker.capacity.unbounded` configuration [\#1500](https://github.com/aklivity/zilla/pull/1500) ([jfallows](https://github.com/jfallows))
- Add `engine.workers.capacity` metric and rename other `engine.worker` metrics to `engine.workers` [\#1499](https://github.com/aklivity/zilla/pull/1499) ([jfallows](https://github.com/jfallows))
- Use GaugesLayout for engine worker capacity metric [\#1496](https://github.com/aklivity/zilla/pull/1496) ([jfallows](https://github.com/jfallows))

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

- Custom Role Claim Support in Zilla JWT Validation [\#1476](https://github.com/aklivity/zilla/issues/1476)

**Merged pull requests:**

- Test missing namespace and binding name [\#1493](https://github.com/aklivity/zilla/pull/1493) ([akrambek](https://github.com/akrambek))
- Support custom role claim [\#1492](https://github.com/aklivity/zilla/pull/1492) ([akrambek](https://github.com/akrambek))
- Update README.md [\#1464](https://github.com/aklivity/zilla/pull/1464) ([anujkarn002](https://github.com/anujkarn002))

## [0.9.145](https://github.com/aklivity/zilla/tree/0.9.145) (2025-06-05)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.144...0.9.145)

**Merged pull requests:**

- Support conditional Thread.interrupt\(\) on exception in EngineRule [\#1489](https://github.com/aklivity/zilla/pull/1489) ([jfallows](https://github.com/jfallows))
- Bump lycheeverse/lychee-action from 2.3.0 to 2.4.1 [\#1473](https://github.com/aklivity/zilla/pull/1473) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.144](https://github.com/aklivity/zilla/tree/0.9.144) (2025-05-31)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.143...0.9.144)

**Closed issues:**

- Can't build Zilla offline due to JitPack-based K3PO dependencies [\#1481](https://github.com/aklivity/zilla/issues/1481)
- Support Overriding `correlation-id` via AsyncAPI Specification [\#1471](https://github.com/aklivity/zilla/issues/1471)

**Merged pull requests:**

- Use flyweights for TLS proxy decoder [\#1485](https://github.com/aklivity/zilla/pull/1485) ([jfallows](https://github.com/jfallows))
- Remove unused gitbook files [\#1484](https://github.com/aklivity/zilla/pull/1484) ([jfallows](https://github.com/jfallows))
- Enhance tcp unbinding spec test [\#1483](https://github.com/aklivity/zilla/pull/1483) ([akrambek](https://github.com/akrambek))
- Overriding `correlation-id` via AsyncAPI Specification [\#1482](https://github.com/aklivity/zilla/pull/1482) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.143](https://github.com/aklivity/zilla/tree/0.9.143) (2025-05-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.142...0.9.143)

**Implemented enhancements:**

- Collect exportable engine metric representing worker capacity usage for auto scaling [\#1465](https://github.com/aklivity/zilla/issues/1465)

**Merged pull requests:**

- Engine worker metrics support [\#1470](https://github.com/aklivity/zilla/pull/1470) ([akrambek](https://github.com/akrambek))

## [0.9.142](https://github.com/aklivity/zilla/tree/0.9.142) (2025-05-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.141...0.9.142)

**Fixed bugs:**

- Zilla crashes with IndexOutOfBoundException at produce with reply capability if message \> 8kb [\#1479](https://github.com/aklivity/zilla/issues/1479)

**Merged pull requests:**

- Fix large message hashing in http-kafka [\#1480](https://github.com/aklivity/zilla/pull/1480) ([bmaidics](https://github.com/bmaidics))

## [0.9.141](https://github.com/aklivity/zilla/tree/0.9.141) (2025-05-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.140...0.9.141)

**Merged pull requests:**

- support dynamic guarded route [\#1478](https://github.com/aklivity/zilla/pull/1478) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.140](https://github.com/aklivity/zilla/tree/0.9.140) (2025-05-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.139...0.9.140)

**Implemented enhancements:**

- Persist gRPC error messages [\#988](https://github.com/aklivity/zilla/issues/988)

**Fixed bugs:**

- `curl` fails to connect to `zilla` in `asyncapi.sse.proxy` and `asyncapi.sse.kafka.proxy` examples [\#1417](https://github.com/aklivity/zilla/issues/1417)

**Merged pull requests:**

- support `guarded` `routes` in TestBindingFactory [\#1477](https://github.com/aklivity/zilla/pull/1477) ([ankitk-me](https://github.com/ankitk-me))
- Remove `-e` option from `echo` & add `--exclude-internal` in `list` [\#1469](https://github.com/aklivity/zilla/pull/1469) ([ankitk-me](https://github.com/ankitk-me))
- Persist gRPC custom error messages [\#1468](https://github.com/aklivity/zilla/pull/1468) ([ankitk-me](https://github.com/ankitk-me))
- fix `asyncapi.sse.kafka.proxy` example & enable `test` [\#1463](https://github.com/aklivity/zilla/pull/1463) ([ankitk-me](https://github.com/ankitk-me))
- added support for validation in asyncapi.sse flow & example fix [\#1462](https://github.com/aklivity/zilla/pull/1462) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.139](https://github.com/aklivity/zilla/tree/0.9.139) (2025-04-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.138...0.9.139)

**Fixed bugs:**

- MQTT client not receiving events in `asyncapi.mqtt.proxy` Example [\#1416](https://github.com/aklivity/zilla/issues/1416)

**Merged pull requests:**

- Ensure SslEngine delegated task completes with signal … [\#1461](https://github.com/aklivity/zilla/pull/1461) ([jfallows](https://github.com/jfallows))
- Fix `asyncapi.mqtt.proxy` example & enable test [\#1460](https://github.com/aklivity/zilla/pull/1460) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.138](https://github.com/aklivity/zilla/tree/0.9.138) (2025-04-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.137...0.9.138)

**Merged pull requests:**

- Simplify TLS decode loop by inlining decodeHandshakeNeedTask [\#1459](https://github.com/aklivity/zilla/pull/1459) ([jfallows](https://github.com/jfallows))

## [0.9.137](https://github.com/aklivity/zilla/tree/0.9.137) (2025-04-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.136...0.9.137)

**Merged pull requests:**

- Use long not int when calculating ack max [\#1458](https://github.com/aklivity/zilla/pull/1458) ([jfallows](https://github.com/jfallows))

## [0.9.136](https://github.com/aklivity/zilla/tree/0.9.136) (2025-04-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.135...0.9.136)

**Implemented enhancements:**

- Support mqtt topics with path parameters required to match guarded identity [\#1382](https://github.com/aklivity/zilla/issues/1382)

**Merged pull requests:**

- Use distinct idle strategy instance per agent [\#1457](https://github.com/aklivity/zilla/pull/1457) ([jfallows](https://github.com/jfallows))
- Bump ubuntu from jammy-20250126 to jammy-20250404 in /cloud/docker-image/src/main/docker [\#1453](https://github.com/aklivity/zilla/pull/1453) ([dependabot[bot]](https://github.com/apps/dependabot))
- MQTT topics with path parameters required to match guarded identity [\#1387](https://github.com/aklivity/zilla/pull/1387) ([epieffe](https://github.com/epieffe))

## [0.9.135](https://github.com/aklivity/zilla/tree/0.9.135) (2025-04-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.134...0.9.135)

**Fixed bugs:**

- Miscalculating default worker capacity [\#1456](https://github.com/aklivity/zilla/issues/1456)
- Problem with oneOf ref in schema [\#1418](https://github.com/aklivity/zilla/issues/1418)

**Merged pull requests:**

- Fix default worker capacity [\#1455](https://github.com/aklivity/zilla/pull/1455) ([akrambek](https://github.com/akrambek))
- support `oneOf` `allOf` & `anyOf` schema in `binding-openapi` & `binding-asyncapi` [\#1454](https://github.com/aklivity/zilla/pull/1454) ([ankitk-me](https://github.com/ankitk-me))
- Add `test.sh` for `amqp.reflect` & skip fix required examples [\#1452](https://github.com/aklivity/zilla/pull/1452) ([ankitk-me](https://github.com/ankitk-me))
- enable Incubator features : zilla examples [\#1451](https://github.com/aklivity/zilla/pull/1451) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.134](https://github.com/aklivity/zilla/tree/0.9.134) (2025-04-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.133...0.9.134)

**Merged pull requests:**

- Set default buffer slot capacity to 32K [\#1449](https://github.com/aklivity/zilla/pull/1449) ([akrambek](https://github.com/akrambek))
- Set TCP flags appropriately to avoid Wireshark TCP dissector errors [\#1442](https://github.com/aklivity/zilla/pull/1442) ([jfallows](https://github.com/jfallows))
- Bump alpine from 3.21.2 to 3.21.3 in /cloud/docker-image/src/main/docker [\#1404](https://github.com/aklivity/zilla/pull/1404) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.133](https://github.com/aklivity/zilla/tree/0.9.133) (2025-04-03)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.132...0.9.133)

**Implemented enhancements:**

- Add a GH action to build the head of `develop` and run example tests [\#1263](https://github.com/aklivity/zilla/issues/1263)

**Merged pull requests:**

- Enforce worker capacity limit across both client and server tcp connections [\#1448](https://github.com/aklivity/zilla/pull/1448) ([akrambek](https://github.com/akrambek))
- shade jose4j in guard-jwt [\#1447](https://github.com/aklivity/zilla/pull/1447) ([ankitk-me](https://github.com/ankitk-me))
- Update workflow to run test on PRs & use `develop-SNAPSHOT` image [\#1436](https://github.com/aklivity/zilla/pull/1436) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.132](https://github.com/aklivity/zilla/tree/0.9.132) (2025-04-02)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.131...0.9.132)

**Merged pull requests:**

- Bind server socket per core [\#1446](https://github.com/aklivity/zilla/pull/1446) ([akrambek](https://github.com/akrambek))
- Increase task.parallelism by default to match workers [\#1445](https://github.com/aklivity/zilla/pull/1445) ([akrambek](https://github.com/akrambek))

## [0.9.131](https://github.com/aklivity/zilla/tree/0.9.131) (2025-03-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.130...0.9.131)

**Implemented enhancements:**

- Auto discover available host resources and dynamically set internal limits [\#984](https://github.com/aklivity/zilla/issues/984)

**Merged pull requests:**

- Auto discover available host resources and dynamically set internal limits [\#1438](https://github.com/aklivity/zilla/pull/1438) ([akrambek](https://github.com/akrambek))

## [0.9.130](https://github.com/aklivity/zilla/tree/0.9.130) (2025-03-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.129...0.9.130)

**Merged pull requests:**

- Verify varuint32\(n\) unpadded size [\#1440](https://github.com/aklivity/zilla/pull/1440) ([jfallows](https://github.com/jfallows))

## [0.9.129](https://github.com/aklivity/zilla/tree/0.9.129) (2025-03-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.128...0.9.129)

**Implemented enhancements:**

- Support inline definition of schemas in asyncapi generation. [\#1103](https://github.com/aklivity/zilla/issues/1103)

**Merged pull requests:**

- Enhance core test functions to support padded length varstring [\#1439](https://github.com/aklivity/zilla/pull/1439) ([jfallows](https://github.com/jfallows))
- rename: `sse.jwt` example to `sse.proxy.jwt` [\#1435](https://github.com/aklivity/zilla/pull/1435) ([ankitk-me](https://github.com/ankitk-me))
- Handle reserved budget in HTTP end frame [\#1430](https://github.com/aklivity/zilla/pull/1430) ([akrambek](https://github.com/akrambek))
- Fix failing test when JVM default locale is not US [\#1359](https://github.com/aklivity/zilla/pull/1359) ([epieffe](https://github.com/epieffe))

## [0.9.128](https://github.com/aklivity/zilla/tree/0.9.128) (2025-03-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.127...0.9.128)

**Fixed bugs:**

- Do not include ztables and zviews into show materialized view and tables command [\#1423](https://github.com/aklivity/zilla/issues/1423)
- Uploading more than 40k size file into filesystem doesn't get fully uploaded [\#1420](https://github.com/aklivity/zilla/issues/1420)

**Merged pull requests:**

- FLUSH after insert to make SHOW command predictable [\#1427](https://github.com/aklivity/zilla/pull/1427) ([akrambek](https://github.com/akrambek))
- Fix data fragmentation in http/1.1 and flowcontrol issue in filesystem [\#1426](https://github.com/aklivity/zilla/pull/1426) ([akrambek](https://github.com/akrambek))
- Ignore ztable and zmaterialized views from relavent non z show commands [\#1424](https://github.com/aklivity/zilla/pull/1424) ([akrambek](https://github.com/akrambek))
- fix: TLS Client debug logging [\#1422](https://github.com/aklivity/zilla/pull/1422) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.127](https://github.com/aklivity/zilla/tree/0.9.127) (2025-03-04)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.126...0.9.127)

**Fixed bugs:**

- Zilla crashes with NPE in asyncapi.mqtt.kafka.proxy example [\#1415](https://github.com/aklivity/zilla/issues/1415)
- zilla crashes with `java.lang.NoClassDefFoundError: com/google/gson/JsonElement` [\#1413](https://github.com/aklivity/zilla/issues/1413)

**Merged pull requests:**

- Allow binding kind proxy to contribute to zilla dump dissector protocol [\#1421](https://github.com/aklivity/zilla/pull/1421) ([jfallows](https://github.com/jfallows))
- fix: NoClassDefFoundError: com/google/gson/JsonElement [\#1414](https://github.com/aklivity/zilla/pull/1414) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.126](https://github.com/aklivity/zilla/tree/0.9.126) (2025-02-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.125...0.9.126)

**Implemented enhancements:**

- Type boolean possible in schema [\#1408](https://github.com/aklivity/zilla/issues/1408)

**Fixed bugs:**

- Java Agent Error while sending Data to Open Telemetry Endpoint \(OTEL Endpoint\) [\#1406](https://github.com/aklivity/zilla/issues/1406)
- Handle double quote when defining the table name in risingwave [\#1379](https://github.com/aklivity/zilla/issues/1379)

**Merged pull requests:**

- Handle double quote in z prefix resources [\#1412](https://github.com/aklivity/zilla/pull/1412) ([akrambek](https://github.com/akrambek))
- Handle TLS Alert.USER\_CANCELED then deferred Alert.CLOSE\_NOTIFY [\#1411](https://github.com/aklivity/zilla/pull/1411) ([jfallows](https://github.com/jfallows))
- support boolean model [\#1409](https://github.com/aklivity/zilla/pull/1409) ([ankitk-me](https://github.com/ankitk-me))
- fix: resolveKind flow for composite binding [\#1407](https://github.com/aklivity/zilla/pull/1407) ([ankitk-me](https://github.com/ankitk-me))
- fix: MQTT subscribe routing [\#1403](https://github.com/aklivity/zilla/pull/1403) ([ankitk-me](https://github.com/ankitk-me))
- fix: mqtt-kafka routing fix [\#1402](https://github.com/aklivity/zilla/pull/1402) ([ankitk-me](https://github.com/ankitk-me))
- fix: NPE due to empty Inline Catalog [\#1399](https://github.com/aklivity/zilla/pull/1399) ([ankitk-me](https://github.com/ankitk-me))
- Append missing system schema to avoid exposing view in show command [\#1398](https://github.com/aklivity/zilla/pull/1398) ([akrambek](https://github.com/akrambek))
- Bump lycheeverse/lychee-action from 2.2.0 to 2.3.0 [\#1397](https://github.com/aklivity/zilla/pull/1397) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump ubuntu from jammy-20240808 to jammy-20250126 in /cloud/docker-image/src/main/docker [\#1393](https://github.com/aklivity/zilla/pull/1393) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump alpine from 3.21.0 to 3.21.2 in /cloud/docker-image/src/main/docker [\#1367](https://github.com/aklivity/zilla/pull/1367) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.125](https://github.com/aklivity/zilla/tree/0.9.125) (2025-02-05)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.124...0.9.125)

**Implemented enhancements:**

- Support gRPC client streaming to Kafka directly [\#642](https://github.com/aklivity/zilla/issues/642)

**Fixed bugs:**

- Zilla Crashes on invalid request payload [\#1394](https://github.com/aklivity/zilla/issues/1394)
- Schema Retrieval Failure Causes Validation Error in Zilla [\#1391](https://github.com/aklivity/zilla/issues/1391)
- Connection refused for MQTT Kafka broker after setting up TLS on the Kafka client [\#1389](https://github.com/aklivity/zilla/issues/1389)
- Zilla Validation not working correctly [\#1385](https://github.com/aklivity/zilla/issues/1385)

**Merged pull requests:**

- Use `OpenapiView` and `AsyncapiView` to generate composite namespaces [\#1396](https://github.com/aklivity/zilla/pull/1396) ([jfallows](https://github.com/jfallows))
- fix: Zilla Crashes on invalid request payload [\#1395](https://github.com/aklivity/zilla/pull/1395) ([ankitk-me](https://github.com/ankitk-me))
- fix: locale-specific formatting due to `MessageFormat.format()` [\#1390](https://github.com/aklivity/zilla/pull/1390) ([ankitk-me](https://github.com/ankitk-me))
- Support gRPC client stream/unary oneway [\#1384](https://github.com/aklivity/zilla/pull/1384) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.124](https://github.com/aklivity/zilla/tree/0.9.124) (2025-01-30)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.123...0.9.124)

**Merged pull requests:**

- Use `OpenapiView` to generate composite namespaces [\#1388](https://github.com/aklivity/zilla/pull/1388) ([jfallows](https://github.com/jfallows))

## [0.9.123](https://github.com/aklivity/zilla/tree/0.9.123) (2025-01-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.122...0.9.123)

**Implemented enhancements:**

- Allow separate Auth config for Catalog definitions [\#1195](https://github.com/aklivity/zilla/issues/1195)

**Merged pull requests:**

- Decode network unconditionally when received window on MQTT session stream [\#1386](https://github.com/aklivity/zilla/pull/1386) ([bmaidics](https://github.com/bmaidics))
- Support secure schema access [\#1369](https://github.com/aklivity/zilla/pull/1369) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.122](https://github.com/aklivity/zilla/tree/0.9.122) (2025-01-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.121...0.9.122)

**Fixed bugs:**

- Logging wrong accept url when http proxy is configured [\#1380](https://github.com/aklivity/zilla/issues/1380)
- Connection to MQTT server over WebSocket fails from web browser [\#1374](https://github.com/aklivity/zilla/issues/1374)
- Zilla unable to produce to Kafka when removing the north/south cache blocks [\#1353](https://github.com/aklivity/zilla/issues/1353)

**Closed issues:**

- Unify ZFUNCTION and ZSTREAM into a single concept [\#1376](https://github.com/aklivity/zilla/issues/1376)

**Merged pull requests:**

- Support configurable TLS client SNI validation and handle FQDNs … [\#1383](https://github.com/aklivity/zilla/pull/1383) ([jfallows](https://github.com/jfallows))
- Log accepted before overriding the headers [\#1381](https://github.com/aklivity/zilla/pull/1381) ([akrambek](https://github.com/akrambek))
- Unify ZFUNCTION and ZSTREAM into a single concept [\#1377](https://github.com/aklivity/zilla/pull/1377) ([akrambek](https://github.com/akrambek))

## [0.9.121](https://github.com/aklivity/zilla/tree/0.9.121) (2025-01-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.120...0.9.121)

**Merged pull requests:**

- Ignore appending allow origin if already present [\#1371](https://github.com/aklivity/zilla/pull/1371) ([akrambek](https://github.com/akrambek))

## [0.9.120](https://github.com/aklivity/zilla/tree/0.9.120) (2025-01-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.119...0.9.120)

**Merged pull requests:**

- Make TLS client HTTPS endpoint identification configurable [\#1375](https://github.com/aklivity/zilla/pull/1375) ([jfallows](https://github.com/jfallows))
- Fix checkstyle in TLS Server [\#1373](https://github.com/aklivity/zilla/pull/1373) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.119](https://github.com/aklivity/zilla/tree/0.9.119) (2025-01-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.118...0.9.119)

**Merged pull requests:**

- Handle CN with spaces [\#1372](https://github.com/aklivity/zilla/pull/1372) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.118](https://github.com/aklivity/zilla/tree/0.9.118) (2025-01-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.117...0.9.118)

**Merged pull requests:**

- update extract `CN` logic to handle entry with `CN` in any order [\#1368](https://github.com/aklivity/zilla/pull/1368) ([ankitk-me](https://github.com/ankitk-me))
- Bump lycheeverse/lychee-action from 2.1.0 to 2.2.0 [\#1358](https://github.com/aklivity/zilla/pull/1358) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.117](https://github.com/aklivity/zilla/tree/0.9.117) (2025-01-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.116...0.9.117)

**Merged pull requests:**

- Flush the insert to have immediate effect [\#1365](https://github.com/aklivity/zilla/pull/1365) ([akrambek](https://github.com/akrambek))

## [0.9.116](https://github.com/aklivity/zilla/tree/0.9.116) (2025-01-09)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.115...0.9.116)

**Fixed bugs:**

- Zilla is unresponsive sometimes & the app logs Stopped but accepts the CRUD requests [\#1312](https://github.com/aklivity/zilla/issues/1312)
- Asyncapi kafka header extraction expression don’t match zilla yaml expressions  [\#1138](https://github.com/aklivity/zilla/issues/1138)

**Merged pull requests:**

- Minor bug fixes in zstream [\#1364](https://github.com/aklivity/zilla/pull/1364) ([akrambek](https://github.com/akrambek))
- fix: kafka header extraction expression in composite zilla.yaml [\#1362](https://github.com/aklivity/zilla/pull/1362) ([ankitk-me](https://github.com/ankitk-me))
- Support GSS encrypt request decoding as part of psql 14.15 client [\#1361](https://github.com/aklivity/zilla/pull/1361) ([akrambek](https://github.com/akrambek))
- Zfunction and Zstream support [\#1354](https://github.com/aklivity/zilla/pull/1354) ([akrambek](https://github.com/akrambek))

## [0.9.115](https://github.com/aklivity/zilla/tree/0.9.115) (2024-12-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.114...0.9.115)

**Merged pull requests:**

- enabling error reporting using `supplyReporter` [\#1348](https://github.com/aklivity/zilla/pull/1348) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.114](https://github.com/aklivity/zilla/tree/0.9.114) (2024-12-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.113...0.9.114)

**Merged pull requests:**

- Update show commands type oid [\#1357](https://github.com/aklivity/zilla/pull/1357) ([akrambek](https://github.com/akrambek))

## [0.9.113](https://github.com/aklivity/zilla/tree/0.9.113) (2024-12-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.112...0.9.113)

**Merged pull requests:**

- Set correct reserved on flush in risingwave [\#1356](https://github.com/aklivity/zilla/pull/1356) ([akrambek](https://github.com/akrambek))

## [0.9.112](https://github.com/aklivity/zilla/tree/0.9.112) (2024-12-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.111...0.9.112)

**Fixed bugs:**

- Docs search bar doesn't work [\#1351](https://github.com/aklivity/zilla/issues/1351)
- InputMismatchException is thrown for `grpc` binding with googleapis/devtools/build proto [\#1230](https://github.com/aklivity/zilla/issues/1230)

**Merged pull requests:**

- Ztable support and convert transformation logic to state machine  [\#1352](https://github.com/aklivity/zilla/pull/1352) ([akrambek](https://github.com/akrambek))
- Improve gRPC parser to allow complex types in optionValue [\#1350](https://github.com/aklivity/zilla/pull/1350) ([bmaidics](https://github.com/bmaidics))
- Bump org.apache.avro:avro from 1.11.3 to 1.12.0 [\#1290](https://github.com/aklivity/zilla/pull/1290) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.111](https://github.com/aklivity/zilla/tree/0.9.111) (2024-12-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.110...0.9.111)

**Implemented enhancements:**

- Replace Ivy APIs for embedded Maven during `zpmw` install [\#1173](https://github.com/aklivity/zilla/issues/1173)

**Merged pull requests:**

- Replace Ivy APIs with embedded Maven during zpmw install [\#1334](https://github.com/aklivity/zilla/pull/1334) ([bmaidics](https://github.com/bmaidics))

## [0.9.110](https://github.com/aklivity/zilla/tree/0.9.110) (2024-12-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.109...0.9.110)

**Merged pull requests:**

- Support ZVIEW command [\#1329](https://github.com/aklivity/zilla/pull/1329) ([akrambek](https://github.com/akrambek))

## [0.9.109](https://github.com/aklivity/zilla/tree/0.9.109) (2024-12-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.108...0.9.109)

## [0.9.108](https://github.com/aklivity/zilla/tree/0.9.108) (2024-12-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.107...0.9.108)

**Merged pull requests:**

- Bump alpine from 3.20.3 to 3.21.0 in /cloud/docker-image/src/main/docker [\#1346](https://github.com/aklivity/zilla/pull/1346) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump lycheeverse/lychee-action from 1.8.0 to 2.1.0 [\#1325](https://github.com/aklivity/zilla/pull/1325) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/checkout from 3 to 4 [\#1219](https://github.com/aklivity/zilla/pull/1219) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump ubuntu from jammy-20240530 to jammy-20240808 in /cloud/docker-image/src/main/docker [\#1205](https://github.com/aklivity/zilla/pull/1205) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.107](https://github.com/aklivity/zilla/tree/0.9.107) (2024-12-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.106...0.9.107)

**Merged pull requests:**

- Bump org.apache.avro:avro from 1.11.3 to 1.11.4 [\#1284](https://github.com/aklivity/zilla/pull/1284) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump com.google.protobuf:protobuf-java from 3.24.4 to 3.25.5 in /runtime/model-protobuf [\#1257](https://github.com/aklivity/zilla/pull/1257) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.106](https://github.com/aklivity/zilla/tree/0.9.106) (2024-12-04)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.105...0.9.106)

**Merged pull requests:**

- Exclude package-info.class from delegate module to avoid empty packages [\#1343](https://github.com/aklivity/zilla/pull/1343) ([jfallows](https://github.com/jfallows))

## [0.9.105](https://github.com/aklivity/zilla/tree/0.9.105) (2024-12-03)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.104...0.9.105)

**Merged pull requests:**

- Json deserialization of nullable fields [\#1342](https://github.com/aklivity/zilla/pull/1342) ([akrambek](https://github.com/akrambek))

## [0.9.104](https://github.com/aklivity/zilla/tree/0.9.104) (2024-12-02)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.103...0.9.104)

**Fixed bugs:**

- `affinity mask must specify at least one bit` crash preventing Zilla from starting on Windows 11 + Docker Compose [\#1338](https://github.com/aklivity/zilla/issues/1338)

**Merged pull requests:**

- Json serialization of nullable fields [\#1341](https://github.com/aklivity/zilla/pull/1341) ([akrambek](https://github.com/akrambek))

## [0.9.103](https://github.com/aklivity/zilla/tree/0.9.103) (2024-11-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.102...0.9.103)

**Fixed bugs:**

- Mqtt selecting incorrect publish stream [\#1327](https://github.com/aklivity/zilla/issues/1327)

**Merged pull requests:**

- resolve nested `$ref` in schema [\#1337](https://github.com/aklivity/zilla/pull/1337) ([ankitk-me](https://github.com/ankitk-me))
- Enhance test vault and test exporter [\#1333](https://github.com/aklivity/zilla/pull/1333) ([jfallows](https://github.com/jfallows))
- validate `begin` frame type for `ws` client [\#1332](https://github.com/aklivity/zilla/pull/1332) ([ankitk-me](https://github.com/ankitk-me))
- Support `directory` syntax & functionality [\#1323](https://github.com/aklivity/zilla/pull/1323) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.102](https://github.com/aklivity/zilla/tree/0.9.102) (2024-11-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.101...0.9.102)

**Implemented enhancements:**

- Support `risingwave` `pgsql` message transformations [\#1208](https://github.com/aklivity/zilla/issues/1208)

**Merged pull requests:**

- Fix MQTT binding selecting incorrect publish stream [\#1328](https://github.com/aklivity/zilla/pull/1328) ([bmaidics](https://github.com/bmaidics))
- Support ALTER STREAM and ALTER TABLE transformation [\#1320](https://github.com/aklivity/zilla/pull/1320) ([akrambek](https://github.com/akrambek))

## [0.9.101](https://github.com/aklivity/zilla/tree/0.9.101) (2024-11-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.100...0.9.101)

**Fixed bugs:**

- Running emqtt\_bench triggers exception in KafkaCacheCursorFactory [\#752](https://github.com/aklivity/zilla/issues/752)

**Closed issues:**

- Support `ALTER STREAM` and `ALTER TABLE` transformation [\#1319](https://github.com/aklivity/zilla/issues/1319)

**Merged pull requests:**

- Support instrumentation via Java agent [\#1321](https://github.com/aklivity/zilla/pull/1321) ([jfallows](https://github.com/jfallows))
- `number` -\> `integer` for `max-age` attribute [\#1316](https://github.com/aklivity/zilla/pull/1316) ([ankitk-me](https://github.com/ankitk-me))
- support file write in binding-filesystem & binding-http-filesystem [\#1300](https://github.com/aklivity/zilla/pull/1300) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.100](https://github.com/aklivity/zilla/tree/0.9.100) (2024-10-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.99...0.9.100)

**Implemented enhancements:**

- Replace jsqlparser with antlr grammar [\#1301](https://github.com/aklivity/zilla/issues/1301)
- Support `pgsql-kafka` binding [\#1058](https://github.com/aklivity/zilla/issues/1058)
- syntax check mqtt topic names and topic filters in zilla.yaml [\#521](https://github.com/aklivity/zilla/issues/521)

**Fixed bugs:**

- Zilla `asyncapi.mqtt.kafka.proxy` crash on startup with NPE: Cannot read field "values" because the return value of "...AsyncapiServerVariableResolver.resolve\(String\)" is null [\#1304](https://github.com/aklivity/zilla/issues/1304)
- Using the Zilla MQTT broker the producer periodically disconnect and only reconnects after pod restart [\#1302](https://github.com/aklivity/zilla/issues/1302)
- SSE notifications stop after about 5-6 times when using kafka-sse [\#1291](https://github.com/aklivity/zilla/issues/1291)
- POST application/protobuf binary data can get stuck [\#1283](https://github.com/aklivity/zilla/issues/1283)
- JSON to Protobuf breaks after processing an invalid message [\#1282](https://github.com/aklivity/zilla/issues/1282)
- Zilla intermittently crashes with IndexOutOfBoundsException at high mqtt load [\#1206](https://github.com/aklivity/zilla/issues/1206)
- Load testing MQTT QOS 0,1,2 with Empty messages in the `retained` topic and Zilla crashing [\#1164](https://github.com/aklivity/zilla/issues/1164)

**Closed issues:**

- `pgsql` `DROP TOPIC` command to `KafkaDeleteTopicsBeginEx` [\#1307](https://github.com/aklivity/zilla/issues/1307)
- Log missing enviroment variables.  [\#1188](https://github.com/aklivity/zilla/issues/1188)

**Merged pull requests:**

- Support asyncapi server variables locally and via references [\#1314](https://github.com/aklivity/zilla/pull/1314) ([jfallows](https://github.com/jfallows))
- Support `https` scheme for zilla.yaml config watcher [\#1313](https://github.com/aklivity/zilla/pull/1313) ([jfallows](https://github.com/jfallows))
- Add missing dependency [\#1311](https://github.com/aklivity/zilla/pull/1311) ([akrambek](https://github.com/akrambek))
- Fix kafka cache fetch server retention issue [\#1310](https://github.com/aklivity/zilla/pull/1310) ([bmaidics](https://github.com/bmaidics))
- pgsql ALTER TOPIC command to register new schema [\#1309](https://github.com/aklivity/zilla/pull/1309) ([akrambek](https://github.com/akrambek))
- Fix IndexOutOfBoundsException at KafkaCacheClientProduceFactory [\#1303](https://github.com/aklivity/zilla/pull/1303) ([bmaidics](https://github.com/bmaidics))
- Log missing enviroment variables [\#1299](https://github.com/aklivity/zilla/pull/1299) ([ankitk-me](https://github.com/ankitk-me))
- Replace jsqlparser with antlr gramma [\#1298](https://github.com/aklivity/zilla/pull/1298) ([akrambek](https://github.com/akrambek))
- syntax check mqtt topic names in zilla.yaml [\#1297](https://github.com/aklivity/zilla/pull/1297) ([ankitk-me](https://github.com/ankitk-me))
- Include prefer wait in watch request even after initial 404 read request [\#1295](https://github.com/aklivity/zilla/pull/1295) ([jfallows](https://github.com/jfallows))
- protobuf validation failure fix [\#1292](https://github.com/aklivity/zilla/pull/1292) ([ankitk-me](https://github.com/ankitk-me))
- Upgrade agrona version [\#1281](https://github.com/aklivity/zilla/pull/1281) ([bmaidics](https://github.com/bmaidics))
- Support DROP TABLE, STREAM, and MATERIALIZED VIEW [\#1266](https://github.com/aklivity/zilla/pull/1266) ([akrambek](https://github.com/akrambek))

## [0.9.99](https://github.com/aklivity/zilla/tree/0.9.99) (2024-10-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.98...0.9.99)

**Merged pull requests:**

- Update advertised protocol version in pgsql server binding [\#1294](https://github.com/aklivity/zilla/pull/1294) ([akrambek](https://github.com/akrambek))
- Support cancel request [\#1293](https://github.com/aklivity/zilla/pull/1293) ([akrambek](https://github.com/akrambek))

## [0.9.98](https://github.com/aklivity/zilla/tree/0.9.98) (2024-10-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.97...0.9.98)

**Fixed bugs:**

- Propagate error code in risingwave binding that's coming either from pgsql-kafka or risingwave [\#1286](https://github.com/aklivity/zilla/issues/1286)
- Zilla get blocked when sending messages to kafka [\#1268](https://github.com/aklivity/zilla/issues/1268)

**Merged pull requests:**

- Propagate error code in risingwave binding that's coming either from pgsql-kafka or risingwave [\#1288](https://github.com/aklivity/zilla/pull/1288) ([akrambek](https://github.com/akrambek))
- Increase write buffer size to accomidate longer path [\#1287](https://github.com/aklivity/zilla/pull/1287) ([akrambek](https://github.com/akrambek))

## [0.9.97](https://github.com/aklivity/zilla/tree/0.9.97) (2024-10-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.96...0.9.97)

**Implemented enhancements:**

- Support `jwt` guarded identity via custom token claim [\#1276](https://github.com/aklivity/zilla/issues/1276)
- Support `insert into` to seed `kafka` messages via `risingwave` binding [\#1274](https://github.com/aklivity/zilla/issues/1274)

**Merged pull requests:**

- `pgsql` DROP TOPIC command to KafkaDeleteTopicsBeginEx plus catalog unregister subject [\#1280](https://github.com/aklivity/zilla/pull/1280) ([akrambek](https://github.com/akrambek))
- external udf - python support [\#1278](https://github.com/aklivity/zilla/pull/1278) ([ankitk-me](https://github.com/ankitk-me))
- Support jwt guarded identity via custom token claim [\#1277](https://github.com/aklivity/zilla/pull/1277) ([akrambek](https://github.com/akrambek))
- Support insert into to seed kafka messages via risingwave binding [\#1275](https://github.com/aklivity/zilla/pull/1275) ([akrambek](https://github.com/akrambek))

## [0.9.96](https://github.com/aklivity/zilla/tree/0.9.96) (2024-10-01)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.95...0.9.96)

**Implemented enhancements:**

- Support Kafka topics create, alter, delete [\#1059](https://github.com/aklivity/zilla/issues/1059)

**Fixed bugs:**

- `zilla` Fails to Load Configuration from Specified location if the initial attempts are unsuccessful [\#1226](https://github.com/aklivity/zilla/issues/1226)

**Merged pull requests:**

- Risingwave SInk primary key fix [\#1273](https://github.com/aklivity/zilla/pull/1273) ([akrambek](https://github.com/akrambek))
- Risingwave and PsqlKafka bug fixes [\#1272](https://github.com/aklivity/zilla/pull/1272) ([akrambek](https://github.com/akrambek))
- create external function issue fix [\#1271](https://github.com/aklivity/zilla/pull/1271) ([ankitk-me](https://github.com/ankitk-me))
- Remove produceRecordFramingSize constraints [\#1270](https://github.com/aklivity/zilla/pull/1270) ([akrambek](https://github.com/akrambek))
- External header pattern fix [\#1269](https://github.com/aklivity/zilla/pull/1269) ([ankitk-me](https://github.com/ankitk-me))
- Detect config update after initial 404 status [\#1267](https://github.com/aklivity/zilla/pull/1267) ([jfallows](https://github.com/jfallows))
-  Support Kafka topics alter, delete [\#1265](https://github.com/aklivity/zilla/pull/1265) ([akrambek](https://github.com/akrambek))

## [0.9.95](https://github.com/aklivity/zilla/tree/0.9.95) (2024-09-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.94...0.9.95)

**Fixed bugs:**

- NPE durring high load: `Cannot invoke "io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaEvaluation.ordinal()" because "evaluation" is null` [\#1253](https://github.com/aklivity/zilla/issues/1253)

**Merged pull requests:**

- Fix KafkaMerged evaluation storage [\#1264](https://github.com/aklivity/zilla/pull/1264) ([bmaidics](https://github.com/bmaidics))
- Bump alpine from 3.20.2 to 3.20.3 in /cloud/docker-image/src/main/docker [\#1235](https://github.com/aklivity/zilla/pull/1235) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.94](https://github.com/aklivity/zilla/tree/0.9.94) (2024-09-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.93...0.9.94)

**Implemented enhancements:**

- Add more events to Zilla's connection with Kafka or other tcp clients [\#1123](https://github.com/aklivity/zilla/issues/1123)
- Ignore case when evaluating enums [\#1109](https://github.com/aklivity/zilla/issues/1109)
- Support `catalog` register new schema version [\#1060](https://github.com/aklivity/zilla/issues/1060)

**Fixed bugs:**

- Zilla KafkaGroup stream stuck on decoding FindCoordinatorResponse [\#1201](https://github.com/aklivity/zilla/issues/1201)
- NPE when a schema isn't found in a schema registry [\#1170](https://github.com/aklivity/zilla/issues/1170)
- Fetching from a log compacted topic pulls all new messages with same key on first call [\#803](https://github.com/aklivity/zilla/issues/803)

**Merged pull requests:**

- Fix parsing newline before end of stream [\#1262](https://github.com/aklivity/zilla/pull/1262) ([akrambek](https://github.com/akrambek))
- Support embedded risngwave functions [\#1261](https://github.com/aklivity/zilla/pull/1261) ([akrambek](https://github.com/akrambek))
- Explicitly set http version for schema registration [\#1260](https://github.com/aklivity/zilla/pull/1260) ([akrambek](https://github.com/akrambek))
- Support risingwave include keyword [\#1259](https://github.com/aklivity/zilla/pull/1259) ([akrambek](https://github.com/akrambek))
- Use END instead of ABORT at network end in MQTT [\#1256](https://github.com/aklivity/zilla/pull/1256) ([bmaidics](https://github.com/bmaidics))
- Risingwave demo bug fixes [\#1254](https://github.com/aklivity/zilla/pull/1254) ([akrambek](https://github.com/akrambek))
- fix: use integer for challenge type [\#1252](https://github.com/aklivity/zilla/pull/1252) ([vordimous](https://github.com/vordimous))
- fix: add or update the transforms pattern regex [\#1251](https://github.com/aklivity/zilla/pull/1251) ([vordimous](https://github.com/vordimous))
- Describe cluster API Support [\#1250](https://github.com/aklivity/zilla/pull/1250) ([akrambek](https://github.com/akrambek))
- Fix mqtt abort issue [\#1249](https://github.com/aklivity/zilla/pull/1249) ([bmaidics](https://github.com/bmaidics))
- create `function` support in `risingwave` binding [\#1248](https://github.com/aklivity/zilla/pull/1248) ([ankitk-me](https://github.com/ankitk-me))
- Ensure extract-key precedes extract-headers … [\#1247](https://github.com/aklivity/zilla/pull/1247) ([jfallows](https://github.com/jfallows))
- Support catalog register new schema version [\#1246](https://github.com/aklivity/zilla/pull/1246) ([akrambek](https://github.com/akrambek))
- Support pgsql-kafka binding [\#1245](https://github.com/aklivity/zilla/pull/1245) ([akrambek](https://github.com/akrambek))
- `zilla` build fix [\#1242](https://github.com/aklivity/zilla/pull/1242) ([ankitk-me](https://github.com/ankitk-me))
- fix: NPE when a schema isn't found in a schema registry [\#1241](https://github.com/aklivity/zilla/pull/1241) ([ankitk-me](https://github.com/ankitk-me))
- Initialize pgsql-kafka binding [\#1239](https://github.com/aklivity/zilla/pull/1239) ([jfallows](https://github.com/jfallows))
- Support Kafka topic create, alter, delete [\#1234](https://github.com/aklivity/zilla/pull/1234) ([akrambek](https://github.com/akrambek))
- fix: fix schema syntax and naming [\#1217](https://github.com/aklivity/zilla/pull/1217) ([vordimous](https://github.com/vordimous))
- `risingwave` binding support [\#1211](https://github.com/aklivity/zilla/pull/1211) ([akrambek](https://github.com/akrambek))

## [0.9.93](https://github.com/aklivity/zilla/tree/0.9.93) (2024-09-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.92...0.9.93)

**Implemented enhancements:**

- Support `pgsql` binding [\#1057](https://github.com/aklivity/zilla/issues/1057)
- Handle large HTTP headers [\#1046](https://github.com/aklivity/zilla/issues/1046)
- Gracefully handle `zilla.yml` instead of `zilla.yaml` [\#580](https://github.com/aklivity/zilla/issues/580)

**Fixed bugs:**

- Zilla OpenAPI not supporting filesystem catalog [\#1225](https://github.com/aklivity/zilla/issues/1225)
- Zilla produces corrupt messages due to incorrect CRC when fragmented [\#1221](https://github.com/aklivity/zilla/issues/1221)
- Issue referencing `guard` in `asyncapi` binding [\#1215](https://github.com/aklivity/zilla/issues/1215)
- Using catalog::apicurio triggers unexpected behaviour [\#1202](https://github.com/aklivity/zilla/issues/1202)
- `400 Bad Request` response from Zilla when Using Java HttpClient [\#1192](https://github.com/aklivity/zilla/issues/1192)
- Investigate connection pool reconnect when Kafka not yet available [\#1153](https://github.com/aklivity/zilla/issues/1153)

**Merged pull requests:**

- Refactor vault handler [\#1236](https://github.com/aklivity/zilla/pull/1236) ([jfallows](https://github.com/jfallows))
- Mqtt flow control fix [\#1233](https://github.com/aklivity/zilla/pull/1233) ([bmaidics](https://github.com/bmaidics))
- Fix incorrect flush acknowledgement in KafkaCacheClientProduceFactory [\#1232](https://github.com/aklivity/zilla/pull/1232) ([bmaidics](https://github.com/bmaidics))
- http binding update to support header overrides  at route level [\#1231](https://github.com/aklivity/zilla/pull/1231) ([ankitk-me](https://github.com/ankitk-me))
- Update asyncapi binding module-info to open parser package [\#1227](https://github.com/aklivity/zilla/pull/1227) ([jfallows](https://github.com/jfallows))
- Link checker [\#1216](https://github.com/aklivity/zilla/pull/1216) ([vordimous](https://github.com/vordimous))
- Fix incorrect CRC combine in Kafka produce client [\#1214](https://github.com/aklivity/zilla/pull/1214) ([bmaidics](https://github.com/bmaidics))
- Reduce compile warnings [\#1213](https://github.com/aklivity/zilla/pull/1213) ([jfallows](https://github.com/jfallows))
- Eclipse IDE import maven projects [\#1212](https://github.com/aklivity/zilla/pull/1212) ([jfallows](https://github.com/jfallows))
- Disable JVM class sharing to avoid error message during build [\#1210](https://github.com/aklivity/zilla/pull/1210) ([jfallows](https://github.com/jfallows))
- Initial risingwave binding projects [\#1209](https://github.com/aklivity/zilla/pull/1209) ([jfallows](https://github.com/jfallows))
- Ensure id encoding is consistent for encode and decode [\#1204](https://github.com/aklivity/zilla/pull/1204) ([jfallows](https://github.com/jfallows))
- Update mqtt session stream to report correct origin id for zilla dump command [\#1203](https://github.com/aklivity/zilla/pull/1203) ([jfallows](https://github.com/jfallows))
- Support pgsql binding [\#1200](https://github.com/aklivity/zilla/pull/1200) ([akrambek](https://github.com/akrambek))
- Initial pgsql binding projects [\#1198](https://github.com/aklivity/zilla/pull/1198) ([jfallows](https://github.com/jfallows))
- Fix: Using `asyncapi client` binding trigger NPE & crashes Zilla [\#1197](https://github.com/aklivity/zilla/pull/1197) ([ankitk-me](https://github.com/ankitk-me))
- Compute kafka produce checksum without staging headers [\#1196](https://github.com/aklivity/zilla/pull/1196) ([jfallows](https://github.com/jfallows))
- Allow content-length header with h2c upgrade [\#1194](https://github.com/aklivity/zilla/pull/1194) ([jfallows](https://github.com/jfallows))
- Kafka cache client: mark entry dirty at flush before notifying the server to process [\#1193](https://github.com/aklivity/zilla/pull/1193) ([bmaidics](https://github.com/bmaidics))
- Ensure streams are cleaned up on authentication failure… [\#1191](https://github.com/aklivity/zilla/pull/1191) ([jfallows](https://github.com/jfallows))

## [0.9.92](https://github.com/aklivity/zilla/tree/0.9.92) (2024-08-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.91...0.9.92)

**Implemented enhancements:**

- Support `extract-key` kafka message transform [\#1176](https://github.com/aklivity/zilla/issues/1176)

**Merged pull requests:**

- Align subject names when using inline catalog [\#1190](https://github.com/aklivity/zilla/pull/1190) ([jfallows](https://github.com/jfallows))
- Support extract-key kafka message transform  [\#1183](https://github.com/aklivity/zilla/pull/1183) ([akrambek](https://github.com/akrambek))

## [0.9.91](https://github.com/aklivity/zilla/tree/0.9.91) (2024-08-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.90...0.9.91)

**Fixed bugs:**

- `asyncapi` binding triggers zilla crash when used with `catalog::apicurio` [\#1185](https://github.com/aklivity/zilla/issues/1185)
- Avro to JSON conversion problem with REST Proxy [\#1169](https://github.com/aklivity/zilla/issues/1169)

**Merged pull requests:**

- Support SKIP\_MANY only kafka headers sequence filter [\#1189](https://github.com/aklivity/zilla/pull/1189) ([jfallows](https://github.com/jfallows))
- Enables `bindings:asyncapi` to use `catalog::apicurio` [\#1186](https://github.com/aklivity/zilla/pull/1186) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.90](https://github.com/aklivity/zilla/tree/0.9.90) (2024-08-05)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.89...0.9.90)

**Implemented enhancements:**

- Support `extract-headers` kafka message transform [\#1175](https://github.com/aklivity/zilla/issues/1175)
- Simplify `sse` support in AsyncAPI specs [\#1151](https://github.com/aklivity/zilla/issues/1151)

**Fixed bugs:**

- Support topic pattern wildcards in `mqtt-kafka` clients [\#1178](https://github.com/aklivity/zilla/issues/1178)
- Connecting to Aiven Kafka over TLS Throws an `java.security.InvalidAlgorithmParameterException: the trustAnchors parameter must be non-empty` Error [\#1115](https://github.com/aklivity/zilla/issues/1115)

**Merged pull requests:**

- Resolve server binding protocol type dissector … [\#1181](https://github.com/aklivity/zilla/pull/1181) ([jfallows](https://github.com/jfallows))
- Apply minimum mqtt timeout constraint … [\#1180](https://github.com/aklivity/zilla/pull/1180) ([jfallows](https://github.com/jfallows))
- Update MQTT wildcard processing for client topic patterns [\#1179](https://github.com/aklivity/zilla/pull/1179) ([jfallows](https://github.com/jfallows))
- Support extract-headers kafka message transform [\#1177](https://github.com/aklivity/zilla/pull/1177) ([akrambek](https://github.com/akrambek))
- Refactor asyncapi binding to simplify SSE asyncapi bindings extension [\#1171](https://github.com/aklivity/zilla/pull/1171) ([jfallows](https://github.com/jfallows))
- Add validation for invalid path in http-kafka [\#1168](https://github.com/aklivity/zilla/pull/1168) ([bmaidics](https://github.com/bmaidics))
- Bump alpine from 3.20.1 to 3.20.2 in /cloud/docker-image/src/main/docker [\#1165](https://github.com/aklivity/zilla/pull/1165) ([dependabot[bot]](https://github.com/apps/dependabot))
- `tls` binding require `vault` and `keys` or `signers` in `options` [\#1159](https://github.com/aklivity/zilla/pull/1159) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.89](https://github.com/aklivity/zilla/tree/0.9.89) (2024-07-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.88...0.9.89)

**Implemented enhancements:**

- Support `asyncapi` mapping `sse` protocol to `kafka` protocol [\#1064](https://github.com/aklivity/zilla/issues/1064)
- Helm chart option for handling HTTP/2 behind an Ingress controller [\#896](https://github.com/aklivity/zilla/issues/896)
- Support `grpc` custom metadata pass through [\#730](https://github.com/aklivity/zilla/issues/730)
- Export telemetry logs to local filesystem [\#298](https://github.com/aklivity/zilla/issues/298)
- Integrate OpenTelemetry collectors by exporting local log events over OTLP [\#297](https://github.com/aklivity/zilla/issues/297)
- Design observable logs configuration syntax [\#296](https://github.com/aklivity/zilla/issues/296)
- `telemetry logs` feature [\#295](https://github.com/aklivity/zilla/issues/295)

**Fixed bugs:**

- custom metadata populated by grpc-server missing  [\#1155](https://github.com/aklivity/zilla/issues/1155)
- Avro validation returns 204 and produces blank message [\#1143](https://github.com/aklivity/zilla/issues/1143)
- Fix WS large message bug [\#725](https://github.com/aklivity/zilla/pull/725) ([bmaidics](https://github.com/bmaidics))

**Merged pull requests:**

- Kafka debug log fix [\#1163](https://github.com/aklivity/zilla/pull/1163) ([ankitk-me](https://github.com/ankitk-me))
- grpc: mutable byte arrays to non-static instance fields [\#1160](https://github.com/aklivity/zilla/pull/1160) ([ankitk-me](https://github.com/ankitk-me))
- Avro validation bug fix [\#1157](https://github.com/aklivity/zilla/pull/1157) ([ankitk-me](https://github.com/ankitk-me))
- custom metadata populated by grpc-server missing fix [\#1156](https://github.com/aklivity/zilla/pull/1156) ([ankitk-me](https://github.com/ankitk-me))
- Support karapace-schema-registry, schema-registry and apicurio-registry catalogs [\#1134](https://github.com/aklivity/zilla/pull/1134) ([jfallows](https://github.com/jfallows))
- grpc custom metadata passthrough implementation [\#1097](https://github.com/aklivity/zilla/pull/1097) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.88](https://github.com/aklivity/zilla/tree/0.9.88) (2024-07-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.87...0.9.88)

**Implemented enhancements:**

- AsyncAPI `http-kafka` header overrides support [\#1141](https://github.com/aklivity/zilla/issues/1141)

**Fixed bugs:**

- AsyncAPI sse kafka filtering support [\#1137](https://github.com/aklivity/zilla/issues/1137)

**Merged pull requests:**

- Support asyncapi authorization in http kafka and sse kafka [\#1150](https://github.com/aklivity/zilla/pull/1150) ([akrambek](https://github.com/akrambek))
- MInor fixes for asyncapi sse-kafka and http-kafka binding support [\#1149](https://github.com/aklivity/zilla/pull/1149) ([akrambek](https://github.com/akrambek))
- Support `sse-kafka` header filters from AsyncAPI sse operation [\#1148](https://github.com/aklivity/zilla/pull/1148) ([jfallows](https://github.com/jfallows))
- Support `http-kafka` header overrides from AsyncAPI http operation [\#1147](https://github.com/aklivity/zilla/pull/1147) ([jfallows](https://github.com/jfallows))
- fix: update readme links [\#1146](https://github.com/aklivity/zilla/pull/1146) ([vordimous](https://github.com/vordimous))
- Support http authorization in asyncapi generation [\#1145](https://github.com/aklivity/zilla/pull/1145) ([akrambek](https://github.com/akrambek))
- update ingress values and implementation [\#1142](https://github.com/aklivity/zilla/pull/1142) ([vordimous](https://github.com/vordimous))

## [0.9.87](https://github.com/aklivity/zilla/tree/0.9.87) (2024-07-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.86...0.9.87)

**Merged pull requests:**

- Support multiple requests with single window ack on shared connection [\#1144](https://github.com/aklivity/zilla/pull/1144) ([jfallows](https://github.com/jfallows))

## [0.9.86](https://github.com/aklivity/zilla/tree/0.9.86) (2024-07-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.85...0.9.86)

**Merged pull requests:**

- Handle incremental key validation when length increases [\#1140](https://github.com/aklivity/zilla/pull/1140) ([akrambek](https://github.com/akrambek))

## [0.9.85](https://github.com/aklivity/zilla/tree/0.9.85) (2024-07-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.84...0.9.85)

**Implemented enhancements:**

- add option to print version information to the cli tools [\#1066](https://github.com/aklivity/zilla/issues/1066)

**Fixed bugs:**

- Support key validation in kafka asyncapi generation [\#1105](https://github.com/aklivity/zilla/issues/1105)
- Asyncapi doesn't generate schema for catalog with avro format [\#1104](https://github.com/aklivity/zilla/issues/1104)

**Closed issues:**

- Add more context to the Kafka API event code log formatter. [\#1126](https://github.com/aklivity/zilla/issues/1126)

**Merged pull requests:**

- Ensure SASL handshake occurs for JoinGroupRequest as needed… [\#1139](https://github.com/aklivity/zilla/pull/1139) ([jfallows](https://github.com/jfallows))
- Add CatalogConfig.builder\(\) methods [\#1133](https://github.com/aklivity/zilla/pull/1133) ([jfallows](https://github.com/jfallows))
- Lint helm chart on local builds and PR builds [\#1132](https://github.com/aklivity/zilla/pull/1132) ([jfallows](https://github.com/jfallows))
- fix: Add custom pod labels and fix notes for connection instructions [\#1130](https://github.com/aklivity/zilla/pull/1130) ([vordimous](https://github.com/vordimous))
- Detect missing events in test exporter [\#1128](https://github.com/aklivity/zilla/pull/1128) ([jfallows](https://github.com/jfallows))
- Enhance Kafka event descriptions [\#1127](https://github.com/aklivity/zilla/pull/1127) ([jfallows](https://github.com/jfallows))
- Bug fixes and improvements to support asyncapi http, sse, and kafka integration [\#1124](https://github.com/aklivity/zilla/pull/1124) ([akrambek](https://github.com/akrambek))
- Add zilla version command [\#1121](https://github.com/aklivity/zilla/pull/1121) ([bmaidics](https://github.com/bmaidics))
- Support reentrant kafka write key for converter [\#1120](https://github.com/aklivity/zilla/pull/1120) ([jfallows](https://github.com/jfallows))
- Enhance TLS key pair verification tests [\#1119](https://github.com/aklivity/zilla/pull/1119) ([jfallows](https://github.com/jfallows))
- Bump alpine from 3.20.0 to 3.20.1 in /cloud/docker-image/src/main/docker [\#1102](https://github.com/aklivity/zilla/pull/1102) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.84](https://github.com/aklivity/zilla/tree/0.9.84) (2024-06-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.83...0.9.84)

**Implemented enhancements:**

- Verify public-private key pair obtained from vault used for TLS handshake [\#1073](https://github.com/aklivity/zilla/issues/1073)

**Closed issues:**

- feat: improve troubleshooting capabilities [\#903](https://github.com/aklivity/zilla/issues/903)

**Merged pull requests:**

- Use default config when missing [\#1118](https://github.com/aklivity/zilla/pull/1118) ([jfallows](https://github.com/jfallows))
- Require test exporter event properties via test schema [\#1117](https://github.com/aklivity/zilla/pull/1117) ([jfallows](https://github.com/jfallows))
- Include engine test sources JAR in release [\#1116](https://github.com/aklivity/zilla/pull/1116) ([jfallows](https://github.com/jfallows))
- Generate asyncapi  schema catalog with avro, protobuf format support [\#1113](https://github.com/aklivity/zilla/pull/1113) ([akrambek](https://github.com/akrambek))
- Add logging of cluster authorization failed error to kafka binding [\#1112](https://github.com/aklivity/zilla/pull/1112) ([attilakreiner](https://github.com/attilakreiner))
- Verify public-private tls key pair [\#1108](https://github.com/aklivity/zilla/pull/1108) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.83](https://github.com/aklivity/zilla/tree/0.9.83) (2024-06-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.82...0.9.83)

**Implemented enhancements:**

- Add asyncapi http-kafka proxy example [\#1077](https://github.com/aklivity/zilla/issues/1077)
- Use miliseconds in metrics [\#1069](https://github.com/aklivity/zilla/issues/1069)
- Promote `filesystem` catalog out of incubator [\#1068](https://github.com/aklivity/zilla/issues/1068)
- Support `asyncapi` mapping `http` protocol to `kafka` protocol [\#1063](https://github.com/aklivity/zilla/issues/1063)
- Support filtering by kafka structured value field\(s\) [\#1062](https://github.com/aklivity/zilla/issues/1062)
- Support remote zilla configuration with change detection [\#1061](https://github.com/aklivity/zilla/issues/1061)
- Use full Event ID and the event name [\#1013](https://github.com/aklivity/zilla/issues/1013)
- Support configuration of MQTT Publish QoS maximum [\#970](https://github.com/aklivity/zilla/issues/970)
- Support `sse` server and client via `asyncapi` [\#952](https://github.com/aklivity/zilla/issues/952)
- Review kafka binding partition offset vs progress offset [\#285](https://github.com/aklivity/zilla/issues/285)

**Fixed bugs:**

- iNotify error when multiple Zilla instances are started in K8s Pods on a Portainer.io host [\#1081](https://github.com/aklivity/zilla/issues/1081)
- Running `emqtt_bench` `sub`  triggers an exception [\#1037](https://github.com/aklivity/zilla/issues/1037)
- MqttSessionBeginEx missing packetIds in zilla dump [\#1028](https://github.com/aklivity/zilla/issues/1028)
- Running `emqtt_bench`  triggers an exception in mqtt during the decoding [\#999](https://github.com/aklivity/zilla/issues/999)
- Intermittent NPE when trying to resolve guards [\#994](https://github.com/aklivity/zilla/issues/994)

**Closed issues:**

- Add SSE payload validation to sse-server binding [\#1076](https://github.com/aklivity/zilla/issues/1076)

**Merged pull requests:**

- Refactor signaler class name [\#1111](https://github.com/aklivity/zilla/pull/1111) ([jfallows](https://github.com/jfallows))
- fix: add volume mounts into the deployment yaml [\#1110](https://github.com/aklivity/zilla/pull/1110) ([vordimous](https://github.com/vordimous))
- Support engine events and detect config watcher failed [\#1107](https://github.com/aklivity/zilla/pull/1107) ([jfallows](https://github.com/jfallows))
- Support special characters for resolving channel ref [\#1101](https://github.com/aklivity/zilla/pull/1101) ([akrambek](https://github.com/akrambek))
- Fix NegativeArraySizeException when receiving mqttFlush [\#1100](https://github.com/aklivity/zilla/pull/1100) ([bmaidics](https://github.com/bmaidics))
- Asyncapi sse kafka proxy [\#1099](https://github.com/aklivity/zilla/pull/1099) ([bmaidics](https://github.com/bmaidics))
- Fix dump mqtt session begin [\#1098](https://github.com/aklivity/zilla/pull/1098) ([attilakreiner](https://github.com/attilakreiner))
- Promote catalog-filesystem out of incubator [\#1096](https://github.com/aklivity/zilla/pull/1096) ([attilakreiner](https://github.com/attilakreiner))
- Fix imports [\#1095](https://github.com/aklivity/zilla/pull/1095) ([attilakreiner](https://github.com/attilakreiner))
- Implement millisecond conversion to metrics [\#1094](https://github.com/aklivity/zilla/pull/1094) ([attilakreiner](https://github.com/attilakreiner))
- filtering by structured value field\(s\) [\#1093](https://github.com/aklivity/zilla/pull/1093) ([ankitk-me](https://github.com/ankitk-me))
- Add sse payload validation [\#1092](https://github.com/aklivity/zilla/pull/1092) ([bmaidics](https://github.com/bmaidics))
- Ensure engine closes after stdout generated [\#1091](https://github.com/aklivity/zilla/pull/1091) ([jfallows](https://github.com/jfallows))
- Await non-empty output before verifying expected vs actual [\#1090](https://github.com/aklivity/zilla/pull/1090) ([jfallows](https://github.com/jfallows))
- Ensure stdout flush without newline before comparison to expected output [\#1089](https://github.com/aklivity/zilla/pull/1089) ([jfallows](https://github.com/jfallows))
- Upgrade zilla docker image to use jdk 22 [\#1088](https://github.com/aklivity/zilla/pull/1088) ([jfallows](https://github.com/jfallows))
- Update k3po dependency [\#1086](https://github.com/aklivity/zilla/pull/1086) ([jfallows](https://github.com/jfallows))
- SSE asyncapi server, client [\#1085](https://github.com/aklivity/zilla/pull/1085) ([bmaidics](https://github.com/bmaidics))
- Java 17 source compatibility [\#1084](https://github.com/aklivity/zilla/pull/1084) ([jfallows](https://github.com/jfallows))
- Bump ubuntu from jammy-20240427 to jammy-20240530 in /cloud/docker-image/src/main/docker [\#1079](https://github.com/aklivity/zilla/pull/1079) ([dependabot[bot]](https://github.com/apps/dependabot))
- Dynamic decode padding by length fix [\#1078](https://github.com/aklivity/zilla/pull/1078) ([ankitk-me](https://github.com/ankitk-me))
- Catalog Handler interface to support dynamic encode padding by length [\#1075](https://github.com/aklivity/zilla/pull/1075) ([ankitk-me](https://github.com/ankitk-me))
- Fix mqtt-kafka non compact test [\#1074](https://github.com/aklivity/zilla/pull/1074) ([bmaidics](https://github.com/bmaidics))
- Http-Kafka AsyncAPI [\#1072](https://github.com/aklivity/zilla/pull/1072) ([bmaidics](https://github.com/bmaidics))
- Support remote zilla configuration with change detection [\#1071](https://github.com/aklivity/zilla/pull/1071) ([attilakreiner](https://github.com/attilakreiner))
- feat: replace port 8080 with 12345 [\#1070](https://github.com/aklivity/zilla/pull/1070) ([vordimous](https://github.com/vordimous))
- Fix NPE when trying to resolve guards [\#1067](https://github.com/aklivity/zilla/pull/1067) ([attilakreiner](https://github.com/attilakreiner))
- Add publish qos max options for mqtt-kafka binding [\#1065](https://github.com/aklivity/zilla/pull/1065) ([bmaidics](https://github.com/bmaidics))
- Added declarative helmfile [\#1054](https://github.com/aklivity/zilla/pull/1054) ([ttimot24](https://github.com/ttimot24))
- Bump alpine from 3.19.1 to 3.20.0 in /cloud/docker-image/src/main/docker [\#1047](https://github.com/aklivity/zilla/pull/1047) ([dependabot[bot]](https://github.com/apps/dependabot))
- Fix TlsNetworkIT by adding cipherSuites [\#1043](https://github.com/aklivity/zilla/pull/1043) ([attilakreiner](https://github.com/attilakreiner))
- feat: replace static event name with dynamic based on event id [\#1029](https://github.com/aklivity/zilla/pull/1029) ([vordimous](https://github.com/vordimous))

## [0.9.82](https://github.com/aklivity/zilla/tree/0.9.82) (2024-05-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.81...0.9.82)

**Fixed bugs:**

- Zilla crashes with `IllegalArgumentException: cannot accept missingValue` when using `defaultOffset: live` [\#1051](https://github.com/aklivity/zilla/issues/1051)
- Zilla crashes on mqtt cli -T option [\#1039](https://github.com/aklivity/zilla/issues/1039)
- Running `emqtt_bench` both `sub` and `pub` triggers an exception [\#1000](https://github.com/aklivity/zilla/issues/1000)
- `http-kafka` will `fetch` messages that have been deleted by a retention policy [\#897](https://github.com/aklivity/zilla/issues/897)

**Merged pull requests:**

- Update to handle catalog IT validation\(resolve schema from subject\) [\#1055](https://github.com/aklivity/zilla/pull/1055) ([ankitk-me](https://github.com/ankitk-me))
- Queue as different kafka produce request if producerId or producerEpoch varies [\#1053](https://github.com/aklivity/zilla/pull/1053) ([akrambek](https://github.com/akrambek))
- Support kafka cache bootstrap with topic default offset live [\#1052](https://github.com/aklivity/zilla/pull/1052) ([jfallows](https://github.com/jfallows))
- Set decoder to ignoreAll after session is taken over by other MQTT client [\#1045](https://github.com/aklivity/zilla/pull/1045) ([bmaidics](https://github.com/bmaidics))
- Add detection of non-compacted session topic [\#1044](https://github.com/aklivity/zilla/pull/1044) ([bmaidics](https://github.com/bmaidics))
- Fix: http-kafka will fetch messages that have been deleted by a reten… [\#1033](https://github.com/aklivity/zilla/pull/1033) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.81](https://github.com/aklivity/zilla/tree/0.9.81) (2024-05-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.80...0.9.81)

**Implemented enhancements:**

- Improve Starting Zilla with the CLI [\#1016](https://github.com/aklivity/zilla/issues/1016)
- Generate `zilla dump` packet captures in timestamp order including across workers [\#959](https://github.com/aklivity/zilla/issues/959)
- Split protocol testing into separate ITs for `zilla dump` command [\#958](https://github.com/aklivity/zilla/issues/958)
- Add zilla context to MQTT consumer groups [\#886](https://github.com/aklivity/zilla/issues/886)

**Fixed bugs:**

- Telemetry attribute service.name doesn't get sent correctly [\#1007](https://github.com/aklivity/zilla/issues/1007)
- Streampay `zilla` instance crashes while trying to access  `https://localhost:9090` [\#975](https://github.com/aklivity/zilla/issues/975)

**Merged pull requests:**

- Add service.name attribute to metrics [\#1048](https://github.com/aklivity/zilla/pull/1048) ([attilakreiner](https://github.com/attilakreiner))
- Starting Zilla with the CLI improvement [\#1042](https://github.com/aklivity/zilla/pull/1042) ([ankitk-me](https://github.com/ankitk-me))
- Sort frames by timestamp in dump command [\#1041](https://github.com/aklivity/zilla/pull/1041) ([attilakreiner](https://github.com/attilakreiner))
- Ensure new mqtt subscriptions are not empty [\#1040](https://github.com/aklivity/zilla/pull/1040) ([jfallows](https://github.com/jfallows))
- Add zilla context to MQTT consumer groups [\#1035](https://github.com/aklivity/zilla/pull/1035) ([bmaidics](https://github.com/bmaidics))
- Split protocol testing into separate ITs for zilla dump command [\#989](https://github.com/aklivity/zilla/pull/989) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.80](https://github.com/aklivity/zilla/tree/0.9.80) (2024-05-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.79...0.9.80)

**Breaking changes:**

- Remove `zilla generate` command [\#960](https://github.com/aklivity/zilla/issues/960)
- Report unused properties based on binding definition [\#808](https://github.com/aklivity/zilla/issues/808)

**Implemented enhancements:**

- Update the Zilla issue Bug Report template with debugging info collection instructions [\#991](https://github.com/aklivity/zilla/issues/991)
- Support multiple specs in `openapi-asyncapi` binding [\#964](https://github.com/aklivity/zilla/issues/964)
- Integrate JMH into `tls` binding [\#961](https://github.com/aklivity/zilla/issues/961)
- Enhance validation for `openapi` and `asyncapi` bindings [\#950](https://github.com/aklivity/zilla/issues/950)
- Support multiple specs in `openapi` binding [\#949](https://github.com/aklivity/zilla/issues/949)
- Support multiple specs in `asyncapi` binding [\#948](https://github.com/aklivity/zilla/issues/948)
- Support `asyncapi` `mqtt` streetlights mapping to `kafka` streetlights [\#947](https://github.com/aklivity/zilla/issues/947)
- Support `mqtt` access log [\#945](https://github.com/aklivity/zilla/issues/945)
- Support `mqtt` client binding authorization [\#940](https://github.com/aklivity/zilla/issues/940)
- Resiliently handle `apicurio` catalog unreachable [\#938](https://github.com/aklivity/zilla/issues/938)
- Resiliently handle `karapace` catalog unreachable [\#937](https://github.com/aklivity/zilla/issues/937)
- Support local `zilla` installation on MacOS via `homebrew` [\#680](https://github.com/aklivity/zilla/issues/680)
- Update bug report template [\#820](https://github.com/aklivity/zilla/pull/820) ([vordimous](https://github.com/vordimous))

**Fixed bugs:**

- Zilla crashes with `IllegalArgumentException` when an Avro payload is fetched as `json` [\#1025](https://github.com/aklivity/zilla/issues/1025)
- MQTT-Kafka qos2: increasing tracked producer sequence number without publishing to Kafka [\#1014](https://github.com/aklivity/zilla/issues/1014)
- OTLP `logs` signal doesn't show up in OpenTelemetry Demo [\#1006](https://github.com/aklivity/zilla/issues/1006)
- Flow control issue in openapi binding [\#1004](https://github.com/aklivity/zilla/issues/1004)
- `mqtt` connecting with longer client id fails [\#1003](https://github.com/aklivity/zilla/issues/1003)
- Running zilla with the `kafka-grpc` binding in a cluster with multiple instances results in each instance delivering a message to the configured `remote_server` [\#882](https://github.com/aklivity/zilla/issues/882)
- Using the `grpc.kafka.proxy` example setup, Zilla will periodically not deliver the message to the gRPC service defined in the `kafka-grpc` binding [\#881](https://github.com/aklivity/zilla/issues/881)
- Flaky kafka-grpc test [\#768](https://github.com/aklivity/zilla/issues/768)

**Merged pull requests:**

- Handle & calculate complex schema padding [\#1038](https://github.com/aklivity/zilla/pull/1038) ([ankitk-me](https://github.com/ankitk-me))
- Fix typo to send abort on abort instead of end [\#1036](https://github.com/aklivity/zilla/pull/1036) ([akrambek](https://github.com/akrambek))
- Bump junit.version from 5.10.1 to 5.10.2 [\#1032](https://github.com/aklivity/zilla/pull/1032) ([dependabot[bot]](https://github.com/apps/dependabot))
- Honor MQTT clean start at QoS2 produce [\#1031](https://github.com/aklivity/zilla/pull/1031) ([bmaidics](https://github.com/bmaidics))
- Use flyweight fields instead of class fields for control [\#1030](https://github.com/aklivity/zilla/pull/1030) ([akrambek](https://github.com/akrambek))
- catalog:apicurio - unify caching across workers to maximize cache hits [\#1027](https://github.com/aklivity/zilla/pull/1027) ([ankitk-me](https://github.com/ankitk-me))
- Use binding id instead of route Id for resolved Id [\#1026](https://github.com/aklivity/zilla/pull/1026) ([akrambek](https://github.com/akrambek))
- MQTT clients access log implementation [\#1023](https://github.com/aklivity/zilla/pull/1023) ([ankitk-me](https://github.com/ankitk-me))
- Unsubscribe on partition reassignment [\#1021](https://github.com/aklivity/zilla/pull/1021) ([akrambek](https://github.com/akrambek))
- Bump commons-cli:commons-cli from 1.6.0 to 1.7.0 [\#1020](https://github.com/aklivity/zilla/pull/1020) ([dependabot[bot]](https://github.com/apps/dependabot))
- Increase mqtt client id limit to 256 [\#1015](https://github.com/aklivity/zilla/pull/1015) ([bmaidics](https://github.com/bmaidics))
- Generate correct crc32c value for the messages with different produceId [\#1011](https://github.com/aklivity/zilla/pull/1011) ([akrambek](https://github.com/akrambek))
- Remove generate command [\#1010](https://github.com/aklivity/zilla/pull/1010) ([attilakreiner](https://github.com/attilakreiner))
- Support configuration of timestamps in zilla transport for k3po [\#1009](https://github.com/aklivity/zilla/pull/1009) ([jfallows](https://github.com/jfallows))
- Support multiple specs in openapi-asyncapi binding [\#1008](https://github.com/aklivity/zilla/pull/1008) ([bmaidics](https://github.com/bmaidics))
- Support multiple specs in openapi binding [\#1005](https://github.com/aklivity/zilla/pull/1005) ([bmaidics](https://github.com/bmaidics))
- Fix secure http detection in OpenAPI [\#1002](https://github.com/aklivity/zilla/pull/1002) ([bmaidics](https://github.com/bmaidics))
- Enhancing validation for openapi and asyncapi bindings [\#1001](https://github.com/aklivity/zilla/pull/1001) ([ankitk-me](https://github.com/ankitk-me))
- Support asyncapi mqtt streetlights mapping to kafka streetlights [\#997](https://github.com/aklivity/zilla/pull/997) ([bmaidics](https://github.com/bmaidics))
- Bump ubuntu from jammy-20240416 to jammy-20240427 in /cloud/docker-image/src/main/docker [\#996](https://github.com/aklivity/zilla/pull/996) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.bitbucket.b\_c:jose4j from 0.9.3 to 0.9.6 [\#995](https://github.com/aklivity/zilla/pull/995) ([dependabot[bot]](https://github.com/apps/dependabot))
- MQTT Websocket bugfix [\#993](https://github.com/aklivity/zilla/pull/993) ([bmaidics](https://github.com/bmaidics))
- Add MQTT client authentication [\#992](https://github.com/aklivity/zilla/pull/992) ([bmaidics](https://github.com/bmaidics))
- `tls`  `client/server/echo` handshake benchmark  [\#990](https://github.com/aklivity/zilla/pull/990) ([akrambek](https://github.com/akrambek))
- Bump ubuntu from jammy-20240111 to jammy-20240416 in /cloud/docker-image/src/main/docker [\#987](https://github.com/aklivity/zilla/pull/987) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump alpine from 3.19.0 to 3.19.1 in /cloud/docker-image/src/main/docker [\#986](https://github.com/aklivity/zilla/pull/986) ([dependabot[bot]](https://github.com/apps/dependabot))
- Update Java build matrix [\#983](https://github.com/aklivity/zilla/pull/983) ([jfallows](https://github.com/jfallows))
- Support multiple specs in asyncapi binding [\#982](https://github.com/aklivity/zilla/pull/982) ([bmaidics](https://github.com/bmaidics))
- Remove event script in favor of handshake script [\#981](https://github.com/aklivity/zilla/pull/981) ([attilakreiner](https://github.com/attilakreiner))
- `echo` `server` handshake benchmark [\#980](https://github.com/aklivity/zilla/pull/980) ([akrambek](https://github.com/akrambek))
- MqttKafka publish intern fix [\#979](https://github.com/aklivity/zilla/pull/979) ([bmaidics](https://github.com/bmaidics))
- Fix multiple exporters issue [\#978](https://github.com/aklivity/zilla/pull/978) ([attilakreiner](https://github.com/attilakreiner))
- unify caching across workers to maximize cache hits [\#977](https://github.com/aklivity/zilla/pull/977) ([ankitk-me](https://github.com/ankitk-me))
- Support reading empty file payload [\#976](https://github.com/aklivity/zilla/pull/976) ([jfallows](https://github.com/jfallows))
- Use format to construct get openapi operation for async rquest [\#967](https://github.com/aklivity/zilla/pull/967) ([akrambek](https://github.com/akrambek))
- binding config schema validation for unused properties [\#929](https://github.com/aklivity/zilla/pull/929) ([ankitk-me](https://github.com/ankitk-me))
- Bump org.agrona:agrona from 1.6.0 to 1.21.1 [\#890](https://github.com/aklivity/zilla/pull/890) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.79](https://github.com/aklivity/zilla/tree/0.9.79) (2024-04-22)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.78...0.9.79)

**Implemented enhancements:**

- Support `filesystem` catalog for local schemas [\#908](https://github.com/aklivity/zilla/issues/908)
- Check for files on startup when the zilla.yaml specifies paths to files or directories [\#292](https://github.com/aklivity/zilla/issues/292)

**Fixed bugs:**

- Fix k3po does not reload labels when labels file size decreases [\#972](https://github.com/aklivity/zilla/pull/972) ([bmaidics](https://github.com/bmaidics))

**Merged pull requests:**

- Support config for mqtt publish qos max [\#971](https://github.com/aklivity/zilla/pull/971) ([jfallows](https://github.com/jfallows))
- Use default kafka client id for kafka client instance id [\#968](https://github.com/aklivity/zilla/pull/968) ([jfallows](https://github.com/jfallows))
- Add vault parameter to exporter [\#966](https://github.com/aklivity/zilla/pull/966) ([attilakreiner](https://github.com/attilakreiner))
- Implement filesystem catalog [\#962](https://github.com/aklivity/zilla/pull/962) ([bmaidics](https://github.com/bmaidics))

## [0.9.78](https://github.com/aklivity/zilla/tree/0.9.78) (2024-04-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.77...0.9.78)

**Merged pull requests:**

- Ensure binding types are populated for `zilla dump` to dissect protocol-specific frames [\#928](https://github.com/aklivity/zilla/pull/928) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.77](https://github.com/aklivity/zilla/tree/0.9.77) (2024-04-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.76...0.9.77)

**Merged pull requests:**

- zilla dump : bindings not found in /var/runtime/zilla directory [\#927](https://github.com/aklivity/zilla/pull/927) ([ankitk-me](https://github.com/ankitk-me))
- README Docs links and formatting fixes [\#926](https://github.com/aklivity/zilla/pull/926) ([vordimous](https://github.com/vordimous))

## [0.9.76](https://github.com/aklivity/zilla/tree/0.9.76) (2024-04-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.75...0.9.76)

**Merged pull requests:**

- IT to validate null message in binding-kafka with Model configured  [\#925](https://github.com/aklivity/zilla/pull/925) ([ankitk-me](https://github.com/ankitk-me))
- Convert non-null payloads only, … [\#923](https://github.com/aklivity/zilla/pull/923) ([jfallows](https://github.com/jfallows))
- Fix validation bug [\#922](https://github.com/aklivity/zilla/pull/922) ([akrambek](https://github.com/akrambek))
- Fix helm chart logo URL [\#920](https://github.com/aklivity/zilla/pull/920) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.75](https://github.com/aklivity/zilla/tree/0.9.75) (2024-04-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.74...0.9.75)

**Implemented enhancements:**

- Support logging of events caused by Model [\#887](https://github.com/aklivity/zilla/issues/887)
- Helm chart QoL improvements [\#884](https://github.com/aklivity/zilla/issues/884)
- Support `mqtt` streetlights AsyncAPI validating proxy [\#880](https://github.com/aklivity/zilla/issues/880)
- Promote components out of incubator [\#879](https://github.com/aklivity/zilla/issues/879)
- Support specific server in AsyncAPI spec in `asyncapi` binding [\#878](https://github.com/aklivity/zilla/issues/878)
- Support specific server in OpenAPI spec in `openapi` binding [\#877](https://github.com/aklivity/zilla/issues/877)
- Support HTTP prefer async with OpenAPI [\#876](https://github.com/aklivity/zilla/issues/876)
- Support OpenAPI and AsyncAPI validation cases [\#814](https://github.com/aklivity/zilla/issues/814)
- Integrate `openapi` and `asyncapi` with `catalog` [\#813](https://github.com/aklivity/zilla/issues/813)

**Fixed bugs:**

- Error in JsonValidatorHandler when the remote registry can't be accessed [\#817](https://github.com/aklivity/zilla/issues/817)
- Zilla doesn't communicate with bitnami/kafka in Taxi demo [\#690](https://github.com/aklivity/zilla/issues/690)
- Bootstrap options on cache\_client vs cache\_server for the kafka binding [\#388](https://github.com/aklivity/zilla/issues/388)

**Merged pull requests:**

- Fix remaing jwt issues [\#918](https://github.com/aklivity/zilla/pull/918) ([akrambek](https://github.com/akrambek))
- Promote components out of incubator [\#917](https://github.com/aklivity/zilla/pull/917) ([jfallows](https://github.com/jfallows))
- Ignore case while checking guard type [\#916](https://github.com/aklivity/zilla/pull/916) ([akrambek](https://github.com/akrambek))
- Handle race condition between k3po and engine… [\#915](https://github.com/aklivity/zilla/pull/915) ([jfallows](https://github.com/jfallows))
- Add apicurio latest version test [\#914](https://github.com/aklivity/zilla/pull/914) ([bmaidics](https://github.com/bmaidics))
- Fix pom.xml for helm-chart [\#912](https://github.com/aklivity/zilla/pull/912) ([attilakreiner](https://github.com/attilakreiner))
- openapi-asyncapi route bug fixes [\#911](https://github.com/aklivity/zilla/pull/911) ([akrambek](https://github.com/akrambek))
- Use per worker registration for composite namespaces [\#910](https://github.com/aklivity/zilla/pull/910) ([jfallows](https://github.com/jfallows))
- Fix schema validation parsing [\#909](https://github.com/aklivity/zilla/pull/909) ([akrambek](https://github.com/akrambek))
- Support latest version in Apicurio [\#907](https://github.com/aklivity/zilla/pull/907) ([bmaidics](https://github.com/bmaidics))
- Remove name from asyncapi.specs.servers [\#906](https://github.com/aklivity/zilla/pull/906) ([bmaidics](https://github.com/bmaidics))
- Update schema to fix leaking implementation details [\#904](https://github.com/aklivity/zilla/pull/904) ([ankitk-me](https://github.com/ankitk-me))
- Update helm chart [\#901](https://github.com/aklivity/zilla/pull/901) ([attilakreiner](https://github.com/attilakreiner))
- Integrate openapi and asyncapi with catalog [\#900](https://github.com/aklivity/zilla/pull/900) ([akrambek](https://github.com/akrambek))
-  Support HTTP prefer async with OpenAPI [\#899](https://github.com/aklivity/zilla/pull/899) ([akrambek](https://github.com/akrambek))
- Asyncapi mqtt improvements [\#898](https://github.com/aklivity/zilla/pull/898) ([bmaidics](https://github.com/bmaidics))
- Support karapace catalog [\#893](https://github.com/aklivity/zilla/pull/893) ([bmaidics](https://github.com/bmaidics))
- Support BindingConfig attach and detach of composite namespaces [\#892](https://github.com/aklivity/zilla/pull/892) ([jfallows](https://github.com/jfallows))
- Cleanup warnings for JDK 21 tools [\#891](https://github.com/aklivity/zilla/pull/891) ([jfallows](https://github.com/jfallows))
- zilla crash while using model-json and schema is not found [\#889](https://github.com/aklivity/zilla/pull/889) ([ankitk-me](https://github.com/ankitk-me))
- Support specific server in OpenAPI spec in openapi binding [\#888](https://github.com/aklivity/zilla/pull/888) ([akrambek](https://github.com/akrambek))
- Support specific server in AsyncAPI spec in asyncapi binding [\#883](https://github.com/aklivity/zilla/pull/883) ([bmaidics](https://github.com/bmaidics))
- Number Validator improvement to support OpenAPI & AsyncAPI specs [\#875](https://github.com/aklivity/zilla/pull/875) ([ankitk-me](https://github.com/ankitk-me))
- Event logs for Model [\#874](https://github.com/aklivity/zilla/pull/874) ([ankitk-me](https://github.com/ankitk-me))
- String Validator improvement to support OpenAPI & AsyncAPI specs [\#873](https://github.com/aklivity/zilla/pull/873) ([ankitk-me](https://github.com/ankitk-me))
- Schema fixes + avoiding duplicate reply begin on mqtt-kafka subscribe stream [\#872](https://github.com/aklivity/zilla/pull/872) ([bmaidics](https://github.com/bmaidics))
- Support logging of schema without expressions [\#871](https://github.com/aklivity/zilla/pull/871) ([jfallows](https://github.com/jfallows))
- Fix metrics [\#869](https://github.com/aklivity/zilla/pull/869) ([attilakreiner](https://github.com/attilakreiner))
- Support metrics in openapi and asyncapi [\#868](https://github.com/aklivity/zilla/pull/868) ([akrambek](https://github.com/akrambek))
- Update README.md [\#867](https://github.com/aklivity/zilla/pull/867) ([llukyanov](https://github.com/llukyanov))
- Integer Validator improvement to support OpenAPI & AsyncAPI specs [\#830](https://github.com/aklivity/zilla/pull/830) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.74](https://github.com/aklivity/zilla/tree/0.9.74) (2024-03-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.73...0.9.74)

**Merged pull requests:**

- Fix http header value offset [\#865](https://github.com/aklivity/zilla/pull/865) ([akrambek](https://github.com/akrambek))
- Support non-404 status codes on authorization failure [\#864](https://github.com/aklivity/zilla/pull/864) ([jfallows](https://github.com/jfallows))

## [0.9.73](https://github.com/aklivity/zilla/tree/0.9.73) (2024-03-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.72...0.9.73)

**Merged pull requests:**

- Read buffer pool size from file when readonly [\#863](https://github.com/aklivity/zilla/pull/863) ([jfallows](https://github.com/jfallows))
- Resolve top level namespace guards in composite namespaces [\#862](https://github.com/aklivity/zilla/pull/862) ([jfallows](https://github.com/jfallows))
- Openapi bug fixes [\#861](https://github.com/aklivity/zilla/pull/861) ([akrambek](https://github.com/akrambek))
- Populate guarded qname for composite namespaces [\#860](https://github.com/aklivity/zilla/pull/860) ([jfallows](https://github.com/jfallows))
- Fix qvault on asyncapi composite binding [\#858](https://github.com/aklivity/zilla/pull/858) ([bmaidics](https://github.com/bmaidics))
- Support guarded qname for composite namespaces [\#857](https://github.com/aklivity/zilla/pull/857) ([jfallows](https://github.com/jfallows))

## [0.9.72](https://github.com/aklivity/zilla/tree/0.9.72) (2024-03-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.71...0.9.72)

**Merged pull requests:**

- Log http access event before validation in both http/1.1 and h2 [\#856](https://github.com/aklivity/zilla/pull/856) ([jfallows](https://github.com/jfallows))
- Fail when failed to acquire budget index [\#855](https://github.com/aklivity/zilla/pull/855) ([bmaidics](https://github.com/bmaidics))
- Conditionally release buffer slot on clean up [\#854](https://github.com/aklivity/zilla/pull/854) ([akrambek](https://github.com/akrambek))
- Fix exporter-otlp schema [\#852](https://github.com/aklivity/zilla/pull/852) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.71](https://github.com/aklivity/zilla/tree/0.9.71) (2024-03-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.70...0.9.71)

**Implemented enhancements:**

- Support `apicurio` as `catalog` for specifications [\#812](https://github.com/aklivity/zilla/issues/812)
- Support remote logging of events via `otlp` [\#785](https://github.com/aklivity/zilla/issues/785)

**Fixed bugs:**

- Openapi and asyncapi parsers throw a null pointer when a none 0 patch version is used. [\#841](https://github.com/aklivity/zilla/issues/841)
- mosquitto\_pub qos 0 fails validation with a valid message [\#838](https://github.com/aklivity/zilla/issues/838)
- Zilla Quickstart gRPC RouteGuide service hangs after lots of messages [\#719](https://github.com/aklivity/zilla/issues/719)

**Merged pull requests:**

- Add key filter support in openapi asyncapi mapping [\#851](https://github.com/aklivity/zilla/pull/851) ([akrambek](https://github.com/akrambek))
- Support verbose output of internally generated composite namespaces [\#850](https://github.com/aklivity/zilla/pull/850) ([jfallows](https://github.com/jfallows))
- Use correct offset when response has no record set [\#849](https://github.com/aklivity/zilla/pull/849) ([akrambek](https://github.com/akrambek))
- Fix binding metadata for composite bindings [\#847](https://github.com/aklivity/zilla/pull/847) ([attilakreiner](https://github.com/attilakreiner))
- CacheProduceIT.shouldRejectMessageValues nondeterministic failure fix [\#845](https://github.com/aklivity/zilla/pull/845) ([ankitk-me](https://github.com/ankitk-me))
- Implement JoinGroup request as first class stream [\#844](https://github.com/aklivity/zilla/pull/844) ([akrambek](https://github.com/akrambek))
- Fix patch detection in openapi and asyncapi [\#843](https://github.com/aklivity/zilla/pull/843) ([akrambek](https://github.com/akrambek))
- Fix mosquitto\_pub fails validation with a valid message [\#840](https://github.com/aklivity/zilla/pull/840) ([bmaidics](https://github.com/bmaidics))
- Add event logs to open telemetry exporter [\#839](https://github.com/aklivity/zilla/pull/839) ([attilakreiner](https://github.com/attilakreiner))
- Add Apicurio catalog [\#827](https://github.com/aklivity/zilla/pull/827) ([bmaidics](https://github.com/bmaidics))

## [0.9.70](https://github.com/aklivity/zilla/tree/0.9.70) (2024-03-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.69...0.9.70)

**Fixed bugs:**

- Using parameter expansion in bash doesn't work in the docker containers.  [\#829](https://github.com/aklivity/zilla/issues/829)
- Zilla crashes when a lot of MQTT clients are connected [\#762](https://github.com/aklivity/zilla/issues/762)

**Merged pull requests:**

- Fix options name, port resolving [\#833](https://github.com/aklivity/zilla/pull/833) ([bmaidics](https://github.com/bmaidics))
- Stabilize Asyncapi test with race condition [\#832](https://github.com/aklivity/zilla/pull/832) ([bmaidics](https://github.com/bmaidics))
- Simplify zilla shell script logic for sh on container images [\#831](https://github.com/aklivity/zilla/pull/831) ([jfallows](https://github.com/jfallows))
- Fix NPE in connection pool due to race condition [\#828](https://github.com/aklivity/zilla/pull/828) ([akrambek](https://github.com/akrambek))
- Refactoring event logs [\#821](https://github.com/aklivity/zilla/pull/821) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.69](https://github.com/aklivity/zilla/tree/0.9.69) (2024-03-04)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.68...0.9.69)

**Implemented enhancements:**

- Use dedicated env var to enable Incubator features [\#800](https://github.com/aklivity/zilla/issues/800)
- Support `http` to `kafka` proxy using `openapi.yaml` and `asyncapi.yaml` [\#742](https://github.com/aklivity/zilla/issues/742)
- Support `mqtt` to `kafka` proxy using `asyncapi.yaml` [\#741](https://github.com/aklivity/zilla/issues/741)
- Support `openapi` `http` proxy using `openapi.yaml` [\#740](https://github.com/aklivity/zilla/issues/740)
- Support `asyncapi` `http` proxy using `asyncapi.yaml` [\#739](https://github.com/aklivity/zilla/issues/739)
- Support `asyncapi` `mqtt` proxy using `asyncapi.yaml` [\#738](https://github.com/aklivity/zilla/issues/738)
- Support local logging of events caused by external actors [\#679](https://github.com/aklivity/zilla/issues/679)
- Support parameters in KafkaTopicsConfig [\#809](https://github.com/aklivity/zilla/pull/809) ([bmaidics](https://github.com/bmaidics))

**Fixed bugs:**

- SEVERE: Problem adapting object of type class NamespaceConfig to interface jakarta.json.JsonObject in class class NamespaceAdapter [\#796](https://github.com/aklivity/zilla/issues/796)
- Zilla is validating `env` vars before replacing them.  [\#795](https://github.com/aklivity/zilla/issues/795)
- Basic Docker Compose Setup Clogs CPU With Error Messages [\#722](https://github.com/aklivity/zilla/issues/722)

**Merged pull requests:**

- Asyncapi and Openapi bug fixes [\#826](https://github.com/aklivity/zilla/pull/826) ([akrambek](https://github.com/akrambek))
- Asyncapi catalog implementation [\#825](https://github.com/aklivity/zilla/pull/825) ([bmaidics](https://github.com/bmaidics))
- Fix NPE in KafkaSignalStream [\#823](https://github.com/aklivity/zilla/pull/823) ([bmaidics](https://github.com/bmaidics))
- Fix early flush sending for retained stream [\#822](https://github.com/aklivity/zilla/pull/822) ([bmaidics](https://github.com/bmaidics))
- Add incubating annotation for stdout exporter [\#819](https://github.com/aklivity/zilla/pull/819) ([jfallows](https://github.com/jfallows))
- MQTT-Kafka asyncapi proxy [\#818](https://github.com/aklivity/zilla/pull/818) ([bmaidics](https://github.com/bmaidics))
- Fix kafka client composite resolvedId [\#816](https://github.com/aklivity/zilla/pull/816) ([bmaidics](https://github.com/bmaidics))
- Use env var to add incubator java option [\#811](https://github.com/aklivity/zilla/pull/811) ([vordimous](https://github.com/vordimous))
- Support http to kafka proxy using openapi.yaml and asyncapi.yaml [\#810](https://github.com/aklivity/zilla/pull/810) ([akrambek](https://github.com/akrambek))
- Structured models require `catalog` config [\#807](https://github.com/aklivity/zilla/pull/807) ([ankitk-me](https://github.com/ankitk-me))
- Include qualified vault name on binding [\#806](https://github.com/aklivity/zilla/pull/806) ([jfallows](https://github.com/jfallows))
- Include config exception cause [\#805](https://github.com/aklivity/zilla/pull/805) ([jfallows](https://github.com/jfallows))
- Kafka asyncapi client [\#804](https://github.com/aklivity/zilla/pull/804) ([bmaidics](https://github.com/bmaidics))
- Support k3po ephemeral option [\#801](https://github.com/aklivity/zilla/pull/801) ([akrambek](https://github.com/akrambek))
- Support asyncapi http proxy using asyncapi.yaml [\#799](https://github.com/aklivity/zilla/pull/799) ([bmaidics](https://github.com/bmaidics))
- Fix kafka sasl schema validation to support expressions [\#798](https://github.com/aklivity/zilla/pull/798) ([akrambek](https://github.com/akrambek))
- Zilla is validating env vars before replacing them [\#797](https://github.com/aklivity/zilla/pull/797) ([akrambek](https://github.com/akrambek))
- Support openapi http proxy using openapi.yaml [\#778](https://github.com/aklivity/zilla/pull/778) ([akrambek](https://github.com/akrambek))
- Support asyncapi mqtt proxy using asyncapi.yaml [\#764](https://github.com/aklivity/zilla/pull/764) ([bmaidics](https://github.com/bmaidics))
- Support local logging of events [\#755](https://github.com/aklivity/zilla/pull/755) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.68](https://github.com/aklivity/zilla/tree/0.9.68) (2024-02-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.67...0.9.68)

**Fixed bugs:**

- Zilla crashes when a large number of MQTT clients connect [\#793](https://github.com/aklivity/zilla/issues/793)

**Merged pull requests:**

- Require group host and port for `kafka` coordinator-specific streams [\#794](https://github.com/aklivity/zilla/pull/794) ([jfallows](https://github.com/jfallows))

## [0.9.67](https://github.com/aklivity/zilla/tree/0.9.67) (2024-02-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.66...0.9.67)

**Implemented enhancements:**

- Use `model` and `view` when describing the message type [\#750](https://github.com/aklivity/zilla/issues/750)
- Support obtaining `protobuf` schemas from `schema registry` for `grpc` services [\#697](https://github.com/aklivity/zilla/issues/697)
- Support idempotent `mqtt` `qos 2` publish to `kafka` [\#677](https://github.com/aklivity/zilla/issues/677)
- Detect and inspect invalid messages received [\#676](https://github.com/aklivity/zilla/issues/676)
- Support incremental validation of fragmented messages sent by client [\#671](https://github.com/aklivity/zilla/issues/671)
- Catalog cache TTL implementation [\#658](https://github.com/aklivity/zilla/pull/658) ([ankitk-me](https://github.com/ankitk-me))

**Fixed bugs:**

- TLSv1.3 client handshake stall [\#791](https://github.com/aklivity/zilla/issues/791)
- Zilla crashes when it tries to send flush on retain stream [\#770](https://github.com/aklivity/zilla/issues/770)
- Running emqtt\_bench triggers exception in connection pool [\#716](https://github.com/aklivity/zilla/issues/716)
- `mqtt-kafka` does not limit client sharding to `mqtt v5` [\#708](https://github.com/aklivity/zilla/issues/708)
- `tls binding` should handle `null` key returned from `vault` [\#395](https://github.com/aklivity/zilla/issues/395)
- Fix zilla crash when it tries to send flush on retain stream [\#784](https://github.com/aklivity/zilla/pull/784) ([bmaidics](https://github.com/bmaidics))
- Limit sharding to mqtt 5 [\#760](https://github.com/aklivity/zilla/pull/760) ([bmaidics](https://github.com/bmaidics))

**Merged pull requests:**

- Simplify TLSv1.3 handshake check [\#792](https://github.com/aklivity/zilla/pull/792) ([jfallows](https://github.com/jfallows))
- Support TLSv1.3 handshake completion [\#790](https://github.com/aklivity/zilla/pull/790) ([jfallows](https://github.com/jfallows))
- Refactor NamespacedId to public API [\#789](https://github.com/aklivity/zilla/pull/789) ([jfallows](https://github.com/jfallows))
- Align affinity for kafka group coordinator [\#788](https://github.com/aklivity/zilla/pull/788) ([jfallows](https://github.com/jfallows))
-  Log validation failure of HTTP messages \(stdout\) [\#781](https://github.com/aklivity/zilla/pull/781) ([ankitk-me](https://github.com/ankitk-me))
- Supply client id by host only, and move defaulting to caller [\#780](https://github.com/aklivity/zilla/pull/780) ([jfallows](https://github.com/jfallows))
- Handle unknown vault keys in tls binding [\#779](https://github.com/aklivity/zilla/pull/779) ([jfallows](https://github.com/jfallows))
- Refactor to use kafka server config per client network stream… [\#777](https://github.com/aklivity/zilla/pull/777) ([jfallows](https://github.com/jfallows))
- update docker-image pom.xml to refer model modules [\#775](https://github.com/aklivity/zilla/pull/775) ([ankitk-me](https://github.com/ankitk-me))
-  Skip invalid Kafka messages during Fetch [\#774](https://github.com/aklivity/zilla/pull/774) ([ankitk-me](https://github.com/ankitk-me))
- Refactoring supplyValidator to MqttServerFactory [\#773](https://github.com/aklivity/zilla/pull/773) ([ankitk-me](https://github.com/ankitk-me))
- TTL based cache update cleanup [\#772](https://github.com/aklivity/zilla/pull/772) ([ankitk-me](https://github.com/ankitk-me))
- HTTP response bug fix and other minor refactoring [\#769](https://github.com/aklivity/zilla/pull/769) ([ankitk-me](https://github.com/ankitk-me))
- Model specific cache detect schema change update [\#767](https://github.com/aklivity/zilla/pull/767) ([ankitk-me](https://github.com/ankitk-me))
- feature/schema-registry catchup with develop [\#765](https://github.com/aklivity/zilla/pull/765) ([ankitk-me](https://github.com/ankitk-me))
- model and view changes [\#763](https://github.com/aklivity/zilla/pull/763) ([ankitk-me](https://github.com/ankitk-me))
- Json Fragment Validator Implementation [\#761](https://github.com/aklivity/zilla/pull/761) ([ankitk-me](https://github.com/ankitk-me))
- Support obtaining protobuf schemas from schema registry for grpc services [\#757](https://github.com/aklivity/zilla/pull/757) ([akrambek](https://github.com/akrambek))
- Bump actions/cache from 3 to 4 [\#748](https://github.com/aklivity/zilla/pull/748) ([dependabot[bot]](https://github.com/apps/dependabot))
- Fragment validator interface & implementation [\#735](https://github.com/aklivity/zilla/pull/735) ([ankitk-me](https://github.com/ankitk-me))
- Qos2 idempotent producer [\#733](https://github.com/aklivity/zilla/pull/733) ([bmaidics](https://github.com/bmaidics))
- Mqtt-kafka single group support cont [\#731](https://github.com/aklivity/zilla/pull/731) ([akrambek](https://github.com/akrambek))
- migrating from Validator to Converter [\#729](https://github.com/aklivity/zilla/pull/729) ([ankitk-me](https://github.com/ankitk-me))
- Catch up dump command with kafka extension changes [\#728](https://github.com/aklivity/zilla/pull/728) ([attilakreiner](https://github.com/attilakreiner))
- Improve mqtt-kafka to use only one kafka consumer group per mqtt client. [\#727](https://github.com/aklivity/zilla/pull/727) ([akrambek](https://github.com/akrambek))
- Protobuf Validation & Conversion [\#691](https://github.com/aklivity/zilla/pull/691) ([ankitk-me](https://github.com/ankitk-me))
- Validator Interface Update & Converter Changes [\#533](https://github.com/aklivity/zilla/pull/533) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.66](https://github.com/aklivity/zilla/tree/0.9.66) (2024-01-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.65...0.9.66)

**Implemented enhancements:**

- Support `openapi` `http` response validation [\#684](https://github.com/aklivity/zilla/issues/684)
- Support `protobuf` conversion to and from `json` for `kafka` messages [\#682](https://github.com/aklivity/zilla/issues/682)
- Support incubator features preview in zilla release docker image [\#670](https://github.com/aklivity/zilla/issues/670)

**Fixed bugs:**

- Schema validation fails before the `${{env.*}}` parameters have been removed [\#583](https://github.com/aklivity/zilla/issues/583)

**Merged pull requests:**

- update license exclude path to include both zpmw files [\#759](https://github.com/aklivity/zilla/pull/759) ([vordimous](https://github.com/vordimous))
- Refactor resolvers to support configuration [\#758](https://github.com/aklivity/zilla/pull/758) ([jfallows](https://github.com/jfallows))
- Fix docker file path [\#756](https://github.com/aklivity/zilla/pull/756) ([akrambek](https://github.com/akrambek))
- Support incubator features preview in zilla release docker image [\#753](https://github.com/aklivity/zilla/pull/753) ([akrambek](https://github.com/akrambek))
- Support  expression for primitive type in json schema [\#751](https://github.com/aklivity/zilla/pull/751) ([akrambek](https://github.com/akrambek))
-  Implement response validation in http client binding [\#732](https://github.com/aklivity/zilla/pull/732) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.65](https://github.com/aklivity/zilla/tree/0.9.65) (2024-01-18)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.64...0.9.65)

**Implemented enhancements:**

- Support `avro` conversion to and from `json` for `kafka` messages [\#681](https://github.com/aklivity/zilla/issues/681)
- Support observability of zilla engine internal streams [\#678](https://github.com/aklivity/zilla/issues/678)
- Simplify configuration of multiple protocols on different tcp ports [\#669](https://github.com/aklivity/zilla/issues/669)
- Simplify kafka client bootstrap server names and ports config [\#619](https://github.com/aklivity/zilla/issues/619)
- MQTT publish QoS 2 as Kafka produce with acks in\_sync\_replicas and idempotent `producerId` [\#605](https://github.com/aklivity/zilla/issues/605)
- Add the option to route by `port` in the `tls` binding [\#564](https://github.com/aklivity/zilla/issues/564)
- Support outbound message transformation from `protobuf` to `json` [\#458](https://github.com/aklivity/zilla/issues/458)
- Support inbound message transformation from `json` to `protobuf` [\#457](https://github.com/aklivity/zilla/issues/457)
- Support outbound message transformation from `avro` to `json` [\#315](https://github.com/aklivity/zilla/issues/315)
- Support inbound message transformation from `json` to `avro` [\#313](https://github.com/aklivity/zilla/issues/313)
- Handle data fragmentation for MQTT binding [\#282](https://github.com/aklivity/zilla/issues/282)
- separating publish streams based on qos [\#726](https://github.com/aklivity/zilla/pull/726) ([bmaidics](https://github.com/bmaidics))
- Add `sse`, `ws`, `fs` extension parsing to `dump` command [\#660](https://github.com/aklivity/zilla/pull/660) ([attilakreiner](https://github.com/attilakreiner))
- Support MQTT fragmented messages [\#651](https://github.com/aklivity/zilla/pull/651) ([bmaidics](https://github.com/bmaidics))

**Fixed bugs:**

- Unable to Run MQTT Example Successfully [\#724](https://github.com/aklivity/zilla/issues/724)
- Http1 server not progressing after reaching full buffer slot size [\#715](https://github.com/aklivity/zilla/issues/715)
- `mqtt-kafka` binding uses 2 different consumer groups per `mqtt` client [\#698](https://github.com/aklivity/zilla/issues/698)
- Optimize memory allocation for `mqtt-kafka` offset tracking [\#675](https://github.com/aklivity/zilla/issues/675)
- connection pool stops handling signals after while causing mqtt client to hang [\#667](https://github.com/aklivity/zilla/issues/667)
- Kafka Merge is getting stall because of intermediate partition offset state [\#666](https://github.com/aklivity/zilla/issues/666)
- Handle large message in grpc binding [\#648](https://github.com/aklivity/zilla/issues/648)
- update zilla jsonschemas [\#637](https://github.com/aklivity/zilla/issues/637)
- Mqtt session takeover is not working when the second client connects to the same Zilla instance [\#620](https://github.com/aklivity/zilla/issues/620)
- http2.network.ConnectionManagementIT.serverSent100kMessage test fails sporadically due to race [\#134](https://github.com/aklivity/zilla/issues/134)
- Fix tcp flow control issue [\#704](https://github.com/aklivity/zilla/pull/704) ([bmaidics](https://github.com/bmaidics))
- Optimize memory allocation for mqtt-kafka offset tracking [\#694](https://github.com/aklivity/zilla/pull/694) ([bmaidics](https://github.com/bmaidics))
- Send disconnect even without mqtt reset extension [\#689](https://github.com/aklivity/zilla/pull/689) ([bmaidics](https://github.com/bmaidics))

**Closed issues:**

- Prototype composite binding support with nested namespaces [\#685](https://github.com/aklivity/zilla/issues/685)
- Build has been failed in local [\#229](https://github.com/aklivity/zilla/issues/229)

**Merged pull requests:**

- Bump ubuntu from jammy-20231128 to jammy-20240111 in /cloud/docker-image/src/main/docker/incubator [\#747](https://github.com/aklivity/zilla/pull/747) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump ubuntu from jammy-20231128 to jammy-20240111 in /cloud/docker-image/src/main/docker/release [\#746](https://github.com/aklivity/zilla/pull/746) ([dependabot[bot]](https://github.com/apps/dependabot))
- Support composite binding config [\#737](https://github.com/aklivity/zilla/pull/737) ([jfallows](https://github.com/jfallows))
- Add amqp extension parsing to dump command [\#723](https://github.com/aklivity/zilla/pull/723) ([attilakreiner](https://github.com/attilakreiner))
- Suppress checkstyle for generated sources [\#721](https://github.com/aklivity/zilla/pull/721) ([jfallows](https://github.com/jfallows))
- Ignore line length check for import and package statements [\#720](https://github.com/aklivity/zilla/pull/720) ([jfallows](https://github.com/jfallows))
- Bump com.fasterxml.jackson.dataformat:jackson-dataformat-yaml from 2.15.2 to 2.16.1 [\#718](https://github.com/aklivity/zilla/pull/718) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump byteman.version from 4.0.21 to 4.0.22 [\#717](https://github.com/aklivity/zilla/pull/717) ([dependabot[bot]](https://github.com/apps/dependabot))
- Http1 server not progressing after reaching full buffer slot size [\#714](https://github.com/aklivity/zilla/pull/714) ([akrambek](https://github.com/akrambek))
- Bump org.apache.maven:maven from 3.9.4 to 3.9.6 [\#712](https://github.com/aklivity/zilla/pull/712) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.maven.plugins:maven-compiler-plugin from 3.11.0 to 3.12.1 [\#711](https://github.com/aklivity/zilla/pull/711) ([dependabot[bot]](https://github.com/apps/dependabot))
- Simplify kafka client bootstrap server names and ports config [\#710](https://github.com/aklivity/zilla/pull/710) ([akrambek](https://github.com/akrambek))
- Align tcp net read window [\#709](https://github.com/aklivity/zilla/pull/709) ([jfallows](https://github.com/jfallows))
- Add kafka extension parsing to dump command [\#706](https://github.com/aklivity/zilla/pull/706) ([attilakreiner](https://github.com/attilakreiner))
- Bump org.codehaus.mojo:exec-maven-plugin from 3.1.0 to 3.1.1 [\#703](https://github.com/aklivity/zilla/pull/703) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.jacoco:jacoco-maven-plugin from 0.8.10 to 0.8.11 [\#701](https://github.com/aklivity/zilla/pull/701) ([dependabot[bot]](https://github.com/apps/dependabot))
- Unnecessary deferred value causes the connection to stall [\#700](https://github.com/aklivity/zilla/pull/700) ([akrambek](https://github.com/akrambek))
- Refactor dispatch agent [\#699](https://github.com/aklivity/zilla/pull/699) ([jfallows](https://github.com/jfallows))
- Reset back initial max once ack is fully caught up with seq [\#696](https://github.com/aklivity/zilla/pull/696) ([akrambek](https://github.com/akrambek))
- Add mqtt extension parsing to dump command [\#695](https://github.com/aklivity/zilla/pull/695) ([attilakreiner](https://github.com/attilakreiner))
- Reject stream if deferred is not set for the fragmented message [\#693](https://github.com/aklivity/zilla/pull/693) ([akrambek](https://github.com/akrambek))
- Remove wrong state assignment in the group cache [\#692](https://github.com/aklivity/zilla/pull/692) ([akrambek](https://github.com/akrambek))
- Bump org.moditect:moditect-maven-plugin from 1.0.0.Final to 1.1.0 [\#688](https://github.com/aklivity/zilla/pull/688) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump commons-cli:commons-cli from 1.3.1 to 1.6.0 [\#687](https://github.com/aklivity/zilla/pull/687) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump junit.version from 5.8.2 to 5.10.1 [\#686](https://github.com/aklivity/zilla/pull/686) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump jakarta.json:jakarta.json-api from 2.0.1 to 2.1.3 [\#674](https://github.com/aklivity/zilla/pull/674) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump jmh.version from 1.12 to 1.37 [\#673](https://github.com/aklivity/zilla/pull/673) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump com.guicedee.services:commons-collections4 from 1.1.0.7 to 1.2.2.1 [\#672](https://github.com/aklivity/zilla/pull/672) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.mockito:mockito-core from 5.3.1 to 5.8.0 [\#665](https://github.com/aklivity/zilla/pull/665) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.slf4j:slf4j-api from 1.7.36 to 2.0.10 [\#664](https://github.com/aklivity/zilla/pull/664) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.hamcrest:hamcrest-library from 1.3 to 2.2 [\#663](https://github.com/aklivity/zilla/pull/663) ([dependabot[bot]](https://github.com/apps/dependabot))
- Update latest and stable offset if it was in stabilizing state [\#661](https://github.com/aklivity/zilla/pull/661) ([akrambek](https://github.com/akrambek))
- Release kafka connection pool budget [\#659](https://github.com/aklivity/zilla/pull/659) ([akrambek](https://github.com/akrambek))
- Handle large message in grpc [\#649](https://github.com/aklivity/zilla/pull/649) ([akrambek](https://github.com/akrambek))
- Feature/tls ports [\#591](https://github.com/aklivity/zilla/pull/591) ([lukefallows](https://github.com/lukefallows))
- Bump eclipse-temurin from 20-alpine to 21-alpine in /cloud/docker-image/src/main/docker/release [\#506](https://github.com/aklivity/zilla/pull/506) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump eclipse-temurin from 20-jdk to 21-jdk in /cloud/docker-image/src/main/docker/incubator [\#505](https://github.com/aklivity/zilla/pull/505) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.slf4j:slf4j-simple from 1.7.21 to 2.0.9 [\#392](https://github.com/aklivity/zilla/pull/392) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump com.github.biboudis:jmh-profilers from 0.1.3 to 0.1.4 [\#385](https://github.com/aklivity/zilla/pull/385) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.eclipse:yasson from 2.0.3 to 3.0.3 [\#346](https://github.com/aklivity/zilla/pull/346) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.64](https://github.com/aklivity/zilla/tree/0.9.64) (2023-12-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.63...0.9.64)

**Merged pull requests:**

- MQTT topic sharding [\#657](https://github.com/aklivity/zilla/pull/657) ([jfallows](https://github.com/jfallows))
- Move everything except fetch and produce to use connection pool [\#656](https://github.com/aklivity/zilla/pull/656) ([akrambek](https://github.com/akrambek))

## [0.9.63](https://github.com/aklivity/zilla/tree/0.9.63) (2023-12-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.62...0.9.63)

**Implemented enhancements:**

- Support MQTT message expiry in `mqtt-kafka` mapping [\#631](https://github.com/aklivity/zilla/issues/631)
- Add grpc extension parsing to the dump command [\#652](https://github.com/aklivity/zilla/pull/652) ([attilakreiner](https://github.com/attilakreiner))
- Add end-to-end testing for the `dump` command [\#646](https://github.com/aklivity/zilla/pull/646) ([attilakreiner](https://github.com/attilakreiner))
- Implement mqtt message expiry [\#640](https://github.com/aklivity/zilla/pull/640) ([bmaidics](https://github.com/bmaidics))
- Improve server sent DISCONNECT reasonCodes [\#634](https://github.com/aklivity/zilla/pull/634) ([bmaidics](https://github.com/bmaidics))

**Fixed bugs:**

- OffsetFetch Request should connect to the coordinator instead of a random member of cluster [\#653](https://github.com/aklivity/zilla/issues/653)
- Mqtt-kakfa will message bugfixes [\#644](https://github.com/aklivity/zilla/pull/644) ([bmaidics](https://github.com/bmaidics))

**Closed issues:**

- gRPC remote\_server gets duplicate messages [\#480](https://github.com/aklivity/zilla/issues/480)
- Log compaction behavior with or without bootstrap is not consistent [\#389](https://github.com/aklivity/zilla/issues/389)

**Merged pull requests:**

- Fix static field [\#655](https://github.com/aklivity/zilla/pull/655) ([akrambek](https://github.com/akrambek))
- OffsetFetch Request should connect to the coordinator instead of a random member of cluster  [\#654](https://github.com/aklivity/zilla/pull/654) ([akrambek](https://github.com/akrambek))
- Bump actions/upload-artifact from 3 to 4 [\#645](https://github.com/aklivity/zilla/pull/645) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump github/codeql-action from 2 to 3 [\#643](https://github.com/aklivity/zilla/pull/643) ([dependabot[bot]](https://github.com/apps/dependabot))
- Fix `java.util.MissingFormatArgumentException` when using Kafka debugging. [\#639](https://github.com/aklivity/zilla/pull/639) ([voutilad](https://github.com/voutilad))
- Json schema errors [\#638](https://github.com/aklivity/zilla/pull/638) ([vordimous](https://github.com/vordimous))
- Add jumbograms and proxy extension parsing to dump command [\#635](https://github.com/aklivity/zilla/pull/635) ([attilakreiner](https://github.com/attilakreiner))
- Bump ubuntu from jammy-20230916 to jammy-20231128 in /cloud/docker-image/src/main/docker/incubator [\#608](https://github.com/aklivity/zilla/pull/608) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump ubuntu from jammy-20230916 to jammy-20231128 in /cloud/docker-image/src/main/docker/release [\#607](https://github.com/aklivity/zilla/pull/607) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.62](https://github.com/aklivity/zilla/tree/0.9.62) (2023-12-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.61...0.9.62)

**Closed issues:**

- MQTT sessions don't show up in Redpanda [\#585](https://github.com/aklivity/zilla/issues/585)

**Merged pull requests:**

- Reinitiate initialId and replyId on  mqtt session reconnection [\#636](https://github.com/aklivity/zilla/pull/636) ([akrambek](https://github.com/akrambek))
- Support ability to connect to specific kafka cluster node hostname [\#633](https://github.com/aklivity/zilla/pull/633) ([akrambek](https://github.com/akrambek))
- Zpm install instrument [\#632](https://github.com/aklivity/zilla/pull/632) ([jfallows](https://github.com/jfallows))
- Bump alpine from 3.18.5 to 3.19.0 in /cloud/docker-image/src/main/docker/release [\#626](https://github.com/aklivity/zilla/pull/626) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.9.61](https://github.com/aklivity/zilla/tree/0.9.61) (2023-12-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.60...0.9.61)

**Implemented enhancements:**

- Kafka GRPC consumer Group Support [\#597](https://github.com/aklivity/zilla/issues/597)
- Integrate Schema Registry / Karapace [\#404](https://github.com/aklivity/zilla/issues/404)
- Apply typed schema to cached messages; `kafka cache fetch server` [\#314](https://github.com/aklivity/zilla/issues/314)
- `schema-registry` feature \(baseline\) [\#265](https://github.com/aklivity/zilla/issues/265)
- Enhance inspection of internal streams [\#154](https://github.com/aklivity/zilla/issues/154)

**Fixed bugs:**

- Group Coordinator sasl scram doesn't have complete full handshake [\#624](https://github.com/aklivity/zilla/issues/624)
- Follow kafka consumer protocol data structure for userdata parsing [\#617](https://github.com/aklivity/zilla/issues/617)
- WebSocket inbound `ping` frames are rejected [\#606](https://github.com/aklivity/zilla/issues/606)
- Fix encoding error when no properties defined by the client [\#627](https://github.com/aklivity/zilla/pull/627) ([bmaidics](https://github.com/bmaidics))

**Closed issues:**

- MQTT client is disconnected and cannot reconnect after sending message [\#623](https://github.com/aklivity/zilla/issues/623)
- Use affinity and Long2ObjectHashmap instead of clientId  [\#432](https://github.com/aklivity/zilla/issues/432)

**Merged pull requests:**

- WebSocket inbound ping frames support [\#629](https://github.com/aklivity/zilla/pull/629) ([akrambek](https://github.com/akrambek))
- Split qos0 and qos12 publish streams, add ISR [\#628](https://github.com/aklivity/zilla/pull/628) ([bmaidics](https://github.com/bmaidics))
- Update kafka client group session timeout defaults [\#625](https://github.com/aklivity/zilla/pull/625) ([jfallows](https://github.com/jfallows))
- Fix handeling sasl scram error in group coordinator [\#622](https://github.com/aklivity/zilla/pull/622) ([akrambek](https://github.com/akrambek))
- Include kafka client id consistently in all kafka protocol encoders [\#621](https://github.com/aklivity/zilla/pull/621) ([jfallows](https://github.com/jfallows))
- Follow kafka consumer protocol data structure for userdata parsing [\#618](https://github.com/aklivity/zilla/pull/618) ([akrambek](https://github.com/akrambek))
- Kafka GRPC consumer Group Support [\#598](https://github.com/aklivity/zilla/pull/598) ([akrambek](https://github.com/akrambek))
- Enhance inspection of internal streams [\#596](https://github.com/aklivity/zilla/pull/596) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.60](https://github.com/aklivity/zilla/tree/0.9.60) (2023-12-05)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.59...0.9.60)

**Implemented enhancements:**

- MQTT publish QoS 1 as Kafka produce with acks in\_sync\_replicas [\#604](https://github.com/aklivity/zilla/issues/604)
- MQTT subscribe QoS 2 as stateful Kafka fetch with `consumerId` for message delivery retry [\#603](https://github.com/aklivity/zilla/issues/603)
- MQTT subscribe QoS 1 as stateful Kafka fetch with `consumerId` for message delivery retry [\#602](https://github.com/aklivity/zilla/issues/602)
- Include metadata in merge reply begin ex [\#601](https://github.com/aklivity/zilla/issues/601)
- Consumer group message acknowledgement support [\#588](https://github.com/aklivity/zilla/issues/588)
- Support mqtt protocol v3.1.1 [\#541](https://github.com/aklivity/zilla/issues/541)
- Generate `http` server request `validators` from `OpenAPI` specification [\#459](https://github.com/aklivity/zilla/issues/459)
- MQTT 3.1.1 implementation [\#582](https://github.com/aklivity/zilla/pull/582) ([bmaidics](https://github.com/bmaidics))

**Fixed bugs:**

- the `tls` binding throws NPE if there are no `options` defined [\#612](https://github.com/aklivity/zilla/issues/612)
- Offset commit request should have next offset instead of consumer message offset [\#592](https://github.com/aklivity/zilla/issues/592)
- `group.min.session.timeout.ms` is null using zilla in front of redpanda [\#581](https://github.com/aklivity/zilla/issues/581)
- java.lang.IllegalStateException: missing file for budgets : /var/run/zilla/budgets127 [\#578](https://github.com/aklivity/zilla/issues/578)

**Closed issues:**

- `prometheus` schema Port and `tcp` schema Port have different validation [\#569](https://github.com/aklivity/zilla/issues/569)
- zilla:correlation-id header sort [\#508](https://github.com/aklivity/zilla/issues/508)

**Merged pull requests:**

- Fix typo [\#616](https://github.com/aklivity/zilla/pull/616) ([bmaidics](https://github.com/bmaidics))
- Wait for replyFlush at commit before closing retained stream [\#615](https://github.com/aklivity/zilla/pull/615) ([bmaidics](https://github.com/bmaidics))
- Fix qos12 [\#614](https://github.com/aklivity/zilla/pull/614) ([bmaidics](https://github.com/bmaidics))
- Start from historical messages if no consumer offsets were committed [\#613](https://github.com/aklivity/zilla/pull/613) ([akrambek](https://github.com/akrambek))
- Tls binding options not required [\#611](https://github.com/aklivity/zilla/pull/611) ([jfallows](https://github.com/jfallows))
- Fix not closing retained stream [\#610](https://github.com/aklivity/zilla/pull/610) ([bmaidics](https://github.com/bmaidics))
- Fix mqtt-kakfa qos1,2 issues [\#609](https://github.com/aklivity/zilla/pull/609) ([bmaidics](https://github.com/bmaidics))
- Bump alpine from 3.18.4 to 3.18.5 in /cloud/docker-image/src/main/docker/release [\#600](https://github.com/aklivity/zilla/pull/600) ([dependabot[bot]](https://github.com/apps/dependabot))
- Include metadata and partitionOffset into merge reply begin [\#599](https://github.com/aklivity/zilla/pull/599) ([akrambek](https://github.com/akrambek))
- Update Helm chart Zilla description [\#595](https://github.com/aklivity/zilla/pull/595) ([vordimous](https://github.com/vordimous))
- Bump actions/setup-java from 3 to 4 [\#594](https://github.com/aklivity/zilla/pull/594) ([dependabot[bot]](https://github.com/apps/dependabot))
- Offset commit fixes [\#593](https://github.com/aklivity/zilla/pull/593) ([akrambek](https://github.com/akrambek))
- Implement QoS 1 and QoS 2 [\#589](https://github.com/aklivity/zilla/pull/589) ([bmaidics](https://github.com/bmaidics))
- Fix prometheus exporter schema [\#587](https://github.com/aklivity/zilla/pull/587) ([attilakreiner](https://github.com/attilakreiner))
- Include validation in the `openapi.http.proxy` generator [\#586](https://github.com/aklivity/zilla/pull/586) ([attilakreiner](https://github.com/attilakreiner))
- Fix mergedReplyBudgetId [\#579](https://github.com/aklivity/zilla/pull/579) ([attilakreiner](https://github.com/attilakreiner))
- MQTT 3.1.1 support - specs [\#570](https://github.com/aklivity/zilla/pull/570) ([bmaidics](https://github.com/bmaidics))
- Consumer group message acknowledgement support [\#538](https://github.com/aklivity/zilla/pull/538) ([akrambek](https://github.com/akrambek))
- Include data payload as hex in the output of log command [\#523](https://github.com/aklivity/zilla/pull/523) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.59](https://github.com/aklivity/zilla/tree/0.9.59) (2023-11-21)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.58...0.9.59)

**Implemented enhancements:**

- Generate `http` server request `validators` from `AsyncAPI` specification [\#460](https://github.com/aklivity/zilla/issues/460)

**Fixed bugs:**

- MQTT topic routing doesn't correctly reject pub/sub requests [\#572](https://github.com/aklivity/zilla/issues/572)
- Fix producing empty message to retained topic [\#577](https://github.com/aklivity/zilla/pull/577) ([bmaidics](https://github.com/bmaidics))
- Fix MQTT topic routing doesn't correctly reject pub/sub requests [\#573](https://github.com/aklivity/zilla/pull/573) ([bmaidics](https://github.com/bmaidics))

**Closed issues:**

- Empty messages on `retained` topic for all MQTT messages [\#575](https://github.com/aklivity/zilla/issues/575)
- \[DOCS\] Fix typos in README file [\#540](https://github.com/aklivity/zilla/issues/540)

**Merged pull requests:**

- Consumer group session timeout defaults [\#584](https://github.com/aklivity/zilla/pull/584) ([jfallows](https://github.com/jfallows))
- Fix json validator to also accept arrays [\#576](https://github.com/aklivity/zilla/pull/576) ([attilakreiner](https://github.com/attilakreiner))
- Include validation in the `asyncapi.http.proxy` generator [\#574](https://github.com/aklivity/zilla/pull/574) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.58](https://github.com/aklivity/zilla/tree/0.9.58) (2023-11-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.57...0.9.58)

**Implemented enhancements:**

- Integrate `http` binding with `validators` [\#455](https://github.com/aklivity/zilla/issues/455)

**Fixed bugs:**

- \[MQTT-Kafka\] Exception runtime.binding.mqtt.kafka.internal.types.MqttExpirySignalFW.wrap\(MqttExpirySignalFW.java:45\) [\#563](https://github.com/aklivity/zilla/issues/563)
- Running mqtt benchmark triggers mqtt exception [\#488](https://github.com/aklivity/zilla/issues/488)
- Fix IndexOutOfBoundsException when receiving expiry signal [\#567](https://github.com/aklivity/zilla/pull/567) ([bmaidics](https://github.com/bmaidics))

**Merged pull requests:**

- Integrate http binding with validators [\#571](https://github.com/aklivity/zilla/pull/571) ([attilakreiner](https://github.com/attilakreiner))
- Fix flow conrol bug + indexoutofbound exception [\#568](https://github.com/aklivity/zilla/pull/568) ([bmaidics](https://github.com/bmaidics))

## [0.9.57](https://github.com/aklivity/zilla/tree/0.9.57) (2023-11-04)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.56...0.9.57)

**Fixed bugs:**

- \[Connection Pool\] binding.kafka.internal.stream.KafkaClientConnectionPool$KafkaClientConnection.doConnectionWindow\(KafkaClientConnectionPool.java:1318\) [\#565](https://github.com/aklivity/zilla/issues/565)
- \[MQTT-Kafka\] Randomly closing the connection in the middle of produce triggers the exception [\#559](https://github.com/aklivity/zilla/issues/559)
- Gracefully handle out of slot exception in kafka cache client produce [\#558](https://github.com/aklivity/zilla/issues/558)
- \[Connection Pool\] Signaling can trigger exception [\#557](https://github.com/aklivity/zilla/issues/557)
- `http-kafka` fetch binding returns malformed JSON when the payload is large  [\#528](https://github.com/aklivity/zilla/issues/528)

**Merged pull requests:**

- Ignore removing ack before receiving complete response even if the stream reply is closed [\#566](https://github.com/aklivity/zilla/pull/566) ([akrambek](https://github.com/akrambek))
- Fix bootstrap test [\#562](https://github.com/aklivity/zilla/pull/562) ([bmaidics](https://github.com/bmaidics))
- Gracefully handle out of slot exception in kafka client produce [\#561](https://github.com/aklivity/zilla/pull/561) ([akrambek](https://github.com/akrambek))
- Better handling negative  edge cases in the connection pool [\#560](https://github.com/aklivity/zilla/pull/560) ([akrambek](https://github.com/akrambek))
- Handle fragmentation in HttpFetchManyProxy [\#556](https://github.com/aklivity/zilla/pull/556) ([akrambek](https://github.com/akrambek))
- Added Contributors section in readme [\#553](https://github.com/aklivity/zilla/pull/553) ([DhanushNehru](https://github.com/DhanushNehru))
- Fix: Added Contribution Section to Readme [\#550](https://github.com/aklivity/zilla/pull/550) ([Kalyanimhala](https://github.com/Kalyanimhala))

## [0.9.56](https://github.com/aklivity/zilla/tree/0.9.56) (2023-10-31)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.55...0.9.56)

**Implemented enhancements:**

- Support `extraEnv` in helm chart [\#520](https://github.com/aklivity/zilla/issues/520)
- `kubernetes autoscaling` feature \(enhanced\) [\#518](https://github.com/aklivity/zilla/issues/518)
- Shard MQTT topic space for client-id specific subset [\#427](https://github.com/aklivity/zilla/issues/427)
- Distribute MQTT topic space across different Kafka topics [\#426](https://github.com/aklivity/zilla/issues/426)
- `AsyncAPI` integration \(baseline\) [\#257](https://github.com/aklivity/zilla/issues/257)
- `OpenAPI` integration \(baseline\) [\#255](https://github.com/aklivity/zilla/issues/255)
- `mqtt-kafka` feature \(baseline\) [\#190](https://github.com/aklivity/zilla/issues/190)
- `telemetry metrics` feature \(baseline\) [\#188](https://github.com/aklivity/zilla/issues/188)
- `grpc-kafka` feature \(baseline\) [\#183](https://github.com/aklivity/zilla/issues/183)

**Fixed bugs:**

- Etag header field name MUST be converted to lowercase prior to their encoding in HTTP/2 [\#551](https://github.com/aklivity/zilla/issues/551)
- BudgetDebitor fails to claim budget after sometime [\#548](https://github.com/aklivity/zilla/issues/548)
- Unexpected flush causes NPE in connection pool [\#546](https://github.com/aklivity/zilla/issues/546)
- \[Consumer Group\] Race  condition while joining simultaneously to the same group id [\#542](https://github.com/aklivity/zilla/issues/542)
- MQTT client connections cause errors/crashes [\#527](https://github.com/aklivity/zilla/issues/527)
- Sporadic github action build failures [\#526](https://github.com/aklivity/zilla/issues/526)
- Unable to write to streams buffer under bidi-stream [\#368](https://github.com/aklivity/zilla/issues/368)
- Fix flow control bug in mqtt-kakfa publish [\#524](https://github.com/aklivity/zilla/pull/524) ([bmaidics](https://github.com/bmaidics))

**Closed issues:**

- Feature: Adding contributors section to the README.md file. [\#545](https://github.com/aklivity/zilla/issues/545)
- gRPC method call doesn't respond when status code is not OK [\#504](https://github.com/aklivity/zilla/issues/504)

**Merged pull requests:**

- Fix mqtt connect decoding bug when remainingLenght.size \> 1 [\#554](https://github.com/aklivity/zilla/pull/554) ([bmaidics](https://github.com/bmaidics))
- Etag header field name MUST be converted to lowercase prior to their encoding in HTTP/2 [\#552](https://github.com/aklivity/zilla/pull/552) ([akrambek](https://github.com/akrambek))
- Don't send window before connection budgetId is assigned [\#549](https://github.com/aklivity/zilla/pull/549) ([akrambek](https://github.com/akrambek))
- Use coordinator member list to check if the heartbeat is allowed [\#547](https://github.com/aklivity/zilla/pull/547) ([akrambek](https://github.com/akrambek))
- Retry sync group request if there is inflight request [\#543](https://github.com/aklivity/zilla/pull/543) ([akrambek](https://github.com/akrambek))
- Add "Back to Top" in Readme.md [\#539](https://github.com/aklivity/zilla/pull/539) ([PrajwalGraj](https://github.com/PrajwalGraj))
- Create an appropriate buffer with the size that accommodates signal frame payload [\#537](https://github.com/aklivity/zilla/pull/537) ([akrambek](https://github.com/akrambek))
- Adjust padding for larger message header and don't include partial data while computing crc32c  [\#536](https://github.com/aklivity/zilla/pull/536) ([akrambek](https://github.com/akrambek))
- Fix dump command to truncate output file if exists [\#534](https://github.com/aklivity/zilla/pull/534) ([attilakreiner](https://github.com/attilakreiner))
- fix typos in README.md [\#532](https://github.com/aklivity/zilla/pull/532) ([shresthasurav](https://github.com/shresthasurav))
- Fixed a typo in README.md [\#529](https://github.com/aklivity/zilla/pull/529) ([saakshii12](https://github.com/saakshii12))
- Sporadic github action build failure fix [\#522](https://github.com/aklivity/zilla/pull/522) ([akrambek](https://github.com/akrambek))
- Propagate gRPC status code when not ok [\#519](https://github.com/aklivity/zilla/pull/519) ([jfallows](https://github.com/jfallows))
- Add extraEnv to Deployment in the helm chart [\#511](https://github.com/aklivity/zilla/pull/511) ([attilakreiner](https://github.com/attilakreiner))
- Client topic space [\#507](https://github.com/aklivity/zilla/pull/507) ([bmaidics](https://github.com/bmaidics))
- Mqtt topic space [\#493](https://github.com/aklivity/zilla/pull/493) ([bmaidics](https://github.com/bmaidics))

## [0.9.55](https://github.com/aklivity/zilla/tree/0.9.55) (2023-10-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.54...0.9.55)

**Implemented enhancements:**

- Use 1-1 helm chart versioning strategy [\#487](https://github.com/aklivity/zilla/issues/487)
- Generate `mqtt` server publish `validators` from `AsyncAPI` specification [\#461](https://github.com/aklivity/zilla/issues/461)
- Integrate `mqtt` binding with `validators` [\#456](https://github.com/aklivity/zilla/issues/456)
- Implement `json` validator [\#454](https://github.com/aklivity/zilla/issues/454)
- Support `inline` catalog for validators [\#453](https://github.com/aklivity/zilla/issues/453)
- Enforce inbound type checking; `kafka cache produce client` [\#312](https://github.com/aklivity/zilla/issues/312)

**Fixed bugs:**

- 0 for no mqtt session expiry should be mapped to max value for the group stream [\#501](https://github.com/aklivity/zilla/issues/501)
- Group stream with same group id may get hang [\#500](https://github.com/aklivity/zilla/issues/500)
- Not cleaning up group stream on group leave response. [\#491](https://github.com/aklivity/zilla/issues/491)
- Connection pool flowcontrol can trigger exception [\#482](https://github.com/aklivity/zilla/issues/482)
- `grpc` server binding sends incorrect `DATA` `flags` for fragmented messages [\#397](https://github.com/aklivity/zilla/issues/397)

**Merged pull requests:**

- 0 for no mqtt session expiry should be mapped to max integer value for the group stream [\#502](https://github.com/aklivity/zilla/pull/502) ([akrambek](https://github.com/akrambek))
- Better handle request with same group id [\#498](https://github.com/aklivity/zilla/pull/498) ([akrambek](https://github.com/akrambek))
- Remove stream on group leave response [\#492](https://github.com/aklivity/zilla/pull/492) ([akrambek](https://github.com/akrambek))
- Connection pool flowcontrol cleanup and minor bug fixes on group [\#490](https://github.com/aklivity/zilla/pull/490) ([akrambek](https://github.com/akrambek))
- update helm configs so appVersion is used as the tag [\#489](https://github.com/aklivity/zilla/pull/489) ([vordimous](https://github.com/vordimous))
- Bump org.apache.avro:avro from 1.11.2 to 1.11.3 [\#486](https://github.com/aklivity/zilla/pull/486) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump alpine from 3.18.3 to 3.18.4 in /cloud/docker-image/src/main/docker/release [\#484](https://github.com/aklivity/zilla/pull/484) ([dependabot[bot]](https://github.com/apps/dependabot))
- Fix publish timeout bug, increase default timeout [\#483](https://github.com/aklivity/zilla/pull/483) ([bmaidics](https://github.com/bmaidics))
- Schema Config Update [\#481](https://github.com/aklivity/zilla/pull/481) ([ankitk-me](https://github.com/ankitk-me))
- Feature/m1 docker build support [\#376](https://github.com/aklivity/zilla/pull/376) ([vordimous](https://github.com/vordimous))

## [0.9.54](https://github.com/aklivity/zilla/tree/0.9.54) (2023-09-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.53...0.9.54)

**Fixed bugs:**

- Additional scenarios in connection pool cleanup can trigger exception [\#475](https://github.com/aklivity/zilla/issues/475)

**Merged pull requests:**

- Integrate inline catalog and json validator with mqtt binding [\#479](https://github.com/aklivity/zilla/pull/479) ([jfallows](https://github.com/jfallows))
- Refactor config command [\#477](https://github.com/aklivity/zilla/pull/477) ([jfallows](https://github.com/jfallows))
- Remove streams only related to specific connection for additional scenarios [\#476](https://github.com/aklivity/zilla/pull/476) ([akrambek](https://github.com/akrambek))
- Cleanup log statements [\#474](https://github.com/aklivity/zilla/pull/474) ([ankitk-me](https://github.com/ankitk-me))
- Json Validator and Inline Schema fix [\#473](https://github.com/aklivity/zilla/pull/473) ([ankitk-me](https://github.com/ankitk-me))
- Http request validators feature flag [\#472](https://github.com/aklivity/zilla/pull/472) ([jfallows](https://github.com/jfallows))
- Avro validator module fix [\#470](https://github.com/aklivity/zilla/pull/470) ([ankitk-me](https://github.com/ankitk-me))
- Include validators in the mqtt config generator [\#467](https://github.com/aklivity/zilla/pull/467) ([attilakreiner](https://github.com/attilakreiner))
- Json Validator [\#466](https://github.com/aklivity/zilla/pull/466) ([ankitk-me](https://github.com/ankitk-me))
- Extract core validators [\#463](https://github.com/aklivity/zilla/pull/463) ([attilakreiner](https://github.com/attilakreiner))
- Mqtt validator implementation [\#452](https://github.com/aklivity/zilla/pull/452) ([bmaidics](https://github.com/bmaidics))
- Cache support for Schema Registry [\#447](https://github.com/aklivity/zilla/pull/447) ([ankitk-me](https://github.com/ankitk-me))
- Inline Catalog [\#445](https://github.com/aklivity/zilla/pull/445) ([ankitk-me](https://github.com/ankitk-me))
- Extract avro validator  [\#440](https://github.com/aklivity/zilla/pull/440) ([attilakreiner](https://github.com/attilakreiner))
- Introduce validators in the http binding [\#435](https://github.com/aklivity/zilla/pull/435) ([attilakreiner](https://github.com/attilakreiner))
- Schema Registry and Kafka Produce Validator [\#434](https://github.com/aklivity/zilla/pull/434) ([ankitk-me](https://github.com/ankitk-me))
- Move validators from binding-kafka to engine [\#415](https://github.com/aklivity/zilla/pull/415) ([attilakreiner](https://github.com/attilakreiner))
- Schema syntax validation for validator config  [\#412](https://github.com/aklivity/zilla/pull/412) ([attilakreiner](https://github.com/attilakreiner))
- Schema registry docker build shaded [\#411](https://github.com/aklivity/zilla/pull/411) ([jfallows](https://github.com/jfallows))
- Schema registry Implementation [\#402](https://github.com/aklivity/zilla/pull/402) ([ankitk-me](https://github.com/ankitk-me))
- UTF16 Validation implementation [\#401](https://github.com/aklivity/zilla/pull/401) ([ankitk-me](https://github.com/ankitk-me))
- API abstraction for SchemaRegistry in Zilla [\#311](https://github.com/aklivity/zilla/pull/311) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.53](https://github.com/aklivity/zilla/tree/0.9.53) (2023-09-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.52...0.9.53)

**Fixed bugs:**

- Connection cleanup can trigger exception  [\#468](https://github.com/aklivity/zilla/issues/468)

**Merged pull requests:**

- Release debitor on stream close and remove streams only related to specific connection [\#469](https://github.com/aklivity/zilla/pull/469) ([akrambek](https://github.com/akrambek))

## [0.9.52](https://github.com/aklivity/zilla/tree/0.9.52) (2023-09-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.51...0.9.52)

**Implemented enhancements:**

- Connection pool for `kafka` binding `heartbeat` requests [\#462](https://github.com/aklivity/zilla/issues/462)
- Enhance `mqtt` binding configuration syntax [\#420](https://github.com/aklivity/zilla/issues/420)
- Mqtt-Kafka session implementation [\#319](https://github.com/aklivity/zilla/issues/319)
- Design `zilla.yaml` configuration syntax for schema types [\#310](https://github.com/aklivity/zilla/issues/310)
- Generate `zilla.yaml` from `AsyncAPI` specification [\#256](https://github.com/aklivity/zilla/issues/256)
- Generate `zilla.yaml` from `OpenAPI` specification\(s\) [\#254](https://github.com/aklivity/zilla/issues/254)
- Support `kafka` consumer groups [\#215](https://github.com/aklivity/zilla/issues/215)
- MQTT guard implementation [\#307](https://github.com/aklivity/zilla/pull/307) ([bmaidics](https://github.com/bmaidics))

**Fixed bugs:**

- Zilla crash during attempted WebSocket connection [\#391](https://github.com/aklivity/zilla/issues/391)
- Index out of bounds exception with HTTP-Kafka proxy [\#293](https://github.com/aklivity/zilla/issues/293)

**Closed issues:**

- Send will message as data frame + reject large packets [\#364](https://github.com/aklivity/zilla/issues/364)
- Support Kafka client request-response with MQTT clients [\#326](https://github.com/aklivity/zilla/issues/326)
- Add guard support for MQTT binding [\#308](https://github.com/aklivity/zilla/issues/308)
- Implement retained feature for mqtt-kafka [\#289](https://github.com/aklivity/zilla/issues/289)

**Merged pull requests:**

- Mqtt client publish fix [\#464](https://github.com/aklivity/zilla/pull/464) ([bmaidics](https://github.com/bmaidics))
- Fix implicit subscribe no packetId reconnection [\#451](https://github.com/aklivity/zilla/pull/451) ([bmaidics](https://github.com/bmaidics))
- Remove clientId from subscribeKey [\#450](https://github.com/aklivity/zilla/pull/450) ([bmaidics](https://github.com/bmaidics))
- Rename config command to generate [\#449](https://github.com/aklivity/zilla/pull/449) ([attilakreiner](https://github.com/attilakreiner))
- Do not include generated subcsriptionId [\#448](https://github.com/aklivity/zilla/pull/448) ([bmaidics](https://github.com/bmaidics))
- Adjust engine backoff strategy configuration [\#446](https://github.com/aklivity/zilla/pull/446) ([jfallows](https://github.com/jfallows))
- Don't close group stream on cluster and describe streams closer [\#444](https://github.com/aklivity/zilla/pull/444) ([akrambek](https://github.com/akrambek))
- Engine configuration worker capacity [\#443](https://github.com/aklivity/zilla/pull/443) ([jfallows](https://github.com/jfallows))
- Remove unused engine configuration [\#442](https://github.com/aklivity/zilla/pull/442) ([jfallows](https://github.com/jfallows))
- Ensure socket channel has finished connecting before attempting to read [\#441](https://github.com/aklivity/zilla/pull/441) ([jfallows](https://github.com/jfallows))
- Mqtt subscription handling bugfix [\#439](https://github.com/aklivity/zilla/pull/439) ([bmaidics](https://github.com/bmaidics))
- Connection pool for kafka group client [\#438](https://github.com/aklivity/zilla/pull/438) ([akrambek](https://github.com/akrambek))
- Add affinity to mqtt server and client binding [\#436](https://github.com/aklivity/zilla/pull/436) ([bmaidics](https://github.com/bmaidics))
- Set init flag for data fragmentation in grpc [\#431](https://github.com/aklivity/zilla/pull/431) ([akrambek](https://github.com/akrambek))
- Fix flow control issue in kafka-grpc [\#430](https://github.com/aklivity/zilla/pull/430) ([akrambek](https://github.com/akrambek))
- Fix known issues in group client [\#428](https://github.com/aklivity/zilla/pull/428) ([akrambek](https://github.com/akrambek))
- Enhance mqtt binding configuration syntax [\#425](https://github.com/aklivity/zilla/pull/425) ([bmaidics](https://github.com/bmaidics))
- Buffer fragmented kafka session signal messages [\#424](https://github.com/aklivity/zilla/pull/424) ([bmaidics](https://github.com/bmaidics))
- Fix flow control bug [\#423](https://github.com/aklivity/zilla/pull/423) ([akrambek](https://github.com/akrambek))
- Serverref change [\#422](https://github.com/aklivity/zilla/pull/422) ([bmaidics](https://github.com/bmaidics))
- Fix finding next partition id [\#419](https://github.com/aklivity/zilla/pull/419) ([akrambek](https://github.com/akrambek))
- Don't end subscribe stream when unsubscribe, no subscription [\#418](https://github.com/aklivity/zilla/pull/418) ([bmaidics](https://github.com/bmaidics))
- Remove default kafka topic names [\#416](https://github.com/aklivity/zilla/pull/416) ([bmaidics](https://github.com/bmaidics))
- Fix consumer assignment causing decoding issue [\#414](https://github.com/aklivity/zilla/pull/414) ([akrambek](https://github.com/akrambek))
- Add test to validate merge produce rejection on wrong partition  [\#410](https://github.com/aklivity/zilla/pull/410) ([akrambek](https://github.com/akrambek))
- Consumer related bug fixes [\#405](https://github.com/aklivity/zilla/pull/405) ([akrambek](https://github.com/akrambek))
- Remove unused extends OptionsConfig from non-options config classes [\#403](https://github.com/aklivity/zilla/pull/403) ([jfallows](https://github.com/jfallows))
- Support consumer protocol [\#400](https://github.com/aklivity/zilla/pull/400) ([akrambek](https://github.com/akrambek))
- Mqtt client implementation [\#398](https://github.com/aklivity/zilla/pull/398) ([bmaidics](https://github.com/bmaidics))
- Support build after local docker zpm install [\#396](https://github.com/aklivity/zilla/pull/396) ([jfallows](https://github.com/jfallows))
- Adapt to consumer group changes [\#394](https://github.com/aklivity/zilla/pull/394) ([bmaidics](https://github.com/bmaidics))
- Bump actions/checkout from 3 to 4 [\#393](https://github.com/aklivity/zilla/pull/393) ([dependabot[bot]](https://github.com/apps/dependabot))
- Merged consumer group support [\#390](https://github.com/aklivity/zilla/pull/390) ([akrambek](https://github.com/akrambek))
- Session expiry [\#387](https://github.com/aklivity/zilla/pull/387) ([bmaidics](https://github.com/bmaidics))
- Request data length is non-negative [\#386](https://github.com/aklivity/zilla/pull/386) ([jfallows](https://github.com/jfallows))
- Fix mqtt-kafka publish bug [\#383](https://github.com/aklivity/zilla/pull/383) ([bmaidics](https://github.com/bmaidics))
- Support configuration property definitions for custom type... [\#382](https://github.com/aklivity/zilla/pull/382) ([jfallows](https://github.com/jfallows))
- Mqtt kafka redirect [\#381](https://github.com/aklivity/zilla/pull/381) ([bmaidics](https://github.com/bmaidics))
- Bump org.apache.ivy:ivy from 2.5.1 to 2.5.2 in /manager [\#377](https://github.com/aklivity/zilla/pull/377) ([dependabot[bot]](https://github.com/apps/dependabot))
- Generate zilla.yaml for asyncapi.mqtt.proxy from an AsyncAPI definition [\#375](https://github.com/aklivity/zilla/pull/375) ([attilakreiner](https://github.com/attilakreiner))
- Review budget debitors [\#374](https://github.com/aklivity/zilla/pull/374) ([jfallows](https://github.com/jfallows))
- Support binding config builder exit [\#373](https://github.com/aklivity/zilla/pull/373) ([jfallows](https://github.com/jfallows))
- Support config builder for MQTT config [\#372](https://github.com/aklivity/zilla/pull/372) ([jfallows](https://github.com/jfallows))
- Bump org.codehaus.mojo:exec-maven-plugin from 1.6.0 to 3.1.0 [\#370](https://github.com/aklivity/zilla/pull/370) ([dependabot[bot]](https://github.com/apps/dependabot))
- Generate zilla.yaml from an AsyncAPI definition [\#369](https://github.com/aklivity/zilla/pull/369) ([attilakreiner](https://github.com/attilakreiner))
- Mqtt kafka will message delivery [\#367](https://github.com/aklivity/zilla/pull/367) ([bmaidics](https://github.com/bmaidics))
- Bump org.apache.maven.plugins:maven-plugin-plugin from 3.5 to 3.9.0 [\#366](https://github.com/aklivity/zilla/pull/366) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump junit:junit from 4.13.1 to 4.13.2 [\#365](https://github.com/aklivity/zilla/pull/365) ([dependabot[bot]](https://github.com/apps/dependabot))
- Send will message as data frame + reject large packets [\#363](https://github.com/aklivity/zilla/pull/363) ([bmaidics](https://github.com/bmaidics))
- Sanitize zip entry path [\#362](https://github.com/aklivity/zilla/pull/362) ([jfallows](https://github.com/jfallows))
- Bump org.apache.maven:maven-core from 3.6.0 to 3.8.1 [\#361](https://github.com/aklivity/zilla/pull/361) ([dependabot[bot]](https://github.com/apps/dependabot))
- Merge consumer group metadata [\#359](https://github.com/aklivity/zilla/pull/359) ([akrambek](https://github.com/akrambek))
- Support dynamic behavior injection in config builder fluent API [\#358](https://github.com/aklivity/zilla/pull/358) ([jfallows](https://github.com/jfallows))
- Bump org.apache.maven.plugins:maven-jar-plugin from 3.2.0 to 3.3.0 [\#357](https://github.com/aklivity/zilla/pull/357) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump com.squareup:javapoet from 1.9.0 to 1.13.0 [\#355](https://github.com/aklivity/zilla/pull/355) ([dependabot[bot]](https://github.com/apps/dependabot))
- Include JDK 20 in build matrix [\#352](https://github.com/aklivity/zilla/pull/352) ([jfallows](https://github.com/jfallows))
- Ignore CacheFetchIT.shouldFetchFilterSyncWithData [\#351](https://github.com/aklivity/zilla/pull/351) ([attilakreiner](https://github.com/attilakreiner))
- Metadata for group merged stream [\#349](https://github.com/aklivity/zilla/pull/349) ([akrambek](https://github.com/akrambek))
- Bump io.fabric8:docker-maven-plugin from 0.39.1 to 0.43.2 [\#348](https://github.com/aklivity/zilla/pull/348) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.sonatype.plexus:plexus-sec-dispatcher from 1.3 to 1.4 [\#347](https://github.com/aklivity/zilla/pull/347) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump io.kokuwa.maven:helm-maven-plugin from 6.6.0 to 6.10.0 [\#345](https://github.com/aklivity/zilla/pull/345) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.maven.plugin-tools:maven-plugin-annotations from 3.5 to 3.9.0 [\#344](https://github.com/aklivity/zilla/pull/344) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump alpine from 3.18.2 to 3.18.3 in /cloud/docker-image/src/main/docker/release [\#343](https://github.com/aklivity/zilla/pull/343) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump eclipse-temurin from 17-alpine to 20-alpine in /cloud/docker-image/src/main/docker/incubator [\#342](https://github.com/aklivity/zilla/pull/342) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump alpine from 3.18.2 to 3.18.3 in /cloud/docker-image/src/main/docker/incubator [\#341](https://github.com/aklivity/zilla/pull/341) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump eclipse-temurin from 17-alpine to 20-alpine in /cloud/docker-image/src/main/docker/release [\#340](https://github.com/aklivity/zilla/pull/340) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.maven.plugins:maven-compiler-plugin from 3.8.0 to 3.11.0 [\#339](https://github.com/aklivity/zilla/pull/339) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/setup-java from 1 to 3 [\#338](https://github.com/aklivity/zilla/pull/338) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/checkout from 2 to 3 [\#337](https://github.com/aklivity/zilla/pull/337) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump actions/cache from 2 to 3 [\#336](https://github.com/aklivity/zilla/pull/336) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.moditect:moditect-maven-plugin from 1.0.0.RC1 to 1.0.0.Final [\#335](https://github.com/aklivity/zilla/pull/335) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump com.mycila:license-maven-plugin from 4.1 to 4.2 [\#334](https://github.com/aklivity/zilla/pull/334) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump org.apache.maven.plugins:maven-source-plugin from 3.0.1 to 3.3.0 [\#333](https://github.com/aklivity/zilla/pull/333) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump byteman.version from 4.0.20 to 4.0.21 [\#332](https://github.com/aklivity/zilla/pull/332) ([dependabot[bot]](https://github.com/apps/dependabot))
- Bump antlr4.version from 4.11.1 to 4.13.0 [\#331](https://github.com/aklivity/zilla/pull/331) ([dependabot[bot]](https://github.com/apps/dependabot))
- Config builders [\#330](https://github.com/aklivity/zilla/pull/330) ([jfallows](https://github.com/jfallows))
- Add hashKey support to merged stream [\#329](https://github.com/aklivity/zilla/pull/329) ([bmaidics](https://github.com/bmaidics))
- Default group session timeout  [\#328](https://github.com/aklivity/zilla/pull/328) ([akrambek](https://github.com/akrambek))
- Include member count as part of group data ex [\#327](https://github.com/aklivity/zilla/pull/327) ([akrambek](https://github.com/akrambek))
- Request-response mqtt-kafka [\#325](https://github.com/aklivity/zilla/pull/325) ([bmaidics](https://github.com/bmaidics))
- Generate zilla.yaml from an OpenAPI definition [\#324](https://github.com/aklivity/zilla/pull/324) ([attilakreiner](https://github.com/attilakreiner))
- Support zilla.yaml config reader and writer [\#323](https://github.com/aklivity/zilla/pull/323) ([jfallows](https://github.com/jfallows))
- Ignore heartbeat if the handshake request hasn't completed yet [\#322](https://github.com/aklivity/zilla/pull/322) ([akrambek](https://github.com/akrambek))
- Support local zpmw install [\#321](https://github.com/aklivity/zilla/pull/321) ([jfallows](https://github.com/jfallows))
- Mqtt kafka sessions [\#318](https://github.com/aklivity/zilla/pull/318) ([bmaidics](https://github.com/bmaidics))
- Mqtt kafka options [\#304](https://github.com/aklivity/zilla/pull/304) ([bmaidics](https://github.com/bmaidics))
- Redirect on mqtt reset using server reference [\#303](https://github.com/aklivity/zilla/pull/303) ([bmaidics](https://github.com/bmaidics))
- Mqtt retained feature [\#290](https://github.com/aklivity/zilla/pull/290) ([bmaidics](https://github.com/bmaidics))
- Support Kafka consumer groups [\#262](https://github.com/aklivity/zilla/pull/262) ([akrambek](https://github.com/akrambek))

## [0.9.51](https://github.com/aklivity/zilla/tree/0.9.51) (2023-07-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.50...0.9.51)

**Implemented enhancements:**

- Enhance `tcp` binding to route by `port` [\#294](https://github.com/aklivity/zilla/issues/294)
- Integrate OpenTelemetry collectors by exporting local metrics over OTLP [\#112](https://github.com/aklivity/zilla/issues/112)

**Closed issues:**

- Add redirect, server reference support to mqtt binding [\#302](https://github.com/aklivity/zilla/issues/302)
- Add options to mqtt-kafa binding so we can change kafka topics [\#300](https://github.com/aklivity/zilla/issues/300)

**Merged pull requests:**

- Fix kafka cache cursor buffer copy [\#317](https://github.com/aklivity/zilla/pull/317) ([bmaidics](https://github.com/bmaidics))
- README Formatting and wording changes [\#306](https://github.com/aklivity/zilla/pull/306) ([vordimous](https://github.com/vordimous))
- Readme overhaul [\#305](https://github.com/aklivity/zilla/pull/305) ([llukyanov](https://github.com/llukyanov))
- Support for tcp binding to route by port numbers [\#299](https://github.com/aklivity/zilla/pull/299) ([lukefallows](https://github.com/lukefallows))
- Create OpenTelemetry exporter and refactor code [\#279](https://github.com/aklivity/zilla/pull/279) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.50](https://github.com/aklivity/zilla/tree/0.9.50) (2023-07-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.49...0.9.50)

**Implemented enhancements:**

- `kubernetes autoscaling` feature \(baseline\) [\#189](https://github.com/aklivity/zilla/issues/189)

**Closed issues:**

- Update image base [\#291](https://github.com/aklivity/zilla/issues/291)

**Merged pull requests:**

- update Helm logo & details and clean up README [\#273](https://github.com/aklivity/zilla/pull/273) ([vordimous](https://github.com/vordimous))

## [0.9.49](https://github.com/aklivity/zilla/tree/0.9.49) (2023-06-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.48...0.9.49)

**Implemented enhancements:**

- Kafka merged flush should support filter changes [\#259](https://github.com/aklivity/zilla/issues/259)

**Closed issues:**

- Null pointer when Headers are null [\#281](https://github.com/aklivity/zilla/issues/281)

**Merged pull requests:**

- Remove unnecessary cursor assignment [\#288](https://github.com/aklivity/zilla/pull/288) ([akrambek](https://github.com/akrambek))
- Eliminate zilla.json warning if file not present [\#286](https://github.com/aklivity/zilla/pull/286) ([jfallows](https://github.com/jfallows))
- Send kafka flush even if data frames were sent to notify client from HISTORICAL to LIVE transition [\#284](https://github.com/aklivity/zilla/pull/284) ([bmaidics](https://github.com/bmaidics))
- KafkaMerged acknowledge flush frame [\#258](https://github.com/aklivity/zilla/pull/258) ([bmaidics](https://github.com/bmaidics))

## [0.9.48](https://github.com/aklivity/zilla/tree/0.9.48) (2023-06-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.47...0.9.48)

**Implemented enhancements:**

- Support additional grpc specific metrics [\#233](https://github.com/aklivity/zilla/issues/233)

**Fixed bugs:**

- CacheMergedIT.shouldFetchMergedMessagesWithIsolationReadCommitted fails only on GitHub Actions [\#239](https://github.com/aklivity/zilla/issues/239)
- CacheFetchIT.shouldReceiveMessagesWithIsolationReadUncommittedWhenAborting fails only on GitHub Actions [\#236](https://github.com/aklivity/zilla/issues/236)

**Merged pull requests:**

- Fix message fragmentation in sse-kafka and flow control in kafka merge [\#283](https://github.com/aklivity/zilla/pull/283) ([akrambek](https://github.com/akrambek))
- Fix dependencies to run TcpServerBM from command line [\#280](https://github.com/aklivity/zilla/pull/280) ([akrambek](https://github.com/akrambek))
- Fix bug with histograms in Prometheus exporter [\#278](https://github.com/aklivity/zilla/pull/278) ([attilakreiner](https://github.com/attilakreiner))
- Include failsafe reports for failed builds [\#276](https://github.com/aklivity/zilla/pull/276) ([ankitk-me](https://github.com/ankitk-me))
- Fix mqtt decoding bug [\#275](https://github.com/aklivity/zilla/pull/275) ([bmaidics](https://github.com/bmaidics))
- Allow Kafka merged stream to change fetch filters dynamically [\#272](https://github.com/aklivity/zilla/pull/272) ([bmaidics](https://github.com/bmaidics))
- Bump jose4j from 0.7.10 to 0.9.3 in /runtime/guard-jwt [\#271](https://github.com/aklivity/zilla/pull/271) ([dependabot[bot]](https://github.com/apps/dependabot))
- Fix mqtt-kafka subscribe stream initial offset [\#270](https://github.com/aklivity/zilla/pull/270) ([bmaidics](https://github.com/bmaidics))
- Implement counter value reader in EngineExtContext [\#261](https://github.com/aklivity/zilla/pull/261) ([attilakreiner](https://github.com/attilakreiner))
- CacheMergedIT.shouldFetchMergedMessagesWithIsolationReadCommitted fix [\#260](https://github.com/aklivity/zilla/pull/260) ([ankitk-me](https://github.com/ankitk-me))
- Cleanup obsolete load counters [\#253](https://github.com/aklivity/zilla/pull/253) ([attilakreiner](https://github.com/attilakreiner))
- GitHub Actions fix [\#251](https://github.com/aklivity/zilla/pull/251) ([ankitk-me](https://github.com/ankitk-me))
- Update README.md [\#250](https://github.com/aklivity/zilla/pull/250) ([jfallows](https://github.com/jfallows))
- Fix the requirements versions in helm chart readme [\#249](https://github.com/aklivity/zilla/pull/249) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.47](https://github.com/aklivity/zilla/tree/0.9.47) (2023-05-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.46...0.9.47)

**Merged pull requests:**

- Update README.md of helm-chart [\#248](https://github.com/aklivity/zilla/pull/248) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.46](https://github.com/aklivity/zilla/tree/0.9.46) (2023-05-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.45...0.9.46)

## [0.9.45](https://github.com/aklivity/zilla/tree/0.9.45) (2023-05-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.44...0.9.45)

**Implemented enhancements:**

- Generic helm chart [\#242](https://github.com/aklivity/zilla/issues/242)

**Merged pull requests:**

- Generic helm chart [\#230](https://github.com/aklivity/zilla/pull/230) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.44](https://github.com/aklivity/zilla/tree/0.9.44) (2023-05-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.43...0.9.44)

**Implemented enhancements:**

- Simplify `zilla.yaml` errors on invalid input [\#222](https://github.com/aklivity/zilla/issues/222)
- MQTT and MQTT-Kafka bindings [\#196](https://github.com/aklivity/zilla/issues/196)
- Support additional http specific metrics [\#111](https://github.com/aklivity/zilla/issues/111)

**Closed issues:**

- Refactor existing MQTT specs [\#179](https://github.com/aklivity/zilla/issues/179)

**Merged pull requests:**

- Readme Updates [\#247](https://github.com/aklivity/zilla/pull/247) ([llukyanov](https://github.com/llukyanov))
- Simplify zilla yaml errors [\#245](https://github.com/aklivity/zilla/pull/245) ([jfallows](https://github.com/jfallows))
- telemetry metrics feature baseline [\#244](https://github.com/aklivity/zilla/pull/244) ([jfallows](https://github.com/jfallows))
- Fix grpc last message id decoding [\#243](https://github.com/aklivity/zilla/pull/243) ([akrambek](https://github.com/akrambek))
- Introduce grpc metrics [\#241](https://github.com/aklivity/zilla/pull/241) ([attilakreiner](https://github.com/attilakreiner))
- Mqtt-kafka binding implementation [\#235](https://github.com/aklivity/zilla/pull/235) ([bmaidics](https://github.com/bmaidics))
- Add http.active.requests and http.duration metrics [\#227](https://github.com/aklivity/zilla/pull/227) ([attilakreiner](https://github.com/attilakreiner))
- Introducing Stream Direction to Optimize Metric Collection [\#224](https://github.com/aklivity/zilla/pull/224) ([attilakreiner](https://github.com/attilakreiner))
- Remove zilla load command [\#223](https://github.com/aklivity/zilla/pull/223) ([attilakreiner](https://github.com/attilakreiner))
- Minimize performance overhead for metric collection [\#217](https://github.com/aklivity/zilla/pull/217) ([attilakreiner](https://github.com/attilakreiner))
-  Fix metrics command, find layout files in the engine dir [\#204](https://github.com/aklivity/zilla/pull/204) ([attilakreiner](https://github.com/attilakreiner))
- Prometheus Exporter [\#203](https://github.com/aklivity/zilla/pull/203) ([attilakreiner](https://github.com/attilakreiner))
- Engine support to exporters [\#202](https://github.com/aklivity/zilla/pull/202) ([attilakreiner](https://github.com/attilakreiner))
- Mqtt binding implementation [\#201](https://github.com/aklivity/zilla/pull/201) ([bmaidics](https://github.com/bmaidics))
- mqtt binding specs [\#191](https://github.com/aklivity/zilla/pull/191) ([bmaidics](https://github.com/bmaidics))
- Metrics schema, extensibility, storage and command line support [\#173](https://github.com/aklivity/zilla/pull/173) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.43](https://github.com/aklivity/zilla/tree/0.9.43) (2023-05-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.42...0.9.43)

**Implemented enhancements:**

- Enhance `grpc` related binding configuration [\#226](https://github.com/aklivity/zilla/issues/226)
- Move `kafka-grpc` options for `grpc` to `with` section of config [\#218](https://github.com/aklivity/zilla/issues/218)
- Remove `zilla load` now that we have `zilla metrics` instead [\#214](https://github.com/aklivity/zilla/issues/214)
- Minimize on-stack performance overhead of metrics recording [\#213](https://github.com/aklivity/zilla/issues/213)
- Support `eager` evaluation of all `kafka` filters and indicate which filters matched [\#209](https://github.com/aklivity/zilla/issues/209)
- Integrate Prometheus by exporting local metrics over HTTP [\#193](https://github.com/aklivity/zilla/issues/193)
- Support gRPC Kafka server streaming [\#186](https://github.com/aklivity/zilla/issues/186)
- Support Kafka gRPC mapping [\#185](https://github.com/aklivity/zilla/issues/185)
- Support gRPC Kafka mapping [\#184](https://github.com/aklivity/zilla/issues/184)
- Enhance kafka binding to notify transition from historical to live messages [\#172](https://github.com/aklivity/zilla/issues/172)
- Support gRPC proxying [\#171](https://github.com/aklivity/zilla/issues/171)
- Convert zilla spec config .json files to .yaml extension and syntax [\#164](https://github.com/aklivity/zilla/issues/164)
- When starting up in verbose mode make sure there is a newline after printing the config [\#157](https://github.com/aklivity/zilla/issues/157)
- Support dynamic `zilla` configuration via `https` [\#139](https://github.com/aklivity/zilla/issues/139)
- Watch `zilla` configuration for changes and apply automatically [\#138](https://github.com/aklivity/zilla/issues/138)
- Enhance idempotency support for HTTP-Kafka binding [\#113](https://github.com/aklivity/zilla/issues/113)
- Provide zilla metrics command to report current values locally [\#110](https://github.com/aklivity/zilla/issues/110)
- Migrate implicit stream open, close, error, bytes metrics to explicit configuration syntax [\#109](https://github.com/aklivity/zilla/issues/109)
- Design observable metrics configuration syntax [\#100](https://github.com/aklivity/zilla/issues/100)
- Enhance http client binding to support h2 protocol [\#99](https://github.com/aklivity/zilla/issues/99)
- Simplify configuration for JWT identity provider signing keys [\#68](https://github.com/aklivity/zilla/issues/68)
- Refactor core.idl with originId and routedId [\#195](https://github.com/aklivity/zilla/pull/195) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- During reconfigure, we don't log errors [\#177](https://github.com/aklivity/zilla/issues/177)
- Flyweight wrapping error race condition [\#146](https://github.com/aklivity/zilla/issues/146)
- CacheMergedIT.shouldFetchMergedMessageValues fails only on GitHub Actions [\#131](https://github.com/aklivity/zilla/issues/131)
- Add log + rollback on reconfigure errors [\#178](https://github.com/aklivity/zilla/pull/178) ([bmaidics](https://github.com/bmaidics))

**Closed issues:**

- Migrate `zilla` README from `zilla.json` to `zilla.yaml` [\#159](https://github.com/aklivity/zilla/issues/159)

**Merged pull requests:**

- Fix NPE caused by overrides [\#238](https://github.com/aklivity/zilla/pull/238) ([akrambek](https://github.com/akrambek))
- Fix typo in flow control, use `responseMax` instead of `requestMax` [\#237](https://github.com/aklivity/zilla/pull/237) ([akrambek](https://github.com/akrambek))
- Consumer group kafka function support [\#232](https://github.com/aklivity/zilla/pull/232) ([akrambek](https://github.com/akrambek))
- Enhance config [\#228](https://github.com/aklivity/zilla/pull/228) ([akrambek](https://github.com/akrambek))
- `grpc-kafka` feature baseline [\#225](https://github.com/aklivity/zilla/pull/225) ([jfallows](https://github.com/jfallows))
- CacheMergedIT.shouldFetchMergedMessageValues failure on GitHub Actions fix [\#221](https://github.com/aklivity/zilla/pull/221) ([ankitk-me](https://github.com/ankitk-me))
- Support eager evaluation of all Kafka filters [\#212](https://github.com/aklivity/zilla/pull/212) ([ankitk-me](https://github.com/ankitk-me))
- Include license header check [\#206](https://github.com/aklivity/zilla/pull/206) ([jfallows](https://github.com/jfallows))
- Change DumpCommandTest [\#194](https://github.com/aklivity/zilla/pull/194) ([bmaidics](https://github.com/bmaidics))
- Fix incorrect Assertion in KafkaFunctionsTest [\#192](https://github.com/aklivity/zilla/pull/192) ([bmaidics](https://github.com/bmaidics))
- Enhance kafka binding to notify transition from historical to live messages [\#181](https://github.com/aklivity/zilla/pull/181) ([ankitk-me](https://github.com/ankitk-me))
- Support verbose schema output on startup [\#175](https://github.com/aklivity/zilla/pull/175) ([jfallows](https://github.com/jfallows))
- Ignore shouldReconfigureWhenModifiedUsingComplexSymlinkChain [\#169](https://github.com/aklivity/zilla/pull/169) ([bmaidics](https://github.com/bmaidics))
- Provide http\(s\) configuration server for zilla.yaml [\#166](https://github.com/aklivity/zilla/pull/166) ([bmaidics](https://github.com/bmaidics))
- Convert zilla spec config .json files to .yaml extension and syntax [\#165](https://github.com/aklivity/zilla/pull/165) ([ankitk-me](https://github.com/ankitk-me))
- Update zilla readme to address yaml changes [\#162](https://github.com/aklivity/zilla/pull/162) ([ankitk-me](https://github.com/ankitk-me))
- Flyweight wrapping error race condition fix [\#161](https://github.com/aklivity/zilla/pull/161) ([ankitk-me](https://github.com/ankitk-me))
- Add a newline to the end of the config if it is not present [\#158](https://github.com/aklivity/zilla/pull/158) ([attilakreiner](https://github.com/attilakreiner))
- Http dynamic configuration [\#156](https://github.com/aklivity/zilla/pull/156) ([bmaidics](https://github.com/bmaidics))
- Dynamic config [\#141](https://github.com/aklivity/zilla/pull/141) ([bmaidics](https://github.com/bmaidics))
- Add schema for specifying an OpenID provider discovery endpoint [\#106](https://github.com/aklivity/zilla/pull/106) ([Alfusainey](https://github.com/Alfusainey))

## [0.9.42](https://github.com/aklivity/zilla/tree/0.9.42) (2023-01-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.41...0.9.42)

**Implemented enhancements:**

- Support YAML syntax for Zilla configuration [\#144](https://github.com/aklivity/zilla/issues/144)

**Merged pull requests:**

- Adding yaml support for zilla config [\#150](https://github.com/aklivity/zilla/pull/150) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.41](https://github.com/aklivity/zilla/tree/0.9.41) (2023-01-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.40...0.9.41)

**Merged pull requests:**

- Pass correct flag for delete payload [\#155](https://github.com/aklivity/zilla/pull/155) ([akrambek](https://github.com/akrambek))

## [0.9.40](https://github.com/aklivity/zilla/tree/0.9.40) (2023-01-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.39...0.9.40)

**Implemented enhancements:**

- Support `{{ mustache }}` syntax in zilla.json [\#91](https://github.com/aklivity/zilla/issues/91)

**Merged pull requests:**

- Adding support for Expression Resolver [\#143](https://github.com/aklivity/zilla/pull/143) ([ankitk-me](https://github.com/ankitk-me))

## [0.9.39](https://github.com/aklivity/zilla/tree/0.9.39) (2023-01-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.38...0.9.39)

## [0.9.38](https://github.com/aklivity/zilla/tree/0.9.38) (2023-01-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.37...0.9.38)

## [0.9.37](https://github.com/aklivity/zilla/tree/0.9.37) (2023-01-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.35...0.9.37)

**Implemented enhancements:**

- Follow standard layout for zilla files in docker image [\#140](https://github.com/aklivity/zilla/issues/140)

**Fixed bugs:**

- NPE when reloading browser page, mid produce [\#151](https://github.com/aklivity/zilla/issues/151)

**Merged pull requests:**

- Prevent NPE when kafka produce is canceled … [\#152](https://github.com/aklivity/zilla/pull/152) ([jfallows](https://github.com/jfallows))
- Modify the layout of zilla files on the docker image [\#142](https://github.com/aklivity/zilla/pull/142) ([attilakreiner](https://github.com/attilakreiner))

## [0.9.35](https://github.com/aklivity/zilla/tree/0.9.35) (2023-01-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.34...0.9.35)

**Fixed bugs:**

- kafka cache treats non-compacted topics as compacted [\#147](https://github.com/aklivity/zilla/issues/147)

**Merged pull requests:**

- Resolve stall for large files served over HTTP/2 [\#149](https://github.com/aklivity/zilla/pull/149) ([jfallows](https://github.com/jfallows))
- Skip ancestor messages for compacted topic configuration only [\#148](https://github.com/aklivity/zilla/pull/148) ([jfallows](https://github.com/jfallows))

## [0.9.34](https://github.com/aklivity/zilla/tree/0.9.34) (2023-01-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.33...0.9.34)

**Implemented enhancements:**

- Support guarded identities in http-kafka and sse-kafka [\#145](https://github.com/aklivity/zilla/pull/145) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Add link to http proxy example [\#137](https://github.com/aklivity/zilla/pull/137) ([akrambek](https://github.com/akrambek))

## [0.9.33](https://github.com/aklivity/zilla/tree/0.9.33) (2022-12-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.32...0.9.33)

**Merged pull requests:**

- Upgrade byteman and mockito to support JDK 19 class file format [\#133](https://github.com/aklivity/zilla/pull/133) ([jfallows](https://github.com/jfallows))
- Added Info & link for SASL/SCRAM Examples [\#132](https://github.com/aklivity/zilla/pull/132) ([ankitk-me](https://github.com/ankitk-me))
- Http2 client support [\#127](https://github.com/aklivity/zilla/pull/127) ([akrambek](https://github.com/akrambek))

## [0.9.32](https://github.com/aklivity/zilla/tree/0.9.32) (2022-11-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.31...0.9.32)

**Implemented enhancements:**

- Implement `zilla dump` command similar to `tcpdump` [\#114](https://github.com/aklivity/zilla/issues/114)

**Merged pull requests:**

- Use try-with-resources to manage lifecycle of writer [\#130](https://github.com/aklivity/zilla/pull/130) ([jfallows](https://github.com/jfallows))
- Reduce pcap frame encoding overhead [\#129](https://github.com/aklivity/zilla/pull/129) ([jfallows](https://github.com/jfallows))
- Support SASL SCRAM authentication [\#126](https://github.com/aklivity/zilla/pull/126) ([ankitk-me](https://github.com/ankitk-me))
- Implement zilla dump command similar to tcpdump [\#121](https://github.com/aklivity/zilla/pull/121) ([bmaidics](https://github.com/bmaidics))

## [0.9.31](https://github.com/aklivity/zilla/tree/0.9.31) (2022-11-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.30...0.9.31)

**Implemented enhancements:**

- Remove merged from kafka binding configuration [\#108](https://github.com/aklivity/zilla/issues/108)
- Simplify duplicate request detection at event-driven microservices [\#71](https://github.com/aklivity/zilla/issues/71)

**Fixed bugs:**

- Error running http.kafka.oneway from zilla-examples [\#117](https://github.com/aklivity/zilla/issues/117)
- Zilla build fails on timeout [\#102](https://github.com/aklivity/zilla/issues/102)

**Merged pull requests:**

- Fix uint32 as array length [\#128](https://github.com/aklivity/zilla/pull/128) ([akrambek](https://github.com/akrambek))
- Adjust expectations to handle the case where we extend window max … [\#125](https://github.com/aklivity/zilla/pull/125) ([jfallows](https://github.com/jfallows))
- Remove merged from kafka binding configuration [\#122](https://github.com/aklivity/zilla/pull/122) ([ankitk-me](https://github.com/ankitk-me))
- Fix NPE caused by no KafkaMergedDataEx present on the DATA frame... [\#120](https://github.com/aklivity/zilla/pull/120) ([attilakreiner](https://github.com/attilakreiner))
- Mark flyweight plugin goals @threadSafe [\#119](https://github.com/aklivity/zilla/pull/119) ([jfallows](https://github.com/jfallows))
- Upgrade ANTLR version [\#118](https://github.com/aklivity/zilla/pull/118) ([jfallows](https://github.com/jfallows))
- Replace HttpRequest with buffer slot [\#105](https://github.com/aklivity/zilla/pull/105) ([akrambek](https://github.com/akrambek))
- Increase frame count for byteman to find matching call stack method [\#104](https://github.com/aklivity/zilla/pull/104) ([jfallows](https://github.com/jfallows))
- Document how to run the benchmark with Java 16+ [\#103](https://github.com/aklivity/zilla/pull/103) ([antonmry](https://github.com/antonmry))
- BugFix: process configURL if the protocol is http [\#101](https://github.com/aklivity/zilla/pull/101) ([Alfusainey](https://github.com/Alfusainey))

## [0.9.30](https://github.com/aklivity/zilla/tree/0.9.30) (2022-09-19)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.29...0.9.30)

**Fixed bugs:**

- Handle produce error [\#97](https://github.com/aklivity/zilla/pull/97) ([akrambek](https://github.com/akrambek))

**Merged pull requests:**

- Support reset extension in test engine [\#96](https://github.com/aklivity/zilla/pull/96) ([akrambek](https://github.com/akrambek))

## [0.9.29](https://github.com/aklivity/zilla/tree/0.9.29) (2022-08-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.28...0.9.29)

**Implemented enhancements:**

- Configurable isolation level for kafka binding [\#85](https://github.com/aklivity/zilla/issues/85)
- Configurable acknowledgement mode for kafka binding [\#84](https://github.com/aklivity/zilla/issues/84)

**Merged pull requests:**

- Support SSE proxy [\#98](https://github.com/aklivity/zilla/pull/98) ([jfallows](https://github.com/jfallows))
- Enhance kafka transaction scenarios… [\#95](https://github.com/aklivity/zilla/pull/95) ([jfallows](https://github.com/jfallows))
- Support read committed and read uncommitted kafka isolation levels [\#94](https://github.com/aklivity/zilla/pull/94) ([jfallows](https://github.com/jfallows))
- Support configuration of produce acks [\#93](https://github.com/aklivity/zilla/pull/93) ([jfallows](https://github.com/jfallows))
- Add tests for distinct partition leader [\#92](https://github.com/aklivity/zilla/pull/92) ([akrambek](https://github.com/akrambek))

## [0.9.28](https://github.com/aklivity/zilla/tree/0.9.28) (2022-07-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.27...0.9.28)

**Fixed bugs:**

- Scope topic partition leader info by both resolved binding and topic … [\#90](https://github.com/aklivity/zilla/pull/90) ([jfallows](https://github.com/jfallows))

## [0.9.27](https://github.com/aklivity/zilla/tree/0.9.27) (2022-07-09)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.26...0.9.27)

**Fixed bugs:**

- Investigate GitHub Actions build inconsistencies [\#23](https://github.com/aklivity/zilla/issues/23)

**Merged pull requests:**

- Support SASL PLAIN mechanism [\#89](https://github.com/aklivity/zilla/pull/89) ([jfallows](https://github.com/jfallows))
- Make authorization accessor one instance per thread [\#88](https://github.com/aklivity/zilla/pull/88) ([akrambek](https://github.com/akrambek))
- Updated the README [\#87](https://github.com/aklivity/zilla/pull/87) ([llukyanov](https://github.com/llukyanov))
- Execute engine openjmh microbenchmarks via shaded test jar [\#82](https://github.com/aklivity/zilla/pull/82) ([jfallows](https://github.com/jfallows))
- Increase jacoco coverage ratio [\#81](https://github.com/aklivity/zilla/pull/81) ([akrambek](https://github.com/akrambek))
- Fix github action [\#78](https://github.com/aklivity/zilla/pull/78) ([akrambek](https://github.com/akrambek))

## [0.9.26](https://github.com/aklivity/zilla/tree/0.9.26) (2022-06-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.25...0.9.26)

**Merged pull requests:**

- Support produce with no reply-to directly to Kafka topic [\#79](https://github.com/aklivity/zilla/pull/79) ([jfallows](https://github.com/jfallows))

## [0.9.25](https://github.com/aklivity/zilla/tree/0.9.25) (2022-06-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.24...0.9.25)

**Fixed bugs:**

- Ignore query parameter in http-filesystem mapping… [\#77](https://github.com/aklivity/zilla/pull/77) ([jfallows](https://github.com/jfallows))

## [0.9.24](https://github.com/aklivity/zilla/tree/0.9.24) (2022-06-08)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.23...0.9.24)

**Fixed bugs:**

- Extract credentials from HTTP path query string even when non-terminal parameter [\#73](https://github.com/aklivity/zilla/issues/73)
- Malformed if-match value triggers exception [\#38](https://github.com/aklivity/zilla/issues/38)

**Merged pull requests:**

- Support query parameter in sse handshake [\#76](https://github.com/aklivity/zilla/pull/76) ([jfallows](https://github.com/jfallows))
- Support path with query when routing http path conditions [\#75](https://github.com/aklivity/zilla/pull/75) ([jfallows](https://github.com/jfallows))
- Use & as end of token separator for query parameter values [\#74](https://github.com/aklivity/zilla/pull/74) ([jfallows](https://github.com/jfallows))
- Update README [\#72](https://github.com/aklivity/zilla/pull/72) ([jfallows](https://github.com/jfallows))
- Add simple example and roadmap to README [\#70](https://github.com/aklivity/zilla/pull/70) ([jfallows](https://github.com/jfallows))
- Include diagram in README [\#69](https://github.com/aklivity/zilla/pull/69) ([jfallows](https://github.com/jfallows))
- Skip adding if-match header to kafka message if etag not present [\#67](https://github.com/aklivity/zilla/pull/67) ([jfallows](https://github.com/jfallows))

## [0.9.23](https://github.com/aklivity/zilla/tree/0.9.23) (2022-05-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.22...0.9.23)

**Merged pull requests:**

- Support trustcacerts without requiring a vault [\#66](https://github.com/aklivity/zilla/pull/66) ([jfallows](https://github.com/jfallows))
- Check extension type id is proxy metadata in tls client and tcp client [\#64](https://github.com/aklivity/zilla/pull/64) ([jfallows](https://github.com/jfallows))
- Ignore Github Actions test [\#62](https://github.com/aklivity/zilla/pull/62) ([jfallows](https://github.com/jfallows))

## [0.9.22](https://github.com/aklivity/zilla/tree/0.9.22) (2022-05-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.21...0.9.22)

**Fixed bugs:**

- Allow tls trustcacerts option to work without vault [\#65](https://github.com/aklivity/zilla/issues/65)
- Ws to tls proxy misinterprets begin extension  [\#63](https://github.com/aklivity/zilla/issues/63)

**Merged pull requests:**

- Prepare for JWT example [\#61](https://github.com/aklivity/zilla/pull/61) ([jfallows](https://github.com/jfallows))

## [0.9.21](https://github.com/aklivity/zilla/tree/0.9.21) (2022-05-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.20...0.9.21)

**Implemented enhancements:**

- Refer to sse-kafka event id progress as etag instead [\#43](https://github.com/aklivity/zilla/issues/43)
- Require exit be omitted from tcp client configuration [\#40](https://github.com/aklivity/zilla/issues/40)

**Fixed bugs:**

- Ensure single fan-in point for fan server... [\#57](https://github.com/aklivity/zilla/pull/57) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Remove exit from tcp client binding schema [\#60](https://github.com/aklivity/zilla/pull/60) ([jfallows](https://github.com/jfallows))
- Rename progress to etag in sse-kafka binding event id format [\#59](https://github.com/aklivity/zilla/pull/59) ([jfallows](https://github.com/jfallows))
- Ignore Github Actions test [\#58](https://github.com/aklivity/zilla/pull/58) ([akrambek](https://github.com/akrambek))

## [0.9.20](https://github.com/aklivity/zilla/tree/0.9.20) (2022-05-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.19...0.9.20)

**Fixed bugs:**

- Clarify semantics of stream client index… [\#56](https://github.com/aklivity/zilla/pull/56) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Ignore GitHub Actions tests [\#55](https://github.com/aklivity/zilla/pull/55) ([akrambek](https://github.com/akrambek))
- Add the readme logo [\#54](https://github.com/aklivity/zilla/pull/54) ([akrambek](https://github.com/akrambek))

## [0.9.19](https://github.com/aklivity/zilla/tree/0.9.19) (2022-05-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.18...0.9.19)

**Merged pull requests:**

- Ensure single writer for kafka cache\_server and echo server [\#53](https://github.com/aklivity/zilla/pull/53) ([jfallows](https://github.com/jfallows))
- Describe Zilla in README.md [\#52](https://github.com/aklivity/zilla/pull/52) ([jfallows](https://github.com/jfallows))

## [0.9.18](https://github.com/aklivity/zilla/tree/0.9.18) (2022-05-23)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.17...0.9.18)

**Implemented enhancements:**

- Optimize transfer-encoding for http-kafka correlated response [\#45](https://github.com/aklivity/zilla/issues/45)
- Allow list of merged topics in kafka binding options to be optional [\#41](https://github.com/aklivity/zilla/issues/41)

**Fixed bugs:**

- Ensure kafka cache entry cannot grow... [\#49](https://github.com/aklivity/zilla/pull/49) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Support merged topics across partitions by default … [\#51](https://github.com/aklivity/zilla/pull/51) ([jfallows](https://github.com/jfallows))
- Use content-length instead of transfer-encoding chunked for correlated responses [\#50](https://github.com/aklivity/zilla/pull/50) ([jfallows](https://github.com/jfallows))
- Use application/octet-stream as default content-type [\#47](https://github.com/aklivity/zilla/pull/47) ([jfallows](https://github.com/jfallows))
- Ensure kafka producer stream has initialized with available window... [\#46](https://github.com/aklivity/zilla/pull/46) ([jfallows](https://github.com/jfallows))

## [0.9.17](https://github.com/aklivity/zilla/tree/0.9.17) (2022-05-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.16...0.9.17)

**Merged pull requests:**

- Support message trailers in kafka cache produce client [\#37](https://github.com/aklivity/zilla/pull/37) ([jfallows](https://github.com/jfallows))

## [0.9.16](https://github.com/aklivity/zilla/tree/0.9.16) (2022-05-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.15...0.9.16)

**Fixed bugs:**

- Handle null kafka message payload [\#29](https://github.com/aklivity/zilla/pull/29) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Support idempotencyKey for http-kafka produce key [\#36](https://github.com/aklivity/zilla/pull/36) ([jfallows](https://github.com/jfallows))
- Include content-length 0 on implicit 404 and 400 responses [\#35](https://github.com/aklivity/zilla/pull/35) ([jfallows](https://github.com/jfallows))
- Support md5 hash of headers and payload to augment zilla:correlation-id [\#34](https://github.com/aklivity/zilla/pull/34) ([jfallows](https://github.com/jfallows))
- Support M1 chip docker image [\#33](https://github.com/aklivity/zilla/pull/33) ([akrambek](https://github.com/akrambek))
- Support SuppressWarnings annotation [\#32](https://github.com/aklivity/zilla/pull/32) ([akrambek](https://github.com/akrambek))
- Support key and etag in event id [\#31](https://github.com/aklivity/zilla/pull/31) ([jfallows](https://github.com/jfallows))
- Send SSE delete event for sse-kafka binding … [\#30](https://github.com/aklivity/zilla/pull/30) ([jfallows](https://github.com/jfallows))
- Include content length 0 in explicit status 404 response [\#24](https://github.com/aklivity/zilla/pull/24) ([jfallows](https://github.com/jfallows))

## [0.9.15](https://github.com/aklivity/zilla/tree/0.9.15) (2022-05-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.14...0.9.15)

**Implemented enhancements:**

- Enhance http-kafka idempotency key [\#28](https://github.com/aklivity/zilla/issues/28)
- Support key in event id field for sse-kafka binding [\#27](https://github.com/aklivity/zilla/issues/27)
- Support etag in event id field for sse-kafka binding [\#26](https://github.com/aklivity/zilla/issues/26)
- Support tombstone messages via sse-kafka binding [\#25](https://github.com/aklivity/zilla/issues/25)
- http-kafka binding [\#17](https://github.com/aklivity/zilla/pull/17) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Support default idempotency key and sync request response … [\#22](https://github.com/aklivity/zilla/pull/22) ([jfallows](https://github.com/jfallows))
- Support initialSeq and initialAck being equal but non-zero [\#21](https://github.com/aklivity/zilla/pull/21) ([jfallows](https://github.com/jfallows))
- Support omitting options from http-kafka binding config [\#20](https://github.com/aklivity/zilla/pull/20) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Improve http-kafka get items scenarios … [\#19](https://github.com/aklivity/zilla/pull/19) ([jfallows](https://github.com/jfallows))
- http binding implicit transfer-encoding chunked … [\#18](https://github.com/aklivity/zilla/pull/18) ([jfallows](https://github.com/jfallows))

## [0.9.14](https://github.com/aklivity/zilla/tree/0.9.14) (2022-03-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.13...0.9.14)

**Implemented enhancements:**

- Promote guard-jwt from incubator [\#16](https://github.com/aklivity/zilla/pull/16) ([jfallows](https://github.com/jfallows))

## [0.9.13](https://github.com/aklivity/zilla/tree/0.9.13) (2022-03-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.12...0.9.13)

**Implemented enhancements:**

- Promote binding-filesystem and binding-http-filesystem from incubator [\#15](https://github.com/aklivity/zilla/pull/15) ([jfallows](https://github.com/jfallows))

## [0.9.12](https://github.com/aklivity/zilla/tree/0.9.12) (2022-03-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.11...0.9.12)

**Implemented enhancements:**

- Promote binding-sse-kafka from incubator [\#14](https://github.com/aklivity/zilla/pull/14) ([jfallows](https://github.com/jfallows))

## [0.9.11](https://github.com/aklivity/zilla/tree/0.9.11) (2022-03-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.10...0.9.11)

## [0.9.10](https://github.com/aklivity/zilla/tree/0.9.10) (2022-03-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.9...0.9.10)

## [0.9.9](https://github.com/aklivity/zilla/tree/0.9.9) (2022-03-24)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.8...0.9.9)

**Implemented enhancements:**

- Support docker image [\#13](https://github.com/aklivity/zilla/pull/13) ([jfallows](https://github.com/jfallows))
- filesystem binding and http-filesystem binding [\#10](https://github.com/aklivity/zilla/pull/10) ([jfallows](https://github.com/jfallows))
- Guard API [\#8](https://github.com/aklivity/zilla/pull/8) ([jfallows](https://github.com/jfallows))

**Fixed bugs:**

- Improve coverage for sse binding deferred end and fix state transition bug [\#11](https://github.com/aklivity/zilla/pull/11) ([jfallows](https://github.com/jfallows))

**Merged pull requests:**

- Test verify JWT roles when index differs [\#9](https://github.com/aklivity/zilla/pull/9) ([jfallows](https://github.com/jfallows))
- Support CORS http/1.1 and http/2 [\#5](https://github.com/aklivity/zilla/pull/5) ([jfallows](https://github.com/jfallows))
- Binding to map from SSE to Kafka [\#4](https://github.com/aklivity/zilla/pull/4) ([jfallows](https://github.com/jfallows))

## [0.9.8](https://github.com/aklivity/zilla/tree/0.9.8) (2022-01-31)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.7...0.9.8)

## [0.9.7](https://github.com/aklivity/zilla/tree/0.9.7) (2022-01-20)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.6...0.9.7)

## [0.9.6](https://github.com/aklivity/zilla/tree/0.9.6) (2022-01-15)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.5...0.9.6)

## [0.9.5](https://github.com/aklivity/zilla/tree/0.9.5) (2022-01-14)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.4...0.9.5)

## [0.9.4](https://github.com/aklivity/zilla/tree/0.9.4) (2022-01-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.3...0.9.4)

## [0.9.3](https://github.com/aklivity/zilla/tree/0.9.3) (2022-01-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.2...0.9.3)

## [0.9.2](https://github.com/aklivity/zilla/tree/0.9.2) (2022-01-13)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.1...0.9.2)

## [0.9.1](https://github.com/aklivity/zilla/tree/0.9.1) (2022-01-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.0...0.9.1)

## [0.9.0](https://github.com/aklivity/zilla/tree/0.9.0) (2022-01-12)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.8.2...0.9.0)

## [0.8.2](https://github.com/aklivity/zilla/tree/0.8.2) (2022-01-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.8.1...0.8.2)

## [0.8.1](https://github.com/aklivity/zilla/tree/0.8.1) (2022-01-10)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.8.0...0.8.1)

## [0.8.0](https://github.com/aklivity/zilla/tree/0.8.0) (2022-01-07)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.7.0...0.8.0)

## [0.7.0](https://github.com/aklivity/zilla/tree/0.7.0) (2022-01-02)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.6.0...0.7.0)

## [0.6.0](https://github.com/aklivity/zilla/tree/0.6.0) (2022-01-01)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.5.2...0.6.0)

## [0.5.2](https://github.com/aklivity/zilla/tree/0.5.2) (2021-12-30)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.5.1...0.5.2)

## [0.5.1](https://github.com/aklivity/zilla/tree/0.5.1) (2021-12-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.5.0...0.5.1)

## [0.5.0](https://github.com/aklivity/zilla/tree/0.5.0) (2021-12-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.4.0...0.5.0)

**Fixed bugs:**

- Mockito test failure only on GitHub Actions [\#3](https://github.com/aklivity/zilla/issues/3)

## [0.4.0](https://github.com/aklivity/zilla/tree/0.4.0) (2021-12-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.3.0...0.4.0)

## [0.3.0](https://github.com/aklivity/zilla/tree/0.3.0) (2021-12-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.2.0...0.3.0)

## [0.2.0](https://github.com/aklivity/zilla/tree/0.2.0) (2021-12-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.1.0...0.2.0)

## [0.1.0](https://github.com/aklivity/zilla/tree/0.1.0) (2021-12-16)

[Full Changelog](https://github.com/aklivity/zilla/compare/21d40009e35a4d777ac8e198febc843cb049320c...0.1.0)



\* *This Changelog was automatically generated by [github_changelog_generator](https://github.com/github-changelog-generator/github-changelog-generator)*
