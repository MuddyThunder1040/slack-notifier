pipelin  parameters {
    string(name: 'BRANCH', defaultValue: 'master', description: 'Branch to build from')
    string(name: 'KEY_NAME', defaultValue: 'my-key-pair', description: 'EC2 Key Pair name for SSH access')
    booleanParam(name: 'AUTO_DETECT_VPC', defaultValue: true, description: 'Auto-detect default VPC and subnet')
    string(name: 'VPC_ID', defaultValue: '', description: 'VPC ID (leave empty for auto-detect)')
    string(name: 'SUBNET_ID', defaultValue: '', description: 'Subnet ID (leave empty for auto-detect)')
  }agent any

  environment {
    AWS_REGION = 'us-east-1'
  }

  parameters {
    string(name: 'BRANCH', defaultValue: 'master', description: 'Branch of aws-topology repo')
    string(name: 'VPC_ID', defaultValue: 'vpc-12345678', description: 'VPC ID for AMI builder instance')
    string(name: 'SUBNET_ID', defaultValue: 'subnet-12345678', description: 'Subnet ID for AMI builder instance')
    string(name: 'KEY_NAME', defaultValue: 'my-key-pair', description: 'EC2 Key Pair name for SSH access')
  }

  stages {
    stage('Setup') {
      steps {
        cleanWs()
        script {
          def branchName = params.BRANCH ?: 'master'
          echo "Cloning aws-topology repository, branch: ${branchName}"
          checkout([
            $class: 'GitSCM',
            branches: [[name: "*/${branchName}"]],
            userRemoteConfigs: [[
              url: 'https://github.com/MuddyThunder1040/aws-topology.git'
            ]]
          ])
        }
      }
    }

    stage('Build AMI') {
      steps {
        withCredentials([
          usernamePassword(credentialsId: 'Aws-cli', passwordVariable: 'AWS_SECRET_ACCESS_KEY', usernameVariable: 'AWS_ACCESS_KEY_ID')
        ]) {
          sh """
            export AWS_ACCESS_KEY_ID=\$AWS_ACCESS_KEY_ID
            export AWS_SECRET_ACCESS_KEY=\$AWS_SECRET_ACCESS_KEY
            
            # Set parameter variables for shell use
            export KEY_NAME="${params.KEY_NAME}"
            
            # Auto-detect VPC and subnet if not provided
            if [ "${params.AUTO_DETECT_VPC}" = "true" ] || [ -z "${params.VPC_ID}" ]; then
              echo "Auto-detecting default VPC and subnet..."
              export VPC_ID=\$(aws ec2 describe-vpcs --filters "Name=is-default,Values=true" --query "Vpcs[0].VpcId" --output text)
              if [ "\$VPC_ID" = "None" ] || [ -z "\$VPC_ID" ]; then
                echo "No default VPC found, using first available VPC..."
                export VPC_ID=\$(aws ec2 describe-vpcs --query "Vpcs[0].VpcId" --output text)
              fi
              export SUBNET_ID=\$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=\$VPC_ID" --query "Subnets[0].SubnetId" --output text)
              echo "Auto-detected VPC_ID=\$VPC_ID, SUBNET_ID=\$SUBNET_ID"
            else
              export VPC_ID="${params.VPC_ID}"
              export SUBNET_ID="${params.SUBNET_ID}"
              echo "Using provided VPC_ID=\$VPC_ID, SUBNET_ID=\$SUBNET_ID"
            fi
            
            # Validate that we have valid VPC and subnet
            if [ "\$VPC_ID" = "None" ] || [ -z "\$VPC_ID" ]; then
              echo "ERROR: Could not determine VPC_ID"
              exit 1
            fi
            if [ "\$SUBNET_ID" = "None" ] || [ -z "\$SUBNET_ID" ]; then
              echo "ERROR: Could not determine SUBNET_ID"
              exit 1
            fi
            
            echo "Using parameters: VPC_ID=\$VPC_ID, SUBNET_ID=\$SUBNET_ID, KEY_NAME=\$KEY_NAME"
            
            # Try to initialize terraform (may fail due to module issues)
            echo "Attempting initial terraform init..."
            terraform init || {
              echo "Initial init failed as expected due to module duplicates"
              
              # Check if module was downloaded despite the error
              if [ -d .terraform/modules/ami_builder/ami ]; then
                echo "Module was downloaded, proceeding with fixes..."
                
                # Fix duplicate declarations in the downloaded module
                echo "Fixing module duplicates..."
                cd .terraform/modules/ami_builder/ami
                
                # Create a backup and then clean main.tf
                cp main.tf main.tf.backup
                
                # Remove duplicate declarations with precise sed commands
                sed -i '1,5d' main.tf  # Remove first 5 lines (variable declarations)
                sed -i '/^provider "aws"/,/^}/d' main.tf  # Remove provider block
                sed -i '/^output "ami_id"/,/^}/d' main.tf  # Remove output block
                sed -i '/^resource "aws_ec2_instance_state" "terminate_builder"/,/^}/d' main.tf  # Remove problematic terminate resource
                
                # Verify the file looks correct
                echo "First few lines of cleaned main.tf:"
                head -10 main.tf
                
                echo "Module main.tf cleaned"
                cd - > /dev/null
                
                # Re-initialize after fixing
                echo "Re-initializing terraform..."
                terraform init -reconfigure
              else
                echo "Module not downloaded, cannot proceed"
                exit 1
              fi
            }
            
            # Apply terraform to build AMI
            echo "Building AMI..."
            terraform apply -target=module.ami_builder -auto-approve \
              -var="vpc_id=\$VPC_ID" \
              -var="subnet_id=\$SUBNET_ID" \
              -var="key_name=\$KEY_NAME"
            
            # Show the AMI ID
            echo "AMI build completed. AMI ID:"
            terraform output ami_id
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
