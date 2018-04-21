#!/bin/bash

set -e


RETVAL=0
VOLUME_GROUP='vg01'
LOGICAL_VOLUME='lvol01'
SCRATCH_DIR='/scratch'
STORAGE_DIR='/efs'


configure_me()
{
    USER_DATA='/var/lib/cloud/instance/user-data.txt'

    if [ -e ${USER_DATA} ]; then
        source ${USER_DATA}
    fi

    echo "Mount ephemeral disks"
    mount_ephemeral_disks

    echo "Setup TMPDIR"
    setup_tmp_dir

    echo "Mount EFS disk"
    mount_efs_disk

    echo "Basic HTCondor setup"
    condor_basic_setup

    MY_IP=`ifconfig eth0 | grep "inet " | sed 's/.*inet //' | sed 's/ .*//'`

    if [ "x${MASTER_IP}" != "x" ]; then
        echo "Configure HTCondor as Worker node"
        setup_worker ${MY_IP} ${MASTER_IP}
    else
        echo "Configure HTCondor as Master node"
        setup_master ${MY_IP}
    fi

    echo "configure swap space"
    setup_swap_space
}


mount_ephemeral_disks()
{
    # Unmount ephemeral storage
    umount /mnt 2>/dev/null || /bin/true

    # Remove existing volume group
    umount ${SCRATCH_DIR} 2>/dev/null || /bin/true

    # Remove the logical volume
    /sbin/lvremove --force /dev/${VOLUME_GROUP}/${LOGICAL_VOLUME} 2>/dev/null || /bin/true

    # Deactivate the volume group
    /sbin/vgchange --activate n ${VOLUME_GROUP} 2>/dev/null || /bin/true

    # Remove the volume group if exists
    /sbin/vgremove ${VOLUME_GROUP} 2>/dev/null || /bin/true

    BD_META_URL='http://169.254.169.254/latest/meta-data/block-device-mapping/'

    LVM_DEVICES=()
    for DEVICE in `curl --silent ${BD_META_URL} | grep ephemeral`; do
        DEVICE=`curl --silent ${BD_META_URL}${DEVICE}`
        DEVICE="/dev/xvd${DEVICE:(-1)}"

        if [ -e ${DEVICE} ]; then
            VALID_DEV_COUNT=$(($VALID_DEV_COUNT + 1))
            LVM_DEVICES+=(${DEVICE})
        fi
    done

    # Remove the physical volumes
    /sbin/pvremove ${LVM_DEVICES[@]} 2>/dev/null || /bin/true

    # LVM Setup
    for DEVICE in "${LVM_DEVICES[@]}"; do
        # Create physical volume
        /sbin/pvcreate --yes ${DEVICE}
    done

    LVM_NUM=${#LVM_DEVICES[@]}
    if [ ${LVM_NUM} -gt 0 ]; then
        # Create the volume group
        /sbin/vgcreate ${VOLUME_GROUP} ${LVM_DEVICES[@]}

        # Activate the volume group
        /sbin/vgchange --activate y ${VOLUME_GROUP}

        # Create a logical volume
        yes | /sbin/lvcreate --extents 100%FREE --name ${LOGICAL_VOLUME} ${VOLUME_GROUP}

        # Format the logical volume
        mkfs.ext4 /dev/${VOLUME_GROUP}/${LOGICAL_VOLUME}

        mkdir --parent ${SCRATCH_DIR}

        mount /dev/${VOLUME_GROUP}/${LOGICAL_VOLUME} ${SCRATCH_DIR}
    fi

}

setup_tmp_dir()
{
    TMP_DIR='/tmp'
    if [ -d ${SCRATCH_DIR} ]; then
        TMP_DIR="${SCRATCH_DIR}/tmp"

        if [ ! -d ${TMP_DIR} ]; then
            mkdir --parent ${TMP_DIR}
            chmod 777 ${TMP_DIR}
            chmod o+t ${TMP_DIR}

            cat > /etc/environment << EOT
export TMPDIR=${TMP_DIR}
EOT
            cat > /etc/profile.d/tmpdir.sh << EOT
export TMPDIR=${TMP_DIR}
EOT
            cat > /etc/profile.d/tmpdir.csh << EOT
setenv TMPDIR ${TMP_DIR}
EOT
        fi
    fi

    return 0
}

mount_efs_disk()
{
    if [ "x${EFS_ID}" != "x" ]; then
        echo "Configure EFS ${EFS_ID}"
        mkdir --parent ${STORAGE_DIR}
        mount --types nfs4 \
              --options nfsvers=4.1,rsize=1048576,wsize=1048576,hard,timeo=600,retrans=2 \
              `curl --silent http://169.254.169.254/latest/meta-data/placement/availability-zone`.${EFS_ID}.efs.us-west-2.amazonaws.com:/ ${STORAGE_DIR}
    fi
}

condor_basic_setup()
{
    cat > /etc/condor/config.d/01-common << EOT
UID_DOMAIN = *
FILESYSTEM_DOMAIN = *
EOT

    if [ -d ${SCRATCH_DIR} ]; then
        mkdir --parent ${SCRATCH_DIR}/condor/{spool,execute}
        chown --recursive condor:condor ${SCRATCH_DIR}/condor

        cat >> /etc/condor/config.d/01-common << EOT
SPOOL = ${SCRATCH_DIR}/condor/spool
EXECUTE = ${SCRATCH_DIR}/condor/execute
EOT
    fi

    rm --force /etc/condor/config.d/02-mode
}

setup_master()
{
    MY_IP=$1
    cat > /etc/condor/config.d/02-mode << EOT
# Master Node Configuration
# https://twiki.grid.iu.edu/bin/view/Documentation/Release3/InstallCondor
DAEMON_LIST = MASTER, COLLECTOR, NEGOTIATOR, SCHEDD

# Dynamic Slot Configuration
# http://research.cs.wisc.edu/htcondor/manual/latest/3_5Policy_Configuration.html

JOB_DEFAULT_REQUESTMEMORY = ifthenelse(MemoryUsage =!= UNDEFINED,MemoryUsage,4096)
EOT
}

setup_worker()
{
    MY_IP=$1
    MASTER_IP=$2

    cat > /etc/condor/config.d/02-mode << EOT
# Worker Node Configuration
# https://twiki.grid.iu.edu/bin/view/Documentation/Release3/InstallCondor
DAEMON_LIST = MASTER, STARTD

CONDOR_HOST = ${MASTER_IP}

# Dynamic Slot Configuration
# http://research.cs.wisc.edu/htcondor/manual/latest/3_5Policy_Configuration.html

NUM_SLOTS = 1
NUM_SLOTS_TYPE_1 = 1
SLOT_TYPE_1 = 100%
SLOT_TYPE_1_PARTITIONABLE = TRUE
EOT

    systemctl stop    tomcat
    systemctl disable tomcat
    systemctl stop    postgresql-9.5
    systemctl disable postgresql-9.5

    return 0
}

setup_swap_space()
{
    # Turn off existing swap partitions
    swapoff --all

    SWAP_DIR='/tmp'
    if [ -d ${SCRATCH_DIR} ]; then
        SWAP_DIR="${SCRATCH_DIR}"
    fi

    dd if=/dev/zero of=${SWAP_DIR}/swap-file bs=1M count=3072
    chmod 600 ${SWAP_DIR}/swap-file
    mkswap ${SWAP_DIR}/swap-file
    swapon ${SWAP_DIR}/swap-file
}

start()
{
    echo -n "Setting up AWS instance: "
    configure_me
    RETVAL=$?
    echo
    return ${RETVAL}
}

stop()
{
    # Turns off all swap partitions
    swapoff --all

    # Turns on swap partitions listed in /etc/fstab
    swapon --all

    # Unmount Ephemeral storage
    if [ -d ${SCRATCH_DIR} ]; then
        umount ${SCRATCH_DIR} 2>/dev/null || /bin/true
        /sbin/vgchange --activate n ${VOLUME_GROUP}
    fi

    # Unmount EFS storage
    if [ -d ${SCRATCH_DIR} ]; then
        umount ${STORAGE_DIR} 2>/dev/null || /bin/true
    fi
}

restart()
{
    stop
    start
}


# ----
# Main
# ----

case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    *)
        echo $"Usage: $0 {start|stop|restart}"
        RETVAL=2
esac

exit ${RETVAL}
