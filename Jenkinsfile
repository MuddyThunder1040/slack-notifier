pipeline {
    agent any
    
    environment {
        ENABLE_SLACK = 'true'
        SLACK_CHANNEL = '#the-restack-notifier'
        SLACK_CREDENTIAL_ID = 'slack-token'
    }
    
    stages {
        stage('Build') {
            steps {
                echo 'Building...'
                echo 'Build completed'
            }
        }
    }
    
    post {
        success {
            script {
                if (env.ENABLE_SLACK == 'true') {
                    slackSend(
                        botUser: true,
                        channel: env.SLACK_CHANNEL,
                        color: 'good',
                        message: "🎉 Pipeline completed successfully!\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_NUMBER}\nURL: ${env.BUILD_URL}",
                        tokenCredentialId: env.SLACK_CREDENTIAL_ID,
                        failOnError: false
                    )
                }
            }
        }
        failure {
            script {
                if (env.ENABLE_SLACK == 'true') {
                    slackSend(
                        botUser: true,
                        channel: env.SLACK_CHANNEL,
                        color: 'danger',
                        message: "💥 Pipeline failed!\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_NUMBER}\nURL: ${env.BUILD_URL}",
                        tokenCredentialId: env.SLACK_CREDENTIAL_ID,
                        failOnError: false
                    )
                }
            }
        }
        always {
            echo 'Pipeline execution completed'
        }
    }
}