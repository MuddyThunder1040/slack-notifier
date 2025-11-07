pipeline {
    agent any

    parameters {
        string(name: 'FILE_NAME', defaultValue: 'newfile.txt', description: 'File name')
        choice(name: 'ACTION', choices: ['ADD', 'REMOVE', 'STATUS'], description: 'Action')
    }
    
    stages {
        stage('Status Check') {
            steps {
                script {
                    echo "Running status check..."
                    build job: 'status', parameters: [
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
                    build job: 'add-file', parameters: [
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
                    build job: 'remove-file', parameters: [
                        string(name: 'FILE_NAME', value: params.FILE_NAME)
                    ]
                }
            }
        }
        
        stage('Final Status') {
            when {
                not { expression { params.ACTION == 'STATUS' } }
            }
            steps {
                script {
                    echo "Running final status check..."
                    build job: 'status', parameters: [
                        string(name: 'FILE_NAME', value: params.FILE_NAME)
                    ]
                }
            }
        }
    }
}