# Software

1. [Install VirtualBox](https://www.virtualbox.org/wiki/Downloads)
2. [Install Packer v0.10.1 or higher](https://www.packer.io/downloads.html)
3. [Create AWS Security Credentials](http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-set-up.html)
4. Configure AWS CLI

```bash
cat >> ~/.aws/config <<EOT
[profile vm-import@stanford]
output = text
region = us-west-2
EOT
```

```bash
cat >> ~/.aws/credentials <<EOT
[vm-import@stanford]
aws_access_key_id = <ACCESS_KEY>
aws_secret_access_key = <SECRET_ACCESS_KEY>
EOT
```

# Generate VMs

```bash
export AWS_DEFAULT_PROFILE='vm-import@stanford'
./build-vm.sh <DISK_VERSION>
```

## VMs built in 3 stages

1. Create a Minimal Cent OS 7 VM.
2. Starting from Minimal VM from Stage 1, create VM for AWS EC2
3. Execute AWS EC2 VM import process.


# Configure AWS EC2 AMI

1. Login to AWS
2. Navigate to EC2 Dashboard
3. Click Images > AMIs
4. For the newly created VM, set the Name
5. Mark the newly created VM, Public (Actions > Modify Image Permissions)
