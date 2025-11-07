pipeline {
    agent any

    parameters {
        string(name: 'FILE_NAME', defaultValue: 'newfile.txt', description: 'Name of the file to manage')
        choice(name: 'ACTION', choices: ['ADD', 'REMOVE', 'STATUS'], description: 'Action to perform on the file')
    }
    
    stages {
        stage('Status Check') {
            when {
                anyOf {
                    expression { params.ACTION == 'ADD' }
                    expression { params.ACTION == 'REMOVE' }
                    expression { params.ACTION == 'STATUS' }
                }
            }
            steps {
                script {
                    echo "Running status check before ${params.ACTION} operation..."
                    build job: 'status-pipeline', parameters: [
                        string(name: 'FILE_NAME', value: params.FILE_NAME)
                    ]
                }
            }
        }
        
        stage('Add File') {
            when {
                expression { params.ACTION == 'ADD' }
            }
            steps {
                script {
                    echo "Adding file: ${params.FILE_NAME}"
                    build job: 'add-file-pipeline', parameters: [
                        string(name: 'FILE_NAME', value: params.FILE_NAME)
                    ]
                }
            }
        }
        
        stage('Remove File') {
            when {
                expression { params.ACTION == 'REMOVE' }
            }
            steps {
                script {
                    echo "Removing file: ${params.FILE_NAME}"
                    build job: 'remove-file-pipeline', parameters: [
                        string(name: 'FILE_NAME', value: params.FILE_NAME)
                    ]
                }
            }
        }
        
        stage('Final Status Check') {
            when {
                anyOf {
                    expression { params.ACTION == 'ADD' }
                    expression { params.ACTION == 'REMOVE' }
                }
            }
            steps {
                script {
                    echo "Running final status check after ${params.ACTION} operation..."
                    build job: 'status-pipeline', parameters: [
                        string(name: 'FILE_NAME', value: params.FILE_NAME)
                    ]
                }
            }
        }
    }
    
    post {
        always {
            echo "Pipeline completed for action: ${params.ACTION} on file: ${params.FILE_NAME}"
        }
        success {
            echo "Pipeline executed successfully!"
        }
        failure {
            echo "Pipeline failed. Check the logs for details."
        }
    }
}