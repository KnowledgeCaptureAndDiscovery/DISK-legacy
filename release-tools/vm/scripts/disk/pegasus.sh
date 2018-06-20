#!/bin/bash


# -------
# Pegasus
# -------

curl --output /etc/yum.repos.d/pegasus.repo http://download.pegasus.isi.edu/wms/download/rhel/7/pegasus.repo
yum  -y install pegasus
