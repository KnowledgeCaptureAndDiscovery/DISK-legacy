#!/bin/bash

rm --recursive --force /root/*

truncate --size 0 `find /var/log -type f | xargs`

yum -y remove gcc kernel-devel `package-cleanup --leaves | grep -v 'Loaded plugins' | xargs`

yum clean all
