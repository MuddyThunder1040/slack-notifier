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
            # Set and validate parameters with fallback
            KEY_NAME="${params.KEY_NAME ?: 'my-key-pair'}"
            echo "Using KEY_NAME: \$KEY_NAME"
            
            if [ -z "\$KEY_NAME" ]; then
              echo "Using fallback KEY_NAME: my-key-pair"
              KEY_NAME="my-key-pair"
            fi
            
            # Auto-detect VPC and subnet
            echo "Auto-detecting AWS resources..."
            VPC_ID=\$(aws ec2 describe-vpcs --filters "Name=is-default,Values=true" --query "Vpcs[0].VpcId" --output text)
            [ "\$VPC_ID" = "None" ] && VPC_ID=\$(aws ec2 describe-vpcs --query "Vpcs[0].VpcId" --output text)
            SUBNET_ID=\$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=\$VPC_ID" --query "Subnets[0].SubnetId" --output text)
            
            # Check if key pair exists, create if not
            echo "Checking if key pair exists: \$KEY_NAME"
            if aws ec2 describe-key-pairs --key-names "\$KEY_NAME" &>/dev/null; then
              echo "Using existing key pair: \$KEY_NAME"
            else
              echo "Creating key pair: \$KEY_NAME"
              aws ec2 create-key-pair --key-name "\$KEY_NAME" --query 'KeyMaterial' --output text > /tmp/\$KEY_NAME.pem
              echo "Key pair created and saved to /tmp/\$KEY_NAME.pem"
            fi
            
            echo "Using VPC: \$VPC_ID, Subnet: \$SUBNET_ID, Key: \$KEY_NAME"
            
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
            terraform apply -target=module.ami_builder -auto-approve \\
              -var="vpc_id=\$VPC_ID" \\
              -var="subnet_id=\$SUBNET_ID" \\
              -var="key_name=\$KEY_NAME"
            
            # Get and display AMI ID
            AMI_ID=\$(terraform output ami_id | tr -d '"')
            echo "========================================="
            echo "AMI BUILD COMPLETED SUCCESSFULLY!"
            echo "AMI ID: \$AMI_ID"
            echo "========================================="
            
            # Save AMI ID to file for downstream jobs
            echo "\$AMI_ID" > ami_id.txt
            
            # Save AMI ID for build display
            echo "AMI_ID=\$AMI_ID" > build_info.properties
          """
        }
      }
    }
    
    stage('Update Build Info') {
      steps {
        script {
          // Read the AMI ID from the properties file
          def buildInfo = readProperties file: 'build_info.properties'
          env.AMI_ID = buildInfo.AMI_ID
          
          // Set build display name and description
          currentBuild.displayName = "#${BUILD_NUMBER} - AMI: ${env.AMI_ID}"
          currentBuild.description = """
            <b>AMI ID:</b> ${env.AMI_ID}<br/>
            <b>Key Pair:</b> ${params.KEY_NAME ?: 'my-key-pair'}<br/>
            <b>Region:</b> ${env.AWS_REGION}<br/>
            <b>Status:</b> <span style="color: green;">✅ Success</span>
          """
          
          // Add a badge for the AMI ID
          try {
            addShortText(text: env.AMI_ID, color: "white", background: "green")
          } catch (Exception e) {
            echo "Badge plugin not available, skipping badge creation"
          }
          
          echo "Build updated with AMI ID: ${env.AMI_ID}"
        }
      }
    }
  }
  
  post {
    always {
      // Archive the AMI ID for use in deployment pipeline
      archiveArtifacts artifacts: 'ami_id.txt,build_info.properties', allowEmptyArchive: true
      cleanWs()
    }
    success {
      script {
        // Ensure AMI ID is visible in build sidebar even if Update Build Info stage didn't run
        if (env.AMI_ID) {
          echo "✅ AMI build completed successfully!"
          echo "🎯 AMI ID: ${env.AMI_ID}"
          echo "📋 Check the build sidebar for AMI details"
        }
      }
    }
    failure {
      script {
        currentBuild.displayName = "#${BUILD_NUMBER} - ❌ FAILED"
        currentBuild.description = """
          <b>Status:</b> <span style="color: red;">❌ Failed</span><br/>
          <b>Key Pair:</b> ${params.KEY_NAME ?: 'my-key-pair'}<br/>
          <b>Region:</b> ${env.AWS_REGION}
        """
        echo "❌ AMI build failed. Check the logs for errors."
      }
    }
  }
}
