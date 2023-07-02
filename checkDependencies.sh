#!/bin/bash
# Check Java dependencies for available updates
mvn versions:display-dependency-updates | tee dependencies.log
mvn versions:display-plugin-updates | tee -a dependencies.log
mvn dependency:tree -Ddetail=true | tee -a dependencies.log
