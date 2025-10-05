pipeline {
    agent any
    
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
            slackSend(
                channel: '#the-restack-notifier',
                color: 'good',
                message: "🎉 Pipeline completed successfully! Job: ${env.JOB_NAME}, Build: ${env.BUILD_NUMBER}"
            )
        }
        failure {
            slackSend(
                channel: '#the-restack-notifier',
                color: 'danger',
                message: "💥 Pipeline failed! Job: ${env.JOB_NAME}, Build: ${env.BUILD_NUMBER}"
            )
        }
        always {
            echo 'Pipeline execution completed'
        }
    }
}