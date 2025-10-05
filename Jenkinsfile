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
                 script {
                     try {
                         slackSend(
                             channel: '#the-restack-notifier',
                             color: 'good',
                             message: "Restack stage completed successfully! Build: ${env.BUILD_NUMBER}",
                             teamDomain: 'weekend-warriors-hq',
                             token: ''
                         )
                     } catch (Exception e) {
                         echo "Slack notification failed: ${e.getMessage()}"
                         // Try alternative method
                         slackSend channel: '#the-restack-notifier', 
                                   color: 'good', 
                                   message: "Restack stage completed successfully! Build: ${env.BUILD_NUMBER}"
                     }
                 }
              }
              failure{
                 script {
                     try {
                         slackSend(
                             channel: '#the-restack-notifier',
                             color: 'danger',
                             message: "Restack stage failed! Build: ${env.BUILD_NUMBER}",
                             teamDomain: 'weekend-warriors-hq',
                             token: ''
                         )
                     } catch (Exception e) {
                         echo "Slack notification failed: ${e.getMessage()}"
                     }
                 }
              }
            }
        }
    }
    post {
        success {
            script {
                echo 'Pipeline completed successfully!'
                try {
                    // Method 1: Try with credentials
                    withCredentials([string(credentialsId: 'slack-connect', variable: 'SLACK_TOKEN')]) {
                        slackSend(
                            channel: '#the-restack-notifier',
                            color: 'good',
                            message: "Pipeline completed successfully! Job: ${env.JOB_NAME}, Build: ${env.BUILD_NUMBER}",
                            teamDomain: 'weekend-warriors-hq',
                            token: SLACK_TOKEN
                        )
                    }
                } catch (Exception e1) {
                    echo "Method 1 failed: ${e1.getMessage()}"
                    try {
                        // Method 2: Try without explicit token
                        slackSend(
                            channel: '#the-restack-notifier',
                            color: 'good',
                            message: "Pipeline completed successfully! Build: ${env.BUILD_NUMBER}"
                        )
                    } catch (Exception e2) {
                        echo "Method 2 failed: ${e2.getMessage()}"
                        // Method 3: Try basic configuration
                        slackSend channel: '#the-restack-notifier', message: "Build ${env.BUILD_NUMBER} completed"
                    }
                }
            }
        }
        failure {
            echo 'Pipeline failed!'
        }
        always {
            echo 'Pipeline execution completed'
        }
    }
}