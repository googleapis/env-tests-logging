// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"sync"
	"time"

	// This is replaced by the local version of cloud logging
	"cloud.google.com/go/compute/metadata"
	"cloud.google.com/go/logging"
	"cloud.google.com/go/pubsub"
)

// PubSubMessage is the message format received over HTTP
// ****************** CloudRun ******************
type pubSubMessage struct {
	Message struct {
		Data       []byte            `json:"data,omitempty"`
		Attributes map[string]string `json:"attributes,omitempty"`
		ID         string            `json:"id"`
	} `json:"message"`
	Subscription string `json:"subscription"`
}

// CloudRun: Processes a Pub/Sub message through HTTP.
// ****************** CloudRun ******************
func pubsubHTTP(w http.ResponseWriter, r *http.Request) {
	var m pubSubMessage
	body, err := ioutil.ReadAll(r.Body)
	if err != nil {
		log.Printf("ioutil.ReadAll: %v", err)
		http.Error(w, "Bad Request", http.StatusBadRequest)
		return
	}
	if err := json.Unmarshal(body, &m); err != nil {
		log.Printf("json.Unmarshal: %v", err)
		http.Error(w, "Bad Request", http.StatusBadRequest)
		return
	}

	msg := string(m.Message.Data)
	args := m.Message.Attributes

	switch msg {
	case "simplelog":
		simplelog(args)
	case "stdLog":
		break
	default:
		break
	}
}

// PubSubMessage is the message format received by CloudFunctions
// ****************** Functions ******************
type PubSubMessage struct {
	Data       []byte            `json:"data"`
	Attributes map[string]string `json:"attributes"`
}

// PubsubFunction is a background Cloud Function triggered by Pub/Sub
// ****************** Functions ******************
func PubsubFunction(ctx context.Context, m PubSubMessage) error {
	log.Printf("Data is: %v", string(m.Data))
	switch string(m.Data) {
	case "simplelog":
		simplelog(m.Attributes)
		break
	case "stdlog":
		break
	default:
		break
	}
	return nil
}

// pullPubsubMsgs is a async pubsub pull for app envs with subscriber configured
// ****************** GAE, TBA ******************
func pullPubsubMsgs(projectID string, sub *pubsub.Subscription) error {
	var mu sync.Mutex
	received := 0
	cctx, cancel := context.WithCancel(ctx)
	err := sub.Receive(cctx, func(ctx context.Context, msg *pubsub.Message) {
		mu.Lock()
		defer mu.Unlock()
		fmt.Printf("Got message: %q\n", string(msg.Data))
		msg.Ack()
		received++
		// Consume 1 message only.
		if received == 1 {
			cancel()
		}
	})
	if err != nil {
		return fmt.Errorf("Receive: %v", err)
	}
	return nil
}

var ctx context.Context

// client *logging.Client

// init executes for all environments, regardless if its a program or package
// TODO: verify that init executes for cloud functions.
func init() {
	ctx = context.Background()
}

// main runs for all environments except GCF
func main() {
	fmt.Println("Application main() executed")

	if os.Getenv("ENABLE_SUBSCRIBER") == "true" {
		// ****************** GAE ******************
		fmt.Println("in block: GAE ")
		projectID, err := metadata.ProjectID()
		if err != nil {
			log.Fatalf("metadata.ProjectID: %v", err)
		}
		fmt.Println("projectID: " + projectID)
		topicID := os.Getenv("PUBSUB_TOPIC")
		if topicID == "" {
			topicID = "logging-test"
		}
		fmt.Println("topicID: " + topicID)
		client, err := pubsub.NewClient(ctx, projectID)
		if err != nil {
			log.Fatalf("pubsub.NewClient: %v", err)
		}
		subscriptionID := topicID + "-subscriber"
		topic := client.Topic(topicID)
		sub, err := client.CreateSubscription(ctx, subscriptionID, pubsub.SubscriptionConfig{
			Topic:       topic,
			AckDeadline: 20 * time.Second,
		})
		if err != nil {
			log.Fatalf("CreateSubscription: %v", err)
		}
		err = pullPubsubMsgs(projectID, sub)
		if err != nil {
			log.Fatalf("pullPubsubMsgs: %v", err)
		}
	}

	// ****************** CloudRun ******************

	http.HandleFunc("/", pubsubHTTP)

	// ****************** CloudRun & AppEngine ******************

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
		log.Printf("Defaulting to port %s", port)
	}
	log.Printf("Listening on port %s", port)
	if err := http.ListenAndServe(":"+port, nil); err != nil {
		log.Fatal(err)
	}
}

// ****************** Test Cases ******************
// [Optional] envctl go <env> trigger simplelog log_name=foo,log_text=bar
func simplelog(args map[string]string) {
	// TODO: refactor this contx, use the global one if it doesn't exist, reinitiate.
	ctx := context.Background()
	projectID, err := metadata.ProjectID()
	if err != nil {
		log.Fatalf("metadata.ProjectID: %v", err)
	}
	client, err := logging.NewClient(ctx, projectID)
	if err != nil {
		log.Fatalf("Failed to create client: %v", err)
	}
	defer client.Close()

	logname := "my-log"
	if val, ok := args["log_name"]; ok {
		logname = val
	}

	logtext := "hello world"
	if val, ok := args["log_text"]; ok {
		logtext = val
	}

	logger := client.Logger(logname).StandardLogger(logging.Info)
	logger.Println(logtext)
}
