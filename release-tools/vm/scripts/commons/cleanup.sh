#!/bin/bash


yum -y remove gcc kernel-devel `package-cleanup --leaves | grep -v 'Loaded plugins' | xargs`

yum clean all
