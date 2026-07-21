pipeline {
    agent any
    
    options {
        quietPeriod(120)
        // Log-rotator instellingen (max 10 dagen/10 builds)
        buildDiscarder(logRotator(daysToKeepStr: '10', numToKeepStr: '10'))
        
        withFolderProperties()
    }
    
    triggers {
        githubPush()
    }
    
    parameters {
        string(name: 'goals', defaultValue: 'clean install -Dtycho.localArtifacts=ignore -Dservoy.url=file:///data/www/latest/servoy_2026.03/update_site/', trim: false)
    }
    
    environment {
        // Alleen Teams is nu nog nodig
        TEAMS_WEBHOOK = credentials('servoy-teams-webhook')
    }
    
    tools {
        jdk 'Java 21'
        maven 'Maven 3.9.16'
    }
    
    stages {
        stage('Build with Tycho 5') {
            steps {
                configFileProvider([
                    configFile(fileId: 'master_mvn_repo', variable: 'MAVEN_SETTINGS'),
                    configFile(fileId: 'maven_toolchain', variable: 'TOOLCHAIN')
                ]) {
                    sh 'mvn -B -s "$MAVEN_SETTINGS" -t "$TOOLCHAIN" $goals'
                }
            }
        }
        stage('Deploy Client Product') {
            steps {
                sh '''
                mkdir -p /data/www/latest/servoy_${BRANCH}/
                cp com.servoy.eclipse.docgenerator.client.product/target/com.servoy.eclipse.docgenerator.client.product-*.zip /data/www/latest/servoy_${BRANCH}/
                '''
            }
        }
    }
    
    post {
        failure {
            office365ConnectorSend webhookUrl: TEAMS_WEBHOOK, status: 'Failed'
        }
        
        unstable {
            office365ConnectorSend webhookUrl: TEAMS_WEBHOOK, status: 'Unstable'
        }
        
        fixed {
            office365ConnectorSend webhookUrl: TEAMS_WEBHOOK, status: 'Back to Normal'
        }

    }
}