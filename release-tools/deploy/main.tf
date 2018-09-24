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
  subnet_id     = "${aws_subnet.disk-vpc-subnet.id}"
  key_name      = "${var.key_name}"
  user_data     = "EFS_ID='${var.aws_efs_id}'"

  vpc_security_group_ids = [
    "${aws_security_group.disk-master.id}",
    "${aws_security_group.disk-master-worker.id}",
  ]

  root_block_device {
    volume_size           = "${var.aws_instance_root_volume_size}"
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

  depends_on = [
    "aws_internet_gateway.disk-internet-gateway",
    "aws_efs_mount_target.efs-mount-target",
  ]

  tags {
    Name = "disk-${var.disk_version}-master"
  }

  volume_tags {
    Name = "disk-${var.disk_version}-master"
  }
}

# Start DISK Worker
resource "aws_instance" "disk-worker" {
  ami           = "${data.aws_ami.disk-ami.id}"
  instance_type = "${var.aws_instance_type}"
  subnet_id     = "${aws_subnet.disk-vpc-subnet.id}"
  key_name      = "${var.key_name}"
  user_data     = "EFS_ID='${var.aws_efs_id}'\nMASTER_IP='${aws_instance.disk-master.private_ip}'"

  count = "${var.workers}"

  vpc_security_group_ids = [
    "${aws_security_group.disk-worker.id}",
    "${aws_security_group.disk-master-worker.id}",
  ]

  root_block_device {
    volume_size           = "${var.aws_instance_root_volume_size}"
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

  depends_on = [
    "aws_internet_gateway.disk-internet-gateway",
    "aws_efs_mount_target.efs-mount-target",
  ]

  tags {
    Name = "disk-${var.disk_version}-worker"
  }

  volume_tags {
    Name = "disk-${var.disk_version}-worker-${count.index}"
  }
}
