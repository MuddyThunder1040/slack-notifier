pipeline {
    agent any
    
    environment {
        // Set to 'true' to enable Slack notifications (requires configuration)
        ENABLE_SLACK = 'true'
        SLACK_CHANNEL = '#the-restack-notifier'
        SLACK_CREDENTIAL_ID = 'slack-token'
        // Optional: Slack workspace URL
        // SLACK_BASE_URL = 'https://hooks.slack.com/services/'
    }
    
    stages {
        stage('restack') {
            steps {
                echo 'restack'
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
                    
                    // Try sending to the channel with botUser enabled
                    def slackResponse = slackSend(
                        botUser: true,
                        channel: env.SLACK_CHANNEL,
                        color: 'good',
                        message: "🎉 Pipeline completed successfully!\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_NUMBER}\nURL: ${env.BUILD_URL}",
                        tokenCredentialId: env.SLACK_CREDENTIAL_ID,
                        failOnError: false
                    )
                    
                    if (slackResponse != null) {
                        echo "✅ Slack notification sent!"
                        echo "Thread ID: ${slackResponse.threadId}"
                        echo "Timestamp: ${slackResponse.ts}"
                    } else {
                        echo "⚠️ Slack response was null"
                        echo ""
                        echo "Troubleshooting steps:"
                        echo "1. Verify the token in Jenkins credential 'slack-token' is the Bot User OAuth Token (xoxb-...)"
                        echo "2. Go to https://api.slack.com/apps → Your App → OAuth & Permissions"
                        echo "3. Check Bot Token Scopes include: chat:write, chat:write.public"
                        echo "4. After adding scopes, reinstall app and update token in Jenkins"
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
                            botUser: true,
                            channel: env.SLACK_CHANNEL,
                            color: 'danger',
                            message: "💥 Pipeline failed! Job: ${env.JOB_NAME}, Build: ${env.BUILD_NUMBER}",
                            tokenCredentialId: env.SLACK_CREDENTIAL_ID ?: '',
                            failOnError: false
                        )
                        echo "✅ Slack failure notification sent!"
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