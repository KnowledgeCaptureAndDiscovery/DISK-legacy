#!/bin/bash
#
# aws-instance-setup
#
# chkconfig: 2345 45 25
# description:

### BEGIN INIT INFO
# Provides: page-setup
# Required-Start: $local_fs $network $syslog
# Required-Stop: $local_fs $syslog
# Should-Start: $syslog
# Should-Stop: $network $syslog
# Default-Start: 2 3 4 5
# Default-Stop: 0 1 6
# Short-Description: PAGE VM Setup
# Description:
### END INIT INFO

# source function library
. /etc/rc.d/init.d/functions

RETVAL=0

VOLUME_GROUP="vg01"
LOGICAL_VOLUME="lvol01"
DIR="/scratch"
LUKS_KEY_DIR="/home/root/.keys"

runlevel=$(set -- $(runlevel); eval "echo \$$#" )


configure_me()
{
    # get the user data
    curl --silent --output /etc/user-data http://169.254.169.254/2009-04-04/user-data
    touch /etc/user-data
    if grep 10. /etc/user-data >/dev/null 2>&1; then
        MASTER_IP=`cat /etc/user-data`
    fi

    mount_ephemeral_disks

    # this is placeholder. till we really need to configure condor stuff
    condor_basic_setup

    MY_IP=`ifconfig eth0 | grep "inet addr:" | sed 's/.*inet addr://' | sed 's/ .*//'`

    if [ "x$MASTER_IP" != "x" ]; then
        setup_worker $MY_IP $MASTER_IP
    else
        setup_master $MY_IP
    fi

    echo "Setting up swap space"
    setup_swap_space

    # the base Page Large VM has a 12GB on /dev/xvda2
    # mount as /software
    #if [ -e /dev/xvda2 ] && [ ! -e /software ]; then
    if [ -e /dev/xvda2 ]; then
	mkdir -p /software
	mount /dev/xvda2 /software/
    fi
}

setup_swap_space()
{

    #turn of existing swap partitions if any
    swapoff -a

    SWAP_DIR=/tmp
    if [ -d /scratch ]; then
	SWAP_DIR=/scratch
    fi

    dd if=/dev/zero of=$SWAP_DIR/myswapfile bs=1M count=3072
    chmod 600 $SWAP_DIR/myswapfile
    mkswap $SWAP_DIR/myswapfile
    swapon $SWAP_DIR/myswapfile
}

mount_ephemeral_disks()
{
    #should have a matching stop method
    mount_ephemeral_disks_lvm_luks
}

