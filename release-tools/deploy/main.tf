#terraform {
#  backend "local" {
#    path = "state/t.tfstate"
#  }
#}

terraform {
  backend "s3" {
    region  = "us-west-2"
    profile = "disk@stanford"
    bucket  = "disk-terraform"
    key     = "state/terraform.tfstate"
  }
}

# Configure the AWS Provider
provider "aws" {
  profile = "disk@stanford"
  region  = "${var.aws_region}"
}

# Start DISK Master
resource "aws_instance" "disk-master" {
  ami           = "${data.aws_ami.disk-ami.id}"
  instance_type = "${var.aws_instance_type}"
  key_name      = "${var.key_name}"
  user_data     = "EFS_ID='${var.aws_efs_id}'"

  security_groups = [
    "disk-master",
    "disk-master-worker",
  ]

  root_block_device {
    delete_on_termination = "${var.delete_volumes}"
  }

  ephemeral_block_device {
    device_name  = "/dev/sdb"
    virtual_name = "ephemeral0"
  }

  ephemeral_block_device {
    device_name  = "/dev/sdc"
    virtual_name = "ephemeral1"
  }

  tags {
    Name = "disk-${var.disk_version}-master"
  }
}

# Start DISK Worker
resource "aws_instance" "disk-worker" {
  ami           = "${data.aws_ami.disk-ami.id}"
  instance_type = "${var.aws_instance_type}"
  key_name      = "${var.key_name}"
  user_data     = "EFS_ID='${var.aws_efs_id}'\nMASTER_IP='${aws_instance.disk-master.private_ip}'"

  count = "${var.workers}"

  security_groups = [
    "disk-worker",
    "disk-master-worker",
  ]

  root_block_device {
    delete_on_termination = "${var.delete_volumes}"
  }

  ephemeral_block_device {
    device_name  = "/dev/sdb"
    virtual_name = "ephemeral0"
  }

  ephemeral_block_device {
    device_name  = "/dev/sdc"
    virtual_name = "ephemeral1"
  }

  tags {
    Name = "disk-${var.disk_version}-worker"
  }
}
