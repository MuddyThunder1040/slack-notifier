pipeline {
  agent any

  environment {
    AWS_REGION = 'us-east-1'
  }

  parameters {
    string(name: 'BRANCH', defaultValue: 'master', description: 'Branch of aws-topology repo')
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
          sh '''
            export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID
            export AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY
            
            # Initialize terraform first to download modules
            terraform init
            
            # Fix duplicate declarations in the downloaded module
            if [ -f .terraform/modules/ami_builder/ami/main.tf ]; then
              echo "Fixing module duplicates..."
              # Remove variable declarations from main.tf (keep only resources)
              sed -i '/^variable "region"/,/^}/d' .terraform/modules/ami_builder/ami/main.tf
              sed -i '/^variable "vpc_id"/,/^}/d' .terraform/modules/ami_builder/ami/main.tf
              sed -i '/^variable "subnet_id"/,/^}/d' .terraform/modules/ami_builder/ami/main.tf
              sed -i '/^variable "key_name"/,/^}/d' .terraform/modules/ami_builder/ami/main.tf
              sed -i '/^variable "app_docker_image"/,/^}/d' .terraform/modules/ami_builder/ami/main.tf
              sed -i '/^provider "aws"/,/^}/d' .terraform/modules/ami_builder/ami/main.tf
              sed -i '/^output "ami_id"/,/^}/d' .terraform/modules/ami_builder/ami/main.tf
              echo "Module fixed successfully"
            fi
            
            # Re-initialize after fixing
            terraform init -reconfigure
            terraform apply -target=module.ami_builder -auto-approve
            terraform output ami_id
          '''
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
