pipeline {
    agent any
    
    environment {
        // Set to 'true' to enable Slack notifications (requires configuration)
        ENABLE_SLACK = 'false'
        SLACK_CHANNEL = '#the-restack-notifier'
        SLACK_CREDENTIAL_ID = 'slack-token'
        // Optional: Slack workspace URL
        // SLACK_BASE_URL = 'https://hooks.slack.com/services/'
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
                    echo "Attempting to send Slack notification..."
                    echo "Channel: ${env.SLACK_CHANNEL}"
                    echo "Credential ID: ${env.SLACK_CREDENTIAL_ID}"
                    
                    def slackResponse = slackSend(
                        channel: env.SLACK_CHANNEL,
                        color: 'good',
                        message: "🎉 Pipeline completed successfully!\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_NUMBER}\nURL: ${env.BUILD_URL}",
                        tokenCredentialId: env.SLACK_CREDENTIAL_ID
                    )
                    
                    if (slackResponse != null && slackResponse.successful) {
                        echo "✅ Slack notification sent successfully!"
                        echo "Response: ${slackResponse}"
                    } else {
                        echo "⚠️ Slack notification may have failed"
                        echo "Response: ${slackResponse}"
                        echo ""
                        echo "Please check:"
                        echo "1. Jenkins credential 'slack-token' contains valid Bot User OAuth Token (xoxb-...)"
                        echo "2. Bot is invited to channel '#the-restack-notifier'"
                        echo "3. Bot has chat:write permission"
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
                        echo "✅ Slack notification sent successfully!"
                    } catch (Exception e) {
                        echo "⚠️ Slack notification failed!"
                        echo "Error: ${e.class.name}: ${e.message}"
                        echo "Stack trace:"
                        e.printStackTrace()
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