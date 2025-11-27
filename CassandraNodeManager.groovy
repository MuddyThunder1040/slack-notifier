pipeline {
    agent any
    
    parameters {
        choice(
            name: 'ACTION',
            choices: ['destroy-single', 'destroy-multiple', 'destroy-all', 'restart-single', 'restart-multiple', 'restart-all', 'start-single', 'start-multiple', 'stop-single', 'stop-multiple'],
            description: 'Select node management action'
        )
        choice(
            name: 'NODE_1',
            choices: ['skip', 'include'],
            description: 'Cassandra Node 1 (port 9042)'
        )
        choice(
            name: 'NODE_2',
            choices: ['skip', 'include'],
            description: 'Cassandra Node 2 (port 9043)'
        )
        choice(
            name: 'NODE_3',
            choices: ['skip', 'include'],
            description: 'Cassandra Node 3 (port 9044)'
        )
        choice(
            name: 'NODE_4',
            choices: ['skip', 'include'],
            description: 'Cassandra Node 4 (port 9045)'
        )
        booleanParam(
            name: 'CONFIRM',
            defaultValue: false,
            description: 'Confirm the action (required for destructive operations)'
        )
    }
    
    environment {
        REPO_URL = 'https://github.com/MuddyThunder1040/aws-topology.git'
        BRANCH = 'master'
    }
    
    stages {
        stage('Checkout') {
            steps {
                echo "Cloning repository: ${env.REPO_URL}"
                git branch: env.BRANCH, url: env.REPO_URL
            }
        }
        
        stage('Install Terraform') {
            steps {
                echo 'Checking Terraform installation...'
                sh '''
                    if ! command -v terraform &> /dev/null; then
                        echo "Terraform not found. Installing..."
                        mkdir -p ~/bin
                        cd /tmp
                        
                        if command -v curl &> /dev/null; then
                            curl -sL https://releases.hashicorp.com/terraform/1.6.6/terraform_1.6.6_linux_amd64.zip -o terraform.zip
                        else
                            echo "ERROR: curl not found"
                            exit 1
                        fi
                        
                        unzip -o terraform.zip
                        chmod +x terraform
                        mv terraform ~/bin/
                        rm -f terraform.zip
                        
                        export PATH=$PATH:~/bin
                        echo "Terraform installed successfully to ~/bin"
                    fi
                    
                    export PATH=$PATH:~/bin
                    terraform version
                '''
            }
        }
        
        stage('Setup Docker') {
            steps {
                echo 'Setting up Docker CLI...'
                sh '''
                    if ! command -v docker &> /dev/null; then
                        echo "Installing Docker CLI..."
                        mkdir -p ~/bin
                        curl -fsSL https://download.docker.com/linux/static/stable/x86_64/docker-27.4.0.tgz -o docker.tgz
                        tar xzvf docker.tgz --strip 1 -C ~/bin docker/docker
                        rm docker.tgz
                        chmod +x ~/bin/docker
                        export PATH=$PATH:~/bin
                        echo "Docker CLI installed: $(~/bin/docker --version)"
                    fi
                    
                    export PATH=$PATH:~/bin
                    if docker ps &> /dev/null; then
                        echo "✅ Docker is accessible!"
                        docker version --format 'Client: {{.Client.Version}} | Server: {{.Server.Version}}'
                    else
                        echo "ERROR: Cannot access Docker"
                        exit 1
                    fi
                '''
            }
        }
        
        stage('Validate Selection') {
            steps {
                script {
                    def selectedNodes = []
                    if (params.NODE_1 == 'include') selectedNodes.add('cassandra-node-1')
                    if (params.NODE_2 == 'include') selectedNodes.add('cassandra-node-2')
                    if (params.NODE_3 == 'include') selectedNodes.add('cassandra-node-3')
                    if (params.NODE_4 == 'include') selectedNodes.add('cassandra-node-4')
                    
                    if (selectedNodes.isEmpty() && !params.ACTION.contains('all')) {
                        error("No nodes selected! Please select at least one node or use an 'all' action.")
                    }
                    
                    def destructiveActions = ['destroy-single', 'destroy-multiple', 'destroy-all', 'stop-single', 'stop-multiple']
                    if (destructiveActions.contains(params.ACTION) && !params.CONFIRM) {
                        error("CONFIRM parameter must be checked for destructive actions!")
                    }
                    
                    echo "Action: ${params.ACTION}"
                    echo "Selected nodes: ${selectedNodes.join(', ')}"
                    env.SELECTED_NODES = selectedNodes.join(' ')
                }
            }
        }
        
        stage('Destroy Single Node') {
            when {
                expression { params.ACTION == 'destroy-single' }
            }
            steps {
                sh '''
                    export PATH=$PATH:~/bin
                    for node in ${SELECTED_NODES}; do
                        echo "🗑️  Destroying ${node}..."
                        docker stop ${node} || true
                        docker rm -f ${node} || true
                        echo "✅ ${node} destroyed"
                    done
                '''
            }
        }
        
        stage('Destroy Multiple Nodes') {
            when {
                expression { params.ACTION == 'destroy-multiple' }
            }
            steps {
                sh '''
                    export PATH=$PATH:~/bin
                    for node in ${SELECTED_NODES}; do
                        echo "🗑️  Destroying ${node}..."
                        docker stop ${node} || true
                        docker rm -f ${node} || true
                        echo "✅ ${node} destroyed"
                    done
                    echo "All selected nodes destroyed"
                '''
            }
        }
        
        stage('Destroy All Nodes') {
            when {
                expression { params.ACTION == 'destroy-all' }
            }
            steps {
                sh '''
                    export PATH=$PATH:~/bin
                    echo "🗑️  Destroying ALL Cassandra nodes..."
                    for i in 1 2 3 4; do
                        node="cassandra-node-${i}"
                        echo "Destroying ${node}..."
                        docker stop ${node} || true
                        docker rm -f ${node} || true
                    done
                    
                    echo "Removing network and volumes..."
                    docker network rm cassandra-network || true
                    docker volume rm cassandra-data-1 cassandra-data-2 cassandra-data-3 cassandra-data-4 || true
                    
                    echo "✅ All nodes, network, and volumes destroyed"
                '''
            }
        }
        
        stage('Stop Nodes') {
            when {
                expression { params.ACTION == 'stop-single' || params.ACTION == 'stop-multiple' }
            }
            steps {
                sh '''
                    export PATH=$PATH:~/bin
                    for node in ${SELECTED_NODES}; do
                        echo "⏸️  Stopping ${node}..."
                        docker stop ${node}
                        echo "✅ ${node} stopped"
                    done
                '''
            }
        }
        
        stage('Start Nodes') {
            when {
                expression { params.ACTION == 'start-single' || params.ACTION == 'start-multiple' }
            }
            steps {
                sh '''
                    export PATH=$PATH:~/bin
                    for node in ${SELECTED_NODES}; do
                        echo "▶️  Starting ${node}..."
                        docker start ${node}
                        echo "✅ ${node} started"
                    done
                '''
            }
        }
        
        stage('Restart Single Node') {
            when {
                expression { params.ACTION == 'restart-single' }
            }
            steps {
                sh '''
                    export PATH=$PATH:~/bin
                    for node in ${SELECTED_NODES}; do
                        echo "🔄 Restarting ${node}..."
                        docker restart ${node}
                        echo "✅ ${node} restarted"
                    done
                '''
            }
        }
        
        stage('Restart Multiple Nodes') {
            when {
                expression { params.ACTION == 'restart-multiple' }
            }
            steps {
                sh '''
                    export PATH=$PATH:~/bin
                    for node in ${SELECTED_NODES}; do
                        echo "🔄 Restarting ${node}..."
                        docker restart ${node}
                        sleep 10
                        echo "✅ ${node} restarted"
                    done
                '''
            }
        }
        
        stage('Restart All Nodes') {
            when {
                expression { params.ACTION == 'restart-all' }
            }
            steps {
                sh '''
                    export PATH=$PATH:~/bin
                    echo "🔄 Restarting ALL Cassandra nodes..."
                    for i in 1 2 3 4; do
                        node="cassandra-node-${i}"
                        echo "Restarting ${node}..."
                        docker restart ${node}
                        sleep 10
                    done
                    echo "✅ All nodes restarted"
                '''
            }
        }
        
        stage('Verify Status') {
            steps {
                sh '''
                    export PATH=$PATH:~/bin
                    echo "📊 Current Cassandra node status:"
                    echo "================================"
                    docker ps -a --filter "name=cassandra-node" --format "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}"
                    
                    # If nodes are running, check cluster status
                    if docker ps --filter "name=cassandra-node-1" --format "{{.Names}}" | grep -q cassandra-node-1; then
                        echo ""
                        echo "🔍 Checking cluster status..."
                        sleep 5
                        docker exec cassandra-node-1 nodetool status || echo "Cluster not ready yet"
                    fi
                '''
            }
        }
    }
    
    post {
        success {
            echo "${params.ACTION} operation completed successfully"
            script {
                try {
                    slackSend(
                        channel: '#the-restack-notifier',
                        color: 'good',
                        message: "✅ Cassandra Node Manager: ${params.ACTION} completed successfully\nNodes: ${env.SELECTED_NODES ?: 'ALL'}",
                        tokenCredentialId: 'slack-token',
                        botUser: true
                    )
                } catch (Exception e) {
                    echo "Slack notification failed: ${e.message}"
                }
            }
        }
        failure {
            echo "${params.ACTION} operation failed"
            script {
                try {
                    slackSend(
                        channel: '#the-restack-notifier',
                        color: 'danger',
                        message: "❌ Cassandra Node Manager: ${params.ACTION} failed\nNodes: ${env.SELECTED_NODES ?: 'ALL'}",
                        tokenCredentialId: 'slack-token',
                        botUser: true
                    )
                } catch (Exception e) {
                    echo "Slack notification failed: ${e.message}"
                }
            }
        }
    }
}
