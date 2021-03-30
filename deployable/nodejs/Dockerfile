# Copyright 2021 Google LLC. All rights reserved.
# Use of this source code is governed by the Apache 2.0
# license that can be found in the LICENSE file.

# Use the official lightweight Node.js 12 image.
# https://hub.docker.com/_/node
FROM node:12-slim

# Create and change to the app directory.
WORKDIR /usr/src/app

# Copy test script and dependencies to the container image.
COPY package*.json ./
COPY app.js ./
COPY nodejs-logging ./nodejs-logging

# Install dependencies.
RUN npm install --production

# Run the web service on container startup.
CMD [ "npm", "start" ]
