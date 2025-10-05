pipeline{
    agent any
    stages{
        stage('restack'){
            steps{
                echo 'restack'
                sleep 10
                echo 'restack done'

            }
            post{
              success{
                 slackSend(
                     channel: '#jenkins',
                     color: 'good',
                     message: "✅ Restack stage completed successfully!\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_NUMBER}\nBranch: ${env.BRANCH_NAME ?: 'master'}",
                     teamDomain: 'weekend-warriors-hq',
                     tokenCredentialId: 'slack-connect'
                 )
              }
              failure{
                 slackSend(
                     channel: '#jenkins',
                     color: 'danger',
                     message: "❌ Restack stage failed!\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_NUMBER}\nBranch: ${env.BRANCH_NAME ?: 'master'}",
                     teamDomain: 'weekend-warriors-hq',
                     tokenCredentialId: 'slack-connect'
                 )
              }
            }
        }
    }
    post {
        success {
            slackSend(
                channel: '#jenkins',
                color: 'good',
                message: "🎉 Pipeline completed successfully!\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_NUMBER}\nBranch: ${env.BRANCH_NAME ?: 'master'}\nDuration: ${currentBuild.durationString}",
                teamDomain: 'weekend-warriors-hq',
                tokenCredentialId: 'slack-connect'
            )
        }
        failure {
            slackSend(
                channel: '#jenkins',
                color: 'danger',
                message: "💥 Pipeline failed!\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_NUMBER}\nBranch: ${env.BRANCH_NAME ?: 'master'}\nDuration: ${currentBuild.durationString}",
                teamDomain: 'weekend-warriors-hq',
                tokenCredentialId: 'slack-connect'
            )
        }
        always {
            echo 'Pipeline execution completed'
        }
    }
}