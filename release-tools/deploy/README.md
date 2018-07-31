# Software

1. [Install Terraform](https://www.terraform.io/downloads.html)
1. [Create AWS Security Credentials](http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-set-up.html)
1. Configure AWS CLI

```bash
cat >> ~/.aws/config <<EOT
[profile disk@stanford]
output = text
region = us-west-2
EOT
```

```bash
cat >> ~/.aws/credentials <<EOT
[disk@stanford]
aws_access_key_id = <ACCESS_KEY>
aws_secret_access_key = <SECRET_ACCESS_KEY>
EOT
```

# Initialize (one time only)

```bash
cd <DISK>/release-tools/deploy
terraform init
```

# Show current infrastructure status

```bash
terraform show
```

# Plan

Use plan command to see how your command will affect the AWS resources

```bash
terraform plan -var "workers=0"
```

# Start master with one worker

```bash
terraform apply -var "workers=1"
```

# Increase no. of workers

```bash
# Worker count must be more than current worker count
terraform apply -var "workers=2"
```

# Decrease no. of workers

```bash
# Worker count must be less than current worker count
terraform apply -var "workers=0"
```

