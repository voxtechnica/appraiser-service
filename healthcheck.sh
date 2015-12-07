#!/usr/bin/env bash
curl -s http://localhost:8081/healthcheck | python -mjson.tool
