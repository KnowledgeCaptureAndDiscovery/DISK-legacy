variable "disk_version" {
  default = "1.2"
}

data "aws_ami" "disk-ami" {
  most_recent = true

  owners = [
    "self",
  ]

  filter {
    name = "name"

    values = [
      "DISK VM ${var.disk_version}",
    ]
  }
}

variable "workers" {
  description = "No. of worker instances to start"
  default     = 0
}

variable "delete_volumes" {
  description = "Should the volumes be deleted on termination?"
  default     = true
}

variable "aws_region" {
  description = "AWS region to launch servers."
  default     = "us-west-2"
}

variable "key_name" {
  description = "Desired name of AWS key pair"
  default     = "wings_test"
}

variable "aws_instance_type" {
  default = "m3.xlarge"
}

variable "aws_efs_id" {
  default = "fs-c676896f"
}

variable "aws_ephemeral_count" {
  type = "map"

  default = {
    m3.xlarge = 2
  }
}

variable "aws_device_map" {
  type = "list"

  default = [
    "b",
    "c",
    "d",
    "e",
    "f",
    "g",
    "h",
    "i",
    "j",
    "k",
    "l",
    "m",
    "n",
    "o",
    "p",
    "q",
    "r",
    "s",
    "t",
    "u",
    "v",
    "w",
    "x",
    "y",
    "z",
  ]
}
