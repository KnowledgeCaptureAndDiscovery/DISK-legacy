#!/bin/bash

dd if=/dev/zero of=${SWAP_FILE} bs=1M count=3072
chmod 600 ${SWAP_FILE}
mkswap ${SWAP_FILE}
swapon ${SWAP_FILE}
