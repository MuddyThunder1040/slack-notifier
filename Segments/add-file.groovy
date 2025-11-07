pipeline {
    agent any

    parameters {
        string(name: 'FILE_NAME', defaultValue: 'newfile.txt', description: 'Name of the file to add')
    }
    stages {
        stage('Add File') {
            steps {
                script {
                    def fileName = params.FILE_NAME
                    content = cat /proc/cpuinfo
                    writeFile file: fileName, text: content
                    echo "File '${fileName}' has been created."
                }
            }
        }
    }
    
       