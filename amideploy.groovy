pipeline {
  agent any
  
  environment {
    AWS_REGION = 'us-east-1'
  }
  
  parameters {
    string(name: 'AMI_ID', defaultValue: '', description: 'AMI ID to deploy (leave empty to use latest from build)')
    choice(name: 'INSTANCE_TYPE', choices: ['t3.micro', 't3.small', 't3.medium', 't3.large'], description: 'EC2 instance type')
    string(name: 'KEY_NAME', defaultValue: 'my-key-pair', description: 'EC2 Key Pair name')
    string(name: 'INSTANCE_NAME', defaultValue: 'appserver-deployment', description: 'Name tag for the instance')
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
    
    stage('Get AMI ID') {
      steps {
        script {
          if (params.AMI_ID.trim()) {
            env.DEPLOY_AMI_ID = params.AMI_ID.trim()
            echo "Using provided AMI ID: ${env.DEPLOY_AMI_ID}"
          } else {
            echo "No AMI ID provided, attempting to get latest from build artifacts..."
            try {
              // Try to copy artifacts from the ami-build job
              copyArtifacts(
                projectName: 'ami-pipeline',
                selector: lastSuccessful(),
                filter: 'ami_id.txt'
              )
              env.DEPLOY_AMI_ID = readFile('ami_id.txt').trim()
              echo "Retrieved AMI ID from build artifacts: ${env.DEPLOY_AMI_ID}"
            } catch (Exception e) {
              error("Could not retrieve AMI ID from build artifacts. Please provide AMI_ID parameter or run ami-build pipeline first.")
            }
          }
        }
      }
    }
    
    stage('Validate AMI') {
      steps {
        withCredentials([
          usernamePassword(credentialsId: 'Aws-cli', passwordVariable: 'AWS_SECRET_ACCESS_KEY', usernameVariable: 'AWS_ACCESS_KEY_ID')
        ]) {
          sh """
            echo "Validating AMI: ${env.DEPLOY_AMI_ID}"
            
            # Check if AMI exists and is available
            AMI_STATE=\$(aws ec2 describe-images --image-ids ${env.DEPLOY_AMI_ID} --query 'Images[0].State' --output text 2>/dev/null || echo "not-found")
            
            if [ "\$AMI_STATE" = "not-found" ]; then
              echo "ERROR: AMI ${env.DEPLOY_AMI_ID} not found!"
              exit 1
            elif [ "\$AMI_STATE" != "available" ]; then
              echo "ERROR: AMI ${env.DEPLOY_AMI_ID} is not available (state: \$AMI_STATE)"
              exit 1
            fi
            
            echo "AMI ${env.DEPLOY_AMI_ID} is available for deployment"
          """
        }
      }
    }
    
    stage('Deploy to AWS') {
      steps {
        withCredentials([
          usernamePassword(credentialsId: 'Aws-cli', passwordVariable: 'AWS_SECRET_ACCESS_KEY', usernameVariable: 'AWS_ACCESS_KEY_ID')
        ]) {
          sh """
            echo "========================================="
            echo "DEPLOYING AMI TO AWS"
            echo "AMI ID: ${env.DEPLOY_AMI_ID}"
            echo "Instance Type: ${params.INSTANCE_TYPE}"
            echo "Key Pair: ${params.KEY_NAME}"
            echo "========================================="
            
            # Auto-detect VPC and subnet
            echo "Auto-detecting AWS resources..."
            VPC_ID=\$(aws ec2 describe-vpcs --filters "Name=is-default,Values=true" --query "Vpcs[0].VpcId" --output text)
            [ "\$VPC_ID" = "None" ] && VPC_ID=\$(aws ec2 describe-vpcs --query "Vpcs[0].VpcId" --output text)
            SUBNET_ID=\$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=\$VPC_ID" --query "Subnets[0].SubnetId" --output text)
            
            echo "Using VPC: \$VPC_ID, Subnet: \$SUBNET_ID"
            
            # Create security group for the deployment
            TIMESTAMP=\$(date +%s)
            SG_NAME="appserver-sg-\$TIMESTAMP"
            
            echo "Creating security group: \$SG_NAME"
            SG_ID=\$(aws ec2 create-security-group \\
              --group-name "\$SG_NAME" \\
              --description "Security group for appserver deployment" \\
              --vpc-id "\$VPC_ID" \\
              --query 'GroupId' --output text)
            
            # Add security group rules
            echo "Adding security group rules..."
            aws ec2 authorize-security-group-ingress \\
              --group-id "\$SG_ID" \\
              --protocol tcp --port 22 --cidr 0.0.0.0/0
            
            aws ec2 authorize-security-group-ingress \\
              --group-id "\$SG_ID" \\
              --protocol tcp --port 80 --cidr 0.0.0.0/0
            
            aws ec2 authorize-security-group-ingress \\
              --group-id "\$SG_ID" \\
              --protocol tcp --port 443 --cidr 0.0.0.0/0
            
            aws ec2 authorize-security-group-ingress \\
              --group-id "\$SG_ID" \\
              --protocol tcp --port 3000 --cidr 0.0.0.0/0
            
            # Launch EC2 instance
            echo "Launching EC2 instance..."
            INSTANCE_ID=\$(aws ec2 run-instances \\
              --image-id "${env.DEPLOY_AMI_ID}" \\
              --instance-type "${params.INSTANCE_TYPE}" \\
              --key-name "${params.KEY_NAME}" \\
              --security-group-ids "\$SG_ID" \\
              --subnet-id "\$SUBNET_ID" \\
              --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${params.INSTANCE_NAME}}]" \\
              --query 'Instances[0].InstanceId' --output text)
            
            echo "Instance launched with ID: \$INSTANCE_ID"
            
            # Wait for instance to be running
            echo "Waiting for instance to be running..."
            aws ec2 wait instance-running --instance-ids "\$INSTANCE_ID"
            
            # Get instance details
            INSTANCE_INFO=\$(aws ec2 describe-instances --instance-ids "\$INSTANCE_ID" --query 'Reservations[0].Instances[0]')
            PUBLIC_IP=\$(echo "\$INSTANCE_INFO" | jq -r '.PublicIpAddress // "N/A"')
            PRIVATE_IP=\$(echo "\$INSTANCE_INFO" | jq -r '.PrivateIpAddress')
            
            echo "========================================="
            echo "DEPLOYMENT COMPLETED SUCCESSFULLY!"
            echo "Instance ID: \$INSTANCE_ID"
            echo "Public IP: \$PUBLIC_IP"
            echo "Private IP: \$PRIVATE_IP"
            echo "Security Group: \$SG_ID"
            echo "========================================="
            echo "Your application should be accessible at:"
            echo "http://\$PUBLIC_IP:3000"
            echo "========================================="
            
            # Save deployment info
            cat > deployment_info.txt << EOF
INSTANCE_ID=\$INSTANCE_ID
PUBLIC_IP=\$PUBLIC_IP
PRIVATE_IP=\$PRIVATE_IP
SECURITY_GROUP_ID=\$SG_ID
AMI_ID=${env.DEPLOY_AMI_ID}
INSTANCE_TYPE=${params.INSTANCE_TYPE}
EOF
          """
        }
      }
    }
  }
  
  post {
    always {
      archiveArtifacts artifacts: 'deployment_info.txt', allowEmptyArchive: true
      cleanWs()
    }
    success {
      echo "Deployment completed successfully! Check the console output for connection details."
    }
    failure {
      echo "Deployment failed. Check the logs for errors."
    }
  }
}
