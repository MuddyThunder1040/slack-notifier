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
        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'Aws-cli']]) {
          sh '''
            terraform init
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
