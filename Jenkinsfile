def preview

pipeline {
  agent { label 'docker-agent' }

  options {
    timestamps()
    timeout(time: 20, unit: 'MINUTES')
    disableConcurrentBuilds(abortPrevious: true)
  }

  stages {
    stage('Prepare') {
      steps {
        script {
          env.SHORT_SHA = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
          env.LABEL = env.CHANGE_ID ? "pr-${env.CHANGE_ID}" : env.BRANCH_NAME.replaceAll('[^a-zA-Z0-9_.-]', '-')
          env.PR    = env.CHANGE_ID ?: 'none'
          env.IMAGE = "previewforge-app:${env.LABEL}-${env.SHORT_SHA}"
          env.HOST  = "pr-${env.PR}.preview.localhost"
          preview = load 'ci/preview.groovy'
          echo "Image: ${env.IMAGE}  |  Preview host: ${env.HOST}"
        }
      }
    }

    stage('Set up Python') {
      steps {
        sh '''
          python3 -m venv .venv
          . .venv/bin/activate
          pip install -q -r app/requirements-dev.txt
        '''
      }
    }

    stage('Quality') {
      parallel {
        stage('Lint') {
          steps { sh '. .venv/bin/activate && cd app && flake8 .' }
        }
        stage('Unit tests + coverage') {
          steps {
            sh '''
              . .venv/bin/activate
              cd app
              pytest -q --cov=app --cov-fail-under=80 --junitxml=report.xml
            '''
          }
          post { always { junit allowEmptyResults: true, testResults: 'app/report.xml' } }
        }
      }
    }

    stage('Build image') {
      steps { sh 'docker build -t "$IMAGE" app/' }
    }

    stage('Security scan (Trivy)') {
      steps {
        sh 'trivy image --exit-code 1 --severity CRITICAL --ignore-unfixed --no-progress "$IMAGE"'
      }
    }

    stage('Deploy preview') {
      when { changeRequest() }
      steps { script { preview.deploy(env.PR, env.IMAGE, env.HOST, env.SHORT_SHA) } }
    }

    stage('Smoke test') {
      when { changeRequest() }
      steps { script { preview.smokeTest(env.PR) } }
    }

    stage('Comment on PR') {
      when { changeRequest() }
      steps { script { preview.comment(env.PR, env.HOST, env.SHORT_SHA) } }
    }
  }

  post {
    success { echo "Done. Preview: http://${env.HOST}" }
    failure { echo 'Build failed. See the failed stage above; the previous preview (if any) is untouched.' }
  }
}
