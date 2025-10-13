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
        git branch: "${params.BRANCH}", url: 'https://github.com/MuddyThunder1040/aws-topology.git'
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
