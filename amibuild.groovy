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
                
                # Keep only resources, remove variables, provider, and output
                grep -v "^variable" main.tf.backup | grep -v "^provider" | grep -v "^output" > main.tf
                
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
            terraform apply -target=module.ami_builder -auto-approve
            
            # Show the AMI ID
            echo "AMI build completed. AMI ID:"
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
