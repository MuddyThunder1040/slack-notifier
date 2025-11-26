pipeline {
    agent any
    
    environment {
        // Set to 'true' to enable Slack notifications (requires configuration)
        ENABLE_SLACK = 'true'
        SLACK_CHANNEL = '#the-restack-notifier'
        SLACK_CREDENTIAL_ID = 'slack-token'
    }
    
    stages {
        stage('restack') {
            steps {
                echo 'restack'
                sleep 10
                echo 'restack done'
            }
        }
    }
    
    post {
        success {
            script {
                if (env.ENABLE_SLACK == 'true') {
                    try {
                        slackSend(
                            channel: env.SLACK_CHANNEL,
                            color: 'good',
                            message: "🎉 Pipeline completed successfully! Job: ${env.JOB_NAME}, Build: ${env.BUILD_NUMBER}",
                            tokenCredentialId: env.SLACK_CREDENTIAL_ID ?: ''
                        )
                    } catch (Exception e) {
                        echo "⚠️ Slack notification failed: ${e.message}"
                    }
                } else {
                    echo "✅ Build successful! (Slack notifications disabled)"
                }
            }
        }
        failure {
            script {
                if (env.ENABLE_SLACK == 'true') {
                    try {
                        slackSend(
                            channel: env.SLACK_CHANNEL,
                            color: 'danger',
                            message: "💥 Pipeline failed! Job: ${env.JOB_NAME}, Build: ${env.BUILD_NUMBER}",
                            tokenCredentialId: env.SLACK_CREDENTIAL_ID ?: ''
                        )
                    } catch (Exception e) {
                        echo "⚠️ Slack notification failed: ${e.message}"
                    }
                } else {
                    echo "❌ Build failed! (Slack notifications disabled)"
                }
            }
        }
        always {
            echo 'Pipeline execution completed'
        }
    }
}