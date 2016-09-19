#!/bin/bash


# ---------------------
# External repositories
# ---------------------

yum -y install https://centos7.iuscommunity.org/ius-release.rpm


# --
# OS
# --

yum -y update


# ------
# Basics
# ------

yum -y install subversion git
yum -y install ant maven
yum -y install python-setuptools python-virtualenv
yum -y install R graphviz
yum -y install tomcat tomcat-native
yum -y install nfs-utils

systemctl enable  tomcat
systemctl start   tomcat
