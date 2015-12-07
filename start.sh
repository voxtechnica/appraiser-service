#!/usr/bin/env bash
JAR=$(ls -1 target/appraisers-*.jar | grep -v sources | tail -n 1)
CFG=appraisers.yaml
java -jar $JAR server $CFG
