# http.kafka.proto.oneway

This example allows a protobuf object to be sent to a REST edpoint as JSON that gets validated and converted to the protobuf when it is produced onto Kafka.

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

## Watch kafka

Open the [Kafka UI](http://localhost:8080/ui/clusters/local/all-topics/my-requests/messages?limit=100&mode=TAILING) or run the kafkacat command:

```bash
docker compose -p zilla-http-kafka-proto-oneway exec kafkacat \
    kafkacat -b kafka.examples.dev:29092 -C -f 'Key:Message | %k:%s\n Headers | %h \n\n' -t my-requests
```

## Publish message with correct proto file

```bash
echo "message:'hello world',count:10" \
  | protoc  --encode Request ./request.proto \
  | curl -s --request POST http://localhost:7114/requests \
    --header "Content-Type: application/protobuf" \
    --data-binary @-
```

## Block message with incorrect proto file

```bash
echo "message:'hello bad type',count:'ten'" \
  | protoc  --encode Request ./request_bad_type.proto \
  | curl -s --request POST http://localhost:7114/requests \
    --header "Content-Type: application/protobuf" \
    --data-binary @-
```

```bash
echo "message:'hello extra field',count:10,extra:'field'" \
  | protoc  --encode Request ./request_extra_field.proto \
  | curl -s --request POST http://localhost:7114/requests \
    --header "Content-Type: application/protobuf" \
    --data-binary @-
```

```bash
echo "message:'hello wrong order',count:10" \
  | protoc  --encode Request ./request_wrong_order.proto \
  | curl -s --request POST http://localhost:7114/requests \
    --header "Content-Type: application/protobuf" \
    --data-binary @-
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
