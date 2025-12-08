pipeline {
    agent any
    
    parameters {
        choice(
            name: 'ACTION',
            choices: ['destroy-all', 'restart-all', 'start-all', 'stop-all', 'status'],
            description: 'Select node management action'
        )
        choice(
            name: 'NUM_NODES',
            choices: ['1', '2', '3', '4'],
            description: 'Number of Cassandra nodes in cluster'
        )
        string(
            name: 'NODES_TO_MANAGE',
            defaultValue: '',
            description: 'Comma-separated node numbers to manage (e.g., "1,3,4"). Leave empty for all nodes.'
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
                        
                        export PATH="$PATH:~/bin"
                        echo "Terraform installed successfully to ~/bin"
                    fi
                    
                    export PATH="$PATH:~/bin"
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
                        export PATH="$PATH:~/bin"
                        echo "Docker CLI installed: $(~/bin/docker --version)"
                    fi
                    
                    export PATH="$PATH:~/bin"
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
                    def numNodes = params.NUM_NODES.toInteger()
                    def selectedNodes = []
                    
                    // Parse nodes to manage
                    if (params.NODES_TO_MANAGE?.trim()) {
                        def nodeNumbers = params.NODES_TO_MANAGE.split(',').collect { it.trim().toInteger() }
                        nodeNumbers.each { nodeNum ->
                            if (nodeNum >= 1 && nodeNum <= numNodes) {
                                selectedNodes.add("cassandra-node-${nodeNum}")
                            } else {
                                error("Invalid node number: ${nodeNum}. Must be between 1 and ${numNodes}")
                            }
                        }
                    } else {
                        // If no specific nodes selected, manage all nodes
                        for (int i = 1; i <= numNodes; i++) {
                            selectedNodes.add("cassandra-node-${i}")
                        }
                    }
                    
                    def destructiveActions = ['destroy-all', 'stop-all']
                    if (destructiveActions.contains(params.ACTION) && !params.CONFIRM) {
                        error("CONFIRM parameter must be checked for destructive actions!")
                    }
                    
                    echo "Action: ${params.ACTION}"
                    echo "Number of nodes in cluster: ${numNodes}"
                    echo "Managing nodes: ${selectedNodes.join(', ')}"
                    env.SELECTED_NODES = selectedNodes.join(' ')
                    env.NUM_NODES = numNodes.toString()
                }
            }
        }
        
        stage('Destroy All Nodes') {
            when {
                expression { params.ACTION == 'destroy-all' }
            }
            steps {
                sh '''
                    export PATH="$PATH:~/bin"
                    echo "🗑️  Destroying selected Cassandra nodes..."
                    for node in ${SELECTED_NODES}; do
                        echo "Destroying ${node}..."
                        docker stop ${node} || true
                        docker rm -f ${node} || true
                    done
                    
                    echo "Cleaning up network and volumes..."
                    docker network rm cassandra-network || true
                    for i in $(seq 1 ${NUM_NODES}); do
                        docker volume rm cassandra-data-${i} || true
                    done
                    
                    echo "✅ Selected nodes, network, and volumes destroyed"
                '''
            }
        }
        
        stage('Stop Nodes') {
            when {
                expression { params.ACTION == 'stop-all' }
            }
            steps {
                sh '''
                    export PATH="$PATH:~/bin"
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
                expression { params.ACTION == 'start-all' }
            }
            steps {
                sh '''
                    export PATH="$PATH:~/bin"
                    for node in ${SELECTED_NODES}; do
                        echo "▶️  Starting ${node}..."
                        docker start ${node}
                        echo "✅ ${node} started"
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
                    export PATH="$PATH:~/bin"
                    echo "🔄 Restarting selected Cassandra nodes..."
                    for node in ${SELECTED_NODES}; do
                        echo "Restarting ${node}..."
                        docker restart ${node}
                        sleep 10
                        echo "✅ ${node} restarted"
                    done
                '''
            }
        }
        
        stage('Verify Status') {
            when {
                expression { params.ACTION == 'status' || params.ACTION == 'restart-all' || params.ACTION == 'start-all' }
            }
            steps {
                sh '''
                    export PATH="$PATH:~/bin"
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
        }
        failure {
            echo "${params.ACTION} operation failed"
        }
    }
}
