// Helper functions loaded by the Jenkinsfile with: load 'ci/preview.groovy'

def deploy(String pr, String image, String host, String sha) {
  withEnv(["PR=${pr}", "IMAGE=${image}", "HOST=${host}", "SHA=${sha}"]) {
    sh '''
      docker rm -f app-pr-${PR} 2>/dev/null || true
      docker run -d --name app-pr-${PR} --network preview \
        --restart unless-stopped \
        -e PR_NUMBER=${PR} -e GIT_SHA=${SHA} \
        --label previewforge.preview=true \
        --label traefik.enable=true \
        --label "traefik.http.routers.pr${PR}.rule=Host(\\`${HOST}\\`)" \
        --label traefik.http.routers.pr${PR}.entrypoints=web \
        --label traefik.http.services.pr${PR}.loadbalancer.server.port=5000 \
        ${IMAGE}
    '''
  }
}

def smokeTest(String pr) {
  withEnv(["PR=${pr}"]) {
    sh '''
      for i in $(seq 1 15); do
        if curl -fsS http://app-pr-${PR}:5000/health; then echo; echo "Smoke test passed"; exit 0; fi
        echo "waiting for app-pr-${PR} ($i/15)..."; sleep 2
      done
      echo "Smoke test FAILED"; docker logs app-pr-${PR} || true; exit 1
    '''
  }
}

def comment(String pr, String host, String sha) {
  withCredentials([usernamePassword(credentialsId: 'github-pat',
                   usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
    withEnv(["PR=${pr}", "HOST=${host}", "SHA=${sha}"]) {
      sh '''
        printf '{"body":"Preview ready: http://%s (commit %s)"}' "$HOST" "$SHA" > comment.json
        curl -fsS -X POST \
          -H "Authorization: Bearer $GH_TOKEN" \
          -H "Accept: application/vnd.github+json" \
          "https://api.github.com/repos/$GITHUB_REPO/issues/$PR/comments" \
          -d @comment.json > /dev/null
        echo "Commented on PR $PR"
      '''
    }
  }
}

return this
