#!/usr/bin/env bash

set -e
set -x

if [ $# -lt 1 ]; then
    echo "Usage: $0 VM_VERSION"
    exit 1
fi


#----------
# Variables
#----------

export AWS_PROFILE='vm-import@ikcap'
export AWS_DEFAULT_PROFILE=${AWS_PROFILE}

URL='http://mirrors.usc.edu/pub/linux/distributions/centos/7/isos/x86_64'
ISO=`curl --silent ${URL}/sha256sum.txt | grep 'NetInstall'`
ISO_NAME=`echo $ISO | cut -d' ' -f2`

VM_NAME='DISKVM'
VM_VERSION=$1
shift


#-----------------------
# Step 1: Build Base Box
#-----------------------

BASE_OUT_FILE="output/base/disk-vm-${VM_VERSION}.ovf"

packer build -var "iso_url=${URL}" \
             -var "iso_name=${ISO_NAME}" \
             -var "out_file=`basename ${BASE_OUT_FILE} .ovf`" \
             -var "vm_version=${VM_VERSION}" \
             -machine-readable \
             $@ 00-base.json | tee log-00.txt


#-----------------------------------------
# Step 2: Build AWS VM (only for releases)
#-----------------------------------------

AWS_OUT_FILE="output/aws/${VM_NAME}-${VM_VERSION}.ova"

packer build -var "base_ovf_path=${BASE_OUT_FILE}" \
             -var "vm_name=${VM_NAME}" \
             -var "vm_version=${VM_VERSION}" \
             -parallel=false \
             -machine-readable \
             $@ 01-disk.json | tee log-01.txt


#-------------------------------
# Step 2b: Configure AWS EC2 AMI
#-------------------------------

# Get AMI ID from log-02.txt
AMI_ID=`grep "aws: AMIs were created" log-01.txt  | grep --extended-regexp --only-matching --word-regexp ami-.*`

# Copy Image
NEW_AMI=`aws ec2 copy-image --source-region 'us-west-2' \
                   --source-image-id ${AMI_ID} \
                   --name "DISK VM ${VM_VERSION}" \
                   --description "DISK VM ${VM_VERSION}"`

aws ec2 wait image-available --image-id "${NEW_AMI}"

# Make it public
aws ec2 modify-image-attribute --image-id "${NEW_AMI}" --launch-permission "{\"Add\": [{\"Group\":\"all\"}]}"

# De-register Old AMI
aws ec2 deregister-image --image-id "${AMI_ID}"
