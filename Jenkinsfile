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
                 slackSend channel: '#jenkins', color: 'good', message: "restack stage successful"
              }

            }
        }
    }
}