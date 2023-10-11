# Changelog

<<<<<<< HEAD
## [Unreleased](https://github.com/aklivity/zilla/tree/HEAD)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.54...HEAD)
=======
## [0.9.55](https://github.com/aklivity/zilla/tree/0.9.55) (2023-10-11)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.54...0.9.55)
>>>>>>> da6753468 (Update CHANGELOG.md)

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
- Schema Config Update [\#481](https://github.com/aklivity/zilla/pull/481) ([aDaemonThread](https://github.com/aDaemonThread))
- Feature/m1 docker build support [\#376](https://github.com/aklivity/zilla/pull/376) ([vordimous](https://github.com/vordimous))

## [0.9.54](https://github.com/aklivity/zilla/tree/0.9.54) (2023-09-26)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.53...0.9.54)

**Fixed bugs:**

- Additional scenarios in connection pool cleanup can trigger exception [\#475](https://github.com/aklivity/zilla/issues/475)

**Merged pull requests:**

- Integrate inline catalog and json validator with mqtt binding [\#479](https://github.com/aklivity/zilla/pull/479) ([jfallows](https://github.com/jfallows))
- Refactor config command [\#477](https://github.com/aklivity/zilla/pull/477) ([jfallows](https://github.com/jfallows))
- Remove streams only related to specific connection for additional scenarios [\#476](https://github.com/aklivity/zilla/pull/476) ([akrambek](https://github.com/akrambek))
- Cleanup log statements [\#474](https://github.com/aklivity/zilla/pull/474) ([aDaemonThread](https://github.com/aDaemonThread))
- Json Validator and Inline Schema fix [\#473](https://github.com/aklivity/zilla/pull/473) ([aDaemonThread](https://github.com/aDaemonThread))
- Http request validators feature flag [\#472](https://github.com/aklivity/zilla/pull/472) ([jfallows](https://github.com/jfallows))
- Avro validator module fix [\#470](https://github.com/aklivity/zilla/pull/470) ([aDaemonThread](https://github.com/aDaemonThread))
- Include validators in the mqtt config generator [\#467](https://github.com/aklivity/zilla/pull/467) ([attilakreiner](https://github.com/attilakreiner))
- Json Validator [\#466](https://github.com/aklivity/zilla/pull/466) ([aDaemonThread](https://github.com/aDaemonThread))
- Extract core validators [\#463](https://github.com/aklivity/zilla/pull/463) ([attilakreiner](https://github.com/attilakreiner))
- Mqtt validator implementation [\#452](https://github.com/aklivity/zilla/pull/452) ([bmaidics](https://github.com/bmaidics))
- Cache support for Schema Registry [\#447](https://github.com/aklivity/zilla/pull/447) ([aDaemonThread](https://github.com/aDaemonThread))
- Inline Catalog [\#445](https://github.com/aklivity/zilla/pull/445) ([aDaemonThread](https://github.com/aDaemonThread))
- Extract avro validator  [\#440](https://github.com/aklivity/zilla/pull/440) ([attilakreiner](https://github.com/attilakreiner))
- Introduce validators in the http binding [\#435](https://github.com/aklivity/zilla/pull/435) ([attilakreiner](https://github.com/attilakreiner))
- Schema Registry and Kafka Produce Validator [\#434](https://github.com/aklivity/zilla/pull/434) ([aDaemonThread](https://github.com/aDaemonThread))
- Move validators from binding-kafka to engine [\#415](https://github.com/aklivity/zilla/pull/415) ([attilakreiner](https://github.com/attilakreiner))
- Schema syntax validation for validator config  [\#412](https://github.com/aklivity/zilla/pull/412) ([attilakreiner](https://github.com/attilakreiner))
- Schema registry docker build shaded [\#411](https://github.com/aklivity/zilla/pull/411) ([jfallows](https://github.com/jfallows))
- Schema registry Implementation [\#402](https://github.com/aklivity/zilla/pull/402) ([aDaemonThread](https://github.com/aDaemonThread))
- UTF16 Validation implementation [\#401](https://github.com/aklivity/zilla/pull/401) ([aDaemonThread](https://github.com/aDaemonThread))
- API abstraction for SchemaRegistry in Zilla [\#311](https://github.com/aklivity/zilla/pull/311) ([aDaemonThread](https://github.com/aDaemonThread))

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

- `kubernetes autoscaling` feature [\#189](https://github.com/aklivity/zilla/issues/189)

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
- Include failsafe reports for failed builds [\#276](https://github.com/aklivity/zilla/pull/276) ([aDaemonThread](https://github.com/aDaemonThread))
- Fix mqtt decoding bug [\#275](https://github.com/aklivity/zilla/pull/275) ([bmaidics](https://github.com/bmaidics))
- Allow Kafka merged stream to change fetch filters dynamically [\#272](https://github.com/aklivity/zilla/pull/272) ([bmaidics](https://github.com/bmaidics))
- Bump jose4j from 0.7.10 to 0.9.3 in /runtime/guard-jwt [\#271](https://github.com/aklivity/zilla/pull/271) ([dependabot[bot]](https://github.com/apps/dependabot))
- Fix mqtt-kafka subscribe stream initial offset [\#270](https://github.com/aklivity/zilla/pull/270) ([bmaidics](https://github.com/bmaidics))
- Implement counter value reader in EngineExtContext [\#261](https://github.com/aklivity/zilla/pull/261) ([attilakreiner](https://github.com/attilakreiner))
- CacheMergedIT.shouldFetchMergedMessagesWithIsolationReadCommitted fix [\#260](https://github.com/aklivity/zilla/pull/260) ([aDaemonThread](https://github.com/aDaemonThread))
- Cleanup obsolete load counters [\#253](https://github.com/aklivity/zilla/pull/253) ([attilakreiner](https://github.com/attilakreiner))
- GitHub Actions fix [\#251](https://github.com/aklivity/zilla/pull/251) ([aDaemonThread](https://github.com/aDaemonThread))
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
- CacheMergedIT.shouldFetchMergedMessageValues failure on GitHub Actions fix [\#221](https://github.com/aklivity/zilla/pull/221) ([aDaemonThread](https://github.com/aDaemonThread))
- Support eager evaluation of all Kafka filters [\#212](https://github.com/aklivity/zilla/pull/212) ([aDaemonThread](https://github.com/aDaemonThread))
- Include license header check [\#206](https://github.com/aklivity/zilla/pull/206) ([jfallows](https://github.com/jfallows))
- Change DumpCommandTest [\#194](https://github.com/aklivity/zilla/pull/194) ([bmaidics](https://github.com/bmaidics))
- Fix incorrect Assertion in KafkaFunctionsTest [\#192](https://github.com/aklivity/zilla/pull/192) ([bmaidics](https://github.com/bmaidics))
- Enhance kafka binding to notify transition from historical to live messages [\#181](https://github.com/aklivity/zilla/pull/181) ([aDaemonThread](https://github.com/aDaemonThread))
- Support verbose schema output on startup [\#175](https://github.com/aklivity/zilla/pull/175) ([jfallows](https://github.com/jfallows))
- Ignore shouldReconfigureWhenModifiedUsingComplexSymlinkChain [\#169](https://github.com/aklivity/zilla/pull/169) ([bmaidics](https://github.com/bmaidics))
- Provide http\(s\) configuration server for zilla.yaml [\#166](https://github.com/aklivity/zilla/pull/166) ([bmaidics](https://github.com/bmaidics))
- Convert zilla spec config .json files to .yaml extension and syntax [\#165](https://github.com/aklivity/zilla/pull/165) ([aDaemonThread](https://github.com/aDaemonThread))
- Update zilla readme to address yaml changes [\#162](https://github.com/aklivity/zilla/pull/162) ([aDaemonThread](https://github.com/aDaemonThread))
- Flyweight wrapping error race condition fix [\#161](https://github.com/aklivity/zilla/pull/161) ([aDaemonThread](https://github.com/aDaemonThread))
- Add a newline to the end of the config if it is not present [\#158](https://github.com/aklivity/zilla/pull/158) ([attilakreiner](https://github.com/attilakreiner))
- Http dynamic configuration [\#156](https://github.com/aklivity/zilla/pull/156) ([bmaidics](https://github.com/bmaidics))
- Dynamic config [\#141](https://github.com/aklivity/zilla/pull/141) ([bmaidics](https://github.com/bmaidics))
- Add schema for specifying an OpenID provider discovery endpoint [\#106](https://github.com/aklivity/zilla/pull/106) ([Alfusainey](https://github.com/Alfusainey))

## [0.9.42](https://github.com/aklivity/zilla/tree/0.9.42) (2023-01-29)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.41...0.9.42)

**Implemented enhancements:**

- Support YAML syntax for Zilla configuration [\#144](https://github.com/aklivity/zilla/issues/144)

**Merged pull requests:**

- Adding yaml support for zilla config [\#150](https://github.com/aklivity/zilla/pull/150) ([aDaemonThread](https://github.com/aDaemonThread))

## [0.9.41](https://github.com/aklivity/zilla/tree/0.9.41) (2023-01-27)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.40...0.9.41)

**Merged pull requests:**

- Pass correct flag for delete payload [\#155](https://github.com/aklivity/zilla/pull/155) ([akrambek](https://github.com/akrambek))

## [0.9.40](https://github.com/aklivity/zilla/tree/0.9.40) (2023-01-25)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.39...0.9.40)

**Implemented enhancements:**

- Support `{{ mustache }}` syntax in zilla.json [\#91](https://github.com/aklivity/zilla/issues/91)

**Merged pull requests:**

- Adding support for Expression Resolver [\#143](https://github.com/aklivity/zilla/pull/143) ([aDaemonThread](https://github.com/aDaemonThread))

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
- Added Info & link for SASL/SCRAM Examples [\#132](https://github.com/aklivity/zilla/pull/132) ([aDaemonThread](https://github.com/aDaemonThread))
- Http2 client support [\#127](https://github.com/aklivity/zilla/pull/127) ([akrambek](https://github.com/akrambek))

## [0.9.32](https://github.com/aklivity/zilla/tree/0.9.32) (2022-11-28)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.31...0.9.32)

**Implemented enhancements:**

- Implement `zilla dump` command similar to `tcpdump` [\#114](https://github.com/aklivity/zilla/issues/114)

**Merged pull requests:**

- Use try-with-resources to manage lifecycle of writer [\#130](https://github.com/aklivity/zilla/pull/130) ([jfallows](https://github.com/jfallows))
- Reduce pcap frame encoding overhead [\#129](https://github.com/aklivity/zilla/pull/129) ([jfallows](https://github.com/jfallows))
- Support SASL SCRAM authentication [\#126](https://github.com/aklivity/zilla/pull/126) ([aDaemonThread](https://github.com/aDaemonThread))
- Implement zilla dump command similar to tcpdump [\#121](https://github.com/aklivity/zilla/pull/121) ([bmaidics](https://github.com/bmaidics))

## [0.9.31](https://github.com/aklivity/zilla/tree/0.9.31) (2022-11-17)

[Full Changelog](https://github.com/aklivity/zilla/compare/0.9.30...0.9.31)

**Implemented enhancements:**

- Remove merged from kafka binding configuration [\#108](https://github.com/aklivity/zilla/issues/108)
- Simplify duplicate request detection at event-driven microservices [\#71](https://github.com/aklivity/zilla/issues/71)

**Fixed bugs:**

- Error running http.kafka.oneway from zilla-examples [\#117](https://github.com/aklivity/zilla/issues/117)
- Zillla build fails on timeout [\#102](https://github.com/aklivity/zilla/issues/102)

**Merged pull requests:**

- Fix uint32 as array length [\#128](https://github.com/aklivity/zilla/pull/128) ([akrambek](https://github.com/akrambek))
- Adjust expectations to handle the case where we extend window max … [\#125](https://github.com/aklivity/zilla/pull/125) ([jfallows](https://github.com/jfallows))
- Remove merged from kafka binding configuration [\#122](https://github.com/aklivity/zilla/pull/122) ([aDaemonThread](https://github.com/aDaemonThread))
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

- Feature request:  Reqeust limiter [\#86](https://github.com/aklivity/zilla/issues/86)
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
