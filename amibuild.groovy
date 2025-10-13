pipeline {
  agent any
  
  environment {
    AWS_REGION = 'us-east-1'
  }
  
  parameters {
    string(
      name: 'KEY_NAME', 
      defaultValue: 'my-key-pair', 
      description: 'EC2 Key Pair name'
    )
  }
  
  stages {
    stage('Setup Repository') {
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
    
    stage('Prepare AWS Resources') {
      steps {
        withCredentials([
          usernamePassword(
            credentialsId: 'Aws-cli', 
            passwordVariable: 'AWS_SECRET_ACCESS_KEY', 
            usernameVariable: 'AWS_ACCESS_KEY_ID'
          )
        ]) {
          script {
            sh '''
              echo "=== Auto-detecting AWS resources ==="
              
              # Find default VPC or use first available
              VPC_ID=$(aws ec2 describe-vpcs \
                --filters "Name=is-default,Values=true" \
                --query "Vpcs[0].VpcId" --output text)
              
              if [ "$VPC_ID" = "None" ]; then
                VPC_ID=$(aws ec2 describe-vpcs \
                  --query "Vpcs[0].VpcId" --output text)
              fi
              
              # Find subnet in the VPC
              SUBNET_ID=$(aws ec2 describe-subnets \
                --filters "Name=vpc-id,Values=$VPC_ID" \
                --query "Subnets[0].SubnetId" --output text)
              
              echo "Found VPC: $VPC_ID"
              echo "Found Subnet: $SUBNET_ID"
              
              # Save to environment for next stage
              echo "export VPC_ID=$VPC_ID" > aws_resources.env
              echo "export SUBNET_ID=$SUBNET_ID" >> aws_resources.env
            '''
          }
        }
      }
    }
    
    stage('Validate Key Pair') {
      steps {
        withCredentials([
          usernamePassword(
            credentialsId: 'Aws-cli', 
            passwordVariable: 'AWS_SECRET_ACCESS_KEY', 
            usernameVariable: 'AWS_ACCESS_KEY_ID'
          )
        ]) {
          script {
            sh '''
              echo "=== Checking EC2 Key Pair ==="
              
              if ! aws ec2 describe-key-pairs \
                   --key-names "${KEY_NAME}" &>/dev/null; then
                echo "Creating key pair: ${KEY_NAME}"
                aws ec2 create-key-pair \
                  --key-name "${KEY_NAME}" \
                  --query 'KeyMaterial' --output text \
                  > /tmp/${KEY_NAME}.pem
                echo "Key saved to /tmp/${KEY_NAME}.pem"
              else
                echo "Using existing key: ${KEY_NAME}"
              fi
            '''
          }
        }
      }
    }
    
    stage('Build AMI') {
      steps {
        withCredentials([
          usernamePassword(
            credentialsId: 'Aws-cli', 
            passwordVariable: 'AWS_SECRET_ACCESS_KEY', 
            usernameVariable: 'AWS_ACCESS_KEY_ID'
          )
        ]) {
          script {
            sh '''
              echo "=== Building AMI with Terraform ==="
              
              # Load AWS resources
              source aws_resources.env
              
              # Create unique timestamp for resources
              TIMESTAMP=$(date +%s)
              echo "Using timestamp: $TIMESTAMP"
              
              # Initialize Terraform
              echo "Initializing Terraform..."
              if ! terraform init; then
                echo "Fixing module conflicts..."
                cd .terraform/modules/ami_builder/ami
                
                # Clean duplicate declarations
                sed -i '1,5d' main.tf
                sed -i '/^provider "aws"/,/^}/d' main.tf
                sed -i '/^output "ami_id"/,/^}/d' main.tf
                sed -i '/^resource "aws_ec2_instance_state"/,/^}/d' main.tf
                
                # Make security group unique
                sed -i "s/ami-builder-sg/ami-builder-sg-$TIMESTAMP/g" main.tf
                
                cd -
                terraform init -reconfigure
              fi
              
              # Apply Terraform configuration
              echo "Creating AMI..."
              terraform apply -target=module.ami_builder -auto-approve \
                -var="vpc_id=$VPC_ID" \
                -var="subnet_id=$SUBNET_ID" \
                -var="key_name=${KEY_NAME}"
              
              # Show result
              AMI_ID=$(terraform output ami_id)
              echo "=== AMI Created Successfully ==="
              echo "AMI ID: $AMI_ID"
            '''
          }
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
