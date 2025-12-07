pipeline {
    agent any
    
    options {
        description('Emergency pipeline for quickly stopping Cassandra containers and checking system resources during critical issues')
    }
    
    parameters {
        choice(
            name: 'ACTION',
            choices: ['stop-all', 'stop-cassandra-only', 'stop-and-remove', 'resource-check'],
            description: 'Emergency action to take'
        )
    }
    
    stages {
        stage('Check System Resources') {
            steps {
                echo 'Checking system resources...'
                sh '''
                    echo "==================================="
                    echo "Current System Load:"
                    echo "==================================="
                    uptime
                    echo ""
                    
                    echo "Memory Usage:"
                    free -h
                    echo ""
                    
                    echo "Docker Container Resources:"
                    docker stats --no-stream
                    echo ""
                    
                    echo "Cassandra Containers:"
                    docker ps --filter "name=cassandra" --format "table {{.Names}}\\t{{.Status}}\\t{{.CPUPerc}}\\t{{.MemUsage}}"
                '''
            }
        }
        
        stage('Stop Cassandra Containers') {
            when {
                expression { params.ACTION == 'stop-all' || params.ACTION == 'stop-cassandra-only' }
            }
            steps {
                echo '🛑 Stopping all Cassandra containers...'
                sh '''
                    echo "Stopping Cassandra nodes..."
                    for node in $(docker ps --filter "name=cassandra-node" --format "{{.Names}}"); do
                        echo "Stopping ${node}..."
                        docker stop ${node} &
                    done
                    
                    # Wait for all stop commands to complete
                    wait
                    
                    echo "✅ All Cassandra nodes stopped"
                    
                    echo ""
                    echo "Remaining containers:"
                    docker ps
                '''
            }
        }
        
        stage('Stop All Docker Containers') {
            when {
                expression { params.ACTION == 'stop-all' }
            }
            steps {
                echo '🛑 Stopping ALL Docker containers...'
                sh '''
                    echo "Stopping all running containers..."
                    docker stop $(docker ps -q) 2>/dev/null || echo "No containers to stop"
                    
                    echo "✅ All containers stopped"
                    docker ps -a
                '''
            }
        }
        
        stage('Stop and Remove') {
            when {
                expression { params.ACTION == 'stop-and-remove' }
            }
            steps {
                echo '🗑️ Stopping and removing Cassandra containers...'
                sh '''
                    echo "Stopping and removing Cassandra nodes..."
                    for node in $(docker ps -a --filter "name=cassandra-node" --format "{{.Names}}"); do
                        echo "Removing ${node}..."
                        docker rm -f ${node} &
                    done
                    
                    wait
                    
                    # Also remove OpsCenter if running
                    docker rm -f opscenter 2>/dev/null || true
                    
                    echo "✅ All Cassandra containers removed"
                    
                    echo ""
                    echo "Cleaning up networks..."
                    docker network rm cassandra-network 2>/dev/null || true
                    
                    echo ""
                    echo "Current system load:"
                    uptime
                '''
            }
        }
        
        stage('Resource Check Only') {
            when {
                expression { params.ACTION == 'resource-check' }
            }
            steps {
                echo 'Resource check completed (see above)'
            }
        }
        
        stage('Post-Action Status') {
            steps {
                sh '''
                    echo ""
                    echo "==================================="
                    echo "System Status After Action:"
                    echo "==================================="
                    uptime
                    echo ""
                    free -h
                    echo ""
                    echo "Remaining containers:"
                    docker ps
                '''
            }
        }
    }
    
    post {
        always {
            echo "Emergency action completed: ${params.ACTION}"
        }
    }
}
