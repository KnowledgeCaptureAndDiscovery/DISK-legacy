# ---------------------------
# Virtual Private Cloud (VPC)
# ---------------------------

resource "aws_vpc" "disk-vpc" {
  cidr_block           = "172.31.0.0/16"
  enable_dns_hostnames = true

  tags {
    Name = "disk-vpc"
  }
}

resource "aws_internet_gateway" "disk-internet-gateway" {
  vpc_id = "${aws_vpc.disk-vpc.id}"

  tags {
    Name = "disk-internet-gateway"
  }
}

resource "aws_subnet" "disk-vpc-subnet" {
  vpc_id                  = "${aws_vpc.disk-vpc.id}"
  cidr_block              = "172.31.0.0/21"
  map_public_ip_on_launch = true
  availability_zone       = "${data.aws_availability_zones.available.names[0]}"

  tags {
    Name = "disk-vpc-subnet"
  }
}

resource "aws_default_route_table" "disk-vpc-default-route-table" {
  default_route_table_id = "${aws_vpc.disk-vpc.default_route_table_id}"

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = "${aws_internet_gateway.disk-internet-gateway.id}"
  }

  tags {
    Name = "disk-vpc-default-route-table"
  }
}

# -------------------
# Elastic File System
# -------------------

/*
 * Disable EFS creation, as we do not want it to be deleted on destroy.
 *
resource "aws_efs_file_system" "disk-efs" {
  tags {
    Name = "disk-efs"
  }

  lifecycle {
    prevent_destroy = true
  }
}
*/

resource "aws_efs_mount_target" "efs-mount-target" {
  file_system_id = "${var.aws_efs_id}"
  subnet_id      = "${aws_subnet.disk-vpc-subnet.id}"

  security_groups = [
    "${aws_security_group.allows-nfs-mount.id}",
  ]
}

# ----------
# Elastic IP
# ----------

resource "aws_eip" "disk-eip" {
  vpc = true

  depends_on = [
    "aws_internet_gateway.disk-internet-gateway",
  ]

  tags {
    Name = "disk-eip"
  }
}

resource "aws_eip_association" "disk-eip-association" {
  instance_id   = "${aws_instance.disk-master.id}"
  allocation_id = "${aws_eip.disk-eip.id}"
}

# ---------------
# Security Groups
# ---------------

resource "aws_security_group" "disk-master" {
  name        = "disk-master"
  description = "DISK: Firewall Configuration (Master)"
  vpc_id      = "${aws_vpc.disk-vpc.id}"

  egress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"

    cidr_blocks = [
      "0.0.0.0/0",
    ]
  }

  ingress {
    from_port = 8080
    to_port   = 8080
    protocol  = "6"

    cidr_blocks = [
      "0.0.0.0/0",
    ]

    description = "Tomcat"
  }

  ingress {
    from_port = 22
    to_port   = 22
    protocol  = "6"

    cidr_blocks = [
      "0.0.0.0/0",
    ]

    description = "SSH"
  }

  ingress {
    from_port = 123
    to_port   = 123
    protocol  = "17"

    cidr_blocks = [
      "0.0.0.0/0",
    ]

    description = "NTP"
  }

  tags {
    Name = "disk-master"
  }
}

resource "aws_security_group" "disk-worker" {
  name        = "disk-worker"
  description = "DISK: Firewall Configuration (Worker)"
  vpc_id      = "${aws_vpc.disk-vpc.id}"

  egress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"

    cidr_blocks = [
      "0.0.0.0/0",
    ]
  }

  ingress {
    from_port = 22
    to_port   = 22
    protocol  = "6"

    cidr_blocks = [
      "0.0.0.0/0",
    ]

    description = "SSH"
  }

  ingress {
    from_port = 123
    to_port   = 123
    protocol  = "17"

    cidr_blocks = [
      "0.0.0.0/0",
    ]

    description = "NTP"
  }

  tags {
    Name = "disk-worker"
  }
}

resource "aws_security_group" "disk-master-worker" {
  name        = "disk-master-worker"
  description = "DISK: Firewall Configuration (Master / Worker)"
  vpc_id      = "${aws_vpc.disk-vpc.id}"

  egress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"

    cidr_blocks = [
      "0.0.0.0/0",
    ]
  }

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"

    security_groups = [
      "${aws_security_group.disk-master.id}",
      "${aws_security_group.disk-worker.id}",
    ]
  }

  tags {
    Name = "disk-master-worker"
  }
}

resource "aws_security_group" "allows-nfs-mount" {
  name        = "allows-nfs-mount"
  description = "DISK: Allow EC2 instance(s) to mount the EFS file system"
  vpc_id      = "${aws_vpc.disk-vpc.id}"

  egress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"

    cidr_blocks = [
      "0.0.0.0/0",
    ]
  }

  ingress {
    from_port = 2049
    to_port   = 2049
    protocol  = "6"

    security_groups = [
      "${aws_security_group.disk-master-worker.id}",
    ]

    description = "NFS"
  }

  tags {
    Name = "allows-nfs-mount"
  }
}
