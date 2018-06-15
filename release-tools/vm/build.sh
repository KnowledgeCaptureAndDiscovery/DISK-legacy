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

export AWS_PROFILE='vm-import@stanford'
export AWS_DEFAULT_PROFILE=${AWS_PROFILE}
export AWS_POLL_DELAY_SECONDS=3

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

if [ ! -f "${BASE_OUT_FILE}" ]; then

    packer build -var "iso_url=${URL}" \
                 -var "iso_name=${ISO_NAME}" \
                 -var "out_file=`basename ${BASE_OUT_FILE} .ovf`" \
                 -var "vm_version=${VM_VERSION}" \
                 -machine-readable \
                 $@ 00-base.json | tee log-00.txt

fi

#-----------------------------------------
# Step 2: Build AWS VM (only for releases)
#-----------------------------------------

BUCKET="disk-vms"
AWS_OUT_FILE="output/aws/${VM_NAME}-${VM_VERSION}.ova"

packer build -var "base_ovf_path=${BASE_OUT_FILE}" \
             -var "vm_name=${VM_NAME}" \
             -var "vm_version=${VM_VERSION}" \
             -var "bucket=${BUCKET}" \
             -parallel=false \
             -machine-readable \
             $@ 01-disk.json | tee log-01.txt


#-------------------------------
# Step 2b: Configure AWS EC2 AMI
#-------------------------------

# Get AMI ID from log-02.txt
AMI_ID=`grep "aws: AMIs were created" log-01.txt  | grep --extended-regexp --only-matching --word-regexp ami-[a-zA-Z0-9]*`

# Copy Image
NEW_AMI=`aws ec2 copy-image --source-region 'us-west-2' \
                   --source-image-id ${AMI_ID} \
                   --name "DISK VM ${VM_VERSION}" \
                   --description "DISK VM ${VM_VERSION}"`

WAIT="aws ec2 wait image-available --image-id ${NEW_AMI}"
$WAIT || $WAIT

# Get snapshot associated with old AMI
SNAP_ID=`aws ec2 describe-images --image-ids ${AMI_ID} --query 'Images[*].BlockDeviceMappings[*].Ebs.SnapshotId'`

# De-register Old AMI
aws ec2 deregister-image --image-id "${AMI_ID}"

# Delete old AMI's snapshot
aws ec2 delete-snapshot --snapshot-id "${SNAP_ID}"

# Tag new AMI and snapshot
NEW_SNAP=`aws ec2 describe-images --image-ids ${NEW_AMI} --query 'Images[*].BlockDeviceMappings[*].Ebs.SnapshotId'`
aws ec2 create-tags --resources "${NEW_AMI}" "${NEW_SNAP}" --tags Key=Name,Value=disk-${VM_VERSION}
