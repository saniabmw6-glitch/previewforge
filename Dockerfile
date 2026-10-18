FROM jenkins/jenkins:lts-jdk17
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
COPY casc/ /usr/share/jenkins/casc/
ENV CASC_JENKINS_CONFIG=/usr/share/jenkins/casc/casc.yaml
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
