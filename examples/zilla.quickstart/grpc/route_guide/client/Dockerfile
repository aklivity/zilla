FROM golang:1.19-alpine as build

WORKDIR /go/grpc-go
COPY . .

RUN go build client.go

FROM alpine
COPY --from=build /go/grpc-go/client .
ENTRYPOINT ["./client"]