mount_ephemeral_disks_lvm_luks(){

    #mounts all ephemeral storage to a single /scratch via lvm
    #and also encrypts using luk

    #unmount any ephemeral storage mounted automatically
    umount /mnt/ || true

    LVM_DEVICES=()
    # m3-xlarge instances have xvdb and xvdc
    for DEV in xvdf xvdg xvdh xvdi xvdj xvdk xvdb xvdc; do
	if [ -e /dev/$DEV ]; then
            VALID_DEV_COUNT=$(($VALID_DEV_COUNT + 1))
            LVM_DEVICES+=("/dev/$DEV")
	fi
    done


    # remove existing volume groups if they exist
    umount /scratch 2>/dev/null || /bin/true
    # remove the logical volume
    yes | /sbin/lvremove /dev/$VOLUME_GROUP/$LOGICAL_VOLUME 2>/dev/null || /bin/true
    # deactivate the volume group
    /sbin/vgchange -a n $VOLUME_GROUP 2>/dev/null || /bin/true
    # remove the volume group if exists
    yes | /sbin/vgremove $VOLUME_GROUP 2>/dev/null || /bin/true
    # remove the physical volumes
    /sbin/pvremove ${LVM_DEVICES[@]} 2>/dev/null || /bin/true

    # do the lvm setup
    for DEVICE in "${LVM_DEVICES[@]}"; do
        # create physical volume corresponding to each device
	/sbin/pvcreate $DEVICE
    done

    LVM_NUM=${#LVM_DEVICES[@]}
    if [ $LVM_NUM -gt 0 ]; then
	# we can setup /scratch via LVM

        #create the volume group corresponding to all the  physical volumes
	/sbin/vgcreate $VOLUME_GROUP ${LVM_DEVICES[@]}

        #activate the volume group
	/sbin/vgchange -a y $VOLUME_GROUP

        # create a logical volume with as much space as free
	# we need to pipe yes as on reboot, some reason LUKS signature is not wiped
	# and we see  WARNING: LUKS signature detected on
	yes | /sbin/lvcreate -l100%FREE -W y -n $LOGICAL_VOLUME $VOLUME_GROUP

	# setup LUKS
	# Do luks
	mkdir -p $LUKS_KEY_DIR
	/bin/dd if=/dev/urandom of="$LUKS_KEY_DIR/scratch.key" bs=4k count=1
	/sbin/cryptsetup luksFormat --batch-mode --key-file "$LUKS_KEY_DIR/scratch.key" --cipher aes-cbc-essiv:sha256 --hash ripemd160 --key-size 256 "/dev/$VOLUME_GROUP/$LOGICAL_VOLUME"
	/sbin/cryptsetup luksOpen --key-file "$LUKS_KEY_DIR/scratch.key" "/dev/$VOLUME_GROUP/$LOGICAL_VOLUME" $LOGICAL_VOLUME
	/usr/bin/shred -u "$LUKS_KEY_DIR/scratch.key"


	#create a file system and mount it
	mkfs.ext4 /dev/mapper/$LOGICAL_VOLUME
	mkdir -p $DIR
	mount /dev/mapper/$LOGICAL_VOLUME $DIR
	chown -R  page:page $DIR
    fi

}




mount_ephemeral_disks_lvm(){

    #mounts all ephemeral storage to a single /scratch via lvm

    #unmount any ephemeral storage mounted automatically
    umount /mnt/ || true

    LVM_DEVICES=()
    # m3-xlarge instances have xvdb and xvdc
    for DEV in xvdf xvdg xvdh xvdi xvdj xvdk xvdb xvdc; do
	if [ -e /dev/$DEV ]; then
            VALID_DEV_COUNT=$(($VALID_DEV_COUNT + 1))
            LVM_DEVICES+=("/dev/$DEV")
	fi
    done


    # remove existing volume groups if they exist
    umount /scratch 2>/dev/null || /bin/true
    # remove the logical volume
    yes | /sbin/lvremove /dev/$VOLUME_GROUP/$LOGICAL_VOLUME 2>/dev/null || /bin/true
    # deactivate the volume group
    /sbin/vgchange -a n $VOLUME_GROUP 2>/dev/null || /bin/true
    # remove the volume group if exists
    yes | /sbin/vgremove $VOLUME_GROUP 2>/dev/null || /bin/true
    # remove the physical volumes
    /sbin/pvremove ${LVM_DEVICES[@]} 2>/dev/null || /bin/true

    # do the lvm setup
    for DEVICE in "${LVM_DEVICES[@]}"; do
        # create physical volume corresponding to each device
	/sbin/pvcreate $DEVICE
    done

    LVM_NUM=${#LVM_DEVICES[@]}
    if [ $LVM_NUM -gt 0 ]; then
	# we can setup /scratch via LVM

        #create the volume group corresponding to all the  physical volumes
	/sbin/vgcreate $VOLUME_GROUP ${LVM_DEVICES[@]}

        #activate the volume group
	/sbin/vgchange -a y $VOLUME_GROUP

        # create a logical volume with as much space as free
	/sbin/lvcreate -l100%FREE -n $LOGICAL_VOLUME $VOLUME_GROUP
	mkfs.ext4 /dev/$VOLUME_GROUP/$LOGICAL_VOLUME
	mkdir -p /scratch
	mount /dev/$VOLUME_GROUP/$LOGICAL_VOLUME $DIR
	chown -R  page:page $DIR
    fi

}



mount_ephemeral_disks_direct()
{
    # mounts each ephemeral disk as a scratch mount point
    # two disks will result in /scratch1 and /scratch2

    VALID_DEV_COUNT=0

    #unmount any ephemeral storage mounted automatically
    umount /mnt/ || true

    # m3-xlarge instances have xvdb and xvdc
    for DEV in xvdf xvdg xvdh xvdi xvdj xvdk xvdb xvdc; do

        if [ -e /dev/$DEV ]; then
            VALID_DEV_COUNT=$(($VALID_DEV_COUNT + 1))
            DIR=/scratch${VALID_DEV_COUNT}
	    mkdir -p $DIR
	    mount /dev/$DEV/ $DIR
	    sudo chown -R page:page $DIR
	fi

    done
}


condor_basic_setup()
{
    return 0
}


setup_master()
{
    MY_IP=$1
    return 0
}


setup_worker() {
    return 0
}


start()
{
        echo -n $"Setting up AWS instance: "
        configure_me && success || failure
        RETVAL=$?
        echo
        return $RETVAL
}

stop()
{
    stop_with_lvm_luks
}

stop_with_lvm_luks()
{
        SWAP_DIR=/tmp
	if [ -d /scratch ]; then
	    SWAP_DIR=/scratch
	fi

	swapoff $SWAP_DIR/myswapfile

	#unmount the /scratch dir if exists
	if [ -d /scratch ]; then
	    umount /scratch

	    #/scratch also means we have lvm and luks setup
	    /sbin/cryptsetup luksClose $LOGICAL_VOLUME
	    /bin/sleep 5
	    /sbin/vgchange -a n $VOLUME_GROUP

	    rm -rf /scratch
	fi

        return 0
}


stop_with_lvm()
{
    SWAP_DIR=/tmp
	if [ -d /scratch ]; then
	    SWAP_DIR=/scratch
	fi

	swapoff $SWAP_DIR/myswapfile

	#unmount the /scratch dir if exists
	if [ -d /scratch ]; then
	    umount /scratch
	    #/scratch also means we have lvm setup
	    /sbin/vgchange -a n $VOLUME_GROUP
	fi

        return 0
}

reload()
{
        start
        stop
}

restart() {
        stop
        start
}

force_reload() {
        restart
}


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
        reload)
                reload
                ;;
        *)
                echo $"Usage: $0 {start|stop|restart|reload}"
                RETVAL=2
esac
exit $RETVAL
