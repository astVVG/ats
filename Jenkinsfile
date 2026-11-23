// CI pipeline: builds and tests the test branch only.
// Deployment arrives in Stage 6 from the prod branch.
pipeline {
    agent any

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        CI = 'true'
        HUSKY = '0'
    }

    stages {
        stage('Tooling check') {
            when { expression { !env.BRANCH_NAME || env.BRANCH_NAME == 'test' } }
            steps {
                sh '''
                    if ! java -version 2>&1 | head -n 1 | grep -Eq 'version "21[."]'; then
                        echo "JDK 21 is required. Found:"
                        java -version
                        exit 1
                    fi
                    case "$(node -v)" in
                        v24*) ;;
                        *) echo "Node 24 is required. Found: $(node -v)"; exit 1 ;;
                    esac
                    java -version
                    node -v
                    npm -v
                    docker version
                '''
            }
        }

        stage('Backend') {
            when { expression { !env.BRANCH_NAME || env.BRANCH_NAME == 'test' } }
            steps {
                dir('backend') {
                    sh './mvnw -B verify'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Frontend') {
            when { expression { !env.BRANCH_NAME || env.BRANCH_NAME == 'test' } }
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm run lint'
                    sh 'npm run test'
                    sh 'npm run build'
                }
            }
        }
    }
}
