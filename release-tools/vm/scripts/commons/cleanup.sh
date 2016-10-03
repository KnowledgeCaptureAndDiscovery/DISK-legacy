#!/bin/bash

rm --recursive --force /root/*

truncate --size 0 `find /var/log -type f | xargs`

yum -y remove gcc kernel-devel `package-cleanup --leaves --quiet`

yum clean all

truncate --size 0 ~/.bash_history ; history -c
