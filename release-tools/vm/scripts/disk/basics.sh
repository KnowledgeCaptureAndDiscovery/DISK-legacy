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

groupadd --gid 91 tomcat

useradd --comment "Apache Tomcat" \
        --home-dir /usr/share/tomcat \
        --no-create-home \
        --uid 91 \
        --gid 91 \
        --shell /sbin/nologin \
        tomcat

yum -y install subversion git
yum -y install ant maven
yum -y install python-setuptools python-virtualenv
yum -y install R graphviz
yum -y install tomcat tomcat-native
yum -y install lvm2 nfs-utils

systemctl enable  tomcat
systemctl start   tomcat
