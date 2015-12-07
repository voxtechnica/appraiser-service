#!/usr/bin/env bash
# Build and Deploy a Debian package for the Appraiser Service
export DEBIAN_FRONTEND=noninteractive

# Build the shaded jar file (assuming this hasn't already been done)
mvn clean package

# Package Version
VER=$(date +%Y.%m.%d-%H%M)
#VER="$CIRCLE_BUILD_NUM"
PKG=voxtechnica-appraisers_$VER
echo "Building $PKG"

# Binary file
mkdir -p $PKG/usr/bin
JAR=$(ls -1 target/appraisers-*.jar | grep -v sources | tail -n 1)
cp $JAR $PKG/usr/bin/appraisers.jar

# Upstart configuration
cp -a deb/etc $PKG/

# Environment-specific configuration
cp appraisers.yaml $PKG/etc/appraisers.yaml

# Control files
cp -a deb/DEBIAN $PKG/
echo "Version: $VER" >> $PKG/DEBIAN/control

# Build the Debian Package
dpkg-deb -b $PKG
rm -Rf $PKG

# TODO: Sign the package and put it in a private apt repository
echo "Complete."
