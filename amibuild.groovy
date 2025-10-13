pipeline {
  agent any

  environment {
    AWS_REGION = 'us-east-1'
  }

  parameters {
    string(name: 'KEY_NAME', defaultValue: 'my-key-pair', description: 'EC2 Key Pair name')
  }

  stages {
    stage('Setup') {
      steps {
        cleanWs()
        checkout([
          $class: 'GitSCM',
          branches: [[name: "*/master"]],
          userRemoteConfigs: [[
            url: 'https://github.com/MuddyThunder1040/aws-topology.git'
          ]]
        ])
      }
    }

    stage('Build AMI') {
      steps {
        withCredentials([
          usernamePassword(credentialsId: 'Aws-cli', passwordVariable: 'AWS_SECRET_ACCESS_KEY', usernameVariable: 'AWS_ACCESS_KEY_ID')
        ]) {
          sh """
            # Auto-detect VPC and subnet
            echo "Auto-detecting AWS resources..."
            VPC_ID=\$(aws ec2 describe-vpcs --filters "Name=is-default,Values=true" --query "Vpcs[0].VpcId" --output text)
            [ "\$VPC_ID" = "None" ] && VPC_ID=\$(aws ec2 describe-vpcs --query "Vpcs[0].VpcId" --output text)
            SUBNET_ID=\$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=\$VPC_ID" --query "Subnets[0].SubnetId" --output text)
            
            echo "Using VPC: \$VPC_ID, Subnet: \$SUBNET_ID, Key: ${params.KEY_NAME}"
            
            # Generate unique timestamp for resources
            TIMESTAMP=\$(date +%s)
            
            # Initialize terraform and fix module issues automatically
            echo "Initializing Terraform..."
            terraform init || {
              echo "Fixing module conflicts..."
              if [ -d .terraform/modules/ami_builder/ami ]; then
                cd .terraform/modules/ami_builder/ami
                sed -i '1,5d' main.tf
                sed -i '/^provider "aws"/,/^}/d' main.tf
                sed -i '/^output "ami_id"/,/^}/d' main.tf
                sed -i '/^resource "aws_ec2_instance_state"/,/^}/d' main.tf
                # Make security group name unique
                sed -i "s/ami-builder-sg/ami-builder-sg-\$TIMESTAMP/g" main.tf
                cd -
                terraform init -reconfigure
              fi
            }
            
            # Build AMI
            echo "Building AMI..."
            terraform apply -target=module.ami_builder -auto-approve \
              -var="vpc_id=\$VPC_ID" \
              -var="subnet_id=\$SUBNET_ID" \
              -var="key_name=${params.KEY_NAME}"
            
            echo "AMI ID: \$(terraform output ami_id)"
          """
        }
      }
    }
  }

  post {
    always {
      cleanWs()
    }
  }
}
