module github.com/googleapis/env-tests-logging/deployable/go/main

go 1.16

require (
	cloud.google.com/go/compute v1.19.1
	cloud.google.com/go/compute/metadata v0.2.3
	cloud.google.com/go/logging v1.7.0
	cloud.google.com/go/pubsub v1.30.0
	google.golang.org/grpc v1.56.3
)

replace cloud.google.com/go/logging => ./logging

replace golang.org/x/sys => golang.org/x/sys v0.0.0-20220811171246-fbc7d0a398ab
