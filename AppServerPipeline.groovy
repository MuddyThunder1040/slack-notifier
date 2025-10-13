// Alternative pipeline file for appserver - can be used if you want a separate pipeline job
// This file can be used to create a separate Jenkins job for the appserver

pipeline {
    agent any
    
    parameters {
        booleanParam(name: 'FORCE_PUSH', defaultValue: false, description: 'Force push Docker image to registry')
        choice(name: 'BRANCH', choices: ['main', 'master', 'develop'], description: 'Branch to build')
    }
    
    environment {
        DOCKER_REGISTRY = 'https://index.docker.io/v1/'
        DOCKER_IMAGE_NAME = 'muddythunder1040/appserver'
        DOCKER_TAG = "${env.BUILD_NUMBER}"
        GITHUB_REPO = 'https://github.com/MuddyThunder1040/appserver.git'
        SLACK_CHANNEL = '#the-restack-notifier'
        DOCKER_CREDENTIALS_ID = 'c71d37ab-7559-4e0e-a3ea-fcf0877f74e'
    }
    
    stages {
        stage('Trigger AppServer Build') {
            steps {
                echo 'Triggering AppServer Docker build pipeline...'
                
                // Call the appserver pipeline
                build job: 'appserver-docker-build', 
                      parameters: [
                          string(name: 'BRANCH', value: params.BRANCH),
                          booleanParam(name: 'FORCE_PUSH', value: params.FORCE_PUSH)
                      ],
                      wait: true,
                      propagate: true
            }
        }
    }
    
    post {
        success {
            slackSend(
                channel: "${SLACK_CHANNEL}",
                color: 'good',
                message: "🚀 AppServer deployment pipeline completed successfully! \n" +
                        "Job: ${env.JOB_NAME}\n" +
                        "Build: ${env.BUILD_NUMBER}\n" +
                        "Branch: ${params.BRANCH}"
            )
        }
        failure {
            slackSend(
                channel: "${SLACK_CHANNEL}",
                color: 'danger',
                message: "❌ AppServer deployment pipeline failed! \n" +
                        "Job: ${env.JOB_NAME}\n" +
                        "Build: ${env.BUILD_NUMBER}\n" +
                        "Branch: ${params.BRANCH}"
            )
        }
    }
}